package io.github.selimerenkaya.phoneviewer

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.github.selimerenkaya.phoneviewer.control.ControlAccessibilityService
import io.github.selimerenkaya.phoneviewer.link.LinkPanel
import io.github.selimerenkaya.phoneviewer.link.LinkService
import io.github.selimerenkaya.phoneviewer.notify.NotificationListener
import io.github.selimerenkaya.phoneviewer.notify.NotifyFeature

// UI shell (lead-owned): one column with the link panel and a button per missing permission.
class MainActivity : Activity() {
    private lateinit var control: Button
    private lateinit var notifications: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 120, 48, 48)
            addView(TextView(context).apply { text = getString(R.string.app_name); textSize = 24f })
            addView(LinkPanel(context)) // link: IP, status, pairing code with Allow/Deny
            control = button(getString(R.string.enable_remote_control)) {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            notifications = button("Allow notification access") {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
            button("Forget paired PCs") {
                AlertDialog.Builder(context)
                    .setMessage("Every PC will have to pair again with a new code.")
                    .setPositiveButton("Forget") { _, _ -> LinkService.forgetAll(context) }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
        }
        setContentView(ScrollView(this).apply { addView(column) })
        startForegroundService(Intent(this, LinkService::class.java)) // link: listen for the PC
        // Android 13+ hides our notifications (incl. "tap to share your screen") until the user allows them.
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    /** Hide each permission button once the user has granted it in Settings. */
    override fun onResume() {
        super.onResume()
        control.visibility = if (ControlAccessibilityService.instance == null) View.VISIBLE else View.GONE
        val listener = ComponentName(this, NotificationListener::class.java)
        val granted = getSystemService(NotificationManager::class.java).isNotificationListenerAccessGranted(listener)
        notifications.visibility = if (granted) View.GONE else View.VISIBLE
    }

    // notify: Android 10+ lets only the focused app read the clipboard
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) NotifyFeature.current?.sendClipboard(this)
    }

    private fun LinearLayout.button(label: String, onClick: () -> Unit) = Button(context).apply {
        text = label
        isAllCaps = false // all-caps turns "i" into "İ" in Turkish
        setOnClickListener { onClick() }
    }.also(::addView)
}
