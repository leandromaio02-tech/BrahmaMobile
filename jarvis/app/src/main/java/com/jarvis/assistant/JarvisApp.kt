package com.jarvis.assistant

import android.app.Application
import androidx.room.Room
import com.jarvis.assistant.services.NotificationHelper

class JarvisApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        NotificationHelper(this).createChannels()
        container = AppContainer(this)
    }
}

class AppContainer(context: android.content.Context) {
    val db: JarvisDatabase = Room.databaseBuilder(
        context.applicationContext,
        JarvisDatabase::class.java,
        "jarvis.db"
    ).fallbackToDestructiveMigration().build()

    val settings = SettingsStore(context.applicationContext)
    val ai = AiEngine(context.applicationContext, db, settings)
}
