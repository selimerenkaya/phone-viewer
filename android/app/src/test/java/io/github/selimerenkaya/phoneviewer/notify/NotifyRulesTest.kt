package io.github.selimerenkaya.phoneviewer.notify

import android.app.Notification
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotifyRulesTest {
    private val own = "io.github.selimerenkaya.phoneviewer"

    @Test fun forwardsPlainNotification() {
        assertTrue(shouldForward(Notification.FLAG_AUTO_CANCEL, "com.whatsapp", own))
    }

    @Test fun skipsOngoingServiceSummaryAndOwn() {
        assertFalse(shouldForward(Notification.FLAG_ONGOING_EVENT, "com.spotify", own))
        assertFalse(shouldForward(Notification.FLAG_FOREGROUND_SERVICE, "com.maps", own))
        assertFalse(shouldForward(Notification.FLAG_GROUP_SUMMARY, "com.whatsapp", own))
        assertFalse(shouldForward(0, own, own))
    }

    @Test fun echoGuardDropsValueThatCameFromPc() {
        val guard = EchoGuard()
        guard.received("from pc")
        assertFalse(guard.shouldSend("from pc"))
        assertTrue(guard.shouldSend("copied on phone"))
        assertFalse(guard.shouldSend("copied on phone"))
        assertTrue(guard.shouldSend("from pc"))
    }
}
