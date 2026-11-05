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
    }
}
