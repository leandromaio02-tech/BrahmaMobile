package com.brahma.mobile.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class WebSearchTool(private val context: Context) {
    private val client = OkHttpClient.Builder().callTimeout(12, TimeUnit.SECONDS).build()

    fun search(query: String): String {
        if (query.isBlank()) return "What should I search for?"
        val q = URLEncoder.encode(query, "UTF-8")
        val url = "https://api.duckduckgo.com/?q=$q&format=json&no_html=1&skip_disambig=1"
        return try {
            val req = Request.Builder().url(url).header("User-Agent", "BrahmaMobile/1.0").build()
            val body = client.newCall(req).execute().use { it.body?.string().orEmpty() }
            val root = JSONObject(body)
            val summary = root.optString("AbstractText", "").trim()
            val topics = root.optJSONArray("RelatedTopics")
            val extra = StringBuilder()
            if (topics != null) {
                for (i in 0 until minOf(topics.length(), 3)) {
                    val obj = topics.optJSONObject(i) ?: continue
                    val text = obj.optString("Text", "").trim()
                    if (text.isNotBlank()) extra.append(" ").append(text.take(140))
                }
            }
            val result = summary.ifBlank { extra.toString().trim() }
            result.ifBlank { "No instant answer for '$query'. Say 'open $query' to browse." }
        } catch (t: Throwable) {
            "Search failed (${t.javaClass.simpleName}). Say 'open $query' to use the browser."
        }
    }

    fun open(query: String): String {
        val q = if (query.startsWith("http")) query
        else "https://www.google.com/search?q=" + URLEncoder.encode(query, "UTF-8")
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(q)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            "Opening $query in your browser."
        } catch (t: Throwable) {
            "No browser found."
        }
    }
}
