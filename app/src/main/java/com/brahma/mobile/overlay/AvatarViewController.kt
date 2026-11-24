package com.brahma.mobile.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView

/** Owns the WebView that renders the Three.js avatar. */
class AvatarViewController(context: Context, private val onTap: () -> Unit) {
    @SuppressLint("SetJavaScriptEnabled")
    val webView: WebView = WebView(context.applicationContext).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.cacheMode = WebSettings.LOAD_NO_CACHE
        settings.setSupportZoom(false)
        settings.mediaPlaybackRequiresUserGesture = false
        setBackgroundColor(Color.TRANSPARENT)
        addJavascriptInterface(JsBridge(), "Brahma")
        loadUrl("file:///android_asset/avatar/index.html")
    }

    private inner class JsBridge {
        @JavascriptInterface
        fun tapped() { onTap() }

        @JavascriptInterface
        fun ready() { expression("neutral") }
    }

    fun expression(name: String) {
        val safe = name.replace(Regex("[^a-z]", setOf(RegexOption.IGNORE_CASE)), "")
        webView.evaluateJavascript("if(window.brahmaSetExpression){brahmaSetExpression('$safe')}", null)
    }

    fun speaking() {
        webView.evaluateJavascript("if(window.brahmaSpeak){brahmaSpeak()}", null)
    }

    fun idle() {
        webView.evaluateJavascript("if(window.brahmaIdle){brahmaIdle()}", null)
    }

    fun destroy() {
        try { webView.destroy() } catch (t: Throwable) {}
    }
}
