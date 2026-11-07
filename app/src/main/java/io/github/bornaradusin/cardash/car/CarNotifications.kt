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
        // Only the ongoing one (the drive), not Waze's other alerts
        if (n.flags and Notification.FLAG_ONGOING_EVENT == 0) return
        val e = n.extras
        fun cs(key: String) = e.getCharSequence(key)?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        // Waze draws its drive notification with its own layout, so the words are often only in
        // that layout and not in the usual fields: both are read
        val fields = listOfNotNull(
            cs(Notification.EXTRA_TITLE), cs(Notification.EXTRA_BIG_TEXT) ?: cs(Notification.EXTRA_TEXT),
            cs(Notification.EXTRA_SUB_TEXT), cs(Notification.EXTRA_INFO_TEXT), cs(Notification.EXTRA_SUMMARY_TEXT),
        ) + e.getCharSequenceArray(Notification.EXTRA_TEXT_LINES).orEmpty().map { it.toString().trim() } +
            listOfNotNull(n.tickerText?.toString()?.trim())
        val layout = layoutTexts(n)
        val lines = (fields + layout)
            .flatMap { it.split('\n') }
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.equals("Waze", ignoreCase = true) }
            .distinct()
        lastWaze.value = lines.joinToString("  |  ").ifEmpty { "(no words in it)" }
        if (lines.isEmpty()) return
        navKey = sbn.key
        val all = lines.joinToString(" ")
        // Waze's notification is there whenever it runs ("Running. Tap to open."); while it's
        // navigating it has a distance or an arrival time
        val idle = all.contains("tap to open", ignoreCase = true) || all.contains("is running", ignoreCase = true)
        val navigating = !idle && (DISTANCE.containsMatchIn(all) || TIME.containsMatchIn(all))
        if (!navigating) {
            nav.value = null
            return
        }
        // The line with the distance first: that's the next turn
        val turnLine = lines.firstOrNull { DISTANCE.containsMatchIn(it) } ?: lines.first()
        val rest = lines.filter { it != turnLine }
        val eta = rest.firstOrNull { TIME.containsMatchIn(it) }
        nav.value = NavInfo(
            title = turnLine,
            text = rest.filter { it != eta }.joinToString(" · ").ifEmpty { null },
            sub = eta,
            icon = n.getLargeIcon(),
        )
    }

    /** The words in a notification's own layout, by drawing it off-screen. */
    private fun layoutTexts(n: Notification): List<String> {
        @Suppress("DEPRECATION")
        val views = listOfNotNull(n.bigContentView, n.contentView).ifEmpty {
            runCatching {
                val b = Notification.Builder.recoverBuilder(this, n)
                listOfNotNull(b.createBigContentView(), b.createContentView())
            }.getOrDefault(emptyList())
        }
        val out = mutableListOf<String>()
        views.forEach { rv ->
            runCatching {
                val parent = android.widget.FrameLayout(this)
                collect(rv.apply(this, parent), out)
            }
        }
        return out
    }

    private fun collect(v: android.view.View, out: MutableList<String>) {
        if (v.visibility != android.view.View.VISIBLE) return
        when (v) {
            is android.widget.TextView -> v.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { out += it }
            is android.view.ViewGroup -> for (i in 0 until v.childCount) collect(v.getChildAt(i), out)
        }
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
        /** Waze's last notification as it came, for Settings, to check what it says while navigating. */
        val lastWaze = MutableStateFlow<String?>(null)
        /** An arrival time or time left: "15:24", "12 min", "1 h 5 min". */
        val TIME = Regex("\\b\\d{1,2}:\\d{2}\\b|\\b\\d+\\s?(min|mins|h|hr)\\b", RegexOption.IGNORE_CASE)
        val DISTANCE = Regex("\\b\\d+(?:[.,]\\d+)?\\s?(m|km|ft|mi|yd)\\b", RegexOption.IGNORE_CASE)
        val connected = MutableStateFlow(false)

        fun allowed(context: Context): Boolean =
            context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)

        fun component(context: Context) = ComponentName(context, CarNotifications::class.java)

        val navFlow: StateFlow<NavInfo?> get() = nav
    }
}
