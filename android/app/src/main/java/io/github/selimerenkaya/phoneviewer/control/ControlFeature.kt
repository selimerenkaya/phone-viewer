package io.github.selimerenkaya.phoneviewer.control

import android.content.Context
import io.github.selimerenkaya.phoneviewer.Feature
import io.github.selimerenkaya.phoneviewer.Send

/** control lane: fill in the body, keep the signature. */
class ControlFeature(private val context: Context, private val send: Send) : Feature {
    override fun onMessage(type: Int, payload: ByteArray) {}
}
