package io.github.bornaradusin.cardash.nav

import java.net.URLEncoder
import java.util.Locale
import org.json.JSONObject

/** A stretch of road with a speed limit. */
class LimitedRoad(val points: List<Pos>, val kmh: Int)

/** Speed limits and traffic lights around a place. */
class RoadArea(val centre: Pos, val roads: List<LimitedRoad>, val signals: List<Pos>)

/**
 * Speed limits and traffic lights from OpenStreetMap, through the Overpass API (free, no key). Fetched
 * for a circle around the car and used until the car nears its edge, so it's one small request every
 * kilometre or so.
 */
object RoadInfo {
    /** The main OpenStreetMap server and two public copies of it: the main one is often busy. */
    private val OVERPASS = listOf(
        "https://overpass-api.de/api/interpreter",
        "https://overpass.private.coffee/api/interpreter",
        "https://maps.mail.ru/osm/tools/overpass/api/interpreter",
    )
    const val RADIUS_M = 1_500

    /** Roads a car drives on: footpaths, tracks and the like left out, so the answer is small and quick. */
    private const val DRIVEN = "motorway|trunk|primary|secondary|tertiary|unclassified|residential|living_street|service|" +
        "motorway_link|trunk_link|primary_link|secondary_link|tertiary_link|road"

    /** Why the last try got nothing, from each server in turn. */
    class Failed(message: String) : java.io.IOException(message)

    suspend fun around(p: Pos): RoadArea {
        val at = String.format(Locale.US, "%d,%.6f,%.6f", RADIUS_M, p.lat, p.lon)
        val q = "[out:json][timeout:25];(way(around:$at)[highway~\"^($DRIVEN)$\"][maxspeed];node(around:$at)[highway=traffic_signals];);out geom;"
        val data = URLEncoder.encode(q, "UTF-8")
        val why = ArrayList<String>()
        var answer: String? = null
        for (server in OVERPASS) {
            answer = try {
                Http.get("$server?data=$data", 25_000)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                why += "${java.net.URI(server).host}: ${e.message ?: e.javaClass.simpleName}"
                null
            }
            if (answer != null) break
        }
        val text = answer ?: throw Failed(why.joinToString("; "))
        // A big town is a lot of road: read it off the main thread
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { parse(p, text) }
    }

    private fun parse(p: Pos, text: String): RoadArea {
        val json = JSONObject(text)
        val els = json.optJSONArray("elements") ?: return RoadArea(p, emptyList(), emptyList())
        val roads = ArrayList<LimitedRoad>()
        val signals = ArrayList<Pos>()
        for (i in 0 until els.length()) {
            val e = els.optJSONObject(i) ?: continue
            when (e.optString("type")) {
                "node" -> signals += Pos(e.optDouble("lat"), e.optDouble("lon"))
                "way" -> {
                    val kmh = kmh(e.optJSONObject("tags")?.optString("maxspeed")) ?: continue
                    val g = e.optJSONArray("geometry") ?: continue
                    val pts = (0 until g.length()).mapNotNull { k -> g.optJSONObject(k)?.let { Pos(it.optDouble("lat"), it.optDouble("lon")) } }
                    if (pts.size >= 2) roads += LimitedRoad(pts, kmh)
                }
            }
        }
        return RoadArea(p, roads, signals)
    }

    /** "50" → 50, "30 mph" → 48; "IE:rural", "none" and the like → nothing to show. */
    fun kmh(tag: String?): Int? {
        val t = tag?.substringBefore(';')?.trim()?.lowercase() ?: return null
        val n = Regex("^(\\d{1,3})(\\s*mph)?$").find(t) ?: return null
        val v = n.groupValues[1].toInt()
        return if (n.groupValues[2].isNotEmpty()) (v * 1.609).toInt() else v
    }

    /**
     * The limit on the road the car is on: the nearest road with a limit within 25 m, preferring one
     * that runs the way the car is heading (at junctions both roads are close).
     */
    fun limitAt(area: RoadArea, here: Pos, heading: Float?): Int? {
        var best: Int? = null
        var bestScore = Double.MAX_VALUE
        for (road in area.roads) {
            val pts = road.points
            for (i in 0 until pts.size - 1) {
                val (_, d) = Geo.project(here, pts[i], pts[i + 1])
                if (d > 25) continue
                var score = d
                if (heading != null) {
                    val b = Geo.bearing(pts[i], pts[i + 1])
                    var diff = kotlin.math.abs(((heading - b) % 360 + 540) % 360 - 180)
                    diff = minOf(diff, 180 - diff)
                    score += diff / 3
                }
                if (score < bestScore) { bestScore = score; best = road.kmh }
            }
        }
        return best
    }
}
