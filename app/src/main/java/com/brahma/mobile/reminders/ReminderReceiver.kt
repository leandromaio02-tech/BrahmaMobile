package com.brahma.mobile.reminders

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.brahma.mobile.R
import com.brahma.mobile.tools.ReminderTools

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val message = intent.getStringExtra(ReminderTools.EXTRA_MESSAGE) ?: "Reminder!"
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Brahma reminders", NotificationManager.IMPORTANCE_HIGH)
            nm.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_brahma)
            .setContentTitle("Brahma reminder")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        nm.notify(message.hashCode(), notification)
    }

    companion object {
        const val CHANNEL_ID = "brahma_reminders"
    }
}
