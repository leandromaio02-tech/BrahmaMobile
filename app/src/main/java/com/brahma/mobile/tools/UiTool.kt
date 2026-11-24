package com.brahma.mobile.tools

import android.view.accessibility.AccessibilityEvent
import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityNodeInfo
import com.brahma.mobile.reflex.BrahmaAccessibilityService

/** UI automation through the optional Accessibility Service. */
class UiTool {
    private val service: BrahmaAccessibilityService?
        get() = BrahmaAccessibilityService.instance

    fun isReady(): Boolean = service != null

    fun clickText(target: String): String {
        val s = service ?: return "Enable Brahma Reflex in Accessibility settings first."
        return if (s.clickText(target)) "Tapped $target" else "Couldn't find $target on screen."
    }

    fun readScreen(): String {
        val s = service ?: return "Enable Brahma Reflex in Accessibility settings first."
        return s.readScreen().take(2000).ifBlank { "Nothing readable on screen." }
    }

    fun back(): String {
        val s = service ?: return "Accessibility service is off."
        s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        return "Back."
    }

    fun home(): String {
        val s = service ?: return "Accessibility service is off."
        s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        return "Home."
    }
}

// Referenced so the import list documents the dependency type.
private val eventType: Int = AccessibilityEvent.TYPE_VIEW_CLICKED
private val nodeAction: Int = AccessibilityNodeInfo.ACTION_CLICK
