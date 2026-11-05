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
import java.util.concurrent.Executors

const val NOTIFICATION = 0x30
const val NOTIFICATION_REMOVED = 0x31
const val CLIPBOARD = 0x32

/** notify lane. While a session is up, services and activities reach it through [current]. */
class NotifyFeature(private val context: Context, private val send: Send) : Feature {
    private val echo = EchoGuard()
    private val io = Executors.newSingleThreadExecutor()

    /** Link writes the socket on the caller thread; callers here are often the main thread. */
    fun post(type: Int, payload: ByteArray) = io.execute { send(type, payload) }

    override fun onConnected() { current = this }

    override fun onDisconnected() { if (current === this) current = null }

    override fun onMessage(type: Int, payload: ByteArray) {
        if (type != CLIPBOARD) return
        val text = runCatching { JSONObject(String(payload)).getString("text") }.getOrNull() ?: return
        echo.received(text)
        Handler(Looper.getMainLooper()).post {
            context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("PC", text))
        }
    }

    /** Sends the phone clipboard to the PC. Call only while [focused] has window focus (Android 10+ rule). */
    fun sendClipboard(focused: Context) {
        val text = focused.getSystemService(ClipboardManager::class.java)
            .primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(focused)?.toString() ?: return
        if (echo.shouldSend(text)) post(CLIPBOARD, JSONObject().put("text", text).toString().toByteArray())
    }

    companion object {
        @Volatile var current: NotifyFeature? = null
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
