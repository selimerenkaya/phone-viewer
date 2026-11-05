package io.github.selimerenkaya.phoneviewer.control

import android.content.Context
import io.github.selimerenkaya.phoneviewer.Feature
import io.github.selimerenkaya.phoneviewer.Send
import org.json.JSONException
import org.json.JSONObject

/**
 * Screen fractions → gesture pixels. [w]×[h] is the display's real size in its *current* rotation
 * (what Display.getRealMetrics reports): the same frame the video shows, so rotation needs no extra swap.
 */
internal fun toPixels(x: Double, y: Double, w: Int, h: Int): Pair<Float, Float> =
    (x.coerceIn(0.0, 1.0) * (w - 1)).toFloat() to (y.coerceIn(0.0, 1.0) * (h - 1)).toFloat()

/**
 * New field text and cursor after typing [insert] (or a backspace) over the selection [start]..[end] of [old].
 * Out-of-range selections (no cursor reported) mean "at the end". Backspace never splits a surrogate pair.
 */
internal fun applyEdit(old: String, start: Int, end: Int, insert: String, backspace: Boolean): Pair<String, Int> {
    var from = if (start in 0..old.length) start else old.length
    val to = end.coerceIn(from, old.length)
    if (backspace && from == to && from > 0) from -= if (from >= 2 && old[from - 1].isLowSurrogate() && old[from - 2].isHighSurrogate()) 2 else 1
    return old.substring(0, from) + insert + old.substring(to) to from + insert.length
}

/** control lane: PC → phone input. Performed by [ControlAccessibilityService]; ignored while it is off. */
class ControlFeature(private val context: Context, private val send: Send) : Feature {
    override fun onMessage(type: Int, payload: ByteArray) {
        val service = ControlAccessibilityService.instance ?: return
        try {
            val m = JSONObject(String(payload, Charsets.UTF_8))
            when (type) {
                0x20 -> service.touch(m.getString("action"), m.getDouble("x"), m.getDouble("y"))
                0x21 -> service.global(m.getString("action"))
                0x22 -> service.text(m.getString("text"))
                0x23 -> service.scroll(m.getDouble("x"), m.getDouble("y"), m.getDouble("dy"))
                0x24 -> service.key(m.getString("key"))
            }
        } catch (e: JSONException) {} // malformed frame: drop it, keep the connection
    }

    override fun onDisconnected() {
        ControlAccessibilityService.instance?.release()
    }
}
