package io.github.selimerenkaya.phoneviewer.video

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import io.github.selimerenkaya.phoneviewer.Feature
import io.github.selimerenkaya.phoneviewer.Send
import org.json.JSONObject
import java.nio.ByteBuffer
import java.util.concurrent.LinkedBlockingQueue
import kotlin.concurrent.thread

/** Video lane, phone side: protocol handling and the send queue. Encoding lives in [ScreenCaptureService]. */
class VideoFeature(private val send: Send) : Feature {
    class Params(val maxSize: Int, val bitrate: Int)

    /** What the PC asked for; null while the PC doesn't want video. */
    @Volatile var params: Params? = null
        private set

    private val out = LinkedBlockingQueue<Pair<Int, ByteArray>>()
    @Volatile private var skipping = false
    private val sender = thread(isDaemon = true, name = "video-send") {
        try {
            while (true) out.take().let { (type, payload) -> send(type, payload) }
        } catch (_: InterruptedException) {
        }
    }

    init {
        current = this
    }

    override fun onMessage(type: Int, payload: ByteArray) {
        when (type) {
            VIDEO_START -> {
                val json = JSONObject(String(payload, Charsets.UTF_8))
                val p = Params(json.getInt("maxSize").coerceIn(144, 4096), json.getInt("bitrate").coerceIn(100_000, 100_000_000))
                params = p
                val service = ScreenCaptureService.running
                if (service != null) service.startStream(p) else askConsent()
            }
            VIDEO_STOP -> {
                params = null
                ScreenCaptureService.running?.stopStream()
                out.clear()
            }
        }
    }

    override fun onDisconnected() {
        params = null
        ScreenCaptureService.running?.stopSelf()
        context?.getSystemService(NotificationManager::class.java)?.cancel(CONSENT_NOTIFICATION)
        sender.interrupt()
        if (current === this) current = null
    }

    fun sendConfig(codec: String, width: Int, height: Int) {
        out.put(VIDEO_CONFIG to JSONObject().put("codec", codec).put("width", width).put("height", height).toString().toByteArray())
    }

    /** Queues a frame, or drops it if the link is backed up. Returns true when the caller should request a key frame. */
    fun sendFrame(key: Boolean, timestampUs: Long, data: ByteArray): Boolean {
        val backedUp = out.size >= MAX_QUEUED
        if (key) {
            skipping = false
            if (backedUp) out.removeIf { it.first == VIDEO_FRAME } // stale frames; this key frame replaces them
        } else if (skipping || backedUp) {
            val wasSkipping = skipping
            skipping = true // P-frames after a dropped one can't decode until the next key frame
            return !wasSkipping
        }
        out.put(VIDEO_FRAME to frame(key, timestampUs, data))
        return false
    }

    /** A background service can't open the consent dialog, so try directly (works if the app is visible) and post a notification. */
    private fun askConsent() {
        val ctx = context ?: return
        val intent = Intent(ctx, ScreenConsentActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(intent)
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Screen sharing", NotificationManager.IMPORTANCE_HIGH))
        nm.notify(CONSENT_NOTIFICATION, Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle("PC wants to see your screen")
            .setContentText("Tap to share your screen")
            .setContentIntent(PendingIntent.getActivity(ctx, 0, intent, PendingIntent.FLAG_IMMUTABLE))
            .setAutoCancel(true)
            .build())
    }

    companion object {
        const val VIDEO_CONFIG = 0x10
        const val VIDEO_FRAME = 0x11
        const val VIDEO_START = 0x12
        const val VIDEO_STOP = 0x13
        const val CHANNEL = "video"
        const val CONSENT_NOTIFICATION = 0x10
        private const val MAX_QUEUED = 3

        /** Set by the link lane (application context) before frames are routed here. */
        @Volatile var context: Context? = null
        /** The live feature, so the capture service can reach it. */
        @Volatile var current: VideoFeature? = null

        /** VIDEO_FRAME payload: u8 flags (bit0 = key) · u64 BE timestamp µs · Annex B data. */
        fun frame(key: Boolean, timestampUs: Long, data: ByteArray): ByteArray =
            ByteBuffer.allocate(9 + data.size).put(if (key) 1 else 0).putLong(timestampUs).put(data).array()

        /** "avc1." + hex of SPS bytes 1..3; [sps] starts at the NAL header byte. */
        fun codecString(sps: ByteArray): String =
            "avc1." + (1..3).joinToString("") { "%02x".format(sps[it]) }

        /** The SPS NAL (type 7) inside Annex B [data], starting at its header byte, or null. */
        fun findSps(data: ByteArray): ByteArray? {
            for (i in 0 until data.size - 3) {
                if (data[i].toInt() == 0 && data[i + 1].toInt() == 0 && data[i + 2].toInt() == 1 && data[i + 3].toInt() and 0x1f == 7) {
                    return data.copyOfRange(i + 3, data.size)
                }
            }
            return null
        }

        /** Fit [w]x[h] so the long side is at most [maxSize], rounded down to multiples of 8 for the encoder. */
        fun scaledSize(w: Int, h: Int, maxSize: Int): Pair<Int, Int> {
            val s = minOf(1.0, maxSize.toDouble() / maxOf(w, h))
            return ((w * s).toInt() and 7.inv()) to ((h * s).toInt() and 7.inv())
        }
    }
}
