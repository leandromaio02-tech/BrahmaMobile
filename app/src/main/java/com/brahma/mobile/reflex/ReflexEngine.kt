package com.brahma.mobile.reflex

import android.Manifest
import android.provider.AlarmClock
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.provider.Settings as AndroidSettings
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.brahma.mobile.tools.ToolRegistry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Sub-50 ms command path. No model, no network, no coroutines: pure regex ->
 * Android Intent / system service. Called on the main thread from the mic UI.
 */
class ReflexEngine(
    private val context: Context,
    private val tools: ToolRegistry,
    private val speak: (String) -> Unit,
    private val onStop: () -> Unit
) {
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun tryHandle(input: String): Boolean {
        val t = input.trim().lowercase(Locale.ROOT)
        if (t.isBlank()) return true

        if (Regex("^(stop|cancel|quiet|shut up|enough|nevermind)[.!]?$").matches(t)) {
            onStop(); say("Stopping."); return true
        }

        Regex("^(?:open|launch|start)\\s+(.+)").find(t)?.let {
            say(openApp(it.groupValues[1].trim())); return true
        }
        Regex("^(?:play|search youtube)\\s+(.+?)\\s+(?:on\\s+)?(?:youtube|yt)$").find(t)?.let {
            say(playYouTube(it.groupValues[1].trim())); return true
        }
        Regex("^play\\s+(.+)").find(t)?.let {
            say(playYouTube(it.groupValues[1].trim())); return true
        }
        Regex("^(?:call|dial|phone)\\s+(.+)").find(t)?.let {
            say(call(it.groupValues[1].trim())); return true
        }
        Regex("^(?:message|text)\\s+(.+)").find(t)?.let {
            say(message(it.groupValues[1].trim())); return true
        }
        Regex("^(?:search|google|lookup)\\s+(.+)").find(t)?.let {
            val q = it.groupValues[1].trim()
            say(tools.web.search(q)); return true
        }
        Regex("^remind me (?:to )?(.+?) in (\\d+)\\s*(minute|minutes|min|m)$").find(t)?.let {
            say(tools.reminders.add(it.groupValues[1], it.groupValues[2].toLong())); return true
        }
        Regex("^alarm in (\\d+)\\s*(minute|minutes|min|m)$").find(t)?.let {
            say(timer(it.groupValues[1].toLong())); return true
        }
        Regex("^alarm at (\\d{1,2})[: ](\\d{2})$").find(t)?.let {
            say(alarmAt(it.groupValues[1].toInt(), it.groupValues[2].toInt())); return true
        }
        Regex("^(?:set )?timer (?:for )?(\\d+)\\s*(minute|minutes|min|m)$").find(t)?.let {
            say(timer(it.groupValues[1].toLong())); return true
        }
        Regex("^run (.+) in termux$").find(t)?.let {
            say(tools.termux.run(it.groupValues[1].trim())); return true
        }
        Regex("^(?:termux|shell) (.+)$").find(t)?.let {
            say(tools.termux.run(it.groupValues[1].trim())); return true
        }
        Regex("^(?:read|open) file (.+)$").find(t)?.let {
            say(tools.files.read(it.groupValues[1].trim())); return true
        }
        Regex("^list files(?: in (.+))?$").find(t)?.let {
            say(tools.files.list(it.groupValues[1]?.trim().orEmpty())); return true
        }
        Regex("^(?:click|tap) (.+)$").find(t)?.let {
            say(tools.ui.clickText(it.groupValues[1].trim())); return true
        }
        if (t.contains("read screen") || t.contains("what's on screen")) {
            say(tools.ui.readScreen()); return true
        }

        if (t.contains("volume")) {
            say(volume(t)); return true
        }
        if (t.contains("brightness")) {
            say(brightness(t)); return true
        }
        if (t.contains("flashlight") || t.contains("torch")) {
            say(torch(t.contains("on") && !t.contains("off"))); return true
        }
        if (t.contains("wifi")) {
            say(openSettings(AndroidSettings.ACTION_WIFI_SETTINGS, "Wi-Fi")); return true
        }
        if (t.contains("bluetooth")) {
            val btOn = BluetoothAdapter.getDefaultAdapter()?.isEnabled == true
            say("Bluetooth is ${if (btOn) "on" else "off"}. Opening settings.")
            openSettings(AndroidSettings.ACTION_BLUETOOTH_SETTINGS, "Bluetooth")
            return true
        }
        if (t.contains("battery")) {
            say(batteryStatus()); return true
        }
        if (Regex("^(what(?:'s| is)? (?:the )?time|time)$").matches(t)) {
            say("It's " + SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date()) + "."); return true
        }
        if (t.contains("date") && !t.contains("update")) {
            say("Today is " + SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date()) + "."); return true
        }
        return false
    }

    private fun say(text: String) {
        if (text.isNotBlank()) speak(text.take(500))
    }

    private fun openApp(name: String): String {
        val pkg = when {
            name.contains("whatsapp") -> "com.whatsapp"
            name.contains("youtube music") -> "com.google.android.apps.youtube.music"
            name.contains("youtube") -> "com.google.android.youtube"
            name.contains("spotify") -> "com.spotify.music"
            name.contains("telegram") -> "org.telegram.messenger"
            name.contains("gmail") -> "com.google.android.gm"
            name.contains("chrome") -> "com.android.chrome"
            name.contains("termux") -> "com.termux"
            name.contains("camera") -> "com.android.camera2"
            name.contains("gallery") || name.contains("photos") -> "com.google.android.apps.photos"
            name.contains("setting") -> "com.android.settings"
            name.contains(".") -> name
            else -> return "I don't know an app called $name."
        }
        return try {
            val launch = context.packageManager.getLaunchIntentForPackage(pkg)
            if (launch != null) {
                context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                "Opening $name."
            } else {
                val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(market)
                "$name isn't installed. Opening its store page."
            }
        } catch (t: Throwable) {
            "Couldn't open $name."
        }
    }

    private fun playYouTube(query: String): String {
        if (query.isBlank()) return "What should I play?"
        val url = "https://www.youtube.com/results?search_query=" +
            Uri.encode(query.replace(" ", "+"))
        return try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            "Playing $query on YouTube."
        } catch (t: Throwable) {
            "No app can open YouTube links."
        }
    }

    private fun call(target: String): String {
        val value = target.replace(" ", "")
        val uri = Uri.parse("tel:$value")
        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED
        val action = if (hasPermission) Intent.ACTION_CALL else Intent.ACTION_DIAL
        return try {
            context.startActivity(Intent(action, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            if (hasPermission) "Calling $target." else "Opening dialer for $target."
        } catch (t: Throwable) {
            "Couldn't start the call."
        }
    }

    private fun message(target: String): String = try {
        val parts = target.split(Regex("\\s+"), limit = 2)
        val uri = Uri.parse("smsto:${parts[0]}")
        val intent = Intent(Intent.ACTION_SENDTO, uri).putExtra("sms_body", parts.getOrNull(1) ?: "")
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        "Opening a message to $target."
    } catch (t: Throwable) {
        "Couldn't open messages."
    }

    private fun volume(t: String): String {
        val stream = if (t.contains("alarm")) AudioManager.STREAM_ALARM
        else if (t.contains("ring")) AudioManager.STREAM_RING
        else AudioManager.STREAM_MUSIC
        val max = audio.getStreamMaxVolume(stream)
        val pct = Regex("(?:volume|set)\\s+(?:to\\s+)?(\\d{1,3})\\s*(?:percent|%)?").find(t)
        return when {
            t.contains("mute") -> {
                audio.setStreamVolume(stream, 0, AudioManager.FLAG_SHOW_UI); "Muted."
            }
            t.contains("unmute") || t.contains("max") -> {
                audio.setStreamVolume(stream, max, AudioManager.FLAG_SHOW_UI); "Volume maxed."
            }
            t.contains("up") || t.contains("increase") || t.contains("louder") -> {
                audio.adjustStreamVolume(stream, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                "Volume up."
            }
            t.contains("down") || t.contains("decrease") || t.contains("quieter") -> {
                audio.adjustStreamVolume(stream, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                "Volume down."
            }
            pct != null -> {
                val v = (pct.groupValues[1].toInt() * max / 100).coerceIn(0, max)
                audio.setStreamVolume(stream, v, AudioManager.FLAG_SHOW_UI)
                "Volume set to ${pct.groupValues[1]} percent."
            }
            else -> "Volume is ${audio.getStreamVolume(stream) * 100 / max} percent."
        }
    }

    private fun brightness(t: String): String {
        if (!AndroidSettings.System.canWrite(context)) {
            context.startActivity(Intent(AndroidSettings.ACTION_MANAGE_WRITE_SETTINGS,
                Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return "Please grant Brahma permission to change system settings."
        }
        val cr = context.contentResolver
        val pct = Regex("brightness\\s+(?:to\\s+)?(\\d{1,3})").find(t)
        return when {
            pct != null -> {
                val n = pct.groupValues[1].toInt().coerceIn(1, 100)
                AndroidSettings.System.putInt(cr, AndroidSettings.System.SCREEN_BRIGHTNESS_MODE,
                    AndroidSettings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                AndroidSettings.System.putInt(cr, AndroidSettings.System.SCREEN_BRIGHTNESS, n * 255 / 100)
                "Brightness set to $n percent."
            }
            t.contains("up") -> adjustBrightness(cr, +40)
            t.contains("down") -> adjustBrightness(cr, -40)
            else -> {
                val v = AndroidSettings.System.getInt(cr, AndroidSettings.System.SCREEN_BRIGHTNESS, 128)
                "Brightness is ${v * 100 / 255} percent."
            }
        }
    }

    private fun adjustBrightness(cr: android.content.ContentResolver, delta: Int): String {
        val cur = AndroidSettings.System.getInt(cr, AndroidSettings.System.SCREEN_BRIGHTNESS, 128)
        val next = (cur + delta).coerceIn(5, 255)
        AndroidSettings.System.putInt(cr, AndroidSettings.System.SCREEN_BRIGHTNESS_MODE,
            AndroidSettings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
        AndroidSettings.System.putInt(cr, AndroidSettings.System.SCREEN_BRIGHTNESS, next)
        return "Brightness set to ${next * 100 / 255} percent."
    }

    private fun torch(on: Boolean): String {
        return try {
        val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = cm.cameraIdList.firstOrNull() ?: return "No camera found."
        val available = cm.getCameraCharacteristics(id)
            .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) as? Boolean ?: false
        if (!available) return "This phone has no flash."
        cm.setTorchMode(id, on)
        if (on) "Flashlight on." else "Flashlight off."
        } catch (t: Throwable) {
            "Flashlight unavailable: ${t.javaClass.simpleName}"
        }
    }

    private fun timer(minutes: Long): String = try {
        context.startActivity(Intent(AlarmClock.ACTION_SET_TIMER)
            .putExtra(AlarmClock.EXTRA_LENGTH, minutes * 60)
            .putExtra(AlarmClock.EXTRA_MESSAGE, "Brahma timer")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        "Timer set for $minutes minute(s)."
    } catch (t: Throwable) {
        "Couldn't set a timer."
    }

    private fun alarmAt(hour: Int, minute: Int): String = try {
        context.startActivity(Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, hour)
            .putExtra(AlarmClock.EXTRA_MINUTES, minute)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        "Alarm set for ${"%02d:%02d".format(hour, minute)}."
    } catch (t: Throwable) {
        "Couldn't set an alarm."
    }

    private fun openSettings(action: String, label: String): String = try {
        context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        "Opening $label settings."
    } catch (t: Throwable) {
        "Couldn't open $label settings."
    }

    private fun batteryStatus(): String {
        val intent = context.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val charging = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val pct = if (scale > 0) level * 100 / scale else -1
        return "Battery is $pct percent and ${if (charging != 0) "charging" else "running on battery"}."
    }

    fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}
