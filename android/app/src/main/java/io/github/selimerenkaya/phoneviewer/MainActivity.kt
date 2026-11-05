package io.github.selimerenkaya.phoneviewer

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

// UI shell (lead-owned). Lanes add their own screens/settings links here only via the lead.
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply {
            text = getString(R.string.app_name)
            textSize = 20f
            setPadding(48, 96, 48, 48)
        })
        // control: remote input needs the accessibility service
        if (io.github.selimerenkaya.phoneviewer.control.ControlAccessibilityService.instance == null) addContentView(
            android.widget.Button(this).apply {
                text = getString(R.string.enable_remote_control)
                setOnClickListener { startActivity(android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            },
            android.widget.FrameLayout.LayoutParams(-2, -2, android.view.Gravity.CENTER),
        )
    }
}
