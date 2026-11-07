package io.github.bornaradusin.cardash

import android.app.Application
import io.github.bornaradusin.cardash.obd.ObdLink

class CarDashApp : Application() {

    /** The OBD dongle: one connection for the whole app. */
    val obd: ObdLink by lazy { ObdLink(this) }

    val settings: Settings by lazy { Settings(this) }

    /** The radio's own CAN box (oil temperature, odometer and the rest). */
    val can: io.github.bornaradusin.cardash.car.CanBox by lazy { io.github.bornaradusin.cardash.car.CanBox(this) }

    override fun onCreate() {
        super.onCreate()
        obd.fuelFactor = settings.fuelFactor.value
        obd.diesel = settings.diesel.value
        obd.dieselQ0 = settings.dieselQ0
        obd.dieselQ1 = settings.dieselQ1
    }
}
