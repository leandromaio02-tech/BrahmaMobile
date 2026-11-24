package com.brahma.mobile

import android.app.Application
import com.brahma.mobile.brain.Brain
import com.brahma.mobile.tools.ToolRegistry
import com.brahma.mobile.util.Settings
import com.brahma.mobile.voice.VoiceManager

class BrahmaApp : Application() {
    lateinit var settings: Settings
        private set
    lateinit var brain: Brain
        private set
    lateinit var tools: ToolRegistry
        private set
    lateinit var voice: VoiceManager
        private set

    override fun onCreate() {
        super.onCreate()
        settings = Settings(this)
        brain = Brain(this, settings)
        tools = ToolRegistry(this)
        voice = VoiceManager(this, brain, tools, settings)
        brain.warmUpAsync()
    }
}
