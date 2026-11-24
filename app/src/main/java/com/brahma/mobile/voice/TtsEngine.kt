package com.brahma.mobile.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * Neural-quality speech via the platform TTS. Android 14+ devices usually ship
 * a neural voice; no model download is required here.
 * To swap in Kokoro ONNX later, replace speak()/stop() behind this class.
 */
class TtsEngine(context: Context) {
    private var tts: TextToSpeech? = null
    var onReady: (() -> Unit)? = null

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale.US)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.setLanguage(Locale.getDefault())
                }
                tts?.setSpeechRate(1.05f)
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {}
                    override fun onStop(utteranceId: String?, interrupted: Boolean) {}
                    override fun onError(utteranceId: String?) {}
                })
                onReady?.invoke()
            }
        }
    }

    val isReady: Boolean get() = tts != null

    fun speak(text: String) {
        val id = "brahma_" + System.currentTimeMillis()
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
    }

    fun stop() { tts?.stop() }

    fun shutdown() {
        tts?.shutdown()
        tts = null
    }
}
