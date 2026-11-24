package com.brahma.mobile.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import com.brahma.mobile.brain.Brain
import com.brahma.mobile.reflex.ReflexEngine
import com.brahma.mobile.tools.ToolRegistry
import com.brahma.mobile.util.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

/** Ties together wake word, STT, barge-in, reflexes and the brain. */
class VoiceManager(
    context: Context,
    private val brain: Brain,
    tools: ToolRegistry,
    private val settings: Settings
) {
    private val app = context.applicationContext
    val tts = TtsEngine(app)
    private val handler = Handler(Looper.getMainLooper())
    private val statusFlow = MutableStateFlow("Idle")
    private var wake: WakeWordDetector? = null
    private var recognizer: SpeechRecognizer? = null
    private var bargeIn: BargeInDetector? = null
    private var wakeRequests = 0

    private val reflex = ReflexEngine(app, tools, { text -> speak(text) }) {
        stopAudio()
    }

    var onTranscript: ((String) -> Unit)? = null

    fun status(): StateFlow<String> = statusFlow

    fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(app, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    fun showNoOverlayHint() {
        statusFlow.value = "Overlay permission denied — grant it in Settings"
    }

    fun start() {
        if (!settings.wakeWordEnabled || !hasMicPermission()) return
        if (wake != null) return
        wakeRequests++
        wake = WakeWordDetector(app) { onWake() }.also { it.start() }
        statusFlow.value = "Say 'brahma'…"
    }

    fun stop() {
        wake?.stop(); wake = null
        recognizer?.stopListening(); recognizer?.destroy(); recognizer = null
        bargeIn?.stop(); bargeIn = null
        tts.stop()
        statusFlow.value = "Idle"
    }

    private fun onWake() {
        wake?.stop(); wake = null
        statusFlow.value = "Wake word heard"
        listenOnce()
    }

    fun listenOnce() {
        if (!hasMicPermission()) {
            statusFlow.value = "Mic permission needed"
            return
        }
        try {
            recognizer?.destroy()
            recognizer = SpeechRecognizer.createSpeechRecognizer(app).also { rec ->
                rec.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        statusFlow.value = "Listening…"
                    }
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() { statusFlow.value = "Thinking…" }
                    override fun onResults(results: Bundle?) {
                        val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull().orEmpty()
                        cleanupRecognizer()
                        if (text.isNotBlank()) process(text)
                        else { statusFlow.value = "Didn't catch that"; restartWakeSoon() }
                    }
                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onError(error: Int) {
                        cleanupRecognizer()
                        statusFlow.value = "Mic error $error"
                        restartWakeSoon()
                    }
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                    putExtra(RecognizerIntent.EXTRA_PROMPT, "Brahma is listening…")
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                }
                rec.startListening(intent)
            }
        } catch (t: Throwable) {
            statusFlow.value = "Speech unavailable"
            restartWakeSoon()
        }
    }

    private fun cleanupRecognizer() {
        try { recognizer?.stopListening() } catch (t: Throwable) {}
        try { recognizer?.destroy() } catch (t: Throwable) {}
        recognizer = null
    }

    fun process(text: String) {
        onTranscript?.invoke(text)
        statusFlow.value = "Thinking…"
        val handled = reflex.tryHandle(text)
        if (!handled) {
            brain.respond(text) { answer -> speak(answer) }
        }
    }

    fun speak(text: String) {
        if (text.isBlank()) { restartWakeSoon(); return }
        if (!settings.voiceEnabled) {
            statusFlow.value = text.take(120)
            restartWakeSoon()
            return
        }
        statusFlow.value = "Speaking…"
        tts.speak(text)
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({
            bargeIn?.stop(); bargeIn = null
            restartWakeSoon()
        }, estimateSpeechMs(text))
        handler.postDelayed({
            bargeIn = BargeInDetector(app) {
                stopAudio()
                listenOnce()
            }.also { it.start() }
        }, 350)
    }

    private fun stopAudio() {
        handler.removeCallbacksAndMessages(null)
        bargeIn?.stop(); bargeIn = null
        tts.stop()
        statusFlow.value = "Interrupted"
    }

    private fun restartWakeSoon() {
        if (!settings.wakeWordEnabled || !hasMicPermission()) return
        handler.postDelayed({
            if (wake == null) {
                wake = WakeWordDetector(app) { onWake() }.also { it.start() }
                statusFlow.value = "Say 'brahma'…"
            }
        }, 400)
    }

    private fun estimateSpeechMs(text: String): Long =
        (text.split(Regex("\\s+")).size * 380L).coerceIn(2500L, 30_000L)
}
