package io.github.selimerenkaya.phoneviewer.link

import java.io.DataInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.security.MessageDigest

// Framing and pairing math from docs/PROTOCOL.md.
const val HELLO = 0x01
const val PAIR_REQUEST = 0x02
const val PAIR_RESULT = 0x03
const val PING = 0x04
const val PONG = 0x05
const val PAIR_NONCE = 0x06
const val PAIR_REVEAL = 0x07
const val MAX_LEN = 8 * 1024 * 1024

fun encodeFrame(type: Int, payload: ByteArray): ByteArray =
    ByteBuffer.allocate(5 + payload.size).put(type.toByte()).putInt(payload.size).put(payload).array()

/** Reads one frame as (type, payload). Throws on EOF or a length over MAX_LEN; the caller closes the connection. */
fun DataInputStream.readFrame(): Pair<Int, ByteArray> {
    val type = readUnsignedByte()
    val len = readInt() // signed: a length >= 2^31 shows up negative
    if (len !in 0..MAX_LEN) throw IOException("bad frame length $len")
    return type to ByteArray(len).also { readFully(it) }
}

fun sha256(vararg parts: ByteArray): ByteArray =
    MessageDigest.getInstance("SHA-256").run { parts.forEach { update(it) }; digest() }

/** 6-digit code both screens show: first 4 bytes of SHA-256(phoneFP ‖ pcFP ‖ phoneNonce ‖ pcNonce) as u32 BE, mod 1e6. */
fun pairingCode(phoneFp: ByteArray, pcFp: ByteArray, phoneNonce: ByteArray, pcNonce: ByteArray): String {
    val u32 = ByteBuffer.wrap(sha256(phoneFp, pcFp, phoneNonce, pcNonce)).int.toLong() and 0xffffffffL
    return (u32 % 1_000_000).toString().padStart(6, '0') // not String.format: some locales print non-ASCII digits
}
