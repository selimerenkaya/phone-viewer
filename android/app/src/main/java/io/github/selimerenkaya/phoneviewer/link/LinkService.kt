package io.github.selimerenkaya.phoneviewer.link

import android.app.Service
import android.content.Intent
import android.os.IBinder

/** link lane: foreground service that owns the TLS server socket and routes frames to features. */
class LinkService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
}
