package com.brahma.mobile.tools

import android.content.Context
import android.content.Intent

/** Sends a command to Termux using its documented external-command API. */
class TermuxTool(private val context: Context) {
    fun run(command: String): String {
        if (command.isBlank()) return "No command given."
        return try {
            val intent = Intent("com.termux.RUN_COMMAND").setPackage("com.termux").apply {
                putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/sh")
                putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-c", command))
                putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home")
                putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
            }
            context.startService(intent)
            "Sent to Termux: $command"
        } catch (t: Throwable) {
            "Termux isn't installed or external commands are disabled: ${t.javaClass.simpleName}"
        }
    }
}
