package io.github.selimerenkaya.phoneviewer.link

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.DataInputStream
import java.io.File
import java.io.IOException

class WireTest {
    private val vectors = File("../../docs/protocol-vectors.json").readText()

    /** Minimal reader for the flat string/number fields of protocol-vectors.json (org.json is stubbed in JVM tests). */
    private fun records(section: String, nextSection: String): List<Map<String, String>> {
        val text = vectors.substringAfter("\"$section\"").substringBefore("\"$nextSection\"")
        val out = mutableListOf<MutableMap<String, String>>()
        Regex(""""(\w+)":\s*(?:"((?:[^"\\]|\\.)*)"|(\d+))""").findAll(text).forEach { m ->
            val (key, str, num) = m.destructured
            if (out.isEmpty() || key in out.last()) out += mutableMapOf()
            out.last()[key] = num.ifEmpty { str.replace("\\\"", "\"") }
        }
        return out
    }

    @Test
    fun framesMatchSharedVectors() {
        val frames = records("frames", "pairingCodes")
        assertEquals(3, frames.size)
        for (v in frames) {
            val type = v.getValue("type").toInt()
            val payload = v["payloadUtf8"]?.toByteArray() ?: v.getValue("payloadHex").hexToByteArray()
            val frame = v.getValue("frameHex").hexToByteArray()
            assertArrayEquals(frame, encodeFrame(type, payload))
            val (t, p) = DataInputStream(frame.inputStream()).readFrame()
            assertEquals(type, t)
            assertArrayEquals(payload, p)
        }
    }

    @Test
    fun oversizedFramesAreRejected() {
        for (hex in listOf("0100800001", "01ffffffff")) {
            assertThrows(IOException::class.java) { DataInputStream(hex.hexToByteArray().inputStream()).readFrame() }
        }
    }

    @Test
    fun commitsAndCodesMatchSharedVectors() {
        val codes = records("pairingCodes", "codecStrings")
        assertEquals(3, codes.size)
        for (v in codes) {
            fun b(k: String) = v.getValue(k).hexToByteArray()
            assertEquals(v["commit"], sha256(b("pcNonce")).toHexString())
            assertEquals(v["code"], pairingCode(b("phoneFingerprint"), b("pcFingerprint"), b("phoneNonce"), b("pcNonce")))
        }
    }
}
