package io.github.selimerenkaya.phoneviewer.video

import android.content.Context
import io.github.selimerenkaya.phoneviewer.Feature
import io.github.selimerenkaya.phoneviewer.Send

/** video lane: fill in the body, keep the signature. */
class VideoFeature(private val context: Context, private val send: Send) : Feature {
    override fun onMessage(type: Int, payload: ByteArray) {}
}
