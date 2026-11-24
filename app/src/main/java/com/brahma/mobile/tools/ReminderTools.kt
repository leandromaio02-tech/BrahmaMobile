package com.brahma.mobile.tools

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.brahma.mobile.reminders.ReminderReceiver

class ReminderTools(private val context: Context) {
    fun add(message: String, afterMinutes: Long): String {
        if (afterMinutes <= 0) return "Give me a positive number of minutes."
        if (message.isBlank()) return "What should I remind you about?"
        val trigger = System.currentTimeMillis() + afterMinutes * 60_000L
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(EXTRA_MESSAGE, message)
        }
        val pi = PendingIntent.getBroadcast(
            context, trigger.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        try {
            val exactAllowed = android.os.Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
            if (exactAllowed) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
            } else {
                am.setWindow(AlarmManager.RTC_WAKEUP, trigger, 60_000L, pi)
            }
        } catch (t: Throwable) {
            am.set(AlarmManager.RTC_WAKEUP, trigger, pi)
        }
        return "Got it. In $afterMinutes minute(s): $message"
    }

    companion object {
        const val EXTRA_MESSAGE = "brahma.reminder.message"
    }
}
