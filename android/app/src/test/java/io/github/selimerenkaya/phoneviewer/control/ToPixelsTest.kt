package io.github.selimerenkaya.phoneviewer.control

import org.junit.Assert.assertEquals
import org.junit.Test

class ToPixelsTest {
    @Test fun portraitCornersAndCentre() {
        assertEquals(0f to 0f, toPixels(0.0, 0.0, 1080, 2400))
        assertEquals(1079f to 2399f, toPixels(1.0, 1.0, 1080, 2400))
        assertEquals(539.5f to 1199.5f, toPixels(0.5, 0.5, 1080, 2400))
    }

    @Test fun rotatedToLandscapeXRunsAlongTheLongSide() {
        // Same phone turned 90°: getRealMetrics now reports 2400×1080, and the video frame is landscape too.
        assertEquals(2399f to 0f, toPixels(1.0, 0.0, 2400, 1080))
        assertEquals(1199.5f to 539.5f, toPixels(0.5, 0.5, 2400, 1080))
    }

    @Test fun outOfRangeIsClamped() {
        assertEquals(0f to 2399f, toPixels(-0.2, 1.3, 1080, 2400))
    }
}
