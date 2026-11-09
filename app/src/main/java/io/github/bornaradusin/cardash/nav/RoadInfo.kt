package io.github.bornaradusin.cardash.nav

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.cos
import kotlin.math.floor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** A stretch of road with a speed limit. */
class LimitedRoad(val points: List<Pos>, val kmh: Int)

/** A speed camera, with the limit it checks when OpenStreetMap says. */
class Camera(val pos: Pos, val kmh: Int?)

/**
 * A GoSafe speed van zone: a stretch of road the Garda's speed vans work on, anywhere along it. [cum]
 * is how far along the line each point is, and [dense] the line with a point about every 100 m, for
 * looking ahead.
 */
class Zone(val name: String, val points: List<Pos>) {
    val id = "$name ${points.first()}"
    val cum: DoubleArray = DoubleArray(points.size).also { c ->
        for (i in 1 until points.size) c[i] = c[i - 1] + Geo.distance(points[i - 1], points[i])
    }
    val dense: List<Pos> = buildList {
        add(points.first())
        for (i in 1 until points.size) {
            val a = points[i - 1]
            val b = points[i]
            val n = (Geo.distance(a, b) / 100.0).toInt()
            for (k in 1..n) add(Pos(a.lat + (b.lat - a.lat) * k / (n + 1), a.lon + (b.lon - a.lon) * k / (n + 1)))
            add(b)
        }
    }
}

/** Speed limits, traffic lights, speed cameras and speed van zones in one square of the map. */
class RoadArea(val roads: List<LimitedRoad>, val signals: List<Pos>, val cameras: List<Camera>, val zones: List<Zone> = emptyList())

/** What's switched on in Settings › Navigation › On the road. */
class RoadSwitches(
    val limits: Boolean,
    val lights: Boolean,
    val cameras: Boolean,
    val zones: Boolean = true,
    /** "Speed camera in 1 kilometre" out loud. */
    val cameraVoice: Boolean = true,
) {
    val any get() = limits || lights || cameras || zones
}

/** Where a square came from, for the line in Settings. */
enum class RoadSource { SAVED, CARDASH, OVERPASS }

/**
 * Speed limits, traffic lights and speed cameras from OpenStreetMap, in squares of 0.05 by 0.08 degrees
 * (about 5 km a side in Ireland), fetched as the car gets near them and kept on the radio for a week.
 *
 * In Ireland and Northern Ireland they come from CarDash's own copy, built from OpenStreetMap every
 * Monday by GitHub (the Roads workflow, on the repository's "roads" branch): one small file per square,
 * quick and always there. Anywhere else they come from the public Overpass servers, which are often
 * busy, so they're asked a few seconds apart and the first answer wins. A square that can't be fetched
 * uses the copy kept from before, however old.
 */
object RoadInfo {
    const val TILE_LAT = 0.05
    const val TILE_LON = 0.08
    private const val SCALE = 100_000.0
    private const val DATA = "https://raw.githubusercontent.com/bornaradusin/CarDash/roads"
    /** Kept a week: the data is rebuilt every Monday. */
    private const val KEEP_MS = 7L * 24 * 3_600_000
    private const val AGENT = "CarDash (github.com/bornaradusin/CarDash)"

    /** The main OpenStreetMap server and two public copies of it: asked in turn, a few seconds apart. */
    private val OVERPASS = listOf(
        "https://overpass-api.de/api/interpreter",
        "https://overpass.private.coffee/api/interpreter",
        "https://maps.mail.ru/osm/tools/overpass/api/interpreter",
    )
    /** Roads a car drives on: footpaths, tracks and the like left out, so the answer is small and quick. */
    private const val DRIVEN = "motorway|trunk|primary|secondary|tertiary|unclassified|residential|living_street|service|" +
        "motorway_link|trunk_link|primary_link|secondary_link|tertiary_link|road"

    /** Why a square couldn't be fetched, from each server in turn. */
    class Failed(message: String) : IOException(message)

    data class Square(val row: Int, val col: Int) {
        val name get() = "${row}_$col"
    }

    class Loaded(val area: RoadArea, val source: RoadSource)

    private class Region(val rows: IntRange, val cols: IntRange, val built: String)

    @Volatile private var region: Region? = null
    @Volatile private var regionAt = 0L
    /** When CarDash's Ireland data was built, once it's been looked at: for Settings. */
    val builtOn: String? get() = region?.built

    fun squareOf(p: Pos) = Square(floor(p.lat / TILE_LAT).toInt(), floor(p.lon / TILE_LON).toInt())

    /** The car's square first, then any next to it within [m] metres of the car. */
    fun squaresNear(p: Pos, m: Double): List<Square> {
        val dLat = m / 111_320.0
        val dLon = m / (111_320.0 * cos(Math.toRadians(p.lat))).coerceAtLeast(1.0)
        val out = linkedSetOf(squareOf(p))
        for (a in doubleArrayOf(0.0, -dLat, dLat)) for (b in doubleArrayOf(0.0, -dLon, dLon)) out += squareOf(Pos(p.lat + a, p.lon + b))
        return out.toList()
    }

    /** One square: the copy kept on the radio if it's under a week old, else fetched (and kept). */
    suspend fun square(dir: File, s: Square): Loaded = withContext(Dispatchers.IO) {
        val file = File(dir, "${s.name}.json")
        val kept = file.takeIf { it.exists() }
        if (kept != null && System.currentTimeMillis() - kept.lastModified() < KEEP_MS) {
            runCatching { parse(kept.readText()) }.getOrNull()?.let { return@withContext Loaded(it, RoadSource.SAVED) }
        }
        try {
            val r = region(dir)
            val ours = r != null && s.row in r.rows && s.col in r.cols
            val text = if (ours) {
                val (code, body) = get("$DATA/tiles/${s.name}.json", 15_000)
                when {
                    code == 200 && body != null -> body
                    // In Ireland but no file: nothing with a limit there (the sea, a bog)
                    code == 404 -> EMPTY
                    else -> throw IOException("CarDash's road data: HTTP $code")
                }
            } else {
                fromOverpass(overpass(s))
            }
            val area = parse(text)
            dir.mkdirs()
            file.writeText(text)
            Loaded(area, if (ours) RoadSource.CARDASH else RoadSource.OVERPASS)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            // Old is better than nothing
            kept?.let { k -> runCatching { parse(k.readText()) }.getOrNull()?.let { return@withContext Loaded(it, RoadSource.SAVED) } }
            throw e
        }
    }

    private const val EMPTY = """{"v":1,"r":[],"l":[],"c":[]}"""

    /** What CarDash's own data covers, looked at once a day (and kept, for when there's no signal). */
    private fun region(dir: File): Region? {
        val now = System.currentTimeMillis()
        if (now - regionAt < 24 * 3_600_000L) return region
        val file = File(dir, "region.json")
        val text = runCatching { get("$DATA/region.json", 10_000) }.getOrNull()
            ?.takeIf { it.first == 200 }?.second
            ?.also { runCatching { dir.mkdirs(); file.writeText(it) } }
            ?: file.takeIf { it.exists() }?.readText()
        region = text?.let { t ->
            runCatching {
                val j = JSONObject(t)
                val rows = j.getJSONArray("rows")
                val cols = j.getJSONArray("cols")
                Region(rows.getInt(0)..rows.getInt(1), cols.getInt(0)..cols.getInt(1), j.optString("built"))
            }.getOrNull()
        }
        regionAt = now
        return region
    }

    /** The square from Overpass: its corners, a little over each edge, so a road on the edge is in. */
    private suspend fun overpass(s: Square): String {
        val m = 0.0006
        val south = s.row * TILE_LAT - m
        val west = s.col * TILE_LON - m
        val bbox = String.format(Locale.US, "%.5f,%.5f,%.5f,%.5f", south, west, south + TILE_LAT + 2 * m, west + TILE_LON + 2 * m)
        val q = "[out:json][timeout:25][bbox:$bbox];" +
            "(way[\"maxspeed\"][\"highway\"~\"^($DRIVEN)$\"];node[\"highway\"~\"^(traffic_signals|speed_camera)$\"];);out geom qt;"
        val data = URLEncoder.encode(q, "UTF-8")
        val done = CompletableDeferred<String>()
        val why = java.util.Collections.synchronizedList(ArrayList<String>())
        // Off on their own, so a server still thinking doesn't hold up the one that answered
        val askers = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        OVERPASS.forEachIndexed { i, server ->
            askers.launch {
                // Each a few seconds after the last, unless one has already answered
                delay(i * 6_000L)
                if (done.isCompleted) return@launch
                val host = URI(server).host
                val (code, body) = try {
                    get("$server?data=$data", 30_000)
                } catch (e: Exception) {
                    -1 to (e.message ?: e.javaClass.simpleName)
                }
                if (code == 200 && body != null) {
                    done.complete(body)
                } else {
                    why += "$host: ${if (code == -1) body else "HTTP $code"}"
                    if (why.size == OVERPASS.size) done.completeExceptionally(Failed(why.joinToString("; ")))
                }
            }
        }
        return try {
            done.await()
        } finally {
            askers.cancel()
        }
    }

    /** Overpass' answer in CarDash's own form, so a square is kept the same way wherever it came from. */
    private fun fromOverpass(text: String): String {
        val els = JSONObject(text).optJSONArray("elements") ?: return EMPTY
        val r = JSONArray()
        val l = JSONArray()
        val c = JSONArray()
        for (i in 0 until els.length()) {
            val e = els.optJSONObject(i) ?: continue
            val tags = e.optJSONObject("tags")
            when (e.optString("type")) {
                "node" -> {
                    val lat = (e.optDouble("lat") * SCALE).toLong()
                    val lon = (e.optDouble("lon") * SCALE).toLong()
                    when (tags?.optString("highway")) {
                        "traffic_signals" -> l.put(lat).put(lon)
                        "speed_camera" -> c.put(lat).put(lon).put(kmh(tags.optString("maxspeed")) ?: 0)
                    }
                }
                "way" -> {
                    val kmh = kmh(tags?.optString("maxspeed")) ?: continue
                    val g = e.optJSONArray("geometry") ?: continue
                    if (g.length() < 2) continue
                    val a = JSONArray().put(kmh)
                    var pLat = 0L
                    var pLon = 0L
                    for (k in 0 until g.length()) {
                        val pt = g.optJSONObject(k) ?: continue
                        val lat = Math.round(pt.optDouble("lat") * SCALE)
                        val lon = Math.round(pt.optDouble("lon") * SCALE)
                        if (a.length() == 1) a.put(lat).put(lon) else a.put(lat - pLat).put(lon - pLon)
                        pLat = lat
                        pLon = lon
                    }
                    r.put(a)
                }
            }
        }
        return JSONObject().put("v", 1).put("r", r).put("l", l).put("c", c).toString()
    }

    private fun parse(text: String): RoadArea {
        val j = JSONObject(text)
        val roads = ArrayList<LimitedRoad>()
        j.optJSONArray("r")?.let { r ->
            for (i in 0 until r.length()) {
                val a = r.optJSONArray(i) ?: continue
                if (a.length() < 5) continue
                val kmh = a.getInt(0)
                var lat = a.getLong(1)
                var lon = a.getLong(2)
                val pts = ArrayList<Pos>(a.length() / 2)
                pts += Pos(lat / SCALE, lon / SCALE)
                var k = 3
                while (k + 1 < a.length()) {
                    lat += a.getLong(k)
                    lon += a.getLong(k + 1)
                    pts += Pos(lat / SCALE, lon / SCALE)
                    k += 2
                }
                if (pts.size >= 2) roads += LimitedRoad(pts, kmh)
            }
        }
        val signals = ArrayList<Pos>()
        j.optJSONArray("l")?.let { l ->
            var k = 0
            while (k + 1 < l.length()) {
                signals += Pos(l.getLong(k) / SCALE, l.getLong(k + 1) / SCALE)
                k += 2
            }
        }
        val cameras = ArrayList<Camera>()
        j.optJSONArray("c")?.let { c ->
            var k = 0
            while (k + 2 < c.length()) {
                cameras += Camera(Pos(c.getLong(k) / SCALE, c.getLong(k + 1) / SCALE), c.getInt(k + 2).takeIf { it > 0 })
                k += 3
            }
        }
        val zones = ArrayList<Zone>()
        j.optJSONArray("z")?.let { z ->
            for (i in 0 until z.length()) {
                val a = z.optJSONArray(i) ?: continue
                if (a.length() < 5) continue
                var lat = a.getLong(1)
                var lon = a.getLong(2)
                val pts = arrayListOf(Pos(lat / SCALE, lon / SCALE))
                var k = 3
                while (k + 1 < a.length()) {
                    lat += a.getLong(k)
                    lon += a.getLong(k + 1)
                    pts += Pos(lat / SCALE, lon / SCALE)
                    k += 2
                }
                if (pts.size >= 2) zones += Zone(a.optString(0), pts)
            }
        }
        return RoadArea(roads, signals, cameras, zones)
    }

    /** The answer's code, and its text when it's 200. */
    private fun get(url: String, timeoutMs: Int): Pair<Int, String?> {
        val c = URL(url).openConnection() as HttpURLConnection
        return try {
            c.connectTimeout = timeoutMs
            c.readTimeout = timeoutMs
            c.setRequestProperty("User-Agent", AGENT)
            c.setRequestProperty("Accept", "application/json")
            val code = c.responseCode
            code to (if (code == 200) c.inputStream.bufferedReader().use { it.readText() } else null)
        } finally {
            c.disconnect()
        }
    }

    /** "50" → 50, "30 mph" → 48; "IE:rural", "none" and the like → nothing to show. */
    fun kmh(tag: String?): Int? {
        val t = tag?.substringBefore(';')?.trim()?.lowercase() ?: return null
        val n = Regex("^(\\d{1,3})(\\s*mph)?$").find(t) ?: return null
        val v = n.groupValues[1].toInt()
        return if (n.groupValues[2].isNotEmpty()) (v * 1.609).toInt() else v
    }

    /** The nearest road with a limit, within 1 km: its limit and how far, rounded to 10 m. For Settings. */
    fun nearestLimited(areas: List<RoadArea>, here: Pos): Pair<Int, Int>? {
        var best: Pair<Int, Double>? = null
        for (area in areas) for (road in area.roads) {
            val pts = road.points
            for (i in 0 until pts.size - 1) {
                val n = here.lat + 0.01
                val so = here.lat - 0.01
                if ((pts[i].lat > n && pts[i + 1].lat > n) || (pts[i].lat < so && pts[i + 1].lat < so)) continue
                val (_, d) = Geo.project(here, pts[i], pts[i + 1])
                if (d <= 1_000 && (best == null || d < best.second)) best = road.kmh to d
            }
        }
        return best?.let { it.first to (Math.round(it.second / 10.0) * 10).toInt() }
    }

    /**
     * The limit on the road the car is on: the nearest road with a limit within 25 m, preferring one
     * that runs the way the car is heading (at junctions both roads are close).
     */
    fun limitAt(areas: List<RoadArea>, here: Pos, heading: Float?): Int? {
        var best: Int? = null
        var bestScore = Double.MAX_VALUE
        for (area in areas) for (road in area.roads) {
            val pts = road.points
            for (i in 0 until pts.size - 1) {
                // Quick skip: both ends well to the north of the car, or both well to the south
                val n = here.lat + 0.001
                val so = here.lat - 0.001
                if ((pts[i].lat > n && pts[i + 1].lat > n) || (pts[i].lat < so && pts[i + 1].lat < so)) continue
                val (_, d) = Geo.project(here, pts[i], pts[i + 1])
                if (d > 25) continue
                var score = d
                if (heading != null) {
                    val b = Geo.bearing(pts[i], pts[i + 1])
                    var diff = kotlin.math.abs(((heading - b) % 360 + 540) % 360 - 180)
                    diff = minOf(diff, 180 - diff)
                    score += diff / 3
                }
                if (score < bestScore) {
                    bestScore = score
                    best = road.kmh
                }
            }
        }
        return best
    }
}
