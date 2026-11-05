package io.github.selimerenkaya.phoneviewer.video

import android.app.Service
import android.content.Intent
import android.os.IBinder

/** video lane: foreground service (type mediaProjection) that runs the encoder. */
class ScreenCaptureService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
}
