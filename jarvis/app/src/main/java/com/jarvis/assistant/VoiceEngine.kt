package com.jarvis.assistant

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

class VoiceEngine(
    private val context: Context,
    private val onPartial: (String) -> Unit,
    private val onFinal: (String) -> Unit,
    private val onSpeechError: (String) -> Unit,
    private val onSpoken: () -> Unit = {}
) {
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ready = false

    init {
        tts = TextToSpeech(context) { status ->
            ready = status == TextToSpeech.SUCCESS
            tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) { onSpoken() }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) { onSpoken() }
            })
        }
    }

    fun available(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    suspend fun start(language: String) = withContext(Dispatchers.Main) {
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            recognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {
                    onSpeechError(when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> "I did not catch that. Try again."
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required."
                        SpeechRecognizer.ERROR_NETWORK -> "Speech service needs a network connection."
                        else -> "Speech recognition failed (code $error)."
                    })
                }
                override fun onResults(results: Bundle?) {
                    val text = results?.getStringArrayList(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty()
                    if (text.isNotBlank()) onFinal(text)
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    val text = partialResults?.getStringArrayList(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty()
                    if (text.isNotBlank()) onPartial(text)
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        recognizer?.startListening(intent)
    }

    suspend fun stop() = withContext(Dispatchers.Main) { recognizer?.stopListening() }

    fun speak(text: String, voiceId: String, rate: Float, language: String) {
        if (!ready || tts == null) { onSpeechError("Speech playback is not ready yet."); return }
        val locale = runCatching { Locale.forLanguageTag(language) }.getOrDefault(Locale.US)
        tts?.language = locale
        tts?.setSpeechRate(rate.coerceIn(0.3f, 3f))
        if (voiceId.isNotBlank()) {
            val voice = voices().find { it.name == voiceId }
            if (voice != null) tts?.voice = voice
        }
        val params = Bundle().apply { putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "jarvis") }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "jarvis")
    }

    fun stopSpeaking() { tts?.stop() }

    fun voices(): List<Voice> = tts?.voices?.toList()?.filter { !it.isNetworkConnectionRequired } ?: emptyList()

    fun destroy() {
        recognizer?.destroy(); recognizer = null
        tts?.shutdown(); tts = null
    }
}
