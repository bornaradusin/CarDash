package io.github.bornaradusin.cardash.car

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable

/** [removable]: installed by the person (or an update of the radio's own), so it can be uninstalled. */
data class AppEntry(val pkg: String, val label: String, val icon: Drawable, val removable: Boolean = true)

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
            .map {
                val flags = it.applicationInfo.flags
                val builtIn = flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM != 0 &&
                    flags and android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP == 0
                AppEntry(it.packageName, it.loadLabel(pm).toString(), it.loadIcon(pm), removable = !builtIn)
            }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    /** Android's own "Uninstall this app?" prompt. */
    fun uninstall(context: Context, pkg: String) {
        context.startActivity(
            Intent(Intent.ACTION_DELETE, android.net.Uri.parse("package:$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    /** The app's page in Android settings: storage, permissions, force stop. */
    fun info(context: Context, pkg: String) {
        context.startActivity(
            Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:$pkg"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
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
