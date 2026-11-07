package io.github.bornaradusin.cardash.nav

import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

/** One thing to do on a route: a turn, a roundabout, arriving… */
data class Step(
    /** OSRM's maneuver type: turn, new name, depart, arrive, merge, on ramp, off ramp, fork, end of road, roundabout… */
    val type: String,
    /** left, right, slight left, sharp right, straight, uturn… */
    val modifier: String?,
    val name: String,
    val ref: String?,
    /** For roundabouts: which exit. */
    val exit: Int?,
    val at: Pos,
    /** Length of the road from this step to the next, and how long it takes. */
    val distance: Double,
    val duration: Double,
    /** Where this step starts in the route's points. */
    val index: Int,
)

/** A planned drive: its line on the map and the steps along it. */
class Route(
    val points: List<Pos>,
    val steps: List<Step>,
    val distance: Double,
    val duration: Double,
    val to: Pos,
) {
    /** Metres from the start to each point. */
    val cum: DoubleArray = DoubleArray(points.size).also { c ->
        for (i in 1 until points.size) c[i] = c[i - 1] + Geo.distance(points[i - 1], points[i])
    }
}

/**
 * Plans routes with OSRM on the OpenStreetMap community's server (routing.openstreetmap.de): free,
 * no key, for light use like one car's routes.
 */
object Router {
    private const val SERVER = "https://routing.openstreetmap.de/routed-car/route/v1/driving/"

    /** A route from here to there; [heading] keeps the first instruction from turning the car around. Throws when it can't. */
    suspend fun route(from: Pos, to: Pos, heading: Float? = null): Route {
        val coords = String.format(Locale.US, "%.6f,%.6f;%.6f,%.6f", from.lon, from.lat, to.lon, to.lat)
        val bearings = heading?.let { String.format(Locale.US, "&bearings=%d,45;", it.toInt().mod(360)) } ?: ""
        val json = JSONObject(Http.get("$SERVER$coords?overview=false&geometries=geojson&steps=true$bearings"))
        if (json.optString("code") != "Ok") throw IllegalStateException(json.optString("message").ifEmpty { "No route found" })
        val r = json.getJSONArray("routes").getJSONObject(0)
        val points = ArrayList<Pos>()
        val steps = ArrayList<Step>()
        val legs = r.getJSONArray("legs")
        for (l in 0 until legs.length()) {
            val ss = legs.getJSONObject(l).getJSONArray("steps")
            for (i in 0 until ss.length()) {
                val s = ss.getJSONObject(i)
                val m = s.getJSONObject("maneuver")
                val geo = s.getJSONObject("geometry").getJSONArray("coordinates")
                val start = if (points.isEmpty()) 0 else points.size - 1
                for (k in 0 until geo.length()) {
                    if (k == 0 && points.isNotEmpty()) continue
                    points += pos(geo.getJSONArray(k))
                }
                steps += Step(
                    type = m.optString("type"),
                    modifier = m.optString("modifier").ifEmpty { null },
                    name = s.optString("name"),
                    ref = s.optString("ref").ifEmpty { null },
                    exit = if (m.has("exit")) m.optInt("exit") else null,
                    at = pos(m.getJSONArray("location")),
                    distance = s.optDouble("distance", 0.0),
                    duration = s.optDouble("duration", 0.0),
                    index = start,
                )
            }
        }
        if (points.size < 2) throw IllegalStateException("No route found")
        return Route(points, steps, r.optDouble("distance"), r.optDouble("duration"), to)
    }

    private fun pos(a: JSONArray) = Pos(a.getDouble(1), a.getDouble(0))
}

/** The road a step goes onto, as said: "Main Street", "the N18". */
fun Step.road(): String? = when {
    name.isNotBlank() -> name
    !ref.isNullOrBlank() -> "the ${ref.substringBefore(';').trim()}"
    else -> null
}

private fun ordinal(n: Int) = when (n) {
    1 -> "first"; 2 -> "second"; 3 -> "third"; 4 -> "fourth"; 5 -> "fifth"; 6 -> "sixth"; 7 -> "seventh"
    else -> "${n}th"
}

private fun turnWord(modifier: String?): String = when (modifier) {
    "left" -> "Turn left"
    "right" -> "Turn right"
    "slight left" -> "Bear left"
    "slight right" -> "Bear right"
    "sharp left" -> "Turn sharp left"
    "sharp right" -> "Turn sharp right"
    "uturn" -> "Make a U-turn"
    else -> "Continue straight"
}

/** What to do, in words: "Turn right onto Main Street", "At the roundabout, take the second exit". */
fun Step.text(): String {
    val road = road()
    val onto = road?.let { " onto $it" } ?: ""
    return when (type) {
        "depart" -> road?.let { "Head along $it" } ?: "Start the route"
        "arrive" -> "Arrive at your destination"
        "turn", "end of road" -> {
            val t = turnWord(modifier)
            (if (type == "end of road") "At the end of the road, ${t.replaceFirstChar { it.lowercase() }}" else t) + onto
        }
        "new name", "continue" -> if (modifier == "uturn") "Make a U-turn$onto" else road?.let { "Continue onto $it" } ?: "Continue straight"
        "merge" -> "Merge$onto"
        "on ramp" -> "Take the slip road$onto"
        "off ramp" -> when (modifier) {
            "left", "slight left", "sharp left" -> "Take the exit on the left$onto"
            else -> "Take the exit$onto"
        }
        "fork" -> when (modifier) {
            "left", "slight left", "sharp left" -> "Keep left$onto"
            "right", "slight right", "sharp right" -> "Keep right$onto"
            else -> "Keep straight$onto"
        }
        "roundabout", "rotary", "roundabout turn" ->
            exit?.let { "At the roundabout, take the ${ordinal(it)} exit$onto" } ?: "Go round the roundabout$onto"
        "exit roundabout", "exit rotary" -> "Leave the roundabout$onto"
        else -> road?.let { "Continue onto $it" } ?: "Continue straight"
    }
}

/** Steps that don't need saying: carrying on along the same road. */
val Step.quiet: Boolean get() = type == "depart" || ((type == "new name" || type == "continue") && modifier in setOf(null, "straight"))
