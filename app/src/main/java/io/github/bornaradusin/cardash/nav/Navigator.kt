package io.github.bornaradusin.cardash.nav

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.speech.tts.TextToSpeech
import androidx.core.content.ContextCompat
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where the car is and, on a route, how far it is to the next turn and to the end. */
data class NavState(
    val here: Pos? = null,
    /** Which way the car points, from the GPS while moving. */
    val heading: Float? = null,
    /** m/s, from the GPS. */
    val speed: Float = 0f,
    val located: Boolean = false,
    val permission: Boolean = false,
    val route: Route? = null,
    val destination: Place? = null,
    /** The next step to do (an index into the route's steps). */
    val next: Int = 1,
    val toNextM: Double = 0.0,
    val leftM: Double = 0.0,
    val leftS: Double = 0.0,
    /** Planning or replanning a route. */
    val planning: Boolean = false,
    val error: String? = null,
    val muted: Boolean = false,
    /** The street the car is on, when there's no route. */
    val street: String? = null,
    /** The speed limit on the road the car is on, km/h, when OpenStreetMap has it. */
    val limit: Int? = null,
    /** Traffic lights around the car. */
    val signals: List<Pos> = emptyList(),
    /** The nearest traffic lights ahead on the way the car is going, metres, within 500 m. */
    val signalAheadM: Int? = null,
    /** The nearest speed camera ahead on the way the car is going, metres, within 1 km, and its limit if known. */
    val cameraAheadM: Int? = null,
    val cameraKmh: Int? = null,
    /** A GoSafe speed van zone ahead within 1 km (metres to it), or the one the car is in (metres of it left). */
    val zoneAheadM: Int? = null,
    val zoneLeftM: Int? = null,
    /** The last camera or zone warning: across the whole screen for a few seconds when it goes off. */
    val alert: RoadAlert? = null,
    /** How getting the speed limits, traffic lights and cameras went, for Settings: what came back, or why not. */
    val roadsStatus: String? = null,
    /** On a route: the car's place on the route line (GPS is a few metres out), and the route's direction there. */
    val onRoute: Pos? = null,
    val routeHeading: Float? = null,
) {
    val nextStep: Step? get() = route?.steps?.getOrNull(next)
    val afterStep: Step? get() = route?.steps?.getOrNull(next + 1)
}

/** A speed camera or speed van zone coming up: [at] is when it went off, [inside] that the car is already in the zone. */
data class RoadAlert(val camera: Boolean, val at: Long, val metres: Int, val inside: Boolean = false, val kmh: Int? = null)

/**
 * CarDash's own navigation: the radio's GPS, routes from OSRM, progress along them, a new route when
 * the car leaves the planned one, and the directions read out.
 */
class Navigator(
    private val context: Context,
    private val spokenOn: () -> Boolean,
    /** Which of speed limits, traffic lights and speed cameras are switched on in Settings. */
    private val roadsOn: () -> RoadSwitches = { RoadSwitches(true, true, true) },
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _state = MutableStateFlow(NavState())
    val state: StateFlow<NavState> = _state

    private val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private var listening = false
    private var lastGps = 0L

    private var tts: TextToSpeech? = null
    private var ttsReady = false

    /** Index of the route point the car was last matched to, so matching looks nearby first. */
    private var matched = 0
    private var offCount = 0
    private var lastPlan = 0L
    private var planJob: Job? = null
    /** Which steps have had which announcements: far, near, now. */
    private val said = HashMap<Int, Int>()
    private var arrivedAt = 0L
    private var streetAt: Pos? = null
    private var streetTime = 0L
    /** The squares of road data around the car, and where each came from. */
    private val squares = HashMap<RoadInfo.Square, RoadInfo.Loaded>()
    private val squareTried = HashMap<RoadInfo.Square, Long>()
    private var squareJob: Job? = null
    private val roadsDir by lazy { java.io.File(context.filesDir, "roads") }
    /** Each camera and zone warned about, and when: once each, then not again for 10 minutes. */
    private val alerted = HashMap<String, Long>()

    private val listener = object : LocationListener {
        override fun onLocationChanged(l: Location) = onFix(l)
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        override fun onProviderEnabled(provider: String) = Unit
        override fun onProviderDisabled(provider: String) = Unit
    }

    fun hasPermission() = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Starts the GPS (once location is allowed). Safe to call again. */
    @SuppressLint("MissingPermission")
    fun start() {
        val allowed = hasPermission()
        _state.update { it.copy(permission = allowed) }
        if (!allowed || listening) return
        listening = true
        runCatching { lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1_000L, 0f, listener, Looper.getMainLooper()) }
        // The network's rough position until the GPS has a fix
        runCatching { lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5_000L, 0f, listener, Looper.getMainLooper()) }
        runCatching {
            val last = listOfNotNull(lm.getLastKnownLocation(LocationManager.GPS_PROVIDER), lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER))
                .maxByOrNull { it.time }
            last?.let { onFix(it) }
        }
    }

    private fun onFix(l: Location) {
        val gps = l.provider == LocationManager.GPS_PROVIDER
        if (gps) lastGps = System.currentTimeMillis()
        // The network's guess only until the GPS has something, and only if the GPS has gone quiet
        else if (System.currentTimeMillis() - lastGps < 10_000) return
        val here = Pos(l.latitude, l.longitude)
        val speed = if (l.hasSpeed()) l.speed else 0f
        val heading = if (l.hasBearing() && speed > 1.5f) l.bearing else _state.value.heading
        _state.update { it.copy(here = here, heading = heading, speed = speed, located = true) }
        _state.value.route?.let { follow(it, here, heading, speed) } ?: lookUpStreet(here)
        roads(here, heading, speed)
    }

    /**
     * The speed limit here, traffic lights and speed cameras ahead, from the squares of road data around
     * the car; fetches the next square before the car gets to it. Only what's switched on in Settings.
     */
    private fun roads(here: Pos, heading: Float?, speed: Float) {
        val on = roadsOn()
        val s = _state.value
        if (!on.any) {
            if (s.limit != null || s.signals.isNotEmpty() || s.signalAheadM != null || s.cameraAheadM != null || s.zoneAheadM != null || s.zoneLeftM != null) {
                _state.update {
                    it.copy(limit = null, signals = emptyList(), signalAheadM = null, cameraAheadM = null, cameraKmh = null, zoneAheadM = null, zoneLeftM = null)
                }
            }
            return
        }
        val near = RoadInfo.squaresNear(here, 600.0)
        val areas = near.mapNotNull { squares[it]?.area }
        val limit = if (on.limits) RoadInfo.limitAt(areas, here, heading) else null
        val lights = if (on.lights) areas.flatMap { it.signals } else emptyList()
        val lightAhead = if (on.lights) ahead(lights, here, heading, 500.0) else null
        val cams = if (on.cameras) areas.flatMap { it.cameras } else emptyList()
        val camAhead = if (on.cameras) cams.mapNotNull { c -> ahead(listOf(c.pos), here, heading, 1_000.0)?.let { d -> c to d } }.minByOrNull { it.second } else null
        val zone = if (on.zones) zoneNow(areas.flatMap { it.zones }.distinctBy { it.id }, here, heading) else null
        val zoneAhead = zone?.takeIf { !it.inside }?.metres
        val zoneLeft = zone?.takeIf { it.inside }?.metres
        if (limit != s.limit || lightAhead != s.signalAheadM || camAhead?.second != s.cameraAheadM || lights.size != s.signals.size ||
            zoneAhead != s.zoneAheadM || zoneLeft != s.zoneLeftM
        ) {
            _state.update {
                it.copy(
                    limit = limit, signals = lights, signalAheadM = lightAhead, cameraAheadM = camAhead?.second, cameraKmh = camAhead?.first?.kmh,
                    zoneAheadM = zoneAhead, zoneLeftM = zoneLeft,
                )
            }
        }
        // The warnings: a camera first (red, and said out loud if that's on), else a zone (orange)
        camAhead?.let { (c, d) ->
            if (alert("camera ${c.pos}", RoadAlert(camera = true, at = 0L, metres = d, kmh = c.kmh)) && on.cameraVoice) {
                val limitSaid = c.kmh?.let { ", limit $it" } ?: ""
                speak("Speed camera in ${Geo.distanceSpoken(d.toDouble())}$limitSaid", flush = false, always = true)
            }
        } ?: zone?.let { z -> alert("zone ${z.id}", RoadAlert(camera = false, at = 0L, metres = z.metres, inside = z.inside)) }

        // Keep the squares round about, forget the rest
        val own = RoadInfo.squareOf(here)
        if (squares.size > 16) squares.keys.retainAll { kotlin.math.abs(it.row - own.row) <= 2 && kotlin.math.abs(it.col - own.col) <= 2 }
        if (squareJob?.isActive == true) return
        val now = System.currentTimeMillis()
        val want = near.firstOrNull { it !in squares && now - (squareTried[it] ?: 0L) > 30_000 } ?: return
        squareTried[want] = now
        squareJob = scope.launch {
            runCatching { RoadInfo.square(roadsDir, want) }
                .onSuccess { got ->
                    squares[want] = got
                    _state.update { it.copy(roadsStatus = roadsStatus()) }
                    _state.value.here?.let { p -> roads(p, _state.value.heading, _state.value.speed) }
                }
                .onFailure { e ->
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    _state.update {
                        it.copy(roadsStatus = "Couldn't get the road data here (trying again in 30 s): ${e.message ?: e.javaClass.simpleName}")
                    }
                }
        }
    }

    /** What's loaded around the car, and where it came from, for Settings. */
    private fun roadsStatus(): String {
        val here = _state.value.here
        val near = here?.let { RoadInfo.squaresNear(it, 600.0) }.orEmpty().mapNotNull { squares[it] }
        val roads = near.sumOf { it.area.roads.size }
        val lights = near.sumOf { it.area.signals.size }
        val cams = near.sumOf { it.area.cameras.size }
        val zones = near.flatMap { it.area.zones }.distinctBy { it.id }.size
        val from = when {
            near.any { it.source == RoadSource.CARDASH } -> "CarDash's Ireland data" + (RoadInfo.builtOn?.let { " (built $it)" } ?: "")
            near.any { it.source == RoadSource.OVERPASS } -> "OpenStreetMap, live"
            else -> "the copy kept on the radio"
        }
        val clock = java.text.SimpleDateFormat("HH:mm", Locale.UK).format(java.util.Date())
        return "$roads roads with a speed limit, $lights traffic lights, $cams speed cameras and $zones speed van zones around you, from $from, at $clock"
    }

    /** Puts up a warning for [key] (a camera or a zone) unless it went off in the last 10 minutes; true if it did now. */
    private fun alert(key: String, a: RoadAlert): Boolean {
        val now = System.currentTimeMillis()
        if ((alerted[key] ?: 0L) > now - 10 * 60_000L) return false
        alerted[key] = now
        if (alerted.size > 300) alerted.entries.removeAll { it.value < now - 10 * 60_000L }
        _state.update { it.copy(alert = a.copy(at = now)) }
        return true
    }

    private class ZoneSpot(val id: String, val inside: Boolean, val metres: Int)

    /**
     * The speed van zone the car is in (within 40 m of its line: how much of it is left, the way the
     * car is going), else the nearest one ahead within 1 km (how far to it).
     */
    private fun zoneNow(zones: List<Zone>, here: Pos, heading: Float?): ZoneSpot? {
        for (z in zones) {
            val pts = z.points
            var best = Double.MAX_VALUE
            var bi = 0
            var bt = 0.0
            for (i in 0 until pts.size - 1) {
                val n = here.lat + 0.003
                val so = here.lat - 0.003
                if ((pts[i].lat > n && pts[i + 1].lat > n) || (pts[i].lat < so && pts[i + 1].lat < so)) continue
                val (t, d) = Geo.project(here, pts[i], pts[i + 1])
                if (d < best) {
                    best = d
                    bi = i
                    bt = t
                }
            }
            if (best > 40) continue
            val along = z.cum[bi] + (z.cum[bi + 1] - z.cum[bi]) * bt
            val total = z.cum.last()
            val forward = heading == null || run {
                val b = Geo.bearing(pts[bi], pts[bi + 1])
                Math.abs(((heading - b + 540.0) % 360.0) - 180.0) < 90.0
            }
            val left = if (forward) total - along else along
            return ZoneSpot(z.id, true, (Math.round(left / 10.0) * 10).toInt())
        }
        var near: Pair<Zone, Int>? = null
        for (z in zones) {
            val d = ahead(z.dense, here, heading, 1_000.0) ?: continue
            if (near == null || d < near.second) near = z to d
        }
        return near?.let { ZoneSpot(it.first.id, false, it.second) }
    }

    /** The nearest of [spots] in front of the car: within [within] metres and 25 degrees either side of the way it's going. */
    private fun ahead(spots: List<Pos>, here: Pos, heading: Float?, within: Double): Int? {
        heading ?: return null
        return spots.mapNotNull { p ->
            val d = Geo.distance(here, p)
            if (d < 8 || d > within) return@mapNotNull null
            val off = Math.abs(((Geo.bearing(here, p) - heading + 540.0) % 360.0) - 180.0)
            if (off <= 25.0) d else null
        }.minOrNull()?.let { (Math.round(it / 10.0) * 10).toInt() }
    }

    // ---------- routes ----------

    /** A route to [to] from here, to show before setting off. */
    suspend fun preview(to: Pos): Route? {
        val s = _state.value
        val from = s.here ?: return null
        return runCatching { Router.route(from, to, s.heading?.takeIf { s.speed > 2f }) }.getOrNull()
    }

    /** Sets off: follows [route] (already planned) or plans one. */
    fun go(place: Place, route: Route?) {
        said.clear()
        matched = 0
        offCount = 0
        arrivedAt = 0L
        _state.update { it.copy(destination = place, route = route, next = 1, error = null) }
        if (route == null) plan(place.pos) else _state.value.here?.let { follow(route, it, _state.value.heading, _state.value.speed) }
        speak("Starting the route to ${place.name}")
    }

    fun end() {
        planJob?.cancel()
        said.clear()
        _state.update { it.copy(route = null, destination = null, planning = false, error = null, next = 1, onRoute = null, routeHeading = null) }
    }

    fun toggleMute() {
        _state.update { it.copy(muted = !it.muted) }
        if (_state.value.muted) tts?.stop()
    }

    private fun plan(to: Pos) {
        val s = _state.value
        val from = s.here ?: run {
            _state.update { it.copy(error = "Waiting for the GPS") }
            // Try again once there's a position
            planJob = scope.launch {
                while (_state.value.here == null) delay(1_000)
                if (_state.value.destination != null) plan(to)
            }
            return
        }
        planJob?.cancel()
        lastPlan = System.currentTimeMillis()
        _state.update { it.copy(planning = true) }
        planJob = scope.launch {
            val r = runCatching { Router.route(from, to, s.heading?.takeIf { s.speed > 2f }) }
            if (_state.value.destination == null) return@launch
            r.onSuccess { route ->
                matched = 0
                offCount = 0
                said.clear()
                _state.update { it.copy(route = route, planning = false, error = null, next = 1) }
                _state.value.here?.let { follow(route, it, _state.value.heading, _state.value.speed) }
            }.onFailure { e ->
                _state.update { it.copy(planning = false, error = if (e is java.io.IOException) "No internet for a new route" else (e.message ?: "No route found")) }
            }
        }
    }

    /** Matches the car to the route, works out what's next, and replans when it has left the route. */
    private fun follow(route: Route, here: Pos, heading: Float?, speed: Float) {
        val pts = route.points
        val from = (matched - 5).coerceAtLeast(0)
        val to = (matched + 80).coerceAtMost(pts.size - 2)
        var best = matched
        var bestT = 0.0
        var bestD = Double.MAX_VALUE
        fun scan(range: IntRange) {
            for (i in range) {
                val (t, d) = Geo.project(here, pts[i], pts[i + 1])
                if (d < bestD) { bestD = d; best = i; bestT = t }
            }
        }
        scan(from..to)
        // Lost: look along the whole route
        if (bestD > 60) scan(0..pts.size - 2)

        if (bestD > OFF_ROUTE_M) {
            offCount++
            if (offCount >= 3 && !_state.value.planning && System.currentTimeMillis() - lastPlan > REPLAN_MS) {
                speak("Finding a new route")
                plan(route.to)
            }
        } else offCount = 0
        matched = best

        val along = route.cum[best] + (route.cum[best + 1] - route.cum[best]) * bestT
        val next = route.steps.indexOfFirst { it.index > best }.let { if (it < 0) route.steps.size - 1 else it }.coerceAtLeast(1)
        val step = route.steps[next]
        val toNext = (route.cum[step.index.coerceAtMost(pts.size - 1)] - along).coerceAtLeast(0.0)
        val leftM = (route.distance.coerceAtLeast(route.cum.last()) - along).coerceAtLeast(0.0)
        val prev = route.steps[next - 1]
        val partOfPrev = if (prev.distance > 0) (toNext / prev.distance).coerceIn(0.0, 1.0) else 0.0
        val leftS = prev.duration * partOfPrev + route.steps.drop(next).sumOf { it.duration }
        val snapped = bestD <= 30
        _state.update {
            it.copy(
                next = next, toNextM = toNext, leftM = leftM, leftS = leftS,
                onRoute = if (snapped) Geo.along(pts[best], pts[best + 1], bestT) else null,
                routeHeading = if (snapped) Geo.bearing(pts[best], pts[best + 1]).toFloat() else null,
            )
        }

        announce(next, step, toNext, speed)
        // There: say so, and end the route a little later
        val atEnd = Geo.distance(here, route.to) < 35 || (next == route.steps.size - 1 && toNext < 25)
        if (atEnd) {
            if (arrivedAt == 0L) {
                arrivedAt = System.currentTimeMillis()
                speak("You have arrived")
                scope.launch {
                    delay(15_000)
                    if (arrivedAt != 0L) end()
                }
            }
        }
    }

    /** Far (about a kilometre or two out at speed), near, and now: each said once per step. */
    private fun announce(index: Int, step: Step, toNext: Double, speed: Float) {
        if (step.quiet) return
        val near = maxOf(200.0, speed * 16.0)
        val now = maxOf(45.0, speed * 4.5)
        val far = if (speed > 22) 2_000.0 else 1_000.0
        val done = said[index] ?: 0
        val text = step.text()
        when {
            toNext <= now && done < 3 -> {
                said[index] = 3
                speak(if (step.type == "arrive") "You have arrived" else text)
            }
            toNext <= near && done < 2 && toNext > now -> {
                said[index] = 2
                speak("In ${Geo.distanceSpoken(toNext)}, ${text.replaceFirstChar { it.lowercase() }}")
            }
            toNext <= far && toNext > near * 2 && done < 1 && step.distance >= 0 -> {
                said[index] = 1
                speak("In ${Geo.distanceSpoken(toNext)}, ${text.replaceFirstChar { it.lowercase() }}")
            }
        }
    }

    private fun lookUpStreet(here: Pos) {
        val now = System.currentTimeMillis()
        val last = streetAt
        if (last != null && (Geo.distance(last, here) < 120 || now - streetTime < 15_000)) return
        streetAt = here
        streetTime = now
        scope.launch {
            val name = runCatching { Places.streetAt(here) }.getOrNull()
            if (name != null) _state.update { it.copy(street = name) }
        }
    }

    // ---------- voice ----------

    /** Says [text] if spoken directions are on (or [always], for camera warnings with their own switch) and not muted. */
    private fun speak(text: String, flush: Boolean = true, always: Boolean = false) {
        if ((!always && !spokenOn()) || _state.value.muted) return
        val t = tts ?: TextToSpeech(context.applicationContext) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) {
                val uk = (tts?.isLanguageAvailable(Locale.UK) ?: -1) >= TextToSpeech.LANG_AVAILABLE
                tts?.setLanguage(if (uk) Locale.UK else Locale.getDefault())
                pending?.let { p -> tts?.speak(p, TextToSpeech.QUEUE_FLUSH, null, "cardash") }
                pending = null
            }
        }.also { tts = it }
        if (ttsReady) t.speak(text, if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, "cardash") else pending = text
    }

    private var pending: String? = null

    private companion object {
        const val OFF_ROUTE_M = 45.0
        const val REPLAN_MS = 5_000L
    }
}
