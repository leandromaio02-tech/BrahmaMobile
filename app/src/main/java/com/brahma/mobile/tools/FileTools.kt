package com.brahma.mobile.tools

import android.content.Context
import java.io.File

/** Reads/writes only inside the app's own external files dir and Downloads. */
class FileTools(private val context: Context) {
    private val base: File = context.getExternalFilesDir(null) ?: context.filesDir
    private val downloads = File(android.os.Environment.getExternalStorageDirectory(), "Download")

    fun list(relative: String = ""): String {
        val dir = resolve(relative) ?: return "That folder is outside my sandbox."
        val kids = dir.listFiles()?.sortedBy { it.name } ?: return "Empty or unreadable folder."
        if (kids.isEmpty()) return "Empty folder."
        return kids.take(40).joinToString("\n") {
            (if (it.isDirectory) "[dir] " else "") + it.name +
                if (it.isFile) " (${it.length()} B)" else ""
        }
    }

    fun read(relative: String): String {
        val f = resolve(relative) ?: return "That file is outside my sandbox."
        if (!f.isFile) return "No such file: $relative"
        if (f.length() > 1_000_000L) return "File is too large to read out loud (>1 MB)."
        return f.readText(Charsets.UTF_8).take(4000).ifBlank { "(empty file)" }
    }

    fun write(relative: String, text: String): String {
        val f = resolve(relative, allowDownloads = false) ?: return "That path is outside my sandbox."
        f.parentFile?.mkdirs()
        f.writeText(text, Charsets.UTF_8)
        return "Wrote ${f.length()} bytes to $relative"
    }

    private fun resolve(path: String, allowDownloads: Boolean = true): File? {
        val clean = path.trim().trimStart('/')
        if (clean.contains("..")) return null
        val inApp = File(base, clean).canonicalFile
        if (allowDownloads && clean.startsWith("Download/")) {
            val inDl = File(downloads, clean.removePrefix("Download/")).canonicalFile
            if (inDl.path.startsWith(downloads.canonicalFile.path)) return inDl
        }
        return if (inApp.path.startsWith(base.canonicalFile.path)) inApp else null
    }
}
