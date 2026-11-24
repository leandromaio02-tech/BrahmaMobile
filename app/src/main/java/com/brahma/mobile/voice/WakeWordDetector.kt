package com.brahma.mobile.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Offline-first wake word. Uses the device's on-device recognizer when present
 * (EXTRA_PREFER_OFFLINE) and watches partial results for the keyword.
 */
class WakeWordDetector(
    context: Context,
    private val keyword: String = "brahma",
    private val onWake: () -> Unit
) : RecognitionListener {
    private val app = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var running = false

    private val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
    }

    fun start() {
        if (running) return
        try {
            recognizer = SpeechRecognizer.createSpeechRecognizer(app).also {
                it.setRecognitionListener(this)
                running = true
                it.startListening(intent)
            }
        } catch (t: Throwable) {
            running = false
        }
    }

    fun stop() {
        running = false
        handler.removeCallbacksAndMessages(null)
        try { recognizer?.stopListening() } catch (t: Throwable) {}
        try { recognizer?.destroy() } catch (t: Throwable) {}
        recognizer = null
    }

    private fun check(bundle: Bundle?) {
        val matches = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: return
        if (matches.any { it.lowercase().contains(keyword) }) {
            running = false
            handler.removeCallbacksAndMessages(null)
            onWake()
        }
    }

    private fun restartSoon() {
        if (!running) return
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ if (running) start() }, 800)
    }

    override fun onPartialResults(bundle: Bundle?) { check(bundle) }
    override fun onResults(bundle: Bundle?) { check(bundle); restartSoon() }
    override fun onReadyForSpeech(params: Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() {}
    override fun onError(error: Int) { restartSoon() }
    override fun onEvent(eventType: Int, params: Bundle?) {}
}
