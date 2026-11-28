package com.jarvis.assistant

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jarvis.assistant.services.AccessibilityBridge
import com.jarvis.assistant.services.JarvisForegroundService
import com.jarvis.assistant.services.JarvisState
import com.jarvis.assistant.services.ScreenCaptureService
import com.jarvis.assistant.services.ShizukuDetector
import com.jarvis.assistant.services.ShizukuStatus
import com.jarvis.assistant.services.WakeWordService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class JarvisUiState(
    val messages: List<ChatMessage> = emptyList(),
    val streaming: String = "",
    val busy: Boolean = false,
    val toolEvents: List<ToolEvent> = emptyList(),
    val status: String = "Systems idle",
    val model: String = "No provider configured",
    val error: String = "",
    val partial: String = "",
    val listening: Boolean = false,
    val voiceActive: Boolean = false,
    val screenActive: Boolean = false,
    val wakeActive: Boolean = false,
    val interactionEnabled: Boolean = false,
    val providerCount: Int = 0,
    val settings: AppSettings = AppSettings(),
    val providers: List<ProviderEntity> = emptyList(),
    val memories: List<MemoryEntity> = emptyList(),
    val tasks: List<TaskEntity> = emptyList(),
    val reminders: List<ReminderEntity> = emptyList(),
    val automations: List<AutomationEntity> = emptyList(),
    val activity: List<ActivityEntity> = emptyList(),
    val permissions: Boolean = false,
    val shizuku: ShizukuStatus = ShizukuStatus(false, false, false, "Checking")
)

class JarvisViewModel(app: Application) : AndroidViewModel(app) {
    private val container = (app as JarvisApp).container
    private val db = container.db
    private val ai = container.ai
    private val settingsStore = container.settings
    private val voice = VoiceEngine(
        context = app.applicationContext,
        onPartial = { _ui.value = _ui.value.copy(partial = it) },
        onFinal = { text ->
            _ui.value = _ui.value.copy(partial = "", listening = false)
            viewModelScope.launch { send(text, speak = _ui.value.voiceActive) }
        },
        onSpeechError = { setError(it) }
    )

    private val _ui = MutableStateFlow(JarvisUiState())
    val ui: StateFlow<JarvisUiState> = _ui.asStateFlow()

    private val conversationId = "main"
    private var confirm: CompletableDeferred<Boolean>? = null

    init {
        viewModelScope.launch {
            launch {
                db.conversationDao().messages(conversationId).collect { list ->
                    _ui.value = _ui.value.copy(messages = list.map { ChatMessage(it.role, it.content, it.imageBase64) })
                }
            }
            launch { ai.providers.collect { list -> _ui.value = _ui.value.copy(providers = list, providerCount = list.size, model = list.firstOrNull()?.modelId ?: "No provider configured") } }
            launch { db.memoryDao().all().collect { _ui.value = _ui.value.copy(memories = it) } }
            launch { db.taskDao().all().collect { _ui.value = _ui.value.copy(tasks = it) } }
            launch { db.reminderDao().all().collect { _ui.value = _ui.value.copy(reminders = it) } }
            launch { db.automationDao().all().collect { _ui.value = _ui.value.copy(automations = it) } }
            launch { db.activityDao().recent().collect { _ui.value = _ui.value.copy(activity = it) } }
            launch { settingsStore.flow.collect { s -> _ui.value = _ui.value.copy(settings = s) } }
            launch { JarvisState.voice.collect { _ui.value = _ui.value.copy(voiceActive = it) } }
            launch { JarvisState.screen.collect { _ui.value = _ui.value.copy(screenActive = it) } }
            launch { JarvisState.wake.collect { _ui.value = _ui.value.copy(wakeActive = it) } }
            launch { AccessibilityBridge.enabled.collect { _ui.value = _ui.value.copy(interactionEnabled = it) } }
            launch {
                db.conversationDao().upsertConversation(
                    com.jarvis.assistant.ConversationEntity(conversationId, "JARVIS conversations", System.currentTimeMillis())
                )
            }
        }
    }

    private fun setError(message: String) {
        _ui.value = _ui.value.copy(error = message)
    }
    fun setErrorForUi(message: String) = setError(message)
    fun clearError() { _ui.value = _ui.value.copy(error = "") }

    fun refreshShizuku() {
        _ui.value = _ui.value.copy(shizuku = ShizukuDetector.detect(getApplication()))
    }

    suspend fun send(text: String, imageBase64: String? = null, agent: Boolean = false, speak: Boolean = false) {
        if (text.isBlank() && imageBase64 == null) return
        val msg = ChatMessage("user", text, imageBase64)
        _ui.value = _ui.value.copy(messages = _ui.value.messages + msg, streaming = "", busy = true,
            toolEvents = emptyList(), status = if (agent) "Planning mission" else "Thinking")
        persist(msg)
        try {
            val answer = if (agent) {
                ai.runAgent(text, onEvent = { ev ->
                    _ui.value = _ui.value.copy(toolEvents = _ui.value.toolEvents + ev, status = ev.step + " " + ev.status)
                }, onConfirm = { name, detail -> askConfirmation(name, detail) })
            } else {
                val role = classifyTask(text)
                val history = (_ui.value.messages + msg).takeLast(12)
                ai.stream(history, role, onToken = { token ->
                    _ui.value = _ui.value.copy(streaming = _ui.value.streaming + token)
                }).let { result ->
                    if (result.error.isNotBlank()) throw Exception(result.error)
                    result.text
                }
            }
            val final = answer.ifBlank { "I could not produce a verified answer." }
            val assistant = ChatMessage("assistant", final)
            _ui.value = _ui.value.copy(messages = _ui.value.messages + assistant, streaming = "", busy = false, status = "Ready")
            persist(assistant)
            if (speak) speak(final)
        } catch (e: Exception) {
            setError(e.message ?: "The request failed.")
            _ui.value = _ui.value.copy(busy = false, streaming = "", status = "Error")
        }
    }

    private suspend fun askConfirmation(name: String, detail: String): Boolean {
        val s = settingsStore.flow.first()
        if (s.autoConfirm) return true
        val deferred = CompletableDeferred<Boolean>()
        confirm = deferred
        _ui.value = _ui.value.copy(status = "Confirmation required: $name")
        return deferred.await()
    }

    fun confirmPending(allow: Boolean) {
        confirm?.complete(allow)
        confirm = null
        _ui.value = _ui.value.copy(status = if (allow) "Approved" else "Declined")
    }

    private suspend fun persist(msg: ChatMessage) = withContext(Dispatchers.IO) {
        db.conversationDao().insertMessage(
            com.jarvis.assistant.MessageEntity(
                conversationId = conversationId,
                role = msg.role,
                content = msg.content,
                imageBase64 = msg.imageBase64
            )
        )
        db.conversationDao().upsertConversation(
            com.jarvis.assistant.ConversationEntity(conversationId, "JARVIS conversations", System.currentTimeMillis(), _ui.value.model)
        )
    }

    fun voiceVoiceNames(): List<String> = voice.voices().map { it.name }
    fun speak(text: String) {
        val s = _ui.value.settings
        voice.speak(text, s.voiceId, s.speechRate, s.language)
    }
    fun stopSpeaking() = voice.stopSpeaking()

    suspend fun startVoice() {
        val granted = ActivityCompat.checkSelfPermission(getApplication(), android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!granted) { setError("Grant microphone permission in Settings before using voice."); return }
        _ui.value = _ui.value.copy(listening = true, status = "Listening")
        try {
            getApplication<Application>().startForegroundService(Intent(getApplication(), JarvisForegroundService::class.java))
        } catch (_: Exception) {}
        voice.start(_ui.value.settings.language)
    }

    suspend fun stopVoice() {
        voice.stop()
        _ui.value = _ui.value.copy(listening = false, status = "Ready")
        getApplication<Application>().stopService(Intent(getApplication(), JarvisForegroundService::class.java))
    }

    fun startWake() {
        val granted = ActivityCompat.checkSelfPermission(getApplication(), android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!granted) { setError("Microphone permission is required for wake word."); return }
        viewModelScope.launch { settingsStore.setWakeWord(true) }
        try { getApplication<Application>().startForegroundService(Intent(getApplication(), WakeWordService::class.java)) } catch (_: Exception) {}
    }
    fun stopWake() {
        viewModelScope.launch { settingsStore.setWakeWord(false) }
        getApplication<Application>().stopService(Intent(getApplication(), WakeWordService::class.java))
    }

    fun startScreenCapture(data: Intent) {
        ScreenCaptureService.start(getApplication(), data)
        viewModelScope.launch { settingsStore.setScreen(true) }
    }
    fun stopScreenCapture() {
        getApplication<Application>().stopService(Intent(getApplication(), com.jarvis.assistant.services.ScreenCaptureService::class.java))
        viewModelScope.launch { settingsStore.setScreen(false) }
    }

    fun askAboutScreen(question: String) {
        val frame = ScreenCaptureService.latestFrame()
        if (frame == null) { setError("No screen frame captured yet. Start screen sharing and wait a moment."); return }
        val bytes = java.io.ByteArrayOutputStream().also { frame.compress(android.graphics.Bitmap.CompressFormat.JPEG, 78, it) }.toByteArray()
        val base = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        viewModelScope.launch { send(question.ifBlank { "What is happening on my screen?" }, base) }
    }

    suspend fun addProvider(
        name: String, baseUrl: String, apiKey: String, modelId: String, role: String,
        maxTokens: Int, temperature: Double, streaming: Boolean, vision: Boolean,
        tools: Boolean, headers: String, timeoutMs: Long
    ) {
        if (baseUrl.isBlank() || modelId.isBlank()) { setError("Base URL and model ID are required."); return }
        val id = UUID.randomUUID().toString()
        ai.saveProvider(ProviderEntity(id, name.ifBlank { "Provider" }, baseUrl.trim(), modelId.trim(), role,
            maxTokens, temperature, streaming, vision, tools, headers, timeoutMs), apiKey)
        setError("")
    }

    fun deleteProvider(p: ProviderEntity) = viewModelScope.launch { ai.deleteProvider(p) }

    fun testProvider(id: String) = viewModelScope.launch {
        _ui.value = _ui.value.copy(status = "Testing provider")
        val p = ai.listProviders().firstOrNull { it.id == id }
        if (p == null) { setError("Provider not found."); return@launch }
        val ok = ai.testProvider(p)
        _ui.value = _ui.value.copy(status = if (ok.first) "Provider OK" else "Provider failed")
        if (!ok.first) setError(ok.second)
    }

    fun discoverModels(id: String) = viewModelScope.launch {
        val p = ai.listProviders().firstOrNull { it.id == id } ?: return@launch
        try {
            val models = ai.discoverModels(p.baseUrl, settingsStore.getApiKey(p.id), p.customHeaders)
            if (models.isNotEmpty()) ai.saveProvider(p.copy(modelId = models.first()), settingsStore.getApiKey(p.id))
            setError("Discovered ${models.size} models. Using ${models.firstOrNull() ?: "current"}.")
        } catch (e: Exception) { setError("Discovery failed: ${e.message?.take(160)}") }
    }

    fun saveMemory(category: String, title: String, content: String, tags: String, importance: Int) = viewModelScope.launch {
        if (title.isBlank() || content.isBlank()) { setError("Title and content are required."); return@launch }
        val existing = db.memoryDao().relevant(200).firstOrNull { it.title.equals(title, true) && it.content == content }
        if (existing != null) return@launch
        db.memoryDao().insert(MemoryEntity(category = category, title = title, content = content, tags = tags, importance = importance))
    }
    fun updateMemory(m: MemoryEntity) = viewModelScope.launch { db.memoryDao().update(m) }
    fun deleteMemory(m: MemoryEntity) = viewModelScope.launch { db.memoryDao().delete(m) }

    fun exportMemories(): String {
        val arr = JSONArray()
        _ui.value.memories.forEach { m ->
            arr.put(JSONObject().put("category", m.category).put("title", m.title).put("content", m.content)
                .put("tags", m.tags).put("importance", m.importance))
        }
        return arr.toString()
    }

    fun importMemories(json: String): Int {
        return try {
            val arr = JSONArray(json)
            var n = 0
            viewModelScope.launch {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    db.memoryDao().insert(MemoryEntity(
                        category = o.optString("category", "Notes"),
                        title = o.optString("title", "Imported"),
                        content = o.optString("content", ""),
                        tags = o.optString("tags", ""),
                        importance = o.optInt("importance", 3)
                    ))
                    n++
                }
            }
            n
        } catch (e: Exception) { setError("Import failed: ${e.message?.take(120)}"); 0 }
    }

    fun searchMemory(query: String) = viewModelScope.launch {
        if (query.isBlank()) return@launch
        val hits = db.memoryDao().relevant(50).filter {
            it.title.contains(query, true) || it.content.contains(query, true) || it.tags.contains(query, true)
        }
        val answer = if (hits.isEmpty()) "No stored memory matches “$query”."
        else hits.joinToString("\n") { "${it.title}: ${it.content.take(300)}" }
        val msg = ChatMessage("assistant", answer)
        _ui.value = _ui.value.copy(messages = _ui.value.messages + msg)
        persist(msg)
    }

    fun addReminder(title: String, dueAt: Long, repeatMinutes: Long = 0) = viewModelScope.launch {
        if (title.isBlank()) { setError("Reminder title is required."); return@launch }
        val id = db.reminderDao().insert(ReminderEntity(title = title, dueAt = dueAt, repeatMinutes = repeatMinutes))
        scheduleReminder(id.toInt(), title, dueAt)
    }
    fun toggleReminder(r: ReminderEntity) = viewModelScope.launch { db.reminderDao().update(r.copy(enabled = !r.enabled)) }
    fun deleteReminder(r: ReminderEntity) = viewModelScope.launch { db.reminderDao().delete(r) }

    private fun scheduleReminder(id: Int, title: String, dueAt: Long) {
        val delay = (dueAt - System.currentTimeMillis()).coerceAtLeast(0)
        val req = androidx.work.OneTimeWorkRequestBuilder<com.jarvis.assistant.services.ReminderWorker>()
            .setInputData(androidx.work.workDataOf("id" to id, "title" to title))
            .setInitialDelay(delay, java.util.concurrent.TimeUnit.MILLISECONDS).build()
        androidx.work.WorkManager.getInstance(getApplication()).enqueueUniqueWork("reminder_$id", androidx.work.ExistingWorkPolicy.REPLACE, req)
    }

    fun addAutomation(name: String, trigger: String, condition: String, action: String) = viewModelScope.launch {
        if (name.isBlank() || trigger.isBlank() || action.isBlank()) { setError("Name, trigger and action are required."); return@launch }
        db.automationDao().insert(AutomationEntity(name = name, trigger = trigger, condition = condition, action = action))
    }
    fun toggleAutomation(a: AutomationEntity) = viewModelScope.launch { db.automationDao().update(a.copy(enabled = !a.enabled)) }
    fun deleteAutomation(a: AutomationEntity) = viewModelScope.launch { db.automationDao().delete(a) }

    fun setTheme(dark: Boolean) = viewModelScope.launch { settingsStore.setDarkMode(dark) }
    fun setPersonality(v: String) = viewModelScope.launch { settingsStore.setPersonality(v) }
    fun setVoiceId(v: String) = viewModelScope.launch { settingsStore.setVoiceId(v) }
    fun setSpeechRate(v: Float) = viewModelScope.launch { settingsStore.setSpeechRate(v) }
    fun setLanguage(v: String) = viewModelScope.launch { settingsStore.setLanguage(v) }
    fun setLowResource(v: Boolean) = viewModelScope.launch { settingsStore.setLowResource(v) }
    fun setAutoConfirm(v: Boolean) = viewModelScope.launch { settingsStore.setAutoConfirm(v) }
    fun setSearchTemplate(v: String) = viewModelScope.launch { settingsStore.setSearchTemplate(v) }
    fun setInteraction(v: Boolean) = viewModelScope.launch { settingsStore.setInteraction(v) }

    fun setMicPermission(granted: Boolean) { _ui.value = _ui.value.copy(permissions = granted) }

    fun openAccessibilitySettings(context: Context) {
        try { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (_: Exception) { setError("Could not open accessibility settings.") }
    }

    suspend fun readDocument(uri: Uri): Pair<String, String?> = withContext(Dispatchers.IO) {
        val cr = getApplication<Application>().contentResolver
        val mime = cr.getType(uri) ?: "text/plain"
        val name = uri.lastPathSegment ?: "document"
        if (mime.startsWith("image/")) {
            val bytes = cr.openInputStream(uri)?.readBytes() ?: throw Exception("Cannot read image")
            name to android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        } else {
            val text = cr.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: throw Exception("Cannot read file")
            name to text.take(60000)
        }
    }

    fun analyzeDocument(name: String, content: String, image: Boolean) {
        val question = if (image) "Analyse this image from $name. Extract visible text and explain what it shows."
        else "Read this document ($name) and summarize the key points with any important details:\n\n${content.take(12000)}"
        viewModelScope.launch { send(question, if (image) content else null) }
    }

    fun clearConversation() = viewModelScope.launch {
        db.conversationDao().clear(conversationId)
        _ui.value = _ui.value.copy(messages = emptyList(), toolEvents = emptyList())
    }

    override fun onCleared() {
        voice.destroy()
        super.onCleared()
    }
}
