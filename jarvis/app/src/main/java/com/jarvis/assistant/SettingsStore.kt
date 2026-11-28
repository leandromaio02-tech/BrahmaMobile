package com.jarvis.assistant

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "jarvis_settings")

class SettingsStore(private val context: Context) {
    private object Keys {
        val DARK = booleanPreferencesKey("dark_mode")
        val PERSONALITY = stringPreferencesKey("personality")
        val VOICE_ID = stringPreferencesKey("voice_id")
        val SPEECH_RATE = floatPreferencesKey("speech_rate")
        val LANGUAGE = stringPreferencesKey("language")
        val WAKE_WORD = booleanPreferencesKey("wake_word")
        val LOW_RESOURCE = booleanPreferencesKey("low_resource")
        val AUTO_CONFIRM = booleanPreferencesKey("auto_confirm")
        val SEARCH_TEMPLATE = stringPreferencesKey("search_template")
        val INTERACTION = booleanPreferencesKey("interaction_enabled")
        val SCREEN = booleanPreferencesKey("screen_enabled")
    }

    private val secure: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "jarvis_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    val flow: Flow<AppSettings> = context.settingsDataStore.data.map { p ->
        AppSettings(
            darkMode = p[Keys.DARK] != false,
            personality = p[Keys.PERSONALITY] ?: DEFAULT_PERSONALITY,
            voiceId = p[Keys.VOICE_ID] ?: "",
            speechRate = p[Keys.SPEECH_RATE] ?: 1.0f,
            language = p[Keys.LANGUAGE] ?: "en-US",
            wakeWord = p[Keys.WAKE_WORD] ?: false,
            lowResource = p[Keys.LOW_RESOURCE] ?: false,
            autoConfirm = p[Keys.AUTO_CONFIRM] ?: false,
            searchTemplate = p[Keys.SEARCH_TEMPLATE] ?: "https://html.duckduckgo.com/html/?q={query}",
            interactionEnabled = p[Keys.INTERACTION] ?: false,
            screenEnabled = p[Keys.SCREEN] ?: false
        )
    }

    suspend fun update(block: suspend (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.settingsDataStore.edit { block(it) }
    }

    suspend fun setDarkMode(value: Boolean) = update { it[Keys.DARK] = value }
    suspend fun setPersonality(value: String) = update { it[Keys.PERSONALITY] = value }
    suspend fun setVoiceId(value: String) = update { it[Keys.VOICE_ID] = value }
    suspend fun setSpeechRate(value: Float) = update { it[Keys.SPEECH_RATE] = value }
    suspend fun setLanguage(value: String) = update { it[Keys.LANGUAGE] = value }
    suspend fun setWakeWord(value: Boolean) = update { it[Keys.WAKE_WORD] = value }
    suspend fun setLowResource(value: Boolean) = update { it[Keys.LOW_RESOURCE] = value }
    suspend fun setAutoConfirm(value: Boolean) = update { it[Keys.AUTO_CONFIRM] = value }
    suspend fun setSearchTemplate(value: String) = update { it[Keys.SEARCH_TEMPLATE] = value }
    suspend fun setInteraction(value: Boolean) = update { it[Keys.INTERACTION] = value }
    suspend fun setScreen(value: Boolean) = update { it[Keys.SCREEN] = value }

    fun putApiKey(providerId: String, value: String) = secure.edit().putString("key_$providerId", value).apply()
    fun getApiKey(providerId: String): String = secure.getString("key_$providerId", "") ?: ""
    fun hasApiKey(providerId: String): Boolean = secure.contains("key_$providerId")
    fun deleteApiKey(providerId: String) = secure.edit().remove("key_$providerId").apply()

    companion object {
        const val DEFAULT_PERSONALITY =
            "You are J.A.R.V.I.S., a professional, intelligent and composed AI assistant. " +
            "Be concise, useful and slightly witty. Never claim an action succeeded unless a tool result confirms it."
    }
}

data class AppSettings(
    val darkMode: Boolean = true,
    val personality: String = SettingsStore.DEFAULT_PERSONALITY,
    val voiceId: String = "",
    val speechRate: Float = 1.0f,
    val language: String = "en-US",
    val wakeWord: Boolean = false,
    val lowResource: Boolean = false,
    val autoConfirm: Boolean = false,
    val searchTemplate: String = "https://html.duckduckgo.com/html/?q={query}",
    val interactionEnabled: Boolean = false,
    val screenEnabled: Boolean = false
)
