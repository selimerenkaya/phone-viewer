package io.github.selimerenkaya.phoneviewer.notify

import android.app.Notification
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import io.github.selimerenkaya.phoneviewer.Feature
import io.github.selimerenkaya.phoneviewer.Send
import org.json.JSONObject

const val NOTIFICATION = 0x30
const val NOTIFICATION_REMOVED = 0x31
const val CLIPBOARD = 0x32

/** notify lane. Link builds one per PC session; services and activities reach it through [current]. */
class NotifyFeature(val send: Send) : Feature {
    private val echo = EchoGuard()

    init { current = this }

    override fun onMessage(type: Int, payload: ByteArray) {
        if (type != CLIPBOARD) return
        val text = runCatching { JSONObject(String(payload)).getString("text") }.getOrNull() ?: return
        val clipboard = app?.getSystemService(ClipboardManager::class.java) ?: return
        echo.received(text)
        Handler(Looper.getMainLooper()).post { clipboard.setPrimaryClip(ClipData.newPlainText("PC", text)) }
    }

    override fun onDisconnected() {
        if (current === this) current = null
    }

    /** Sends the phone clipboard to the PC. Call only while [context] has window focus (Android 10+ rule). */
    fun sendClipboard(context: Context) {
        val text = context.getSystemService(ClipboardManager::class.java)
            .primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString() ?: return
        if (echo.shouldSend(text)) send(CLIPBOARD, JSONObject().put("text", text).toString().toByteArray())
    }

    companion object {
        @Volatile var current: NotifyFeature? = null
        // shortcut: set by MainActivity/NotificationListener; if link's service outlives both after a process restart, PC → phone clipboard is dropped until the app is opened.
        @Volatile var app: Context? = null
    }
}

/** Remembers the last clipboard text seen from either side, so a value is never bounced back where it came from. */
class EchoGuard {
    private var last: String? = null

    @Synchronized fun received(text: String) { last = text }

    @Synchronized fun shouldSend(text: String): Boolean {
        if (text == last) return false
        last = text
        return true
    }
}

/** Ongoing, foreground-service and group-summary notifications, and our own, stay on the phone. */
fun shouldForward(flags: Int, packageName: String, ownPackage: String): Boolean =
    packageName != ownPackage &&
        flags and (Notification.FLAG_ONGOING_EVENT or Notification.FLAG_FOREGROUND_SERVICE or Notification.FLAG_GROUP_SUMMARY) == 0
