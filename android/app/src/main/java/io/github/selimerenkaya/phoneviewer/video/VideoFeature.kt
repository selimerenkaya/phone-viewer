package io.github.selimerenkaya.phoneviewer.video

import io.github.selimerenkaya.phoneviewer.Feature
import io.github.selimerenkaya.phoneviewer.Send

/** video lane: fill in the body, keep the signature. */
class VideoFeature(private val send: Send) : Feature {
    override fun onMessage(type: Int, payload: ByteArray) {}
}
