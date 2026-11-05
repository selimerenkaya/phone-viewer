package io.github.selimerenkaya.phoneviewer.control

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.accessibilityservice.GestureDescription.StrokeDescription
import android.content.Intent
import android.graphics.Path
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * control lane: performs gestures, global actions and text input.
 * All gesture state lives on the main thread; gestures run one at a time from [queue].
 */
class ControlAccessibilityService : AccessibilityService() {
    companion object {
        /** Set while the user has the service enabled; [ControlFeature] reaches it through this. */
        @Volatile var instance: ControlAccessibilityService? = null
    }

    private class Ev(val action: String, val x: Float, val y: Float, val y2: Float = 0f)

    private val main = Handler(Looper.getMainLooper())
    private val queue = ArrayDeque<Ev>()
    private var busy = false
    private var stroke: StrokeDescription? = null // non-null while the remote finger is down
    private var lastX = 0f
    private var lastY = 0f
    private var lastTime = 0L

    override fun onServiceConnected() { instance = this }
    override fun onUnbind(intent: Intent?): Boolean { instance = null; return super.onUnbind(intent) }
    override fun onDestroy() { instance = null; super.onDestroy() }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    private fun px(x: Double, y: Double): Pair<Float, Float> {
        val m = DisplayMetrics()
        @Suppress("DEPRECATION") // still the simplest full-screen, current-rotation size on API 29+
        getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY).getRealMetrics(m)
        return toPixels(x, y, m.widthPixels, m.heightPixels)
    }

    fun touch(action: String, x: Double, y: Double) {
        val (px, py) = px(x, y)
        main.post { enqueue(Ev(action, px, py)) }
    }

    /** A short swipe from (x, y) by dy screen fractions. */
    fun scroll(x: Double, y: Double, dy: Double) {
        val (px, py) = px(x, y)
        val (_, py2) = px(x, y + dy)
        main.post { enqueue(Ev("scroll", px, py, py2)) }
    }

    /** Lift any remote finger that is still down. */
    fun release() = main.post {
        queue.clear()
        if (stroke != null) enqueue(Ev("up", lastX, lastY))
    }

    fun global(action: String) = main.post {
        performGlobalAction(when (action) {
            "back" -> GLOBAL_ACTION_BACK
            "home" -> GLOBAL_ACTION_HOME
            "recents" -> GLOBAL_ACTION_RECENTS
            "notifications" -> GLOBAL_ACTION_NOTIFICATIONS
            "power_dialog" -> GLOBAL_ACTION_POWER_DIALOG
            else -> return@post
        })
    }

    /** Insert [t] at the cursor of the focused editable field (end of text if no cursor). */
    fun text(t: String) = main.post { focusedField()?.let { edit(it, t, backspace = false) } }

    /** "backspace" deletes the selection or the character before the cursor; "enter" presses the keyboard's action key. */
    fun key(k: String) = main.post {
        val node = focusedField() ?: return@post
        when (k) {
            "backspace" -> edit(node, "", backspace = true)
            "enter" -> if (Build.VERSION.SDK_INT >= 30) node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
                       else edit(node, "\n", backspace = false) // Android 10 has no IME-enter action
        }
    }

    private fun focusedField() = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.takeIf { it.isEditable }

    private fun edit(node: AccessibilityNodeInfo, insert: String, backspace: Boolean) {
        // shortcut: password fields report masked text, so typing there replaces it with dots; fine for v1
        val old = if (node.isShowingHintText) "" else node.text?.toString() ?: ""
        val (text, cursor) = applyEdit(old, node.textSelectionStart, node.textSelectionEnd, insert, backspace)
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        })
        node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, Bundle().apply {
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, cursor)
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, cursor)
        })
    }

    private fun enqueue(e: Ev) {
        if (e.action == "move" && queue.lastOrNull()?.action == "move") queue.removeLast() // keep only the newest move
        queue.addLast(e)
        pump()
    }

    private val done = object : GestureResultCallback() {
        override fun onCompleted(g: GestureDescription?) { busy = false; pump() }
        override fun onCancelled(g: GestureDescription?) { busy = false; stroke = null; pump() } // continuation is broken
    }

    // A new dispatch cancels the running one, so each stroke piece waits for the previous to finish.
    private fun pump() {
        if (busy) return
        val e = queue.removeFirstOrNull() ?: return
        val now = SystemClock.uptimeMillis()
        val dur = (now - lastTime).coerceIn(1, 100) // replay moves at roughly the PC's pace so flings keep their speed
        val s = when (e.action) {
            "down" -> StrokeDescription(line(e.x, e.y, e.x, e.y), 0, 1, true) // willContinue: finger stays down (long-press)
            "move", "up" -> stroke?.continueStroke(line(lastX, lastY, e.x, e.y), 0, dur, e.action == "move") ?: return pump()
            "scroll" -> if (stroke != null) return pump() else StrokeDescription(line(e.x, e.y, e.x, e.y2), 0, 200)
            else -> return pump()
        }
        stroke = s.takeIf { e.action == "down" || e.action == "move" }
        lastX = e.x; lastY = e.y; lastTime = now
        busy = dispatchGesture(GestureDescription.Builder().addStroke(s).build(), done, null)
        if (!busy) { stroke = null; pump() }
    }

    private fun line(x1: Float, y1: Float, x2: Float, y2: Float) = Path().apply { moveTo(x1, y1); lineTo(x2, y2) }
}
