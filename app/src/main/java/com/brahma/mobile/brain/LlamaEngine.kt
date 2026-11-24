package com.brahma.mobile.brain

import android.content.Context
import dev.ffmpegkit.llama.Llama
import dev.ffmpegkit.llama.LlamaConfig
import dev.ffmpegkit.llama.LlamaModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Real on-device llama.cpp inference via the prebuilt arm64 AAR.
 * Put GGUF weights in the app's external files/models directory.
 */
class LlamaEngine(private val context: Context) : LlmEngine {
    override val name = "On-device llama.cpp"

    @Volatile
    private var model: LlamaModel? = null
    var systemPrompt: String = ""

    fun modelFile(name: String): File = File(context.getExternalFilesDir("models"), name)

    fun modelsDir(): File = context.getExternalFilesDir("models")
        ?: File(context.filesDir, "models")

    suspend fun load(name: String): Boolean = withContext(Dispatchers.IO) {
        val file = modelFile(name)
        if (!file.exists() || file.length() < 1024L) return@withContext false
        val current = model
        if (current != null && current.isLoaded) return@withContext true
        val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)
        val config = LlamaConfig(contextSize = 2048, threads = threads, gpuLayers = 0)
        return@withContext try {
            model = Llama.loadModel(file.absolutePath, config)
            true
        } catch (t: Throwable) {
            false
        }
    }

    override fun isReady(): Boolean = model?.isLoaded == true

    override suspend fun generate(prompt: String, maxTokens: Int): String =
        withContext(Dispatchers.IO) {
            val loaded = model ?: throw IllegalStateException("No GGUF model loaded")
            Llama.complete(
                model = loaded,
                prompt = prompt,
                systemPrompt = systemPrompt,
                maxTokens = maxTokens
            ).text
        }

    fun release() {
        model?.let { Llama.releaseModel(it) }
        model = null
    }
}
