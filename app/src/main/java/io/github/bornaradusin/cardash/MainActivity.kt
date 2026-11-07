package io.github.bornaradusin.cardash

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings as AndroidSettings
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
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import io.github.bornaradusin.cardash.car.Apps
import io.github.bornaradusin.cardash.car.CarNotifications
import io.github.bornaradusin.cardash.car.Media
import io.github.bornaradusin.cardash.car.RadioFiles
import io.github.bornaradusin.cardash.obd.ObdLink
import io.github.bornaradusin.cardash.car.CanRole
import io.github.bornaradusin.cardash.car.canValues
import io.github.bornaradusin.cardash.car.effectivePicks
import io.github.bornaradusin.cardash.ui.CarDash
import io.github.bornaradusin.cardash.ui.DashActions
import io.github.bornaradusin.cardash.ui.DashState
import io.github.bornaradusin.cardash.ui.Scaled
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private val app get() = application as CarDashApp
    private lateinit var media: Media

    /** Bumped on resume, so permissions and installed apps are checked again after a trip to Settings. */
    private var resumes by mutableIntStateOf(0)
    private var homePresses by mutableIntStateOf(0)
    private var radioFiles by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        media = Media(this)

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
                runCatching { startActivity(Intent(AndroidSettings.ACTION_HOME_SETTINGS)) }
                    .onFailure { startActivity(Intent(AndroidSettings.ACTION_SETTINGS)) }
            },
            setName = { app.settings.setName(it) },
            setFullScreen = {
                app.settings.setFullScreen(it)
                applyFullScreen(it)
            },
            saveRadioFiles = { saveRadioFiles() },
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
        )

        setContent {
            val obd by app.obd.state.collectAsState()
            val nav by CarNotifications.nav.collectAsState()
            val now by media.now.collectAsState()
            val name by app.settings.name.collectAsState()
            val slots by app.settings.slots.collectAsState()
            val fullScreen by app.settings.fullScreen.collectAsState()
            val icons by app.settings.icons.collectAsState()
            val lastWaze by CarNotifications.lastWaze.collectAsState()
            val musicApp by app.settings.musicApp.collectAsState()
            val diesel by app.settings.diesel.collectAsState()
            val can by app.can.state.collectAsState()
            val canPicks by app.settings.canPicks.collectAsState()
            val service by app.service.state.collectAsState()
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
                now = now,
                apps = apps,
                slots = slots,
                icons = icons,
                lastWaze = lastWaze,
                musicApp = musicApp,
                hasNotifications = remember(resumes) { CarNotifications.allowed(this@MainActivity) },
                fullScreen = fullScreen,
                homePresses = homePresses,
                diesel = diesel,
                radioFiles = radioFiles,
                can = can,
                canPicks = usedPicks,
                canValues = fromCan,
                service = service,
            )
            Scaled { CarDash(state, actions) }
        }
    }

    override fun onResume() {
        super.onResume()
        resumes++
        app.obd.start()
        app.can.start()
        media.preferred = musicApp()
        media.start()
        applyFullScreen(app.settings.fullScreen.value)
    }

    override fun onDestroy() {
        media.stop()
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
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

    private fun saveRadioFiles() {
        if (radioFiles?.startsWith("Saving") == true) return
        radioFiles = "Saving…"
        Thread {
            val result = runCatching { RadioFiles.save(this) { msg -> runOnUiThread { radioFiles = msg } } }
            runOnUiThread {
                radioFiles = result.fold(
                    onSuccess = { r ->
                        "Saved ${r.files} files (${r.bytes / 1_048_576} MB) to Download/CarDash. Copy that folder to a USB stick and send the files." +
                            if (r.skipped.isEmpty()) "" else " Not copied: " + r.skipped.joinToString(", ")
                    },
                    onFailure = { "Couldn't save the files: ${it.message}" },
                )
            }
        }.start()
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
