package io.github.selimerenkaya.phoneviewer.control

import org.junit.Assert.assertEquals
import org.junit.Test

class ApplyEditTest {
    @Test fun insertsAtCursor() = assertEquals("abXc" to 3, applyEdit("abc", 2, 2, "X", false))
    @Test fun replacesSelection() = assertEquals("aXd" to 2, applyEdit("abcd", 1, 3, "X", false))
    @Test fun noCursorMeansEnd() = assertEquals("abcX" to 4, applyEdit("abc", -1, -1, "X", false))
    @Test fun backspaceDeletesCharBeforeCursor() = assertEquals("ac" to 1, applyEdit("abc", 2, 2, "", true))
    @Test fun backspaceDeletesSelection() = assertEquals("ad" to 1, applyEdit("abcd", 1, 3, "", true))
    @Test fun backspaceAtStartDoesNothing() = assertEquals("abc" to 0, applyEdit("abc", 0, 0, "", true))
    @Test fun backspaceRemovesWholeEmoji() = assertEquals("a" to 1, applyEdit("a😀", 3, 3, "", true))
}
