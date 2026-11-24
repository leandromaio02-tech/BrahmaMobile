package com.brahma.mobile

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.brahma.mobile.overlay.FloatingAvatarService
import com.brahma.mobile.ui.BrahmaTheme
import com.brahma.mobile.ui.MainScreen
import com.brahma.mobile.util.brahmaApp
import com.brahma.mobile.wallpaper.ApodManager
import com.brahma.mobile.wallpaper.ApodWorker
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    private lateinit var apod: ApodManager
    private var briefing by mutableStateOf("No briefing yet — tap 'Refresh APOD'.")
    private var modelRefresh by mutableStateOf(0)

    private lateinit var micLauncher: ActivityResultLauncher<String>
    private lateinit var notifLauncher: ActivityResultLauncher<String>
    private lateinit var modelLauncher: ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        apod = ApodManager(this)

        micLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) brahmaApp.voice.start()
        }
        notifLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
        modelLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri == null) return@registerForActivityResult
            lifecycleScope.launch {
                val dir = File(getExternalFilesDir("models"), "")
                dir.mkdirs()
                val target = File(dir, "qwen2.5-1.5b-instruct-q4_k_m.gguf")
                try {
                    contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(target).use { out ->
                            val buf = ByteArray(65_536)
                            while (true) {
                                val n = input.read(buf)
                                if (n <= 0) break
                                out.write(buf, 0, n)
                            }
                        }
                    }
                    brahmaApp.settings.modelFile = target.name
                    modelRefresh++
                    brahmaApp.brain.warmUpAsync()
                    briefing = "Model imported (${target.length() / 1_048_576} MB). Loading on device…"
                } catch (t: Throwable) {
                    briefing = "Model import failed: ${t.javaClass.simpleName}"
                }
            }
        }

        briefing = apod.briefing()

        scheduleWallpaper()

        setContent {
            BrahmaTheme {
                MainScreen(
                    settings = brahmaApp.settings,
                    brain = brahmaApp.brain,
                    voice = brahmaApp.voice,
                    briefing = briefing,
                    modelRefresh = modelRefresh,
                    onMicPermission = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                    onOverlayPermission = {
                        if (!AndroidSettings.canDrawOverlays(this)) {
                            startActivity(Intent(
                                AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:$packageName")
                            ))
                        }
                    },
                    onNotificationPermission = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    onAccessibility = { startActivity(Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS)) },
                    onShowAvatar = {
                        brahmaApp.settings.avatarVisible = true
                        startAvatar()
                    },
                    onHideAvatar = {
                        brahmaApp.settings.avatarVisible = false
                        startService(Intent(this, FloatingAvatarService::class.java).apply {
                            action = FloatingAvatarService.ACTION_STOP
                        })
                    },
                    onRefreshApod = {
                        lifecycleScope.launch { briefing = apod.refreshWallpaper() }
                    },
                    onImportModel = { modelLauncher.launch("*/*") }
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (brahmaApp.settings.wakeWordEnabled && brahmaApp.voice.hasMicPermission()) {
            brahmaApp.voice.start()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val text = intent.getStringExtra(FloatingAvatarService.EXTRA_TEXT)
        if (!text.isNullOrBlank()) brahmaApp.voice.speak(text)
    }

    private fun startAvatar() {
        if (!AndroidSettings.canDrawOverlays(this)) {
            startActivity(Intent(
                AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            ))
            return
        }
        val intent = Intent(this, FloatingAvatarService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent)
            else startService(intent)
        } catch (t: Throwable) {
            brahmaApp.voice.showNoOverlayHint()
        }
    }

    private fun scheduleWallpaper() {
        if (!brahmaApp.settings.autoWallpaper) return
        val request = PeriodicWorkRequestBuilder<ApodWorker>(1, TimeUnit.DAYS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "brahma_apod", ExistingPeriodicWorkPolicy.KEEP, request
        )
    }
}
