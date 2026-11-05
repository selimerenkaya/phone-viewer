package io.github.selimerenkaya.phoneviewer.notify

import io.github.selimerenkaya.phoneviewer.Feature
import io.github.selimerenkaya.phoneviewer.Send

/** notify lane: fill in the body, keep the signature. */
class NotifyFeature(private val send: Send) : Feature {
    override fun onMessage(type: Int, payload: ByteArray) {}
}
