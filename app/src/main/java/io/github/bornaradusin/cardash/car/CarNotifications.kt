package io.github.bornaradusin.cardash.car

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Waze's next turn, as its navigation notification shows it. */
data class NavInfo(
    val title: String,
    val text: String?,
    val sub: String?,
    /** Waze's own turn arrow, when it gives one. */
    val icon: Icon?,
)

/**
 * Sees other apps' notifications (with the person's permission, from Settings). CarDash uses it for
 * two things only: Waze's next turn, and, through Android, which app is playing music.
 */
class CarNotifications : NotificationListenerService() {

    override fun onListenerConnected() {
        connected.value = true
        runCatching { activeNotifications?.forEach { onNotificationPosted(it) } }
    }

    override fun onListenerDisconnected() {
        connected.value = false
        nav.value = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName != WAZE) return
        val n = sbn.notification ?: return
        // Only the ongoing navigation one, not Waze's other alerts
        if (n.flags and Notification.FLAG_ONGOING_EVENT == 0) return
        val e = n.extras
        val title = e.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val text = (e.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: e.getCharSequence(Notification.EXTRA_TEXT))?.toString()?.trim()
        val sub = e.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.trim()
        if (title.isEmpty() && text.isNullOrEmpty()) return
        navKey = sbn.key
        nav.value = NavInfo(title.ifEmpty { text.orEmpty() }, text.takeIf { title.isNotEmpty() }, sub, n.getLargeIcon())
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.key == navKey) {
            navKey = null
            nav.value = null
        }
    }

    companion object {
        const val WAZE = "com.waze"
        private var navKey: String? = null

        val nav = MutableStateFlow<NavInfo?>(null)
        val connected = MutableStateFlow(false)

        fun allowed(context: Context): Boolean =
            context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)

        fun component(context: Context) = ComponentName(context, CarNotifications::class.java)

        val navFlow: StateFlow<NavInfo?> get() = nav
    }
}
