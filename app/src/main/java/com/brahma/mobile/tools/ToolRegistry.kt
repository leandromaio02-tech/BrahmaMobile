package com.brahma.mobile.tools

import android.content.Context

class ToolRegistry(context: Context) {
    val files = FileTools(context)
    val reminders = ReminderTools(context)
    val web = WebSearchTool(context)
    val termux = TermuxTool(context)
    val ui = UiTool()

    fun describe(): String =
        "Available tools: files.list, files.read, files.write, reminders.add, " +
        "web.search, web.open, termux.run, ui.clickText, ui.readScreen, ui.back, ui.home."

    fun execute(name: String, arg: String): String = try {
        when (name.lowercase()) {
            "files.list" -> files.list(arg)
            "files.read" -> files.read(arg)
            "files.write" -> {
                val parts = arg.split("\n", limit = 2)
                if (parts.size == 2) files.write(parts[0], parts[1]) else "Use: path\ntext"
            }
            "reminders.add" -> {
                val parts = arg.split("|")
                if (parts.size == 2) reminders.add(parts[0], parts[1].toLongOrNull() ?: 5)
                else "Use: message|minutes"
            }
            "web.search" -> web.search(arg)
            "web.open" -> web.open(arg)
            "termux.run" -> termux.run(arg)
            "ui.clicktext" -> ui.clickText(arg)
            "ui.readscreen" -> ui.readScreen()
            "ui.back" -> ui.back()
            "ui.home" -> ui.home()
            else -> "Unknown tool: $name. ${describe()}"
        }
    } catch (t: Throwable) {
        "Tool error: ${t.javaClass.simpleName}: ${t.message}"
    }
}
