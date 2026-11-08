package io.github.bornaradusin.cardash.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Line icons on a 24-unit grid, drawn with a 2-unit stroke and tinted where they're used. */
object Ic {
    private fun icon(name: String, vararg paths: String, width: Float = 2f): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            paths.forEach {
                addPath(
                    pathData = addPathNodes(it),
                    stroke = SolidColor(Color.White),
                    strokeLineWidth = width,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    private fun circle(cx: Float, cy: Float, r: Float) = "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0"

    val home = icon("home", "M3 11l9-8 9 8", "M5 10v10h14V10")
    val apps = icon("apps", "M5.5 4h3A1.5 1.5 0 0 1 10 5.5v3A1.5 1.5 0 0 1 8.5 10h-3A1.5 1.5 0 0 1 4 8.5v-3A1.5 1.5 0 0 1 5.5 4z",
        "M15.5 4h3A1.5 1.5 0 0 1 20 5.5v3a1.5 1.5 0 0 1-1.5 1.5h-3A1.5 1.5 0 0 1 14 8.5v-3A1.5 1.5 0 0 1 15.5 4z",
        "M5.5 14h3a1.5 1.5 0 0 1 1.5 1.5v3A1.5 1.5 0 0 1 8.5 20h-3A1.5 1.5 0 0 1 4 18.5v-3A1.5 1.5 0 0 1 5.5 14z",
        "M15.5 14h3a1.5 1.5 0 0 1 1.5 1.5v3a1.5 1.5 0 0 1-1.5 1.5h-3a1.5 1.5 0 0 1-1.5-1.5v-3a1.5 1.5 0 0 1 1.5-1.5z")
    val gauge = icon("gauge", "M12 14l4-4", "M3.3 17a10 10 0 1 1 17.4 0")
    val settings = icon("settings", circle(12f, 12f, 3f), "M12 2v3", "M12 19v3", "M2 12h3", "M19 12h3",
        "M4.9 4.9l2.1 2.1", "M17 17l2.1 2.1", "M4.9 19.1L7 17", "M17 7l2.1-2.1")
    val pin = icon("pin", "M12 21s-7-6.2-7-11.5A7 7 0 0 1 19 9.5C19 14.8 12 21 12 21z", circle(12f, 9.5f, 2.5f))
    val obd = icon("obd", "M5 9l3-3h8l3 3v8a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1z", "M9 12h.01", "M12 12h.01", "M15 12h.01")
    val music = icon("music", "M9 18V5l12-2v13", circle(6f, 18f, 3f), circle(18f, 16f, 3f))
    val play = icon("play", "M7 4l13 8-13 8z")
    val pause = icon("pause", "M9 5v14", "M15 5v14", width = 2.6f)
    val next = icon("next", "M5 4l10 8-10 8z", "M19 5v14")
    val previous = icon("previous", "M19 20L9 12l10-8z", "M5 19V5")
    val external = icon("external", "M7 17L17 7", "M9 7h8v8", width = 2.4f)
    val turn = icon("turn", "M7 20V11a3 3 0 0 1 3-3h9", "M15 4l4 4-4 4", width = 2.4f)
    // The oil can from the car's dash: the can with its handle and cap, the spout, and a drop
    val oil = icon("oil", "M6 10.5h9l6.5-3-5.5 8.5H6z", "M6 11.5H3.5v3H6", "M10.5 10.5V8.5", "M8.5 8.5h4",
        "M21.5 12.5c-.6.9-1 1.5-1 2a1 1 0 0 0 2 0c0-.5-.4-1.1-1-2z")
    // A turbo: the snail housing with its outlet, and the compressor wheel inside
    val boost = icon("boost", "M16.6 9.6H21V5H10A7 7 0 1 0 16.6 9.6",
        "M10 12c0-1.6.8-2.4 2-2.6", "M10 12c1.39.8 1.68 1.89 1.25 3.03", "M10 12c-1.39.8-2.48.51-3.25-.43")
    val battery = icon("battery", "M5 7h14a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V9a2 2 0 0 1 2-2z", "M7 4v3", "M17 4v3", "M7 13h3", "M15 11.5v3", "M13.5 13h3")
    val air = icon("air", "M3 8h11a3 3 0 1 0-3-3", "M3 12h16a3 3 0 1 1-3 3", "M3 16h7")
    // The check-engine shape without the exclamation mark, for the engine's temperature
    val engine = icon("engine", "M6 10h2l2-2h5l2 2v2h2v-2h2v8h-2v-2h-2v2l-2 2H8l-2-2z", "M3 11v6", "M3 14h3", "M10 5h5", "M12.5 5v3")
    // An intake manifold: the throttle inlet, the plenum and its runners
    val intake = icon("intake", "M3 6.5h4", "M7 4.5h12a2 2 0 0 1 2 2a2 2 0 0 1-2 2H7z",
        "M8.5 8.5c0 4-2 6-2 10", "M12.5 8.5v10", "M16.5 8.5c0 4 2 6 2 10", "M4.5 20h16")
    val coolant = icon("coolant", "M12 3v11", circle(12f, 17f, 3f), "M12 7h3", "M12 10h3")
    val fuel = icon("fuel", "M4 21V5a2 2 0 0 1 2-2h6a2 2 0 0 1 2 2v16", "M3 21h12", "M14 9h2a2 2 0 0 1 2 2v5a1.5 1.5 0 0 0 3 0V8l-3-3", "M7 7h4")
    val check = icon("check", circle(12f, 12f, 9f), "M8 12l3 3 5-6")
    val warn = icon("warn", "M12 3L2 20h20z", "M12 10v4", "M12 17h.01")
    val plus = icon("plus", "M12 5v14", "M5 12h14")
    val minus = icon("minus", "M5 12h14")
    val back = icon("back", "M15 18l-6-6 6-6")
    val close = icon("close", "M6 6l12 12", "M18 6L6 18")

    // Navigation
    val search = icon("search", circle(10.5f, 10.5f, 6.5f), "M15.5 15.5L21 21")
    val volume = icon("volume", "M4 9v6h4l5 4V5L8 9z", "M16.5 9.5a3.5 3.5 0 0 1 0 5", "M19 7a7 7 0 0 1 0 10")
    val muted = icon("muted", "M4 9v6h4l5 4V5L8 9z", "M17 9.5l5 5", "M22 9.5l-5 5")
    val work = icon("work", "M4 8h16a1 1 0 0 1 1 1v10a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V9a1 1 0 0 1 1-1z", "M9 8V6a2 2 0 0 1 2-2h2a2 2 0 0 1 2 2v2")
    val clock = icon("clock", circle(12f, 12f, 9f), "M12 7v5l3 2")
    val download = icon("download", "M12 4v10", "M8 10l4 4 4-4", "M5 19h14")

    // The keyboard
    val backspace = icon("backspace", "M9 5h11a1 1 0 0 1 1 1v12a1 1 0 0 1-1 1H9l-6-7z", "M12 9.5l5 5", "M17 9.5l-5 5")
    val shift = icon("shift", "M12 4l8 8h-4.5v7h-7v-7H4z")
    val hideKeys = icon("hideKeys", "M4 5h16v10H4z", "M7 8h.01", "M10 8h.01", "M13 8h.01", "M16 8h.01", "M8 12h8", "M9 18l3 3 3-3")

    // Service
    val wrench = icon("wrench", "M14.7 6.3a4 4 0 0 0-5.4 5.4L3 18l3 3 6.3-6.3a4 4 0 0 0 5.4-5.4l-2.5 2.5-2.4-.6-.6-2.4z")
    val drop = icon("drop", "M12 3s-6 7-6 11a6 6 0 0 0 12 0c0-4-6-11-6-11z")
    val filter = icon("filter", "M3 5h18l-7 8v6l-4 2v-8z")
    val plug = icon("plug", "M12 2v3", "M9 5h6v4H9z", "M10 9h4v6h-4z", "M12 15v3l2 2")
    val pencil = icon("pencil", "M4 20h4L19 9l-4-4L4 16z", "M13 7l4 4")
    val calendar = icon("calendar", "M5 5h14a1 1 0 0 1 1 1v13a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1z", "M4 10h16", "M8 3v4", "M16 3v4")

    // Turn arrows for Waze's next turn
    val turnLeft = icon("turnLeft", "M17 20V11a3 3 0 0 0-3-3H5", "M9 4L5 8l4 4", width = 2.4f)
    val turnRight = icon("turnRight", "M7 20V11a3 3 0 0 1 3-3h9", "M15 4l4 4-4 4", width = 2.4f)
    val keepLeft = icon("keepLeft", "M14 20v-6L7 7", "M7 12V7h5", width = 2.4f)
    val keepRight = icon("keepRight", "M10 20v-6l7-7", "M17 12V7h-5", width = 2.4f)
    val straight = icon("straight", "M12 20V4", "M7 9l5-5 5 5", width = 2.4f)
    val uTurn = icon("uTurn", "M8 20V9a4 4 0 0 1 8 0v4", "M12 10l4 4 4-4", width = 2.4f)
    val roundabout = icon("roundabout", circle(12f, 11f, 4f), "M12 15v6", "M15 8l3-3", "M14 5h4v4", width = 2.4f)
    val arrive = icon("arrive", "M5 21V4", "M5 4h11l-2 4 2 4H5", width = 2.4f)

    // Dashboard icons for the app buttons
    val androidAuto = icon("androidAuto", "M12 3l8 18-8-5-8 5z")
    val waze = icon("waze", "M5 16a8 8 0 1 1 6 3H9l-3 2v-3a4 4 0 0 1-1-2z", "M10 10h.01", "M15 10h.01", "M10 13.5a3 3 0 0 0 5 0")
    val video = icon("video", "M7 5h10a4 4 0 0 1 4 4v6a4 4 0 0 1-4 4H7a4 4 0 0 1-4-4V9a4 4 0 0 1 4-4z", "M10 9l5 3-5 3z")
    val phone = icon("phone", "M5 4h4l2 5-2.5 1.5a11 11 0 0 0 5 5L15 13l5 2v4a2 2 0 0 1-2 2A16 16 0 0 1 3 6a2 2 0 0 1 2-2")
    val radio = icon("radio", "M5 8h14a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-8a2 2 0 0 1 2-2z", "M7 8l10-5", circle(8f, 14f, 2f), "M14 13h4", "M14 16h4")
    val map = icon("map", "M9 4L3 6v14l6-2 6 2 6-2V4l-6 2z", "M9 4v14", "M15 6v14")
    val podcast = icon("podcast", "M12 11a1 1 0 1 0 0 .01", "M8.5 15.5a5 5 0 1 1 7 0", "M6 18a8.5 8.5 0 1 1 12 0", "M12 15v6")
    val bluetooth = icon("bluetooth", "M7 7l10 10-5 4V3l5 4L7 17")
    val camera = icon("camera", "M4 8h3l2-3h6l2 3h3a1 1 0 0 1 1 1v9a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V9a1 1 0 0 1 1-1z", circle(12f, 13f, 3.5f))
    val globe = icon("globe", circle(12f, 12f, 9f), "M3 12h18", "M12 3a14 14 0 0 1 0 18", "M12 3a14 14 0 0 0 0 18")
    val folder = icon("folder", "M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z")
    val car = icon("car", "M5 16l1.5-5A2 2 0 0 1 8.4 9.5h7.2a2 2 0 0 1 1.9 1.5L19 16", "M4 16h16v3H4z", "M7 19v1.5", "M17 19v1.5")
    val star = icon("star", "M12 3l2.7 5.6 6.1.9-4.4 4.3 1 6.1L12 17l-5.4 2.9 1-6.1L3.2 9.5l6.1-.9z")
    val headphones = icon("headphones", "M4 15v-3a8 8 0 0 1 16 0v3", "M4 15h3v5H5a1 1 0 0 1-1-1z", "M20 15h-3v5h2a1 1 0 0 0 1-1z")

    /** The icons a person can choose for an app button, by the key Settings stores. */
    val choices: List<Pair<String, ImageVector>> by lazy {
        listOf(
            "android_auto" to androidAuto, "waze" to waze, "map" to map, "music" to music, "headphones" to headphones,
            "video" to video, "podcast" to podcast, "radio" to radio, "phone" to phone, "bluetooth" to bluetooth,
            "camera" to camera, "globe" to globe, "folder" to folder, "car" to car, "gauge" to gauge, "star" to star,
            "settings" to settings, "apps" to apps,
        )
    }

    fun byKey(key: String): ImageVector? = choices.firstOrNull { it.first == key }?.second
}
