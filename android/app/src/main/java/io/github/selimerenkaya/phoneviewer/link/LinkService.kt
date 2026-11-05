package io.github.selimerenkaya.phoneviewer.link

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import io.github.selimerenkaya.phoneviewer.control.ControlFeature
import io.github.selimerenkaya.phoneviewer.notify.NotifyFeature
import io.github.selimerenkaya.phoneviewer.video.VideoFeature
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.IOException
import java.io.OutputStream
import java.security.SecureRandom
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLSocket
import kotlin.concurrent.thread

/** link lane: foreground service that owns the TLS server socket and routes frames to features. */
class LinkService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    private val features by lazy { listOf(VideoFeature(::send), ControlFeature(::send), NotifyFeature(::send)) } // 0x10, 0x20, 0x30
    private val pins by lazy { getSharedPreferences("link", MODE_PRIVATE) }

    /** Output of the running session; null before pairing finishes and after disconnect. */
    @Volatile private var session: OutputStream? = null
    private var started = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel("link", "PC connection", NotificationManager.IMPORTANCE_LOW))
        val note = Notification.Builder(this, "link")
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentTitle("Phone Viewer is reachable on Wi-Fi")
            .build()
        startForeground(1, note, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        if (!started) {
            started = true
            thread(name = "link") { serve() }
        }
        return START_STICKY
    }

    /** One PC at a time: accept, run that connection to its end, accept the next. */
    private fun serve() {
        val server = try {
            Tls.serverSocket(PORT)
        } catch (e: IOException) {
            return setStatus("Cannot listen on port $PORT: ${e.message}")
        }
        while (true) {
            setStatus("Waiting for a PC")
            try {
                (server.accept() as SSLSocket).use { handle(it) }
            } catch (e: Exception) {
                // A dropped link or a peer breaking the protocol: close it and wait for the next one.
            } finally {
                code = null
                if (session != null) {
                    session = null
                    features.forEach { it.onDisconnected() }
                }
            }
        }
    }

    private fun handle(socket: SSLSocket) {
        socket.soTimeout = 15_000 // the PC pings every 5 s once the session runs
        socket.startHandshake()
        val pcFp = sha256(socket.session.peerCertificates[0].encoded).toHexString()
        val input = DataInputStream(BufferedInputStream(socket.inputStream))
        val out = socket.outputStream
        var trusted = pcFp in pinned()

        write(out, HELLO, JSONObject().put("version", VERSION).put("name", Build.MODEL).put("paired", trusted))
        var (type, payload) = input.readFrame()
        if (type == PAIR_REQUEST) {
            val request = JSONObject(String(payload))
            val phoneNonce = ByteArray(32).also { SecureRandom().nextBytes(it) }
            write(out, PAIR_NONCE, JSONObject().put("nonce", phoneNonce.toHexString()))
            val (revealType, reveal) = input.readFrame()
            check(revealType == PAIR_REVEAL)
            val pcNonce = JSONObject(String(reveal)).getString("nonce").hexToByteArray()
            check(sha256(pcNonce).contentEquals(request.getString("commit").hexToByteArray())) { "commit mismatch" }

            decisions.clear()
            code = pairingCode(Tls.fingerprint, pcFp.hexToByteArray(), phoneNonce, pcNonce)
            setStatus("${request.optString("pcName", "A PC")} wants to pair")
            trusted = decisions.poll(2, TimeUnit.MINUTES) == true
            code = null
            write(out, PAIR_RESULT, JSONObject().put("ok", trusted))
            if (!trusted) return
            pins.edit().putStringSet("pins", pinned() + pcFp).apply()
            input.readFrame().let { type = it.first; payload = it.second }
        }
        check(type == HELLO && trusted)

        session = out
        setStatus("Connected to ${JSONObject(String(payload)).optString("name", "PC")}")
        thread(isDaemon = true, name = "link-ping") {
            while (session === out) {
                Thread.sleep(5_000)
                send(PING, ByteArray(0))
            }
        }
        while (true) {
            val (t, p) = input.readFrame()
            if (t == PING) send(PONG, ByteArray(0))
            else if (t in 0x10..0x3f) features[(t shr 4) - 1].onMessage(t, p)
            // other link frames and unknown types are ignored
        }
    }

    private fun pinned(): Set<String> = pins.getStringSet("pins", emptySet())!!

    /** Real Send for the features. Frames sent while no session is up, or after it broke, are dropped. */
    private fun send(type: Int, payload: ByteArray) {
        val out = session ?: return
        try {
            write(out, type, payload)
        } catch (_: IOException) {
            // the reader thread notices the broken link and ends the session
        }
    }

    private fun write(out: OutputStream, type: Int, payload: ByteArray) = synchronized(out) { out.write(encodeFrame(type, payload)) }
    private fun write(out: OutputStream, type: Int, json: JSONObject) = write(out, type, json.toString().toByteArray())

    companion object {
        const val PORT = 27183
        private const val VERSION = 1

        /** What the UI shows. [code] is set while a PC waits for Allow/Deny. */
        @Volatile var status = "Starting"; private set
        @Volatile var code: String? = null; private set
        /** Called on the link thread whenever [status] or [code] changes. */
        @Volatile var onChange: (() -> Unit)? = null
        private val decisions = LinkedBlockingQueue<Boolean>()

        /** The user tapped Allow (true) or Deny (false) for the pairing code on screen. */
        fun decide(allow: Boolean) { decisions.offer(allow) }

        private fun setStatus(s: String) { status = s; onChange?.invoke() }
    }
}
