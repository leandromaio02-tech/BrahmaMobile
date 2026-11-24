package com.brahma.mobile.util

import android.content.Context

/** Tiny SharedPreferences-backed settings store. No heavy DI, low RAM. */
class Settings(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("brahma_prefs", Context.MODE_PRIVATE)

    var cloudEnabled: Boolean
        get() = prefs.getBoolean("cloud_enabled", false)
        set(value) = prefs.edit().putBoolean("cloud_enabled", value).apply()

    var provider: String
        get() = prefs.getString("provider", "gemini") ?: "gemini"
        set(value) = prefs.edit().putString("provider", value).apply()

    var apiKey: String
        get() = prefs.getString("api_key", "") ?: ""
        set(value) = prefs.edit().putString("api_key", value).apply()

    var openRouterModel: String
        get() = prefs.getString("or_model", "meta-llama/llama-3.1-8b-instruct:free") ?: "meta-llama/llama-3.1-8b-instruct:free"
        set(value) = prefs.edit().putString("or_model", value).apply()

    var modelFile: String
        get() = prefs.getString("model_file", "qwen2.5-1.5b-instruct-q4_k_m.gguf")
            ?: "qwen2.5-1.5b-instruct-q4_k_m.gguf"
        set(value) = prefs.edit().putString("model_file", value).apply()

    var wakeWordEnabled: Boolean
        get() = prefs.getBoolean("wake_enabled", true)
        set(value) = prefs.edit().putBoolean("wake_enabled", value).apply()

    var voiceEnabled: Boolean
        get() = prefs.getBoolean("voice_enabled", true)
        set(value) = prefs.edit().putBoolean("voice_enabled", value).apply()

    var autoWallpaper: Boolean
        get() = prefs.getBoolean("auto_wallpaper", true)
        set(value) = prefs.edit().putBoolean("auto_wallpaper", value).apply()

    var avatarVisible: Boolean
        get() = prefs.getBoolean("avatar_visible", false)
        set(value) = prefs.edit().putBoolean("avatar_visible", value).apply()

    var persona: String
        get() = prefs.getString("persona", "You are Brahma, a concise, warm, witty 3D AI companion on an Android phone. Keep answers under 60 words. Use plain text only.")
            ?: ""
        set(value) = prefs.edit().putString("persona", value).apply()
}
