package com.brahma.mobile.reflex

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/** Optional service used for screen reading and UI clicking reflexes. */
class BrahmaAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    fun clickText(target: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val nodes = root.findAccessibilityNodeInfosByText(target)
        for (node in nodes) {
            var current: AccessibilityNodeInfo? = node
            while (current != null) {
                if (current.isClickable && current.isEnabled) {
                    return current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                }
                current = current.parent
            }
        }
        return false
    }

    fun readScreen(): String {
        val root = rootInActiveWindow ?: return ""
        val out = StringBuilder()
        collect(root, out, 0)
        return out.toString()
    }

    private fun collect(node: AccessibilityNodeInfo?, out: StringBuilder, depth: Int) {
        if (node == null || depth > 12 || out.length > 3000) return
        val text = node.text?.toString().orEmpty()
        val desc = node.contentDescription?.toString().orEmpty()
        val label = text.ifBlank { desc }
        if (label.isNotBlank()) out.append(label).append('\n')
        for (i in 0 until node.childCount) {
            collect(node.getChild(i), out, depth + 1)
        }
    }

    companion object {
        @Volatile
        var instance: BrahmaAccessibilityService? = null
            private set
    }
}
