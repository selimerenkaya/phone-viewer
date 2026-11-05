package io.github.selimerenkaya.phoneviewer.video

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch

class VideoFeatureTest {
    private fun hex(s: String) = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    @Test
    fun codecStringsMatchSharedVectors() {
        // shortcut: regex instead of a JSON lib (org.json is a stub in unit tests)
        val json = File("../../docs/protocol-vectors.json").readText()
        val pairs = Regex("\"spsHex\":\\s*\"(\\w+)\",\\s*\"codec\":\\s*\"([\\w.]+)\"").findAll(json).toList()
        assertEquals(2, pairs.size)
        for (m in pairs) {
            val (sps, codec) = m.destructured
            assertEquals(codec, VideoFeature.codecString(hex(sps)))
            // the encoder's config buffer is Annex B: SPS then PPS, each behind a start code
            val csd = hex("00000001$sps" + "0000000168ce3c80")
            assertEquals(codec, VideoFeature.codecString(VideoFeature.findSps(csd)!!))
        }
    }

    @Test
    fun frameHeader() {
        val f = VideoFeature.frame(true, 0x0102030405060708, byteArrayOf(0, 0, 0, 1, 0x65))
        assertArrayEquals(hex("01" + "0102030405060708" + "0000000165"), f)
        assertEquals(0, VideoFeature.frame(false, 0, ByteArray(0))[0].toInt())
    }

    @Test
    fun scaledSize() {
        assertEquals(864 to 1920, VideoFeature.scaledSize(1080, 2400, 1920))
        assertEquals(1080 to 2400, VideoFeature.scaledSize(1080, 2400, 4096))
        assertEquals(1920 to 864, VideoFeature.scaledSize(2400, 1080, 1920))
    }

    @Test
    fun dropsNonKeyFramesWhenBackedUpUntilNextKeyFrame() {
        val gate = CountDownLatch(1)
        val sent = mutableListOf<Int>()
        val f = VideoFeature { _, p -> gate.await(); synchronized(sent) { sent += p[0].toInt() } }
        val d = ByteArray(1)
        // the sender thread holds one frame (blocked on gate); three more fill the queue
        repeat(4) { assertFalse(f.sendFrame(false, 0, d)); Thread.sleep(20) }
        assertTrue(f.sendFrame(false, 0, d)) // first drop: ask for a key frame
        assertFalse(f.sendFrame(false, 0, d)) // still skipping, already asked
        assertFalse(f.sendFrame(true, 0, d)) // key frame replaces the stale queue
        assertFalse(f.sendFrame(false, 0, d))
        gate.countDown()
        Thread.sleep(200)
        assertEquals(listOf(0, 1, 0), synchronized(sent) { sent.toList() })
        f.onDisconnected()
    }
}
