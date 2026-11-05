package io.github.selimerenkaya.phoneviewer.video

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle

/** Shows Android's screen-capture consent dialog, then hands the token to [ScreenCaptureService]. */
class ScreenConsentActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) return // dialog already showing after a config change
        val mpm = getSystemService(MediaProjectionManager::class.java)
        startActivityForResult(
            // Whole screen only: mirroring a single app would break control's coordinates.
            if (Build.VERSION.SDK_INT >= 34) mpm.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
            else mpm.createScreenCaptureIntent(),
            0,
        )
    }

    @Deprecated("Activity result API needs androidx")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == RESULT_OK && data != null && VideoFeature.current?.params != null) {
            startForegroundService(Intent(this, ScreenCaptureService::class.java)
                .putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode)
                .putExtra(ScreenCaptureService.EXTRA_DATA, data))
        }
        finish()
    }
}
