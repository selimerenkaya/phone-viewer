package io.github.selimerenkaya.phoneviewer.notify

import android.app.Activity
import android.widget.Toast

/** notify lane: see-through activity; reads the clipboard once focused (Android 10+ rule), sends, finishes. */
class ClipboardActivity : Activity() {
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) return
        val feature = NotifyFeature.current
        if (feature == null) Toast.makeText(this, "Not connected to a PC", Toast.LENGTH_SHORT).show()
        else feature.sendClipboard(this)
        finish()
    }
}
