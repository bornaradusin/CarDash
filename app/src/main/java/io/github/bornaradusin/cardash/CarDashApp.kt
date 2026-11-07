package io.github.bornaradusin.cardash

import android.app.Application
import io.github.bornaradusin.cardash.obd.ObdLink

class CarDashApp : Application() {

    /** The OBD dongle: one connection for the whole app. */
    val obd: ObdLink by lazy { ObdLink(this) }

    val settings: Settings by lazy { Settings(this) }
}
