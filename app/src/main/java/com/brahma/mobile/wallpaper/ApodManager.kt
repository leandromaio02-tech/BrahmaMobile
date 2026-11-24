package com.brahma.mobile.wallpaper

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** NASA Astronomy Picture of the Day: wallpaper + a one-sentence briefing. */
class ApodManager(private val context: Context) {
    data class Apod(
        val date: String,
        val title: String,
        val explanation: String,
        val url: String,
        val mediaType: String
    )

    private val client = OkHttpClient.Builder().callTimeout(25, TimeUnit.SECONDS).build()
    private val prefs = context.applicationContext.getSharedPreferences("brahma_apod", Context.MODE_PRIVATE)

    suspend fun fetch(): Apod = withContext(Dispatchers.IO) {
        val url = "https://api.nasa.gov/planetary/apod?api_key=DEMO_KEY&thumbs=true"
        val req = Request.Builder().url(url).header("User-Agent", "BrahmaMobile/1.0").build()
        val body = client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("APOD HTTP ${resp.code}")
            resp.body?.string().orEmpty()
        }
        val json = JSONObject(body)
        Apod(
            date = json.optString("date", ""),
            title = json.optString("title", "Today in space"),
            explanation = json.optString("explanation", ""),
            url = json.optString("url", ""),
            mediaType = json.optString("media_type", "image")
        )
    }

    suspend fun refreshWallpaper(): String = withContext(Dispatchers.IO) {
        try {
            val apod = fetch()
            var applied = false
            if (apod.mediaType == "image" && apod.url.isNotBlank()) {
                val imageReq = Request.Builder().url(apod.url).build()
                val bytes = client.newCall(imageReq).execute().use { resp ->
                    if (!resp.isSuccessful) throw IOException("Image HTTP ${resp.code}")
                    resp.body?.bytes() ?: ByteArray(0)
                }
                val bitmap: Bitmap? = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                if (bitmap != null) {
                    WallpaperManager.getInstance(context).setBitmap(bitmap)
                    applied = true
                }
            }
            val brief = "NASA APOD · ${apod.title}. " +
                apod.explanation.replace(Regex("\\s+"), " ").take(220)
            prefs.edit().putString(KEY_BRIEF, brief).putString(KEY_DATE, apod.date).apply()
            if (applied) "$brief\n(Wallpaper updated.)" else brief
        } catch (t: Throwable) {
            "APOD unavailable right now (${t.javaClass.simpleName})."
        }
    }

    fun briefing(): String = prefs.getString(KEY_BRIEF, "No briefing yet — tap 'Refresh APOD'.") ?: ""

    companion object {
        const val KEY_BRIEF = "brief"
        const val KEY_DATE = "date"
    }
}
