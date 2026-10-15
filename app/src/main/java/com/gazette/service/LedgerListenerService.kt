package com.gazette.service

import android.app.Notification
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.gazette.engine.LedgerEngine

// Catches every posted notification, builds the candidate text, dedupes, and
// hands it to the engine queue. No package allowlist: any app's notification
// is a candidate, the pipeline decides. Nothing is persisted or shown here.
class LedgerListenerService : NotificationListenerService() {

    private val seen = HashMap<String, Long>()

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val notification = sbn.notification ?: return
        if (sbn.packageName == packageName) return
        if (notification.flags and Notification.FLAG_ONGOING_EVENT != 0) return
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val extras = notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        val body = if (bigText.isNotBlank()) bigText else text
        val candidate = listOf(title, body).filter { it.isNotBlank() }.joinToString(" ")
        if (candidate.isBlank()) return

        val now = SystemClock.elapsedRealtime()
        seen.entries.removeAll { now - it.value > DEDUPE_WINDOW_MS }
        if (seen[candidate] != null) return
        seen[candidate] = now

        LedgerEngine.submit(candidate, sbn.packageName)
    }

    private companion object {
        const val DEDUPE_WINDOW_MS = 60_000L
    }
}
