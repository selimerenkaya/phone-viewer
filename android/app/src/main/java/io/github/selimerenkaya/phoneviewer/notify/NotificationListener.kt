package io.github.selimerenkaya.phoneviewer.notify

import android.app.Notification
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import org.json.JSONObject

/** notify lane: forwards posted/removed notifications to the PC while a session is active. */
class NotificationListener : NotificationListenerService() {
    override fun onCreate() {
        super.onCreate()
        NotifyFeature.app = applicationContext
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val feature = NotifyFeature.current ?: return
        if (!shouldForward(sbn.notification.flags, sbn.packageName, packageName)) return
        val extras = sbn.notification.extras
        val json = JSONObject()
            .put("id", sbn.key)
            .put("app", appLabel(sbn.packageName))
            .put("title", extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: "")
            .put("text", extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: "")
            .put("posted", sbn.postTime)
        feature.send(NOTIFICATION, json.toString().toByteArray())
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        val feature = NotifyFeature.current ?: return
        if (!shouldForward(sbn.notification.flags, sbn.packageName, packageName)) return
        feature.send(NOTIFICATION_REMOVED, JSONObject().put("id", sbn.key).toString().toByteArray())
    }

    private fun appLabel(pkg: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        pkg
    }
}
