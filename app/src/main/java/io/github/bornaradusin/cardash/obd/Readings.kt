package io.github.bornaradusin.cardash.obd

/** The latest values from the car. Null = not read yet, or the car doesn't report it. */
data class Readings(
    val rpm: Float? = null,
    val speedKmh: Float? = null,
    val coolantC: Float? = null,
    val intakeC: Float? = null,
    val oilC: Float? = null,
    val ambientC: Float? = null,
    /** Intake manifold pressure, kPa absolute. */
    val mapKpa: Float? = null,
    val baroKpa: Float? = null,
    val throttlePct: Float? = null,
    val loadPct: Float? = null,
    val fuelPct: Float? = null,
    val volts: Float? = null,
    /** The engine warning light. */
    val milOn: Boolean? = null,
    val faultCount: Int? = null,
) {
    /** Boost in bar: manifold pressure above the outside air (101.3 kPa when the car doesn't say). */
    val boostBar: Float?
        get() = mapKpa?.let { (it - (baroKpa ?: 101.3f)) / 100f }
}

enum class Link {
    /** No USB dongle plugged in. */
    NO_DONGLE,
    /** Android hasn't allowed CarDash to use the dongle yet. */
    NEEDS_PERMISSION,
    CONNECTING,
    /** The dongle answers but the car doesn't: usually the ignition is off. */
    NO_CAR,
    CONNECTED,
    /** Something else went wrong; it tries again by itself. */
    ERROR,
}

data class ObdState(
    val link: Link = Link.NO_DONGLE,
    val readings: Readings = Readings(),
    /** The dongle's own name, e.g. "ELM327 v1.5". */
    val adapter: String? = null,
    /** The car's protocol, as the dongle found it. */
    val protocol: String? = null,
    val problem: String? = null,
    /** Fault codes from the last scan; null = not scanned yet. */
    val faults: List<String>? = null,
    val scanning: Boolean = false,
    /** Readings per second, for the status line. */
    val rate: Float = 0f,
    /** The serial speed the dongle answered at. */
    val baud: Int? = null,
    /** The last lines sent to and from the dongle, for Settings. */
    val log: List<String> = emptyList(),
    val trip: Trip = Trip(),
)

/** This drive, worked out from the speed readings while the car answers. */
data class Trip(val meters: Double = 0.0, val drivingMs: Long = 0, val maxKmh: Float = 0f) {
    val avgKmh: Double get() = if (drivingMs > 0) meters / 1000.0 / (drivingMs / 3_600_000.0) else 0.0
}
