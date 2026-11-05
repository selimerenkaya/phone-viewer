package io.github.selimerenkaya.phoneviewer.control

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/** control lane: performs gestures, global actions and text input. */
class ControlAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
}
