package io.github.bornaradusin.cardash.nav

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** A place on the earth. */
data class Pos(val lat: Double, val lon: Double)

object Geo {
    private const val EARTH_M = 6_371_000.0

    /** Metres between two places. */
    fun distance(a: Pos, b: Pos): Double {
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val h = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * EARTH_M * atan2(sqrt(h), sqrt(1 - h))
    }

    /** Compass direction from a to b, 0 to 360. */
    fun bearing(a: Pos, b: Pos): Double {
        val la1 = Math.toRadians(a.lat)
        val la2 = Math.toRadians(b.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val y = sin(dLon) * cos(la2)
        val x = cos(la1) * sin(la2) - sin(la1) * cos(la2) * cos(dLon)
        return (Math.toDegrees(atan2(y, x)) + 360) % 360
    }

    /**
     * Where p falls on the line a–b: how far along it (0 to 1) and how far p is from it, in metres.
     * Flat-earth sums are fine over the length of a road segment.
     */
    fun project(p: Pos, a: Pos, b: Pos): Pair<Double, Double> {
        val k = cos(Math.toRadians(p.lat))
        val ax = a.lon * k
        val bx = b.lon * k
        val px = p.lon * k
        val dx = bx - ax
        val dy = b.lat - a.lat
        val len2 = dx * dx + dy * dy
        val t = if (len2 == 0.0) 0.0 else (((px - ax) * dx + (p.lat - a.lat) * dy) / len2).coerceIn(0.0, 1.0)
        val q = Pos(a.lat + dy * t, (ax + dx * t) / k)
        return t to distance(p, q)
    }

    /** A point part-way along a–b. */
    fun along(a: Pos, b: Pos, t: Double) = Pos(a.lat + (b.lat - a.lat) * t, a.lon + (b.lon - a.lon) * t)

    /** The point m metres from p in compass direction [bearing] (flat-earth, for short hops). */
    fun offset(p: Pos, bearing: Double, m: Double): Pos {
        val r = Math.toRadians(bearing)
        val dLat = Math.toDegrees(m * cos(r) / EARTH_M)
        val dLon = Math.toDegrees(m * sin(r) / (EARTH_M * cos(Math.toRadians(p.lat))))
        return Pos(p.lat + dLat, p.lon + dLon)
    }

    /** "300 m", "1.4 km", "12 km". */
    fun distanceText(m: Double): String = when {
        m < 950 -> "${((m / 10).roundToInt() * 10).coerceAtLeast(0)} m"
        m < 9_950 -> String.format(java.util.Locale.US, "%.1f km", m / 1000)
        else -> "${(m / 1000).roundToInt()} km"
    }

    /** Distance for the voice: "300 metres", "1.5 kilometres". */
    fun distanceSpoken(m: Double): String = when {
        m < 950 -> "${((m / 50).roundToInt() * 50).coerceAtLeast(50)} metres"
        m < 1_250 -> "1 kilometre"
        m < 9_750 -> {
            val half = (m / 500).roundToInt() / 2.0
            if (half % 1.0 == 0.0) "${half.toInt()} kilometres" else "$half kilometres"
        }
        else -> "${(m / 1000).roundToInt()} kilometres"
    }
}
