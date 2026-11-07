package io.github.bornaradusin.cardash.car

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow

/** Google Maps' next turn, as its navigation notification shows it. */
data class NavInfo(
    /** How far to the turn: "300 m". */
    val distance: String?,
    /** What to do: "Turn right onto Main Street". */
    val instruction: String,
    /** When it'll get there: "18:26". */
    val arrive: String?,
    /** "14 min". */
    val timeLeft: String?,
    /** "9.2 km". */
    val distanceLeft: String?,
    /** Google's own turn arrow. */
    val icon: Icon?,
)

/**
 * Sees other apps' notifications (with the person's permission, from Settings). CarDash uses it for
 * two things only: Google Maps' next turn while it navigates, and, through Android, which app is
 * playing music.
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
        if (sbn.packageName != GOOGLE_MAPS) return
        val n = sbn.notification ?: return
        // Only the drive itself: ongoing, navigation
        if (n.flags and Notification.FLAG_ONGOING_EVENT == 0) return
        if (n.category != null && n.category != Notification.CATEGORY_NAVIGATION) return
        val e = n.extras
        fun cs(key: String) = e.getCharSequence(key)?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        val title = cs(Notification.EXTRA_TITLE)
        val text = cs(Notification.EXTRA_BIG_TEXT) ?: cs(Notification.EXTRA_TEXT)
        val sub = cs(Notification.EXTRA_SUB_TEXT)
        // While navigating: the title is the distance to the turn and the text what to do; some
        // versions put both in the title ("300 m · Turn right")
        val titleDistance = title?.let { DISTANCE.find(it)?.value }
        val distance = titleDistance ?: text?.let { DISTANCE.find(it)?.value }
        val instruction = listOfNotNull(
            title?.let { if (titleDistance != null) it.replace(titleDistance, "") else it },
            text,
        ).map { it.trim(' ', '·', '-', '–', ',') }.filter { it.isNotEmpty() }.joinToString(" · ")
        if (instruction.isEmpty() && distance == null) return
        // "14 min · 9.2 km · 18:26 ETA"
        val parts = sub?.split('·')?.map { it.trim() }.orEmpty()
        val timeLeft = parts.firstOrNull { TIME_LEFT.containsMatchIn(it) }
        val distanceLeft = parts.firstOrNull { DISTANCE.matches(it) }
        val arrive = parts.firstNotNullOfOrNull { CLOCK.find(it)?.value }
        navKey = sbn.key
        nav.value = NavInfo(
            distance = distance,
            instruction = instruction.ifEmpty { "Follow the route" },
            arrive = arrive,
            timeLeft = timeLeft,
            distanceLeft = distanceLeft,
            icon = n.getLargeIcon(),
        )
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.key == navKey) {
            navKey = null
            nav.value = null
        }
    }

    companion object {
        const val GOOGLE_MAPS = "com.google.android.apps.maps"
        private var navKey: String? = null

        /** Google Maps' next turn while it's navigating; null otherwise. */
        val nav = MutableStateFlow<NavInfo?>(null)
        val DISTANCE = Regex("\\d+(?:[.,]\\d+)?\\s?(m|km|ft|mi|yd)\\b", RegexOption.IGNORE_CASE)
        private val TIME_LEFT = Regex("\\d+\\s?(min|mins|h|hr|hrs)\\b", RegexOption.IGNORE_CASE)
        private val CLOCK = Regex("\\b\\d{1,2}:\\d{2}(\\s?[AaPp][Mm])?")
        val connected = MutableStateFlow(false)

        fun allowed(context: Context): Boolean =
            context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)

        fun component(context: Context) = ComponentName(context, CarNotifications::class.java)
    }
}
