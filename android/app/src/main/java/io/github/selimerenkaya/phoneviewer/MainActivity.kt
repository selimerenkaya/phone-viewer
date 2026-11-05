package io.github.selimerenkaya.phoneviewer

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import android.content.Intent
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import io.github.selimerenkaya.phoneviewer.link.LinkPanel
import io.github.selimerenkaya.phoneviewer.link.LinkService
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.FrameLayout
import io.github.selimerenkaya.phoneviewer.notify.NotifyFeature

// UI shell (lead-owned). Lanes add their own screens/settings links here only via the lead.
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply {
            text = getString(R.string.app_name)
            textSize = 20f
            setPadding(48, 96, 48, 48)
        })
        startForegroundService(Intent(this, LinkService::class.java)) // link: listen for the PC
        addContentView(LinkPanel(this), ViewGroup.LayoutParams(MATCH_PARENT, WRAP_CONTENT)) // link: IP, status, pairing
        // notify: opens Notification access settings
        addContentView(Button(this).apply {
            text = "Notification access"
            isAllCaps = false // all-caps turns "i" into "İ" in Turkish
            setOnClickListener { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        }, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.CENTER)) // centered: edge-to-edge puts the bottom under the nav bar
    }

    // notify: Android 10+ lets only the focused app read the clipboard
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) NotifyFeature.current?.sendClipboard(this)
    }
}
