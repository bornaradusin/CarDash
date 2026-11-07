package io.github.bornaradusin.cardash

import android.app.Application
import io.github.bornaradusin.cardash.car.CanRole
import io.github.bornaradusin.cardash.car.ServiceBook
import io.github.bornaradusin.cardash.car.canValues
import io.github.bornaradusin.cardash.car.effectivePicks
import io.github.bornaradusin.cardash.nav.Navigator
import io.github.bornaradusin.cardash.nav.OfflineMaps
import io.github.bornaradusin.cardash.nav.Places
import io.github.bornaradusin.cardash.obd.Gears
import io.github.bornaradusin.cardash.obd.Link
import io.github.bornaradusin.cardash.obd.ObdLink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre

class CarDashApp : Application() {

    /** The OBD dongle: one connection for the whole app. */
    val obd: ObdLink by lazy { ObdLink(this) }

    val settings: Settings by lazy { Settings(this) }

    /** The radio's own CAN box (oil temperature, odometer and the rest). */
    val can: io.github.bornaradusin.cardash.car.CanBox by lazy { io.github.bornaradusin.cardash.car.CanBox(this) }

    /** What needs servicing, counted down from the car's odometer. */
    val service: ServiceBook by lazy { ServiceBook(this, settings.diesel.value) }

    /** CarDash's own navigation: GPS, routes, directions. */
    val nav: Navigator by lazy { Navigator(this) { settings.spoken.value } }

    /** Home, Work and recent places. */
    val places: Places by lazy { Places(this) }

    /** Map areas saved for driving without signal. */
    val offlineMaps: OfflineMaps by lazy { OfflineMaps(this) { settings.wifiOnly.value } }

    /** Which gear the car is in, worked out from the revs and the speed. */
    val gears: Gears by lazy { Gears(this) }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // The map library, once for the whole app
        MapLibre.getInstance(this)
        obd.fuelFactor = settings.fuelFactor.value
        obd.diesel = settings.diesel.value
        obd.dieselQ0 = settings.dieselQ0
        obd.dieselQ1 = settings.dieselQ1

        // The car's odometer from the CAN box, as the dashboard shows it
        scope.launch {
            combine(can.state, settings.canPicks) { c, picks -> canValues(c.values, effectivePicks(c.values, picks))[CanRole.ODOMETER] }
                .distinctUntilChanged()
                .collect { service.onCanOdometer(it) }
        }
        // Distance from OBD speed, for when the CAN box gives no odometer
        scope.launch {
            obd.state.map { it.trip.meters }.distinctUntilChanged().collect { service.onDriven(it) }
        }
        // The gear, from each reading
        scope.launch {
            obd.state.map { it.readings.rpm to it.readings.speedKmh }.distinctUntilChanged().collect { (rpm, kmh) -> gears.feed(rpm, kmh) }
        }
        // A new drive: the car answers again after a few minutes without it (or CarDash started)
        scope.launch {
            var lastConnected = System.currentTimeMillis()
            obd.state.map { it.link == Link.CONNECTED }.collect { connected ->
                if (!connected) return@collect
                val now = System.currentTimeMillis()
                if (now - lastConnected > NEW_DRIVE_MS) service.newDrive()
                lastConnected = now
            }
        }
    }

    private companion object {
        const val NEW_DRIVE_MS = 3 * 60_000L
    }
}
