package io.github.bornaradusin.cardash.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/** CarDash's colours: near-black, steel greys and one accent (red unless the person picks another). */
object C {
    val bg = Color(0xFF07080B)
    val bgLift = Color(0xFF161A22)
    val panel = Color(0xFF0E1015)
    val panelHi = Color(0xFF171B23)
    val line = Color(0xFF262B34)
    val lineHi = Color(0xFF3A414D)
    val ink = Color(0xFFEEF2F7)
    val ink2 = Color(0xFFC9D2DD)
    val muted = Color(0xFF9AA3B2)
    val good = Color(0xFF4CC38A)
    val track = Color(0xFF20252E)

    /** The accent. Called red because that's what it is unless changed; everything drawn with it follows a change. */
    var red by mutableStateOf(RED)
        private set
    /** The accent darkened into the panels: borders of things that need attention. */
    var redDeep by mutableStateOf(deep(RED))
        private set
    /** The accent barely there: backgrounds behind an accent icon. */
    var redTint by mutableStateOf(tint(RED))
        private set
    /** The accent lightened: a gauge past its warning level. */
    var redLight by mutableStateOf(light(RED))
        private set

    fun setAccent(c: Color) {
        red = c
        redDeep = deep(c)
        redTint = tint(c)
        redLight = light(c)
    }

    val RED = Color(0xFFFF2D40)

    private fun deep(c: Color) = lerp(panel, c, 0.31f)
    private fun tint(c: Color) = lerp(panel, c, 0.07f)
    private fun light(c: Color) = lerp(c, Color.White, 0.3f)
}
