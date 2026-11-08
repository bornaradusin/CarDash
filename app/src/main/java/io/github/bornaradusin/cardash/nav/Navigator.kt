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
    /** How getting the speed limits and traffic lights went, for Settings: what came back, or why not. */
    val roadsStatus: String? = null,
    /** On a route: the car's place on the route line (GPS is a few metres out), and the route's direction there. */
    val onRoute: Pos? = null,
    val routeHeading: Float? = null,
) {
    val nextStep: Step? get() = route?.steps?.getOrNull(next)
    val afterStep: Step? get() = route?.steps?.getOrNull(next + 1)
}

/**
 * CarDash's own navigation: the radio's GPS, routes from OSRM, progress along them, a new route when
 * the car leaves the planned one, and the directions read out.
 */
class Navigator(private val context: Context, private val spokenOn: () -> Boolean) {
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
    private var area: RoadArea? = null
    private var areaJob: Job? = null
    private var areaTried = 0L

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
        roads(here, heading)
    }

    /** The speed limit here, and traffic lights around; fetches the next area before the car leaves this one. */
    private fun roads(here: Pos, heading: Float?) {
        val a = area
        if (a != null) {
            val limit = RoadInfo.limitAt(a, here, heading)
            val ahead = signalAhead(a.signals, here, heading)
            if (limit != _state.value.limit || ahead != _state.value.signalAheadM) _state.update { it.copy(limit = limit, signalAheadM = ahead) }
        }
        val near = a != null && Geo.distance(a.centre, here) < RoadInfo.RADIUS_M - 600
        val now = System.currentTimeMillis()
        if (near || areaJob?.isActive == true || now - areaTried < 30_000) return
        areaTried = now
        areaJob = scope.launch {
            runCatching { RoadInfo.around(here) }
                .onSuccess { fresh ->
                    area = fresh
                    val clock = java.text.SimpleDateFormat("HH:mm", java.util.Locale.UK).format(java.util.Date())
                    val status = "Got speed limits on ${fresh.roads.size} roads and ${fresh.signals.size} traffic lights within " +
                        "${RoadInfo.RADIUS_M / 1000.0} km at $clock, from OpenStreetMap"
                    _state.update {
                        it.copy(signals = fresh.signals, limit = RoadInfo.limitAt(fresh, here, heading),
                            signalAheadM = signalAhead(fresh.signals, here, heading), roadsStatus = status)
                    }
                }
                .onFailure { e ->
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    _state.update { it.copy(roadsStatus = "Couldn't get speed limits and traffic lights (trying again in 30 s): ${e.message ?: e.javaClass.simpleName}") }
                }
        }
    }

    /** The nearest lights in front of the car: within 500 m and 25 degrees either side of the way it's going. */
    private fun signalAhead(signals: List<Pos>, here: Pos, heading: Float?): Int? {
        heading ?: return null
        return signals.mapNotNull { p ->
            val d = Geo.distance(here, p)
            if (d < 8 || d > 500) return@mapNotNull null
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

    private fun speak(text: String) {
        if (!spokenOn() || _state.value.muted) return
        val t = tts ?: TextToSpeech(context.applicationContext) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) {
                val uk = (tts?.isLanguageAvailable(Locale.UK) ?: -1) >= TextToSpeech.LANG_AVAILABLE
                tts?.setLanguage(if (uk) Locale.UK else Locale.getDefault())
                pending?.let { p -> tts?.speak(p, TextToSpeech.QUEUE_FLUSH, null, "cardash") }
                pending = null
            }
        }.also { tts = it }
        if (ttsReady) t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "cardash") else pending = text
    }

    private var pending: String? = null

    private companion object {
        const val OFF_ROUTE_M = 45.0
        const val REPLAN_MS = 15_000L
    }
}
