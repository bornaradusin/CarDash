package io.github.bornaradusin.cardash.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * The change up / change down icons: a gear lever's pattern (three gates, the cross bar and the
 * lever's knob) in white, with the arrow in the accent colour above it (up) or below it (down).
 * Drawn from the icons Kevin chose; shown only while it's time to change gear.
 */
object ShiftIcons {
    private fun rrect(x0: Float, y0: Float, x1: Float, y1: Float, r: Float) =
        "M${x0 + r} ${y0}H${x1 - r}A$r $r 0 0 1 $x1 ${y0 + r}V${y1 - r}A$r $r 0 0 1 ${x1 - r} ${y1}" +
            "H${x0 + r}A$r $r 0 0 1 $x0 ${y1 - r}V${y0 + r}A$r $r 0 0 1 ${x0 + r} ${y0}Z"

    private fun circle(cx: Float, cy: Float, r: Float) = "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0Z"

    /** The lever pattern, in a 360-wide frame; the up icon spans y 0–480, the down icon y 190–640. */
    private fun gate(dy: Float) = listOf(
        rrect(20f, 200f - dy, 77f, 472f - dy, 28.5f),
        rrect(152f, 208f - dy, 212f, 472f - dy, 30f),
        rrect(282f, 200f - dy, 340f, 330f - dy, 29f),
        rrect(20f, 307f - dy, 310f, 367f - dy, 30f),
        circle(312f, 340f - dy, 50f),
    )

    private val ARROW_UP = listOf("M45 157L185 17L320 157H225V240H140V157Z")
    private val ARROW_DOWN = listOf("M48 300L313 300L181 432Z", "M136 220H223V300H136Z")

    fun up(accent: Color) = build("changeUp", 480f, gate(0f), ARROW_UP, accent)
    fun down(accent: Color) = build("changeDown", 450f, gate(190f), ARROW_DOWN, accent)

    private fun build(name: String, height: Float, gate: List<String>, arrow: List<String>, accent: Color): ImageVector {
        val line = Color(0xFF0A0C10)
        return ImageVector.Builder(name, 64.dp, (64f * height / 360f).dp, 360f, height).apply {
            // A soft glow round the arrow, then the arrow
            arrow.forEach {
                addPath(addPathNodes(it), stroke = SolidColor(accent.copy(alpha = 0.35f)), strokeLineWidth = 40f, strokeLineJoin = StrokeJoin.Round)
            }
            arrow.forEach {
                addPath(addPathNodes(it), fill = SolidColor(accent), stroke = SolidColor(line), strokeLineWidth = 9f, strokeLineJoin = StrokeJoin.Round)
            }
            // The pattern: a dark outline round all of it, then the white over the top (one shape, no inner lines)
            gate.forEach {
                addPath(addPathNodes(it), fill = SolidColor(line), stroke = SolidColor(line), strokeLineWidth = 18f, strokeLineJoin = StrokeJoin.Round)
            }
            gate.forEach { addPath(addPathNodes(it), fill = SolidColor(C.ink)) }
        }.build()
    }
}
