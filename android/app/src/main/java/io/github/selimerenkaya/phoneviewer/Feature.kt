package io.github.selimerenkaya.phoneviewer

/**
 * Contract between the link lane and each feature lane (see docs/AGENTS.md). Lead-owned.
 * Link constructs every feature once as `XFeature(applicationContext, send)` when LinkService starts.
 */
typealias Send = (type: Int, payload: ByteArray) -> Unit

interface Feature {
    /** Called by link once the session starts (after HELLO in both directions). `send` works from here on. */
    fun onConnected() {}
    /** Called by the link router for every frame whose type is in this feature's range. */
    fun onMessage(type: Int, payload: ByteArray)
    fun onDisconnected() {}
}
