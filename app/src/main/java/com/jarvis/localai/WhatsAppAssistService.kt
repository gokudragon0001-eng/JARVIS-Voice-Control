package com.jarvis.localai

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Optional helper: after the user enables this service, it can tap WhatsApp's Send/Voice call UI
 * for the single action explicitly requested by the user. WhatsApp UI updates may change labels. */
class WhatsAppAssistService : AccessibilityService(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        tts = TextToSpeech(this, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) tts?.language = Locale("ur", "PK")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val prefs = getSharedPreferences("jarvis_automation", MODE_PRIVATE)
        val pending = prefs.getString("pending_action", null) ?: return
        if (rootInActiveWindow == null || rootInActiveWindow.packageName?.toString()?.startsWith("com.whatsapp") != true) return
        val root = rootInActiveWindow ?: return
        val target = if (pending == "send") listOf("send", "send message") else listOf("voice call", "audio call")
        val node = findClickable(root, target) ?: return
        val clicked = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        if (clicked) {
            prefs.edit().remove("pending_action").apply()
            val message = if (pending == "send") "Aapka kaam kar diya gaya hai." else "WhatsApp call shuru ki ja rahi hai."
            tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "jarvis_action_done")
        }
    }

    private fun findClickable(node: AccessibilityNodeInfo, labels: List<String>): AccessibilityNodeInfo? {
        val label = listOfNotNull(node.text?.toString(), node.contentDescription?.toString()).joinToString(" ").lowercase()
        if (labels.any { label == it || label.contains(it) } && node.isEnabled) {
            var current: AccessibilityNodeInfo? = node
            while (current != null && !current.isClickable) current = current.parent
            if (current != null) return current
        }
        for (i in 0 until node.childCount) {
            val found = node.getChild(i)?.let { findClickable(it, labels) }
            if (found != null) return found
        }
        return null
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
