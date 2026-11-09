package io.github.bornaradusin.cardash

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import io.github.bornaradusin.cardash.car.Apps
import io.github.bornaradusin.cardash.car.CanRole
import io.github.bornaradusin.cardash.car.CarNotifications
import io.github.bornaradusin.cardash.car.Media
import io.github.bornaradusin.cardash.car.canValues
import io.github.bornaradusin.cardash.car.effectivePicks
import io.github.bornaradusin.cardash.nav.Places
import io.github.bornaradusin.cardash.nav.SharedPlace
import io.github.bornaradusin.cardash.obd.ObdLink
import io.github.bornaradusin.cardash.ui.C
import io.github.bornaradusin.cardash.ui.CarDash
import io.github.bornaradusin.cardash.ui.CarKeyboardHost
import io.github.bornaradusin.cardash.ui.DashActions
import io.github.bornaradusin.cardash.ui.DashState
import io.github.bornaradusin.cardash.ui.Scaled
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private val app get() = application as CarDashApp
    private lateinit var media: Media

    /** Bumped on resume, so permissions and installed apps are checked again after a trip to Settings. */
    private var resumes by mutableIntStateOf(0)
    private var homePresses by mutableIntStateOf(0)
    /** Goes up each time a shared place's route starts: back to the main dash, map in the middle. */
    private var sharedRoutes by mutableIntStateOf(0)
    /** A shared place that couldn't be found on its own: its name and address, for Where to? */
    private var searchFor by mutableStateOf<String?>(null)
    /** Asked once per start, so the map doesn't keep asking after a no. */
    private var askedLocation = false
    private val locationRequest = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { app.nav.start() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        media = Media(this)
        takeShared(intent)

        val actions = DashActions(
            launch = { pkg -> Apps.launch(this, pkg) },
            setSlot = { i, pkg -> app.settings.setSlot(i, pkg) },
            playPause = { media.playPause(musicApp()) },
            next = { media.next() },
            previous = { media.previous() },
            scanFaults = { app.obd.scanFaults() },
            askUsbAgain = { app.obd.askAgain() },
            setIcon = { i, key -> app.settings.setIcon(i, key) },
            setMusicApp = {
                app.settings.setMusicApp(it)
                media.preferred = it
            },
            allowNotifications = {
                startActivity(Intent(AndroidSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            },
            chooseHome = {
                // Android's own "use as the home app?" where it has one (10 and later), else Home settings
                val roles = if (android.os.Build.VERSION.SDK_INT >= 29) getSystemService(android.app.role.RoleManager::class.java) else null
                val asked = android.os.Build.VERSION.SDK_INT >= 29 && roles != null &&
                    roles.isRoleAvailable(android.app.role.RoleManager.ROLE_HOME) &&
                    runCatching { startActivity(roles.createRequestRoleIntent(android.app.role.RoleManager.ROLE_HOME)) }.isSuccess
                if (!asked) {
                    runCatching { startActivity(Intent(AndroidSettings.ACTION_HOME_SETTINGS)) }
                        .onFailure { startActivity(Intent(AndroidSettings.ACTION_SETTINGS)) }
                }
            },
            setName = { app.settings.setName(it) },
            setFullScreen = {
                app.settings.setFullScreen(it)
                applyFullScreen(it)
            },
            matchFuel = idle@{ carSays ->
                val now = app.obd.state.value.readings
                val rpm = now.rpm
                val load = now.loadPct
                if (app.obd.diesel) {
                    // Diesel at idle: the per-injection offset that gives what the car says
                    if (rpm != null && rpm > 300f && load != null && carSays > 0f) {
                        val mg = carSays * ObdLink.DIESEL_G_PER_L / 3600f * 30f * 1000f / rpm
                        app.settings.dieselQ0 = mg - app.obd.dieselQ1 * load
                        app.obd.dieselQ0 = app.settings.dieselQ0
                    }
                    return@idle
                }
                // Petrol: the factor that turns the air-flow figure now into what the car says
                val raw = now.fuelLphRaw
                if (raw != null && raw > 0.05f && carSays > 0f) {
                    app.settings.setFuelFactor(carSays / raw)
                    app.obd.fuelFactor = app.settings.fuelFactor.value
                }
            },
            setCanPick = { role, pick -> app.settings.setCanPick(role, pick) },
            uninstall = { pkg -> Apps.uninstall(this, pkg) },
            appInfo = { pkg -> Apps.info(this, pkg) },
            matchFuelAverage = drive@{ carAvg ->
                val trip = app.obd.state.value.trip
                if (app.obd.diesel) {
                    // Diesel over a drive: keep the idle offset, find the load slope that gives the car's figure
                    val grams = carAvg / 100.0 * (trip.meters / 1000.0) * ObdLink.DIESEL_G_PER_L
                    if (trip.injectionsLoad > 0.0 && carAvg > 0f && trip.meters > 500) {
                        val q1 = ((grams - app.obd.dieselQ0 * trip.injections) / trip.injectionsLoad).toFloat()
                        if (q1 > 0f) {
                            app.settings.dieselQ1 = q1
                            app.obd.dieselQ1 = q1
                        }
                    }
                    return@drive
                }
                // Petrol: this drive's fuel was counted with the factor in use, so scale that factor
                val ours = trip.avgL100
                if (ours != null && ours > 0.1 && carAvg > 0f) {
                    app.settings.setFuelFactor(app.obd.fuelFactor * (carAvg / ours.toFloat()))
                    app.obd.fuelFactor = app.settings.fuelFactor.value
                }
            },
            resetFuel = {
                app.settings.setFuelFactor(1f)
                app.obd.fuelFactor = 1f
                app.settings.dieselQ0 = ObdLink.DIESEL_Q0
                app.settings.dieselQ1 = ObdLink.DIESEL_Q1
                app.obd.dieselQ0 = ObdLink.DIESEL_Q0
                app.obd.dieselQ1 = ObdLink.DIESEL_Q1
            },
            setDiesel = { on ->
                app.settings.setDiesel(on)
                app.obd.diesel = on
                // Spark plugs or the fuel filter, if that item hasn't been set up yet
                app.service.setEngine(on)
            },
            saveService = { app.service.save(it) },
            removeService = { app.service.remove(it) },
            serviceDone = { app.service.done(it) },
            setRemindKm = { app.service.setKmBefore(it) },
            setRemindDays = { app.service.setDaysBefore(it) },
            setCarKm = { app.service.setCarKm(it) },
            serviceLater = { app.service.later() },
            setSpeedWarnOn = { app.settings.setSpeedWarnOn(it) },
            setSpeedWarnKmh = { app.settings.setSpeedWarnKmh(it) },
            setShiftWarnOn = { app.settings.setShiftWarnOn(it) },
            setShiftWarnRpm = { app.settings.setShiftWarnRpm(it) },
            setTopGear = { app.settings.setTopGear(it) },
            setGearCount = { app.gears.setCount(it) },
            setShiftHints = { app.settings.setShiftHints(it) },
            setRoadLimits = { app.settings.setRoadLimits(it) },
            setRoadLights = { app.settings.setRoadLights(it) },
            setRoadCameras = { app.settings.setRoadCameras(it) },
            setRoadZones = { app.settings.setRoadZones(it) },
            setCameraVoice = { app.settings.setCameraVoice(it) },
            reloadRoads = { app.nav.reloadRoads() },
            setShiftUpAt = { g, r -> app.settings.setShiftUpAt(g, r) },
            setShiftDownAt = { g, r -> app.settings.setShiftDownAt(g, r) },
            resetShiftPoints = { app.settings.resetShiftPoints() },
            relearnGears = { app.gears.forget() },
            search = { q -> Places.search(q, app.nav.state.value.here, app.settings.googleKey.value) },
            preview = { p -> app.nav.preview(p.pos) },
            go = { p, route ->
                app.places.used(p)
                app.nav.go(p, route)
            },
            endRoute = { app.nav.end() },
            toggleMute = { app.nav.toggleMute() },
            setHome = { app.places.setHome(it) },
            setWork = { app.places.setWork(it) },
            forgetRecent = { app.places.forget(it) },
            askLocation = { askLocation(force = false) },
            allowLocation = { askLocation(force = true) },
            searchTaken = { searchFor = null },
            setSpoken = { app.settings.setSpoken(it) },
            setGoogleKey = { app.settings.setGoogleKey(it) },
            setAccent = { argb ->
                app.settings.setAccent(argb)
                C.setAccent(Color(app.settings.accent.value))
            },
            saveMapAround = { app.nav.state.value.here?.let { app.offlineMaps.saveAround(it, 50.0, "Around here, 50 km") } },
            saveMapCountry = { app.offlineMaps.saveCountry(it) },
            freeMapMb = { app.offlineMaps.freeMb() },
            saveMapRoute = {
                val n = app.nav.state.value
                n.route?.let { app.offlineMaps.saveAlong(it, "Route to ${n.destination?.name ?: "destination"}") }
            },
            pauseMap = { app.offlineMaps.pause(it) },
            resumeMap = { app.offlineMaps.resume(it) },
            updateMap = { app.offlineMaps.update(it) },
            removeMap = { app.offlineMaps.remove(it) },
            exportMaps = { uri, done -> app.offlineMaps.exportTo({ contentResolver.openOutputStream(uri) }, done) },
            importMaps = { uri, done -> app.offlineMaps.importFrom({ contentResolver.openInputStream(uri) }, done) },
            setWifiOnly = {
                app.settings.setWifiOnly(it)
                app.offlineMaps.applyNetwork()
            },
        )
        C.setAccent(Color(app.settings.accent.value))
        app.offlineMaps.start()

        setContent {
            val obd by app.obd.state.collectAsState()
            val google by CarNotifications.nav.collectAsState()
            val nav by app.nav.state.collectAsState()
            val now by media.now.collectAsState()
            val name by app.settings.name.collectAsState()
            val slots by app.settings.slots.collectAsState()
            val fullScreen by app.settings.fullScreen.collectAsState()
            val icons by app.settings.icons.collectAsState()
            val musicApp by app.settings.musicApp.collectAsState()
            val diesel by app.settings.diesel.collectAsState()
            val can by app.can.state.collectAsState()
            val canPicks by app.settings.canPicks.collectAsState()
            val service by app.service.state.collectAsState()
            val speedWarnOn by app.settings.speedWarnOn.collectAsState()
            val speedWarnKmh by app.settings.speedWarnKmh.collectAsState()
            val shiftWarnOn by app.settings.shiftWarnOn.collectAsState()
            val shiftWarnRpm by app.settings.shiftWarnRpm.collectAsState()
            val topGear by app.settings.topGear.collectAsState()
            val gear by app.gears.gear.collectAsState()
            val gearsFound by app.gears.found.collectAsState()
            val gearCount by app.gears.count.collectAsState()
            val gearRatios by app.gears.learnt.collectAsState()
            val rolling by app.gears.rolling.collectAsState()
            val shiftUpAt by app.settings.shiftUpAt.collectAsState()
            val shiftDownAt by app.settings.shiftDownAt.collectAsState()
            val shiftHints by app.settings.shiftHints.collectAsState()
            val roadLimits by app.settings.roadLimits.collectAsState()
            val roadLights by app.settings.roadLights.collectAsState()
            val roadCameras by app.settings.roadCameras.collectAsState()
            val roadZones by app.settings.roadZones.collectAsState()
            val cameraVoice by app.settings.cameraVoice.collectAsState()
            val accent by app.settings.accent.collectAsState()
            val spoken by app.settings.spoken.collectAsState()
            val googleKey by app.settings.googleKey.collectAsState()
            val wifiOnly by app.settings.wifiOnly.collectAsState()
            val home by app.places.home.collectAsState()
            val work by app.places.work.collectAsState()
            val recent by app.places.recent.collectAsState()
            val mapAreas by app.offlineMaps.areas.collectAsState()
            val usedPicks = remember(can, canPicks) { effectivePicks(can.values, canPicks) }
            val fromCan = remember(can, usedPicks) { canValues(can.values, usedPicks) }
            // The CAN box's values go where the dashboard shows them; OBD fills in the rest
            val merged = obd.copy(
                readings = obd.readings.copy(
                    oilC = fromCan[CanRole.OIL] ?: obd.readings.oilC,
                    odometerKm = fromCan[CanRole.ODOMETER],
                    ambientC = fromCan[CanRole.OUTSIDE] ?: obd.readings.ambientC,
                    coolantC = obd.readings.coolantC ?: fromCan[CanRole.COOLANT],
                ),
            )
            var apps by remember { mutableStateOf(emptyList<io.github.bornaradusin.cardash.car.AppEntry>()) }
            LaunchedEffect(resumes) {
                apps = withContext(Dispatchers.IO) { Apps.all(this@MainActivity) }
            }
            val state = DashState(
                name = name,
                obd = merged,
                nav = nav,
                google = google,
                now = now,
                apps = apps,
                slots = slots,
                icons = icons,
                musicApp = musicApp,
                hasNotifications = remember(resumes) { CarNotifications.allowed(this@MainActivity) },
                isHomeApp = remember(resumes) { isHomeApp() },
                fullScreen = fullScreen,
                homePresses = homePresses,
                sharedRoutes = sharedRoutes,
                searchFor = searchFor,
                diesel = diesel,
                can = can,
                canPicks = usedPicks,
                canValues = fromCan,
                service = service,
                speedWarnOn = speedWarnOn,
                speedWarnKmh = speedWarnKmh,
                shiftWarnOn = shiftWarnOn,
                shiftWarnRpm = shiftWarnRpm,
                topGear = topGear,
                gear = gear,
                rolling = rolling,
                shiftUpAt = shiftUpAt,
                shiftDownAt = shiftDownAt,
                gearsFound = gearsFound,
                gearCount = gearCount,
                gearRatios = gearRatios,
                shiftHints = shiftHints,
                roadLimits = roadLimits,
                roadLights = roadLights,
                roadCameras = roadCameras,
                roadZones = roadZones,
                cameraVoice = cameraVoice,
                accent = accent,
                spoken = spoken,
                googleKey = googleKey,
                wifiOnly = wifiOnly,
                home = home,
                work = work,
                recent = recent,
                mapAreas = mapAreas,
            )
            Scaled {
                CarKeyboardHost { CarDash(state, actions) }
                // An update found by itself waits until the car stands still
                io.github.bornaradusin.cardash.ui.UpdatePrompt(app.updater, moving = (obd.readings.speedKmh ?: (nav.speed * 3.6f)) > 3f)
            }
        }
    }

    /** Whether CarDash is the radio's home app, which the Home button and starting the radio open. */
    private fun isHomeApp(): Boolean {
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            val roles = getSystemService(android.app.role.RoleManager::class.java)
            if (roles != null && roles.isRoleAvailable(android.app.role.RoleManager.ROLE_HOME))
                return roles.isRoleHeld(android.app.role.RoleManager.ROLE_HOME)
        }
        val home = packageManager.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), android.content.pm.PackageManager.MATCH_DEFAULT_ONLY,
        )
        return home?.activityInfo?.packageName == packageName
    }

    override fun onResume() {
        super.onResume()
        resumes++
        app.obd.start()
        app.can.start()
        media.preferred = musicApp()
        media.start()
        applyFullScreen(app.settings.fullScreen.value)
        // The GPS, if location is allowed (asked for the first time the map or Where to? opens)
        app.nav.start()
    }

    /** Asks for location for the map and directions; [force] asks again even after a no this time. */
    private fun askLocation(force: Boolean) {
        if (app.nav.hasPermission()) {
            app.nav.start()
            return
        }
        if (askedLocation && !force) return
        // Said no for good: Android won't ask again, so open CarDash's page in Android's settings
        if (askedLocation && force && !shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
            runCatching {
                startActivity(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.fromParts("package", packageName, null)))
            }
            return
        }
        askedLocation = true
        locationRequest.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    override fun onDestroy() {
        media.stop()
        shareScope.cancel()
        super.onDestroy()
    }

    private val shareScope = MainScope()

    /**
     * A place shared to CarDash (Google Maps' Share → CarDash, or a geo: link from another app):
     * finds where it is and starts the route there straight away.
     */
    private fun takeShared(intent: Intent?) {
        intent ?: return
        val text = when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(intent.getStringExtra(Intent.EXTRA_TEXT), intent.getStringExtra(Intent.EXTRA_SUBJECT))
                .joinToString("\n")
            Intent.ACTION_VIEW -> intent.dataString
            else -> null
        }?.takeIf { it.isNotBlank() } ?: return
        // Taken: so the same share isn't driven to twice
        intent.action = null
        Toast.makeText(this, "Finding the place…", Toast.LENGTH_SHORT).show()
        shareScope.launch {
            val found = runCatching { SharedPlace.from(text, app.settings.googleKey.value, app.nav.state.value.here) }.getOrNull()
            val place = found?.place
            if (place == null) {
                // Not found on its own: the name and address go in Where to? to pick from
                val words = found?.words
                if (words != null) {
                    searchFor = words
                    Toast.makeText(this@MainActivity, "Pick the place from the search", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@MainActivity, "There was no place in what was shared", Toast.LENGTH_LONG).show()
                }
                return@launch
            }
            val route = app.nav.preview(place.pos)
            app.places.used(place)
            app.nav.go(place, route)
            sharedRoutes++
            Toast.makeText(this@MainActivity, "Route to ${place.name}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        takeShared(intent)
        // The dongle was just plugged in: look for it straight away
        app.obd.start()
        // The radio's Home button while CarDash is open: back to the Home page
        if (intent.hasCategory(Intent.CATEGORY_HOME)) homePresses++
    }

    /** Pressing Back on Home does nothing: there's nowhere further back to go. */
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() = Unit

    /** The app Play starts when nothing is playing: the one chosen in Settings, else YouTube Music. */
    private fun musicApp(): String? {
        val installed = Apps.all(this)
        val chosen = app.settings.musicApp.value?.takeIf { pkg -> installed.any { it.pkg == pkg } }
        return chosen ?: Apps.defaultFor(2, installed)?.pkg
    }

    private fun applyFullScreen(on: Boolean) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (on) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}
