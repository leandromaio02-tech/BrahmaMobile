package com.brahma.mobile.overlay

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings as AndroidSettings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.brahma.mobile.MainActivity
import com.brahma.mobile.R
import com.brahma.mobile.util.brahmaApp

/** Foreground service hosting the draggable 3D avatar bubble as a system overlay. */
class FloatingAvatarService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var params: WindowManager.LayoutParams
    private var overlay: View? = null
    private var avatar: AvatarViewController? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())
        if (!AndroidSettings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }
        params = WindowManager.LayoutParams(320, 400, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 40
            y = 180
        }

        val controller = AvatarViewController(this) { brahmaApp.voice.listenOnce() }
        avatar = controller
        val view = buildView(controller)
        overlay = view
        windowManager.addView(view, params)
    }

    private fun buildView(controller: AvatarViewController): View {
        val background = GradientDrawable().apply {
            setColor(Color.parseColor("#E60B0D17"))
            cornerRadius = 26f
            setStroke(3, Color.parseColor("#6C5CE7"))
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            this.background = background
            setPadding(8, 0, 8, 8)
        }

        val handle = TextView(this).apply {
            text = "⠿  DRAG ME"
            setTextColor(Color.parseColor("#A48BFF"))
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(0, 6, 0, 6)
        }
        root.addView(handle, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        root.addView(controller.webView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val mic = Button(this).apply { text = "MIC" }
        val close = Button(this).apply { text = "CLOSE" }
        mic.setOnClickListener { brahmaApp.voice.listenOnce() }
        close.setOnClickListener { stopSelf() }
        row.addView(mic, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(close, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(row, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        var lastX = 0f
        var lastY = 0f
        handle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = event.rawX; lastY = event.rawY; true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x += (event.rawX - lastX).toInt()
                    params.y += (event.rawY - lastY).toInt()
                    lastX = event.rawX; lastY = event.rawY
                    try { windowManager.updateViewLayout(root, params) } catch (t: Throwable) {}
                    true
                }
                else -> false
            }
        }
        return root
    }

    override fun onBind(intent: Intent?): android.os.IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_SPEAK -> {
                val text = intent.getStringExtra(EXTRA_TEXT).orEmpty()
                if (text.isNotBlank()) {
                    brahmaApp.voice.speak(text)
                    avatar?.expression("speaking")
                }
            }
            ACTION_EXPRESSION -> avatar?.expression(intent.getStringExtra(EXTRA_EXPR) ?: "neutral")
            ACTION_STOP -> stopSelf()
        }
        return START_STICKY
    }

    private fun buildNotification(): android.app.Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Brahma Avatar", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_brahma)
            .setContentTitle("Brahma is awake")
            .setContentText("Floating companion active")
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        overlay?.let { view -> runCatching { windowManager.removeView(view) } }
        overlay = null
        avatar?.destroy()
        avatar = null
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "brahma_avatar"
        const val NOTIFICATION_ID = 42
        const val ACTION_SPEAK = "com.brahma.SPEAK"
        const val ACTION_EXPRESSION = "com.brahma.EXPRESSION"
        const val ACTION_STOP = "com.brahma.STOP"
        const val EXTRA_TEXT = "com.brahma.extra.TEXT"
        const val EXTRA_EXPR = "com.brahma.extra.EXPR"
    }
}
