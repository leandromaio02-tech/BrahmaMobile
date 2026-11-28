package com.jarvis.assistant

import android.content.Context
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.jarvis.assistant.services.ReminderWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

fun classifyTask(text: String): String {
    val t = text.lowercase()
    return when {
        Regex("\\b(code|bug|compile|kotlin|java|python|function|script|debug)\\b").containsMatchIn(t) -> "coding"
        Regex("\\b(analyze|compare|reason|plan|complex|why|evaluate|summarize research)\\b").containsMatchIn(t) -> "reasoning"
        Regex("\\b(screen|screenshot|image|photo|picture|diagram|chart|what is on my)\\b").containsMatchIn(t) -> "vision"
        Regex("\\b(transcribe|audio|record|speech to text)\\b").containsMatchIn(t) -> "speech"
        else -> "fast"
    }
}

fun extractSseText(line: String): String? {
    if (!line.startsWith("data:")) return null
    val data = line.removePrefix("data:").trim()
    if (data == "[DONE]" || data.isEmpty()) return null
    return try {
        val root = JSONObject(data)
        val choices = root.optJSONArray("choices") ?: return null
        val sb = StringBuilder()
        for (i in 0 until choices.length()) {
            val delta = choices.optJSONObject(i)?.optJSONObject("delta") ?: continue
            val token = delta.optString("content", "")
            if (!token.isNullOrEmpty()) sb.append(token)
        }
        sb.toString().ifEmpty { null }
    } catch (e: Exception) { null }
}

fun chatEndpoint(baseUrl: String): String {
    val b = baseUrl.trim().trimEnd('/')
    return when {
        b.contains("/chat/completions") -> b
        b.endsWith("/v1") -> "$b/chat/completions"
        else -> "$b/v1/chat/completions"
    }
}

data class StreamResult(val text: String, val latencyMs: Long, val error: String = "")

data class ToolOutcome(val name: String, val ok: Boolean, val detail: String)

class AiEngine(
    private val context: Context,
    private val db: JarvisDatabase,
    private val settings: SettingsStore
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    val providers: Flow<List<ProviderEntity>> = db.providerDao().all()

    suspend fun listProviders(): List<ProviderEntity> = withContext(Dispatchers.IO) { db.providerDao().list() }

    suspend fun saveProvider(entity: ProviderEntity, apiKey: String) = withContext(Dispatchers.IO) {
        db.providerDao().upsert(entity)
        settings.putApiKey(entity.id, apiKey)
    }

    suspend fun deleteProvider(entity: ProviderEntity) = withContext(Dispatchers.IO) {
        db.providerDao().delete(entity)
        settings.deleteApiKey(entity.id)
    }

    suspend fun discoverModels(baseUrl: String, apiKey: String, headers: String): List<String> = withContext(Dispatchers.IO) {
        val url = baseUrl.trim().trimEnd('/').let { if (it.endsWith("/v1")) "$it/models" else "$it/v1/models" }
        val req = requestBuilder(url, apiKey, headers).get().build()
        client.newCall(req).execute().use { res ->
            val body = res.body?.string().orEmpty()
            if (!res.isSuccessful) throw Exception("HTTP ${res.code}: ${body.take(180)}")
            val root = JSONObject(body)
            val arr = root.optJSONArray("data") ?: JSONArray()
            (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.optString("id")?.takeIf { id -> id.isNotEmpty() } }
        }
    }

    private fun requestBuilder(url: String, apiKey: String, headers: String): Request.Builder {
        val b = Request.Builder().url(url).header("Accept", "application/json")
        if (apiKey.isNotBlank()) b.header("Authorization", "Bearer $apiKey")
        try {
            val h = JSONObject(headers.ifBlank { "{}" })
            h.keys().forEach { key -> b.header(key, h.getString(key)) }
        } catch (_: Exception) {}
        return b
    }

    suspend fun testProvider(provider: ProviderEntity): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val key = settings.getApiKey(provider.id)
        val msg = listOf(ChatMessage("user", "Reply with the single word: OK"))
        val result = stream(msg, provider.role, forced = provider, onToken = {})
        if (result.error.isBlank()) true to result.text.take(120)
        else false to result.error
    }

    suspend fun stream(
        messages: List<ChatMessage>,
        role: String = "fast",
        forced: ProviderEntity? = null,
        onToken: (String) -> Unit
    ): StreamResult = withContext(Dispatchers.IO) {
        val all = db.providerDao().list()
        val chosen = forced ?: all.firstOrNull { it.role == role && it.lastError.isBlank() }
            ?: all.firstOrNull { it.lastError.isBlank() }
            ?: all.firstOrNull()
        if (chosen == null) return@withContext StreamResult("", 0, "No AI provider configured. Open Model Center and add an OpenAI-compatible endpoint.")

        val key = settings.getApiKey(chosen.id)
        val url = chatEndpoint(chosen.baseUrl)
        val payload = JSONObject().apply {
            put("model", chosen.modelId)
            put("stream", chosen.streaming)
            put("max_tokens", chosen.maxTokens.coerceAtLeast(64))
            put("temperature", chosen.temperature.coerceIn(0.0, 2.0))
            put("messages", JSONArray().apply {
                messages.forEach { m ->
                    val obj = JSONObject().put("role", m.role)
                    val image = m.imageBase64
                    if (!image.isNullOrBlank() && chosen.supportsVision) {
                        obj.put("content", JSONArray()
                            .put(JSONObject().put("type", "text").put("text", m.content))
                            .put(JSONObject().put("type", "image_url")
                                .put("image_url", JSONObject().put("url", "data:image/jpeg;base64,$image"))))
                    } else {
                        obj.put("content", m.content)
                    }
                    put(obj)
                }
            })
        }
        val body = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val req = requestBuilder(url, key, chosen.customHeaders)
            .header("Content-Type", "application/json")
            .post(body).build()

        val start = System.currentTimeMillis()
        val sb = StringBuilder()
        try {
            client.newCall(req).execute().use { res ->
                if (!res.isSuccessful) {
                    val err = res.body?.string().orEmpty().take(300)
                    db.providerDao().update(chosen.copy(lastError = "HTTP ${res.code}: $err", latencyMs = System.currentTimeMillis() - start))
                    return@withContext StreamResult("", System.currentTimeMillis() - start, "Provider error: HTTP ${res.code}. $err")
                }
                if (chosen.streaming) {
                    val source = res.body?.source()
                    if (source == null) {
                        val text = res.body?.string().orEmpty()
                        return@withContext StreamResult(text, System.currentTimeMillis() - start, "")
                    }
                    while (true) {
                        val line = source.readUtf8Line() ?: break
                        val token = extractSseText(line) ?: continue
                        sb.append(token)
                        onToken(token)
                    }
                } else {
                    val root = JSONObject(res.body?.string().orEmpty())
                    val text = root.optJSONArray("choices")?.optJSONObject(0)
                        ?.optJSONObject("message")?.optString("content", "") ?: ""
                    sb.append(text)
                    onToken(text)
                }
            }
            val latency = System.currentTimeMillis() - start
            db.providerDao().update(chosen.copy(lastError = "", latencyMs = latency))
            StreamResult(sb.toString(), latency)
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - start
            val msg = e.message ?: "Network error"
            db.providerDao().update(chosen.copy(lastError = msg.take(240), latencyMs = latency))
            StreamResult(sb.toString(), latency, msg.take(240))
        }
    }

    suspend fun runAgent(
        goal: String,
        onEvent: (ToolEvent) -> Unit,
        onConfirm: suspend (String, String) -> Boolean
    ): String = withContext(Dispatchers.IO) {
        val settingsNow = settings.flow
        var autoConfirm = false
        // Read a single snapshot without collecting forever.
        kotlinx.coroutines.flow.first(settingsNow).let { autoConfirm = it.autoConfirm }
        val steps = plan(goal)
        val observations = mutableListOf<String>()
        onEvent(ToolEvent("Plan", "running", "${steps.size} steps prepared"))
        steps.forEachIndexed { index, step ->
            onEvent(ToolEvent(step, "running", "Step ${index + 1} of ${steps.size}"))
            val outcome = executeTool(step, goal, autoConfirm, onConfirm)
            observations.add("${step}: ${outcome.detail}")
            onEvent(ToolEvent(step, if (outcome.ok) "done" else "failed", outcome.detail.take(240)))
        }
        db.activityDao().insert(ActivityEntity("agent", goal.take(200)))
        val finalMessages = listOf(
            ChatMessage("system", SettingsStore.DEFAULT_PERSONALITY + "\n\nComplete the objective using the verified observations. Never invent results."),
            ChatMessage("user", "Objective: $goal\n\nObservations:\n" + observations.joinToString("\n") + "\n\nWrite the final answer.")
        )
        val result = stream(finalMessages, classifyTask(goal))
        if (result.error.isNotBlank()) "I completed the steps, but the final report failed: ${result.error}\n\nObserved:\n" + observations.joinToString("\n")
        else result.text
    }

    private fun plan(goal: String): List<String> {
        val t = goal.lowercase()
        val steps = mutableListOf<String>()
        if (Regex("\\b(search|find|research|latest|information|news|docs|documentation)\\b").containsMatchIn(t)) steps.add("Web research")
        if (Regex("\\b(remember|memory|yesterday|discussed|saved|project)\\b").containsMatchIn(t)) steps.add("Memory lookup")
        if (Regex("\\b(screen|screenshot|visible|display)\\b").containsMatchIn(t)) steps.add("Screen analysis")
        if (Regex("\\b(remind|reminder|schedule|later|tomorrow)\\b").containsMatchIn(t)) steps.add("Set reminder")
        if (Regex("\\b(open|launch|navigate|tap|click|settings)\\b").containsMatchIn(t)) steps.add("Interface action")
        if (steps.isEmpty()) steps.add("Reasoning")
        steps.add("Compose report")
        return steps
    }

    private suspend fun executeTool(
        step: String,
        goal: String,
        autoConfirm: Boolean,
        onConfirm: suspend (String, String) -> Boolean
    ): ToolOutcome = when (step) {
        "Web research" -> {
            val query = goal.replace(Regex("(?i)^(jarvis,?\\s*)?(search|find|research|look up)\\s+"), "").take(160)
            webSearch(query, kotlinx.coroutines.flow.first(settings.flow).searchTemplate)
        }
        "Memory lookup" -> {
            val q = goal.take(120)
            val hits = db.memoryDao().relevant(8).filter {
                it.title.contains(q, true) || it.content.contains(q, true) || it.tags.contains(q, true)
            }
            ToolOutcome("memory", true, if (hits.isEmpty()) "No stored memory matched the request."
            else hits.joinToString("\n") { "${it.title}: ${it.content.take(200)}" })
        }
        "Screen analysis" -> {
            val bitmap = com.jarvis.assistant.services.ScreenCaptureService.latestFrame()
            if (bitmap == null) ToolOutcome("screen", false, "No authorized screen capture is active. Start screen sharing first.")
            else {
                val bytes = java.io.ByteArrayOutputStream().also { bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 78, it) }.toByteArray()
                val base = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                val res = stream(listOf(ChatMessage("user", "Describe this screen precisely. Read visible text and explain the current state.", base)), "vision")
                ToolOutcome("screen", res.error.isBlank(), res.text.ifBlank { res.error })
            }
        }
        "Set reminder" -> {
            val title = goal.replace(Regex("(?i)^(jarvis,?\\s*)?(remind me to|remind me|reminder:)\\s*"), "", ignoreCase = true).take(120)
            val due = System.currentTimeMillis() + 60 * 60 * 1000L
            val id = db.reminderDao().insert(ReminderEntity(title = title, dueAt = due))
            val req = OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInputData(workDataOf("id" to id.toInt(), "title" to title))
                .setInitialDelay(60, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueue(req)
            ToolOutcome("reminder", true, "Reminder saved for one hour from now: $title")
        }
        "Interface action" -> {
            val detail = "Perform an interface action derived from: ${goal.take(100)}"
            if (!autoConfirm && !onConfirm("Interface action", detail)) ToolOutcome("interface", false, "You declined the action preview. Nothing was changed.")
            else {
                com.jarvis.assistant.services.AccessibilityBridge.enqueue(goal)
                ToolOutcome("interface", true, "Action queued to the authorized accessibility service.")
            }
        }
        else -> ToolOutcome("reasoning", true, "Analysed the objective: ${goal.take(160)}")
    }

    private fun webSearch(query: String, template: String): ToolOutcome {
        val url = template.replace("{query}", java.net.URLEncoder.encode(query, "UTF-8"))
        return try {
            val req = Request.Builder().url(url).header("User-Agent", "JarvisAndroid/1.0").build()
            client.newCall(req).execute().use { res ->
                val html = res.body?.string().orEmpty()
                val results = parseSearchResults(html)
                if (results.isEmpty()) ToolOutcome("web", false, "Search returned no readable results. Check the search endpoint in Settings.")
                else ToolOutcome("web", true, results.joinToString("\n") { "${it.first}: ${it.second}" })
            }
        } catch (e: Exception) {
            ToolOutcome("web", false, "Search failed: ${e.message?.take(120)}")
        }
    }

    private fun parseSearchResults(html: String): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        val link = Regex("""href="(https?://[^"]+)"[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)
        val clean = Regex("<[^>]+>")
        for (m in link.findAll(html)) {
            val title = clean.replace(m.groupValues[2], "").trim()
            if (title.length > 12 && out.size < 6) out.add(title.take(120) to m.groupValues[1].take(200))
        }
        return out
    }
}

