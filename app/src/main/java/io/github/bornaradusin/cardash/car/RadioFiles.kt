package io.github.bornaradusin.cardash.car

import android.content.ContentValues
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/**
 * Copies the radio's own apps to Download/CarDash, so how its Vehicle settings app reads the CAN box
 * (oil temperature, odometer) can be worked out. Copies Vehicle settings and anything about the car,
 * CAN, MCU or the radio's maker, plus packages.txt listing every app.
 */
object RadioFiles {

    /** Apps bigger than this are listed but not copied. */
    private const val MAX_BYTES = 80L * 1024 * 1024

    data class Result(val files: Int, val bytes: Long, val skipped: List<String>)

    fun save(context: Context, progress: (String) -> Unit): Result {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) error("Needs Android 10 or newer")
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(0).sortedBy { it.packageName }
        val list = StringBuilder()
        var files = 0
        var bytes = 0L
        val skipped = mutableListOf<String>()

        apps.forEach { app ->
            val label = runCatching { app.loadLabel(pm).toString() }.getOrDefault("")
            val system = app.flags and ApplicationInfo.FLAG_SYSTEM != 0
            list.append(app.packageName).append('\t').append(label).append('\t')
                .append(if (system) "system" else "user").append('\t').append(app.sourceDir).append('\n')
            if (!wanted(app.packageName, label, system)) return@forEach

            val paths = listOfNotNull(app.sourceDir) + app.splitSourceDirs.orEmpty()
            paths.forEachIndexed { i, path ->
                val file = File(path)
                when {
                    !file.canRead() -> skipped += "${app.packageName} (can't be read)"
                    file.length() > MAX_BYTES -> skipped += "${app.packageName} (${file.length() / 1_048_576} MB, too big)"
                    else -> {
                        progress("Saving ${label.ifEmpty { app.packageName }}…")
                        val name = app.packageName + (if (i == 0) "" else "-split$i") + ".apk"
                        write(context, name, "application/vnd.android.package-archive") { out -> file.inputStream().use { it.copyTo(out) } }
                        files++
                        bytes += file.length()
                    }
                }
            }
        }

        val info = "Radio: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE}), Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}), build ${Build.DISPLAY}\n\n"
        write(context, "packages.txt", "text/plain") { it.write((info + list).toByteArray()) }
        return Result(files + 1, bytes, skipped)
    }

    private fun wanted(pkg: String, label: String, system: Boolean): Boolean {
        val p = pkg.lowercase()
        val l = label.lowercase()
        if (p == "com.android.systemui") return true
        val words = listOf("vehicle", "car", "can", "mcu", "obd", "canbus", "settings", "systemui", "launcher")
        if (words.any { it in l } || listOf("can", "mcu", "vehicle", "carinfo", "carsetting", "canbus", "systemui", "launcher").any { it in p }) {
            return system || "vehicle" in l || "can" in p
        }
        if (!system) return false
        val stock = listOf("com.google.", "com.android.", "android", "com.qualcomm.", "com.sprd.", "com.spreadtrum.", "com.unisoc.", "org.chromium.")
        return stock.none { p.startsWith(it) }
    }

    private fun write(context: Context, name: String, mime: String, body: (java.io.OutputStream) -> Unit) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/CarDash")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("Couldn't create $name in Downloads")
        resolver.openOutputStream(uri)?.use(body) ?: error("Couldn't write $name")
        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
    }
}
