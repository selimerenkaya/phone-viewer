package io.github.selimerenkaya.phoneviewer

/** Contract between the link lane and each feature lane (see docs/AGENTS.md). Lead-owned. */
typealias Send = (type: Int, payload: ByteArray) -> Unit

interface Feature {
    /** Called by the link router for every frame whose type is in this feature's range. */
    fun onMessage(type: Int, payload: ByteArray)
    fun onDisconnected() {}
}
