package com.example.braillink

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Accessibility service that keeps a best-effort "latest visible text" snapshot.
 * The Activity can read the snapshot with getLatestText().
 *
 * Important: the service must be enabled by the user in Settings → Accessibility.
 */
class ScreenTextAccessibilityService : AccessibilityService() {

    companion object {
        // Latest snapshot (volatile-ish). Access from MainActivity on UI thread is fine.
        @Volatile
        private var latestSnapshot: String = ""

        // Mutex for safe writes in case of concurrent events (not strictly necessary).
        private val mutex = Mutex()

        /** Call to get whatever text the service has most recently captured. */
        fun getLatestText(): String = latestSnapshot
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        // You can set service-wide configuration here if needed.
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        try {
            // Only update snapshot on content/window/text changes for performance
            if (event == null) return
            val t = when (event.eventType) {
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> buildSnapshot()
                else -> null
            }
            if (!t.isNullOrEmpty()) {
                // update snapshot safely
                // Using runBlocking is not ideal in real service, but this is small; using Mutex with blocking allowed since updates are quick.
                // We'll update atomically:
                // NOTE: we can't call suspend functions here easily; keep updates synchronous and fast.
                latestSnapshot = t
            }
        } catch (_: Exception) {
            // ignore exceptions to keep service stable
        }
    }

    override fun onInterrupt() {
        // Required override: no-op
    }

    /** Build a best-effort string of visible text in the active window. */
    private fun buildSnapshot(): String {
        val root: AccessibilityNodeInfo? = rootInActiveWindow ?: return ""
        val sb = StringBuilder()
        collectTextFromNode(root!!, sb)

        val raw = sb.toString().trim()

        // Basic sanitization: collapse whitespace
        val cleaned = raw.replace(Regex("\\s+"), " ").trim()

        // Limit length to avoid huge dumps
        return if (cleaned.length > 3000) cleaned.substring(0, 3000) else cleaned
    }


    /** Recursively collect text & contentDescription from visible nodes. */
    private fun collectTextFromNode(node: AccessibilityNodeInfo, out: StringBuilder) {
        try {
            // skip invisible nodes
            if (!node.isVisibleToUser) return

            // Ignore password fields
            if (node.isPassword) return

            // prefer node.text if present
            val txt = node.text?.toString()
            if (!txt.isNullOrBlank()) {
                val s = txt.trim()
                if (s.length > 0) {
                    if (out.isNotEmpty()) out.append(" ")
                    out.append(s)
                }
            } else {
                val desc = node.contentDescription?.toString()
                if (!desc.isNullOrBlank()) {
                    val s = desc.trim()
                    if (s.length > 0) {
                        if (out.isNotEmpty()) out.append(" ")
                        out.append(s)
                    }
                }
            }

            // Recurse children
            val childCount = node.childCount
            for (i in 0 until childCount) {
                val child = node.getChild(i)
                if (child != null) {
                    collectTextFromNode(child, out)
                    child.recycle()
                }
            }
        } catch (_: Exception) {
            // ignore node traversal errors
        }
    }
}

