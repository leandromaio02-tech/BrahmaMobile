package com.brahma.mobile.brain

import java.util.Locale

/** Deterministic offline fallback so Brahma always answers without a model. */
class RulesBrain {
    fun answer(input: String): String {
        val q = input.trim().lowercase(Locale.ROOT)
        return when {
            q.isBlank() -> "I'm here. Say 'brahma' and talk to me."
            q.contains("who are you") || q.contains("what are you") ->
                "I'm Brahma, your on-device 3D companion. I can open apps, control volume and brightness, set alarms, read files, and chat — mostly without the net."
            q.contains("what can you do") || q.contains("help") || q.contains("commands") ->
                "Try: open WhatsApp, play lo-fi on YouTube, volume up, brightness 60, flashlight on, alarm in 10 minutes, call mom, read my notes, search android 15, remind me in 5 minutes to stretch, run uptime in Termux."
            q.contains("how are you") -> "Running cool and quiet on your phone. Ready when you are."
            q.contains("thank") -> "Anytime."
            q.contains("time") -> "Your clock knows better than I do — ask me 'time' and I'll say it out loud."
            q.contains("joke") -> "Why did the neural net cross the road? To get to the other gradient."
            q.contains("offline") || q.contains("cloud") ->
                "On-device is the default. Flip the cloud toggle in Settings for Gemini or OpenRouter fallback."
            else -> "I heard: $input. My small local brain has no model loaded yet, so I kept this short. Add a GGUF model in Settings > Brain for full offline chat, or enable cloud fallback."
        }
    }
}
