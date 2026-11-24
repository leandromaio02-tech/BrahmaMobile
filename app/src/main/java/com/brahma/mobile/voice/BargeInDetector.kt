package com.brahma.mobile.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlin.math.abs

/** Dependency-free barge-in: listens for a short burst of sound and interrupts TTS. */
class BargeInDetector(private val context: Context, private val onSound: () -> Unit) {
    private var thread: Thread? = null
    @Volatile private var active = false

    fun start() {
        if (active) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) return
        active = true
        thread = Thread {
            val rate = 16_000
            val size = AudioRecord.getMinBufferSize(
                rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            if (size <= 0) { active = false; return@Thread }
            var recorder: AudioRecord? = null
            try {
                recorder = AudioRecord(
                    MediaRecorder.AudioSource.MIC, rate,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, size
                )
                recorder.startRecording()
                val data = ShortArray(size / 2)
                var loud = 0
                val deadline = System.currentTimeMillis() + 8_000L
                while (active && System.currentTimeMillis() < deadline) {
                    val n = recorder.read(data, 0, data.size)
                    if (n <= 0) continue
                    var peak = 0
                    for (i in 0 until n) peak = maxOf(peak, abs(data[i].toInt()))
                    loud = if (peak > 1200) loud + 1 else 0
                    if (loud >= 2) {
                        onSound()
                        break
                    }
                }
            } catch (t: Throwable) {
                // Mic busy or denied; barge-in simply won't fire this round.
            } finally {
                try { recorder?.stop() } catch (t: Throwable) {}
                try { recorder?.release() } catch (t: Throwable) {}
                active = false
            }
        }.also { it.isDaemon = true; it.start() }
    }

    fun stop() { active = false }
}
