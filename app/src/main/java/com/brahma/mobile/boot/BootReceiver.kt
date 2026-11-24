package com.brahma.mobile.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.brahma.mobile.overlay.FloatingAvatarService
import com.brahma.mobile.util.brahmaApp

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!context.brahmaApp.settings.avatarVisible) return
        try {
            val service = Intent(context, FloatingAvatarService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(service)
            } else {
                context.startService(service)
            }
        } catch (t: Throwable) {
            // OEM restrictions may block background starts; the user can toggle from the UI.
        }
    }
}
