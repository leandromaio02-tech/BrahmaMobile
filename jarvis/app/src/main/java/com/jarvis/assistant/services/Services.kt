package com.jarvis.assistant.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jarvis.assistant.MainActivity
import com.jarvis.assistant.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue

object JarvisState {
    private val _voice = MutableStateFlow(false)
    val voice: StateFlow<Boolean> = _voice
    private val _screen = MutableStateFlow(false)
    val screen: StateFlow<Boolean> = _screen
    private val _wake = MutableStateFlow(false)
    val wake: StateFlow<Boolean> = _wake
    fun voice(v: Boolean) { _voice.value = v }
    fun screen(v: Boolean) { _screen.value = v }
    fun wake(v: Boolean) { _wake.value = v }
}

class NotificationHelper(private val context: Context) {
    fun createChannels() {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val specs = listOf(
            CHANNEL_VOICE to context.getString(R.string.channel_voice),
            CHANNEL_SCREEN to context.getString(R.string.channel_screen),
            CHANNEL_TASKS to context.getString(R.string.channel_tasks),
            CHANNEL_REMINDERS to context.getString(R.string.channel_reminders)
        )
        specs.forEach { (id, name) ->
            val existing = manager.getNotificationChannel(id)
            if (existing == null) {
                manager.createNotificationChannel(NotificationChannel(id, name, NotificationManager.IMPORTANCE_LOW))
            }
        }
    }

    fun ongoing(channel: String, title: String, text: String, stopAction: String? = null): Notification {
        val open = PendingIntent.getActivity(
            context, 1, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val b = android.app.Notification.Builder(context, channel)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
        if (stopAction != null) {
            val stop = PendingIntent.getBroadcast(
                context, 2, Intent(stopAction).setPackage(context.packageName),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            b.addAction(android.R.drawable.ic_delete, "Stop", stop)
        }
        return b.build()
    }

    fun notify(channel: String, title: String, text: String, id: Int) {
        createChannels()
        val open = PendingIntent.getActivity(
            context, 3, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = android.app.Notification.Builder(context, channel)
            .setContentTitle(title).setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java)?.notify(id, n)
    }

    companion object {
        const val CHANNEL_VOICE = "jarvis_voice"
        const val CHANNEL_SCREEN = "jarvis_screen"
        const val CHANNEL_TASKS = "jarvis_tasks"
        const val CHANNEL_REMINDERS = "jarvis_reminders"
        const val ACTION_STOP_VOICE = "com.jarvis.assistant.STOP_VOICE"
        const val ACTION_STOP_SCREEN = "com.jarvis.assistant.STOP_SCREEN"
        const val ACTION_STOP_WAKE = "com.jarvis.assistant.STOP_WAKE"
    }
}

class StopActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            NotificationHelper.ACTION_STOP_VOICE -> context.stopService(Intent(context, JarvisForegroundService::class.java))
            NotificationHelper.ACTION_STOP_SCREEN -> context.stopService(Intent(context, ScreenCaptureService::class.java))
            NotificationHelper.ACTION_STOP_WAKE -> context.stopService(Intent(context, WakeWordService::class.java))
        }
    }
}

class JarvisForegroundService : Service() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "stop") { stopSelf(); return START_NOT_STICKY }
        JarvisState.voice(true)
        startForeground(101, NotificationHelper(this).ongoing(
            NotificationHelper.CHANNEL_VOICE, "JARVIS voice session",
            "Listening and speaking. Tap Stop to end.", NotificationHelper.ACTION_STOP_VOICE))
        return START_STICKY
    }
    override fun onDestroy() { JarvisState.voice(false); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
}

class WakeWordService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: android.speech.SpeechRecognizer? = null
    private var listening = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "stop") { stopSelf(); return START_NOT_STICKY }
        JarvisState.wake(true)
        startForeground(103, NotificationHelper(this).ongoing(
            NotificationHelper.CHANNEL_VOICE, "Hey Jarvis is active",
            "Battery-conscious wake detection. Tap Stop to end.", NotificationHelper.ACTION_STOP_WAKE))
        startLoop()
        return START_STICKY
    }

    private fun startLoop() {
        if (androidx.core.app.ActivityCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            NotificationHelper(this).notify(NotificationHelper.CHANNEL_VOICE, "Wake word unavailable", "Microphone permission is not granted.", 401)
            stopSelf(); return
        }
        recognizer = android.speech.SpeechRecognizer.createSpeechRecognizer(this)
        val intent = Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(android.speech.RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(android.speech.RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        recognizer?.setRecognitionListener(object : android.speech.RecognitionListener {
            override fun onReadyForSpeech(params: android.os.Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { listening = false }
            override fun onError(error: Int) { listening = false; handler.postDelayed({ startLoop() }, 1500) }
            override fun onResults(results: android.os.Bundle?) {
                listening = false
                val heard = results?.getStringArrayList(android.speech.RecognizerIntent.RESULT_RECOGNIZED_TEXT)?.firstOrNull().orEmpty()
                if (heard.contains("hey jarvis", true)) {
                    NotificationHelper(this@WakeWordService).notify(NotificationHelper.CHANNEL_VOICE, "Hey Jarvis heard", "Opening the assistant.", 402)
                    startActivity(Intent(this@WakeWordService, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    stopSelf()
                } else handler.postDelayed({ startLoop() }, 600)
            }
            override fun onPartialResults(partialResults: android.os.Bundle?) {}
            override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
        })
        listening = true
        recognizer?.startListening(intent)
    }

    override fun onDestroy() { recognizer?.destroy(); JarvisState.wake(false); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
}

class ScreenCaptureService : Service() {
    private var captureWidth = 0
    private var captureHeight = 0
    private var projection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "stop") { stopSelf(); return START_NOT_STICKY }
        val data = intent?.getParcelableExtra<Intent>("data")
        if (data == null) { stopSelf(); return START_NOT_STICKY }
        val manager = getSystemService(MediaProjectionManager::class.java)
        projection = manager?.getMediaProjection(android.app.Activity.RESULT_OK, data)
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        (getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay.getMetrics(metrics)
        val w = (metrics.widthPixels / 2).coerceAtLeast(320)
        val h = (metrics.heightPixels / 2).coerceAtLeast(480)
        captureWidth = w
        captureHeight = h
        imageReader = ImageReader.newInstance(w, h, ImageFormat.RGBA_8888, 2)
        virtualDisplay = projection?.createVirtualDisplay(
            "jarvis_screen", w, h, metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, handler
        )
        JarvisState.screen(true)
        startForeground(102, NotificationHelper(this).ongoing(
            NotificationHelper.CHANNEL_SCREEN, "Screen sharing active",
            "JARVIS can analyse the captured screen. Tap Stop to end.", NotificationHelper.ACTION_STOP_SCREEN))
        projection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { stopSelf() }
        }, handler)
        return START_STICKY
    }

    override fun onDestroy() {
        virtualDisplay?.release(); imageReader?.close(); projection?.stop()
        JarvisState.screen(false)
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        @Volatile private var lastFrame: Bitmap? = null
        fun latestFrame(): Bitmap? = lastFrame
        fun start(context: Context, data: Intent) {
            val i = Intent(context, ScreenCaptureService::class.java).putExtra("data", data)
            context.startForegroundService(i)
        }
    }

    init {
        handler.postDelayed(object : Runnable {
            override fun run() {
                val image = imageReader?.acquireLatestImage()
                if (image != null) {
                    try {
                        val plane = image.planes[0]
                        val buffer = plane.buffer
                        val bitmap = Bitmap.createBitmap(captureWidth, captureHeight, Bitmap.Config.ARGB_8888)
                        bitmap.copyPixelsFromBuffer(buffer)
                        lastFrame = bitmap.copy(Bitmap.Config.ARGB_8888, false)
                    } catch (_: Exception) {}
                    image.close()
                }
                handler.postDelayed(this, 700)
            }
        }, 300)
    }
}

object AccessibilityBridge {
    private val queue = ConcurrentLinkedQueue<String>()
    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled
    fun enqueue(command: String) { queue.add(command) }
    internal fun next(): String? = queue.poll()
    internal fun setEnabled(v: Boolean) { _enabled.value = v }
}

class JarvisAccessibilityService : android.accessibilityservice.AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private val poll = object : Runnable {
        override fun run() {
            AccessibilityBridge.next()?.let { act(it) }
            handler.postDelayed(this, 400)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        AccessibilityBridge.setEnabled(true)
        handler.post(poll)
    }

    override fun onAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent?) {}

    private fun act(command: String) {
        val text = command.lowercase()
        if (text.contains("open settings")) {
            try {
                val intent = android.content.Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            } catch (_: Exception) {}
            return
        }
        val root = rootInActiveWindow ?: return
        val nodes = mutableListOf<android.view.accessibility.AccessibilityNodeInfo>()
        fun walk(n: android.view.accessibility.AccessibilityNodeInfo?, depth: Int) {
            if (n == null || depth > 12) return
            nodes.add(n)
            for (i in 0 until n.childCount) walk(n.getChild(i), depth + 1)
        }
        walk(root, 0)
        val target = nodes.firstOrNull { node ->
            val label = (node.text ?: node.contentDescription ?: "").toString()
            label.isNotBlank() && text.contains(label.take(24).lowercase())
        } ?: return
        target.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
    }

    override fun onInterrupt() {}
    override fun onDestroy() { AccessibilityBridge.setEnabled(false); handler.removeCallbacks(poll); super.onDestroy() }

    companion object {
        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0)
            if (enabled == 0) return false
            val value = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
            return value.contains(context.packageName + "/" + JarvisAccessibilityService::class.java.name)
        }
    }
}

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val title = inputData.getString("title") ?: "Reminder"
        NotificationHelper(applicationContext).notify(NotificationHelper.CHANNEL_REMINDERS, "JARVIS reminder", title, 500 + (System.currentTimeMillis() % 1000).toInt())
        return Result.success()
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val app = context.applicationContext as? JarvisApp ?: return@launch
            app.container.db.reminderDao().all().collect { list ->
                list.filter { it.enabled && it.dueAt > System.currentTimeMillis() }.forEach { r ->
                    val req = androidx.work.OneTimeWorkRequestBuilder<ReminderWorker>()
                        .setInputData(androidx.work.workDataOf("title" to r.title))
                        .setInitialDelay(r.dueAt - System.currentTimeMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)
                        .build()
                    androidx.work.WorkManager.getInstance(context).enqueueUniqueWork("reminder_${r.id}", androidx.work.ExistingWorkPolicy.REPLACE, req)
                }
                return@collect
            }
        }
    }
}

data class ShizukuStatus(
    val installed: Boolean,
    val running: Boolean,
    val permission: Boolean,
    val label: String
)

object ShizukuDetector {
    fun detect(context: Context): ShizukuStatus {
        val installed = try {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0) != null
        } catch (_: Exception) { false }
        if (!installed) return ShizukuStatus(false, false, false, "Not installed")
        return try {
            val cls = Class.forName("moe.shizuku.api.Shizuku")
            val version = cls.getMethod("getServiceVersion").invoke(null) as? Int ?: -1
            val perm = cls.getMethod("checkPermission").invoke(null) as? Int ?: -1
            val running = version > 0
            val granted = perm == 0
            ShizukuStatus(true, running, granted,
                when {
                    !running -> "Service not running"
                    !granted -> "Permission not granted"
                    else -> "Ready"
                })
        } catch (_: Exception) {
            ShizukuStatus(true, false, false, "Installed, API unavailable")
        }
    }
}
