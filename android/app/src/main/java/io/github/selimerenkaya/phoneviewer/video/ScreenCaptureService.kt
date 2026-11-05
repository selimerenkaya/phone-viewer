package io.github.selimerenkaya.phoneviewer.video

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Display
import android.view.Surface
import kotlin.concurrent.thread

/**
 * Foreground service (type mediaProjection) that runs the encoder. Started by [ScreenConsentActivity]
 * with the consent token. The projection and its one virtual display live until the PC disconnects;
 * VIDEO_STOP/START and rotation only swap the encoder, because Android 14+ allows one virtual display per token.
 */
class ScreenCaptureService : Service() {
    private lateinit var displays: DisplayManager
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var codec: MediaCodec? = null
    private var surface: Surface? = null
    private var drain: Thread? = null
    @Volatile private var draining = false
    private var params: VideoFeature.Params? = null
    private var size = Point()

    private val rotation = object : DisplayManager.DisplayListener {
        override fun onDisplayChanged(id: Int) {
            val p = params ?: return
            if (id == Display.DEFAULT_DISPLAY && screenSize() != size) startStream(p)
        }
        override fun onDisplayAdded(id: Int) {}
        override fun onDisplayRemoved(id: Int) {}
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        nm.cancel(VideoFeature.CONSENT_NOTIFICATION)
        startForeground(1, Notification.Builder(this, VideoFeature.CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle("Sharing screen with PC")
            .build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        @Suppress("DEPRECATION")
        val data = intent?.getParcelableExtra<Intent>(EXTRA_DATA)
        val p = VideoFeature.current?.params
        if (data == null || p == null || projection != null) {
            if (projection == null) stopSelf()
            return START_NOT_STICKY
        }
        val proj = getSystemService(MediaProjectionManager::class.java)
            .getMediaProjection(intent.getIntExtra(EXTRA_RESULT_CODE, 0), data)
            ?: run { stopSelf(); return START_NOT_STICKY }
        proj.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() = stopSelf() // user ended sharing from the system UI
        }, Handler(Looper.getMainLooper()))
        projection = proj
        displays = getSystemService(DisplayManager::class.java)
        displays.registerDisplayListener(rotation, Handler(Looper.getMainLooper()))
        running = this
        startStream(p)
        return START_NOT_STICKY
    }

    @Suppress("DEPRECATION") // getRealSize: one call that works on API 29..37
    private fun screenSize() = Point().also { displays.getDisplay(Display.DEFAULT_DISPLAY).getRealSize(it) }

    /** (Re)starts the encoder at the current screen size; the first output is the config, then a key frame. */
    @Synchronized
    fun startStream(p: VideoFeature.Params) {
        val proj = projection ?: return
        stopStream()
        params = p
        size = screenSize()
        val (w, h) = VideoFeature.scaledSize(size.x, size.y, p.maxSize)
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, p.bitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, 60)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 10)
            setLong(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, 100_000) // a still screen still answers key-frame requests
        }
        val c = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        c.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val s = c.createInputSurface()
        c.start()
        val vd = display
        if (vd == null) {
            display = proj.createVirtualDisplay("phone-viewer", w, h, resources.displayMetrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, s, null, null)
        } else {
            vd.resize(w, h, resources.displayMetrics.densityDpi)
            vd.surface = s
        }
        codec = c
        surface = s
        draining = true
        drain = thread(name = "video-encode") { drain(c, w, h) }
    }

    private fun drain(c: MediaCodec, w: Int, h: Int) {
        val info = MediaCodec.BufferInfo()
        var csd = ByteArray(0)
        while (draining) {
            val i = c.dequeueOutputBuffer(info, 100_000)
            if (i < 0) continue
            val data = ByteArray(info.size)
            c.getOutputBuffer(i)!!.apply { position(info.offset) }.get(data)
            c.releaseOutputBuffer(i, false)
            val queue = VideoFeature.current?.queue ?: continue
            if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                csd = data
                queue.sendConfig(VideoFeature.codecString(VideoFeature.findSps(data)!!), w, h)
            } else {
                val key = info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0
                if (queue.sendFrame(key, info.presentationTimeUs, if (key) csd + data else data)) {
                    c.setParameters(Bundle().apply { putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0) })
                }
            }
        }
    }

    @Synchronized
    fun stopStream() {
        params = null
        draining = false
        drain?.join()
        drain = null
        display?.surface = null
        codec?.run { stop(); release() }
        codec = null
        surface?.release()
        surface = null
    }

    override fun onDestroy() {
        if (running === this) running = null
        if (projection != null) {
            displays.unregisterDisplayListener(rotation)
            stopStream()
            display?.release()
            projection?.stop()
        }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_DATA = "data"
        /** The live service, so [VideoFeature] can drive it. */
        @Volatile var running: ScreenCaptureService? = null
    }
}
