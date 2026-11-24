package com.brahma.mobile.brain

import android.content.Context
import com.brahma.mobile.util.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Chooses the best available engine and always returns something quickly. */
class Brain(private val context: Context, private val settings: Settings) {
    private val llama = LlamaEngine(context)
    private val cloud = CloudEngine(settings)
    private val rules = RulesBrain()

    init { llama.systemPrompt = settings.persona }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun warmUpAsync() {
        scope.launch(Dispatchers.IO) { llama.load(settings.modelFile) }
    }

    fun engineName(): String = when {
        settings.cloudEnabled && cloud.isReady() -> "Cloud · ${settings.provider}"
        llama.isReady() -> "On-device · llama.cpp"
        else -> "Offline · rules"
    }

    fun isLocalReady(): Boolean = llama.isReady()

    fun localModelInfo(): String = buildString {
        val f = llama.modelFile(settings.modelFile)
        append(if (f.exists()) "GGUF: ${f.name} (${f.length() / 1_048_576} MB)"
        else "No GGUF found in: ${llama.modelsDir().absolutePath}")
        append("\nLocal engine: ${if (llama.isReady()) "ready" else "waiting for model"}")
    }

    fun respond(input: String, onResult: (String) -> Unit) {
        scope.launch(Dispatchers.IO) {
            val prompt = "${settings.persona}\n\nUser: $input\nBrahma:"
            val answer = runCatching {
                when {
                    settings.cloudEnabled && cloud.isReady() -> cloud.generate(prompt)
                    llama.isReady() -> llama.generate(prompt)
                    else -> rules.answer(input)
                }
            }.getOrElse { rules.answer(input) }
            withContext(Dispatchers.Main) { onResult(answer) }
        }
    }
}
