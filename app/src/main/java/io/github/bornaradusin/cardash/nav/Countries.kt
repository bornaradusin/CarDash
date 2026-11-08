package io.github.bornaradusin.cardash.nav

import android.content.Context
import java.text.Normalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * A country (or a part Natural Earth keeps apart, like Scotland, Alaska or Northern Ireland) whose map
 * can be saved: its outline, widened a little so the coast and the roads over the border are in, and
 * a rough download size. From assets/countries.json, built from Natural Earth's 1:50m map units.
 */
data class Country(
    val name: String,
    val code: String,
    /** Other names people search with (UK, Holland…). */
    val also: String,
    val lat: Double,
    val lon: Double,
    /** Tiles from zoom 5 to 14, roughly. */
    val tiles: Long,
    /** Download size in MB, roughly. */
    val mb: Long,
    /** The outline as a GeoJSON MultiPolygon. */
    val geometry: String,
) {
    private val words = fold("$name $also")

    fun matches(query: String): Boolean {
        val q = fold(query).trim()
        return q.isEmpty() || q.split(' ').filter { it.isNotEmpty() }.all { w -> words.split(' ').any { it.startsWith(w) } }
    }

    /** Whether the outline has [here] in it. */
    fun contains(here: Pos): Boolean {
        val polys = JSONObject(geometry).getJSONArray("coordinates")
        for (i in 0 until polys.length()) {
            val rings = polys.getJSONArray(i)
            if (rings.length() == 0) continue
            if (inside(rings.getJSONArray(0), here.lon, here.lat)) {
                var hole = false
                for (k in 1 until rings.length()) if (inside(rings.getJSONArray(k), here.lon, here.lat)) hole = true
                if (!hole) return true
            }
        }
        return false
    }

    companion object {
        fun fold(s: String): String =
            Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").replace(Regex("[^a-z0-9 ]"), " ")

        private fun inside(ring: JSONArray, x: Double, y: Double): Boolean {
            var c = false
            var j = ring.length() - 1
            for (i in 0 until ring.length()) {
                val a = ring.getJSONArray(i)
                val b = ring.getJSONArray(j)
                val xi = a.getDouble(0); val yi = a.getDouble(1)
                val xj = b.getDouble(0); val yj = b.getDouble(1)
                if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) c = !c
                j = i
            }
            return c
        }
    }
}

object Countries {
    @Volatile private var loaded: List<Country>? = null

    suspend fun all(context: Context): List<Country> = loaded ?: withContext(Dispatchers.IO) {
        loaded ?: run {
            val text = context.assets.open("countries.json").bufferedReader().use { it.readText() }
            val arr = JSONArray(text)
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                Country(
                    o.getString("name"), o.optString("code"), o.optString("also"),
                    o.getDouble("lat"), o.getDouble("lon"), o.getLong("tiles"), o.getLong("mb"),
                    o.getJSONObject("geometry").toString(),
                )
            }.also { loaded = it }
        }
    }

    /** The one the car is in first, then the nearest few, for the top of the list. */
    fun nearby(all: List<Country>, here: Pos?, count: Int = 6): List<Country> {
        here ?: return emptyList()
        val near = all.sortedBy { distanceKm(here, Pos(it.lat, it.lon)) }.take(10)
        val inIt = near.filter { it.contains(here) }.sortedBy { it.mb }      // Ireland before Ireland and NI
        return (inIt + near.filterNot { it in inIt }).take(count)
    }

    private fun distanceKm(a: Pos, b: Pos): Double {
        val r = 6371.0
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val h = Math.sin(dLat / 2).let { it * it } +
            Math.cos(Math.toRadians(a.lat)) * Math.cos(Math.toRadians(b.lat)) * Math.sin(dLon / 2).let { it * it }
        return 2 * r * Math.asin(Math.sqrt(h))
    }

    /** "about 860 MB", "about 4.7 GB". */
    fun size(mb: Long): String = when {
        mb < 1000 -> "about ${((mb + 5) / 10 * 10).coerceAtLeast(5)} MB"
        mb < 10_000 -> "about ${"%.1f".format(java.util.Locale.UK, mb / 1000.0)} GB"
        else -> "about ${"%,d".format(java.util.Locale.UK, (mb + 500) / 1000)} GB"
    }
}
