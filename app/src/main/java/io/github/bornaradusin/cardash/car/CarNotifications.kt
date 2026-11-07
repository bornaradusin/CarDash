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
        // "14 min · 9.2 km · 18:26 ETA", or "11:06 pm ETA"; some versions put parts elsewhere
        val parts = listOfNotNull(sub, cs(Notification.EXTRA_INFO_TEXT), cs(Notification.EXTRA_SUMMARY_TEXT))
            .flatMap { it.split('·', '•', '|') }.map { it.trim() }.filter { it.isNotEmpty() }
        val timeLeft = parts.firstNotNullOfOrNull { p -> if (CLOCK.containsMatchIn(p)) null else TIME_LEFT_FULL.find(p)?.value }
        val distanceLeft = parts.firstNotNullOfOrNull { p -> if (CLOCK.containsMatchIn(p) || TIME_LEFT.containsMatchIn(p)) null else DISTANCE.find(p)?.value }
        // In 24-hour time like the clock on Home ("11:06 pm" is 23:06), so it fits
        val arrive = (parts + listOfNotNull(text)).firstNotNullOfOrNull { CLOCK.find(it) }?.let { clock24(it) }
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
        /** "40 min", "1 hr 5 min", "2 h". */
        private val TIME_LEFT_FULL = Regex(
            "(\\d+\\s?(h|hr|hrs|hour|hours)\\s*)?\\d+\\s?(min|mins)\\b|\\d+\\s?(h|hr|hrs|hour|hours)\\b",
            RegexOption.IGNORE_CASE,
        )
        /** A time of day; Android puts a narrow no-break space before am/pm. */
        private val CLOCK = Regex("\\b(\\d{1,2}):(\\d{2})(?:[\\s\\u00A0\\u202F]?([AaPp])\\.?[Mm]\\.?)?")

        private fun clock24(m: MatchResult): String {
            var h = m.groupValues[1].toInt()
            val ampm = m.groupValues[3].lowercase()
            if (ampm == "p" && h < 12) h += 12
            if (ampm == "a" && h == 12) h = 0
            return "%d:%s".format(h, m.groupValues[2])
        }
        val connected = MutableStateFlow(false)

        fun allowed(context: Context): Boolean =
            context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)

        fun component(context: Context) = ComponentName(context, CarNotifications::class.java)
    }
}
