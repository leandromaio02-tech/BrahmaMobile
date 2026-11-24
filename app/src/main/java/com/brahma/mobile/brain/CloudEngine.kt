package com.brahma.mobile.brain

import com.brahma.mobile.util.Settings
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Cloud fallback: Gemini or OpenRouter. Only used when the toggle is on. */
class CloudEngine(private val settings: Settings) : LlmEngine {
    override val name = "Cloud LLM"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .build()

    override fun isReady(): Boolean = settings.apiKey.isNotBlank()

    override suspend fun generate(prompt: String, maxTokens: Int): String {
        if (!isReady()) throw IllegalStateException("No API key configured")
        return if (settings.provider == "openrouter") openRouter(prompt, maxTokens)
        else gemini(prompt, maxTokens)
    }

    private fun gemini(prompt: String, maxTokens: Int): String {
        val model = "gemini-1.5-flash"
        val parts = JSONArray().put(JSONObject().put("text", prompt))
        val content = JSONObject().put("parts", parts)
        val body = JSONObject()
            .put("contents", JSONArray().put(content))
            .put("generationConfig", JSONObject().put("maxOutputTokens", maxTokens))
            .toString()

        val url = "https://generativelanguage.googleapis.com/v1beta/models/" +
            model + ":generateContent?key=" + settings.apiKey
        val req = Request.Builder()
            .url(url)
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        val text = client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("Gemini HTTP ${resp.code}")
            resp.body?.string().orEmpty()
        }
        val root = JSONObject(text)
        val candidates = root.optJSONArray("candidates")
        if (candidates == null || candidates.length() == 0) return "No response from Gemini."
        val partsOut = candidates.getJSONObject(0).optJSONObject("content")?.optJSONArray("parts")
        val sb = StringBuilder()
        if (partsOut != null) {
            for (i in 0 until partsOut.length()) {
                sb.append(partsOut.getJSONObject(i).optString("text", ""))
            }
        }
        return sb.toString().ifBlank { "No response from Gemini." }
    }

    private fun openRouter(prompt: String, maxTokens: Int): String {
        val body = JSONObject()
            .put("model", settings.openRouterModel)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
            .put("max_tokens", maxTokens)
            .toString()
        val req = Request.Builder()
            .url("https://openrouter.ai/api/v1/chat/completions")
            .header("Authorization", "Bearer ${settings.apiKey}")
            .header("HTTP-Referer", "https://github.com/brahma-mobile")
            .header("X-Title", "Brahma Mobile")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        val text = client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("OpenRouter HTTP ${resp.code}")
            resp.body?.string().orEmpty()
        }
        val choices = JSONObject(text).optJSONArray("choices")
        if (choices == null || choices.length() == 0) return "No response from OpenRouter."
        return choices.getJSONObject(0).optJSONObject("message")?.optString("content", "")
            ?.ifBlank { "No response from OpenRouter." } ?: "No response from OpenRouter."
    }
}
