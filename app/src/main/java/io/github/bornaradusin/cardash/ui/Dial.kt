package io.github.bornaradusin.cardash.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private const val START = 135f
private const val SWEEP = 270f

/**
 * A round dial like a car's: a steel bezel, ticks, numbers, a glowing needle and a lit arc up to
 * the value. [label] turns a major tick's value into its number ("3" for 3,000 rpm). What sits in
 * the middle is up to the caller. A null [value] parks the needle at zero, dimmed.
 */
@Composable
fun Dial(
    value: Float?,
    max: Float,
    major: Float,
    minor: Float,
    modifier: Modifier = Modifier,
    redFrom: Float? = null,
    labelSize: Float = 0.075f,
    /** Numbers every this much (a multiple of [major]); fewer numbers can be bigger. */
    labelEvery: Float = major,
    label: (Float) -> String = { it.toInt().toString() },
    needle: Boolean = true,
    /** Draws the small arc low in the dial (coolant on the RPM dial); its fill, 0..1, or null for empty. */
    bottomArc: Boolean = false,
    bottomFill: Float? = null,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val shown by animateFloatAsState((value ?: 0f).coerceIn(0f, max), tween(250), label = "dial")
    val paint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC)
        }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = min(size.width, size.height) / 2f
            val c = Offset(size.width / 2f, size.height / 2f)
            // Bezel and face
            drawCircle(Brush.verticalGradient(listOf(Color(0xFF8A93A0), Color(0xFF2A303B), Color(0xFF5A626E)), c.y - r, c.y + r), r, c)
            drawCircle(Brush.radialGradient(listOf(Color(0xFF1A1E26), Color(0xFF0A0C10)), Offset(c.x, c.y - r * 0.1f), r * 1.05f), r * 0.965f, c)

            val arcR = r * 0.94f
            fun arc(from: Float, to: Float, color: Color, width: Float, glow: Boolean) {
                val a0 = START + SWEEP * from / max
                val sweep = SWEEP * (to - from) / max
                if (sweep <= 0f) return
                val tl = Offset(c.x - arcR, c.y - arcR)
                val sz = Size(arcR * 2, arcR * 2)
                if (glow) drawArc(color.copy(alpha = 0.25f), a0, sweep, false, tl, sz, style = Stroke(width * 3f))
                drawArc(color, a0, sweep, false, tl, sz, style = Stroke(width, cap = StrokeCap.Butt))
            }
            arc(0f, max, C.track, r * 0.035f, false)
            redFrom?.let { arc(it, max, C.red, r * 0.045f, true) }
            if (value != null) arc(0f, shown, C.red, r * 0.035f, true)

            // Ticks and numbers
            var v = 0f
            while (v <= max + 0.001f) {
                val isMajor = (v / major) - kotlin.math.round(v / major) in -0.001f..0.001f
                val a = Math.toRadians((START + SWEEP * v / max).toDouble())
                val outer = r * 0.88f
                val inner = outer - r * (if (isMajor) 0.12f else 0.06f)
                val inRed = redFrom != null && v >= redFrom
                val tickColor = if (inRed) C.red else C.ink2
                drawLine(tickColor, point(c, a, inner), point(c, a, outer), if (isMajor) r * 0.018f else r * 0.009f, StrokeCap.Round)
                val labelled = isMajor && ((v / labelEvery) - kotlin.math.round(v / labelEvery)) in -0.001f..0.001f
                if (labelled) {
                    paint.textSize = r * labelSize * 2f
                    paint.color = (if (inRed) C.red else C.ink).toArgb()
                    val p = point(c, a, r * 0.6f)
                    drawContext.canvas.nativeCanvas.drawText(label(v), p.x, p.y + paint.textSize * 0.35f, paint)
                }
                v += minor
            }

            if (bottomArc) {
                // Across the bottom of the face, filling from the left; centred on the dial, so it keeps its curve
                val br = r * 0.777f
                val tl = Offset(c.x - br, c.y - br)
                val sz = Size(br * 2, br * 2)
                val w = r * 0.04f
                drawArc(C.track, 125f, -70f, false, tl, sz, style = Stroke(w, cap = StrokeCap.Round))
                val f = (bottomFill ?: 0f).coerceIn(0f, 1f)
                if (bottomFill != null && f > 0.01f) {
                    drawArc(C.red.copy(alpha = 0.25f), 125f, -70f * f, false, tl, sz, style = Stroke(w * 2.6f, cap = StrokeCap.Round))
                    drawArc(C.red, 125f, -70f * f, false, tl, sz, style = Stroke(w, cap = StrokeCap.Round))
                }
            }

            if (needle) needle(c, r, Math.toRadians((START + SWEEP * shown / max).toDouble()), if (value != null) C.red else C.muted)
        }
        content()
    }
}

private fun point(c: Offset, a: Double, d: Float) = Offset(c.x + d * cos(a).toFloat(), c.y + d * sin(a).toFloat())

private fun DrawScope.needle(c: Offset, r: Float, a: Double, color: Color) {
    val tip = point(c, a, r * 0.78f)
    drawLine(color.copy(alpha = 0.25f), c, tip, r * 0.08f, StrokeCap.Round)
    drawLine(color, c, tip, r * 0.03f, StrokeCap.Round)
    drawCircle(Color(0xFF0A0C10), r * 0.1f, c)
    drawCircle(C.lineHi, r * 0.1f, c, style = Stroke(r * 0.018f))
}
