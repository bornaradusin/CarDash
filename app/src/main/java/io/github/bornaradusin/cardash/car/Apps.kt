package io.github.bornaradusin.cardash.car

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable

data class AppEntry(val pkg: String, val label: String, val icon: Drawable)

/** The radio's installed apps, for the app buttons and the app drawer. */
object Apps {

    fun all(context: Context): List<AppEntry> {
        val pm = context.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(launcher, 0)
            .asSequence()
            .map { it.activityInfo }
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName }
            .map { AppEntry(it.packageName, it.loadLabel(pm).toString(), it.loadIcon(pm)) }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    fun launch(context: Context, pkg: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }

    /**
     * The usual app for each Home button when the person hasn't picked one: Android Auto (the radio's
     * own phone-mirroring app, found by its name), Waze, YouTube Music, YouTube.
     */
    fun defaultFor(slot: Int, installed: List<AppEntry>): AppEntry? = when (slot) {
        0 -> installed.firstOrNull { a ->
            listOf("android auto", "carplay", "zlink", "carlink", "autokit", "tlink", "easyconnection").any { it in a.label.lowercase() }
        } ?: installed.firstOrNull { it.pkg == "com.google.android.projection.gearhead" }
        1 -> installed.firstOrNull { it.pkg == CarNotifications.WAZE }
        2 -> installed.firstOrNull { it.pkg == "com.google.android.apps.youtube.music" }
        3 -> installed.firstOrNull { it.pkg == "com.google.android.youtube" }
        else -> null
    }

    /** What a Home button is called when nothing is installed for it yet. */
    val slotNames = listOf("Android Auto", "Waze", "YouTube Music", "YouTube")
}
