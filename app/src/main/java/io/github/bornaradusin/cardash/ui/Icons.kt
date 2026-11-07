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
    val oil = icon("oil", "M3 10h4l2-2h5l7 3-5 6H6l-3-3z", "M20 15c0 1 .7 2 1 2")
    val boost = icon("boost", circle(12f, 12f, 9f), circle(12f, 12f, 2f), "M12 3v4", "M19.8 16.5l-3.5-2", "M4.2 16.5l3.5-2")
    val battery = icon("battery", "M5 7h14a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V9a2 2 0 0 1 2-2z", "M7 4v3", "M17 4v3", "M7 13h3", "M15 11.5v3", "M13.5 13h3")
    val air = icon("air", "M3 8h11a3 3 0 1 0-3-3", "M3 12h16a3 3 0 1 1-3 3", "M3 16h7")
    val coolant = icon("coolant", "M12 3v11", circle(12f, 17f, 3f), "M12 7h3", "M12 10h3")
    val fuel = icon("fuel", "M4 21V5a2 2 0 0 1 2-2h6a2 2 0 0 1 2 2v16", "M3 21h12", "M14 9h2a2 2 0 0 1 2 2v5a1.5 1.5 0 0 0 3 0V8l-3-3", "M7 7h4")
    val check = icon("check", circle(12f, 12f, 9f), "M8 12l3 3 5-6")
    val warn = icon("warn", "M12 3L2 20h20z", "M12 10v4", "M12 17h.01")
    val plus = icon("plus", "M12 5v14", "M5 12h14")
    val back = icon("back", "M15 18l-6-6 6-6")
}
