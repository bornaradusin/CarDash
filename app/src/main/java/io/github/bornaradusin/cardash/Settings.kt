package io.github.bornaradusin.cardash

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import io.github.bornaradusin.cardash.car.CanPick
import io.github.bornaradusin.cardash.car.CanRole

/** What the person has chosen, kept on the radio. */
class Settings(context: Context) {
    private val sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _name = MutableStateFlow(sp.getString("name", null) ?: "CarDash")
    /** Shown top left. */
    val name: StateFlow<String> = _name

    fun setName(v: String) {
        val clean = v.trim().take(20).ifEmpty { "CarDash" }
        sp.edit().putString("name", clean).apply()
        _name.value = clean
    }

    private val _slots = MutableStateFlow((0 until SLOTS).map { sp.getString("slot$it", null) })
    /** The package for each app button on Home; null = pick the usual app for that slot. */
    val slots: StateFlow<List<String?>> = _slots

    fun setSlot(i: Int, pkg: String) {
        sp.edit().putString("slot$i", pkg).apply()
        _slots.value = _slots.value.toMutableList().also { it[i] = pkg }
    }

    private val _icons = MutableStateFlow((0 until SLOTS).map { sp.getString("icon$it", null) ?: DEFAULT_ICONS[it] })
    /** The dashboard icon for each app button; "app" = the app's own icon. */
    val icons: StateFlow<List<String>> = _icons

    fun setIcon(i: Int, key: String) {
        sp.edit().putString("icon$i", key).apply()
        _icons.value = _icons.value.toMutableList().also { it[i] = key }
    }

    private val _musicApp = MutableStateFlow(sp.getString("musicApp", null))
    /** The music player Play starts, and whose music the bar prefers; null = YouTube Music. */
    val musicApp: StateFlow<String?> = _musicApp

    fun setMusicApp(pkg: String) {
        sp.edit().putString("musicApp", pkg).apply()
        _musicApp.value = pkg
    }

    private val _diesel = MutableStateFlow(sp.getBoolean("diesel", false))
    val diesel: StateFlow<Boolean> = _diesel

    fun setDiesel(v: Boolean) {
        sp.edit().putBoolean("diesel", v).apply()
        _diesel.value = v
        // The change points are kept for each kind of engine: the other kind's come back
        _shiftUpAt.value = points(upKey(v), shiftUpDefault(v))
        _shiftDownAt.value = points(downKey(v), shiftDownDefault(v))
    }

    var dieselQ0: Float
        get() = sp.getFloat("dieselQ0", io.github.bornaradusin.cardash.obd.ObdLink.DIESEL_Q0)
        set(v) = sp.edit().putFloat("dieselQ0", v).apply()

    var dieselQ1: Float
        get() = sp.getFloat("dieselQ1", io.github.bornaradusin.cardash.obd.ObdLink.DIESEL_Q1)
        set(v) = sp.edit().putFloat("dieselQ1", v).apply()

    private val _fuelFactor = MutableStateFlow(sp.getFloat("fuelFactor", 1f))
    /** Scales the air-flow fuel figure to match the car's own. */
    val fuelFactor: StateFlow<Float> = _fuelFactor

    fun setFuelFactor(v: Float) {
        val f = v.coerceIn(0.05f, 3f)
        sp.edit().putFloat("fuelFactor", f).apply()
        _fuelFactor.value = f
    }

    private val _canPicks = MutableStateFlow(
        CanRole.entries.mapNotNull { role -> CanPick.decode(sp.getString("can_${role.name}", null))?.let { role to it } }.toMap(),
    )
    /** Which CAN box value is which (oil temperature, odometer…). */
    val canPicks: StateFlow<Map<CanRole, CanPick>> = _canPicks

    fun setCanPick(role: CanRole, pick: CanPick?) {
        sp.edit().apply { if (pick == null) remove("can_${role.name}") else putString("can_${role.name}", pick.encode()) }.apply()
        _canPicks.value = if (pick == null) _canPicks.value - role else _canPicks.value + (role to pick)
    }

    private val _fullScreen = MutableStateFlow(sp.getBoolean("fullScreen", true))
    /** Hides the radio's own bars (a swipe from the edge brings them back). */
    val fullScreen: StateFlow<Boolean> = _fullScreen

    fun setFullScreen(v: Boolean) {
        sp.edit().putBoolean("fullScreen", v).apply()
        _fullScreen.value = v
    }

    private val _speedWarnOn = MutableStateFlow(sp.getBoolean("speedWarnOn", true))
    /** "Slow down" across the whole screen when over [speedWarnKmh]. */
    val speedWarnOn: StateFlow<Boolean> = _speedWarnOn

    fun setSpeedWarnOn(v: Boolean) {
        sp.edit().putBoolean("speedWarnOn", v).apply()
        _speedWarnOn.value = v
    }

    private val _speedWarnKmh = MutableStateFlow(sp.getInt("speedWarnKmh", SPEED_WARN_DEFAULT))
    val speedWarnKmh: StateFlow<Int> = _speedWarnKmh

    fun setSpeedWarnKmh(v: Int) {
        val k = (v / SPEED_WARN_STEP * SPEED_WARN_STEP).coerceIn(SPEED_WARN_MIN, SPEED_WARN_MAX)
        sp.edit().putInt("speedWarnKmh", k).apply()
        _speedWarnKmh.value = k
    }

    private val _shiftWarnOn = MutableStateFlow(sp.getBoolean("shiftWarnOn", true))
    /** "Shift up" across the whole screen over [shiftWarnRpm], except in top gear. */
    val shiftWarnOn: StateFlow<Boolean> = _shiftWarnOn

    fun setShiftWarnOn(v: Boolean) {
        sp.edit().putBoolean("shiftWarnOn", v).apply()
        _shiftWarnOn.value = v
    }

    private val _shiftWarnRpm = MutableStateFlow(sp.getInt("shiftWarnRpm", SHIFT_WARN_DEFAULT))
    val shiftWarnRpm: StateFlow<Int> = _shiftWarnRpm

    fun setShiftWarnRpm(v: Int) {
        val r = (v / SHIFT_WARN_STEP * SHIFT_WARN_STEP).coerceIn(SHIFT_WARN_MIN, SHIFT_WARN_MAX)
        sp.edit().putInt("shiftWarnRpm", r).apply()
        _shiftWarnRpm.value = r
    }

    private val _shiftHints = MutableStateFlow(sp.getBoolean("shiftHints", true))
    /** The change up / change down icons by the dials, shown only when it's time to change. */
    val shiftHints: StateFlow<Boolean> = _shiftHints

    fun setShiftHints(v: Boolean) {
        sp.edit().putBoolean("shiftHints", v).apply()
        _shiftHints.value = v
    }

    private val _roadLimits = MutableStateFlow(sp.getBoolean("roadLimits", true))
    /** The speed limit sign, from OpenStreetMap. */
    val roadLimits: StateFlow<Boolean> = _roadLimits

    fun setRoadLimits(v: Boolean) {
        sp.edit().putBoolean("roadLimits", v).apply()
        _roadLimits.value = v
    }

    private val _roadLights = MutableStateFlow(sp.getBoolean("roadLights", true))
    /** Traffic lights on the map and the distance to the next ones. */
    val roadLights: StateFlow<Boolean> = _roadLights

    fun setRoadLights(v: Boolean) {
        sp.edit().putBoolean("roadLights", v).apply()
        _roadLights.value = v
    }

    private val _roadCameras = MutableStateFlow(sp.getBoolean("roadCameras", true))
    /** The speed camera sign and "Speed camera ahead". */
    val roadCameras: StateFlow<Boolean> = _roadCameras

    fun setRoadCameras(v: Boolean) {
        sp.edit().putBoolean("roadCameras", v).apply()
        _roadCameras.value = v
    }

    private fun points(key: String, default: List<Int>): List<Int> {
        val saved = sp.getString(key, null)?.split(',')?.map { it.toIntOrNull() } ?: return default
        return default.mapIndexed { i, d -> saved.getOrNull(i) ?: d }
    }

    private val _roadZones = MutableStateFlow(sp.getBoolean("roadZones", true))
    /** The Garda's GoSafe speed van zones: the sign, and the orange flash 1 km before one. */
    val roadZones: StateFlow<Boolean> = _roadZones

    fun setRoadZones(v: Boolean) {
        sp.edit().putBoolean("roadZones", v).apply()
        _roadZones.value = v
    }

    private val _cameraVoice = MutableStateFlow(sp.getBoolean("cameraVoice", true))
    /** "Speed camera in 1 kilometre" out loud, whether or not spoken directions are on. */
    val cameraVoice: StateFlow<Boolean> = _cameraVoice

    fun setCameraVoice(v: Boolean) {
        sp.edit().putBoolean("cameraVoice", v).apply()
        _cameraVoice.value = v
    }

    private fun upKey(diesel: Boolean) = if (diesel) "shiftUpAt.diesel" else "shiftUpAt.petrol"
    private fun downKey(diesel: Boolean) = if (diesel) "shiftDownAt.diesel" else "shiftDownAt.petrol"

    /** Points set before they were kept per engine: they were set for the engine picked now. */
    private fun movedShiftPoints(): Boolean {
        val d = _diesel.value
        val e = sp.edit()
        sp.getString("shiftUpAt", null)?.let { e.putString(upKey(d), it).remove("shiftUpAt") }
        sp.getString("shiftDownAt", null)?.let { e.putString(downKey(d), it).remove("shiftDownAt") }
        e.apply()
        return true
    }

    private val _shiftUpAt = MutableStateFlow(movedShiftPoints().let { points(upKey(_diesel.value), shiftUpDefault(_diesel.value)) })
    /** The change-up sign, gear by gear (1st first): the rpm it comes up at, for the kind of engine picked. */
    val shiftUpAt: StateFlow<List<Int>> = _shiftUpAt

    private val _shiftDownAt = MutableStateFlow(points(downKey(_diesel.value), shiftDownDefault(_diesel.value)))
    /** The change-down sign, gear by gear (1st first, which has none): the rpm it comes up under. */
    val shiftDownAt: StateFlow<List<Int>> = _shiftDownAt

    fun setShiftUpAt(gear: Int, rpm: Int) {
        if (gear !in 1..SHIFT_UP_DIESEL.size) return
        val r = (rpm / SHIFT_POINT_STEP * SHIFT_POINT_STEP).coerceIn(SHIFT_UP_MIN, SHIFT_UP_MAX)
        val list = _shiftUpAt.value.toMutableList().also { it[gear - 1] = r }
        sp.edit().putString(upKey(_diesel.value), list.joinToString(",")).apply()
        _shiftUpAt.value = list
    }

    fun setShiftDownAt(gear: Int, rpm: Int) {
        if (gear !in 2..SHIFT_DOWN_DIESEL.size) return
        val r = (rpm / SHIFT_POINT_STEP * SHIFT_POINT_STEP).coerceIn(SHIFT_DOWN_MIN, SHIFT_DOWN_MAX)
        val list = _shiftDownAt.value.toMutableList().also { it[gear - 1] = r }
        sp.edit().putString(downKey(_diesel.value), list.joinToString(",")).apply()
        _shiftDownAt.value = list
    }

    /** Back to the points suggested for the kind of engine picked (Settings › Car › Fuel use). */
    fun resetShiftPoints() {
        val d = _diesel.value
        sp.edit().remove(upKey(d)).remove(downKey(d)).apply()
        _shiftUpAt.value = shiftUpDefault(d)
        _shiftDownAt.value = shiftDownDefault(d)
    }

    private val _topGear = MutableStateFlow(sp.getFloat("topGearRpmPerKmh", 0f))
    /**
     * Engine rpm per km/h in top gear, learnt while driving (the lowest steady figure seen), so
     * "Shift up" doesn't show when there's no gear left. 0 until it's been learnt.
     */
    val topGear: StateFlow<Float> = _topGear

    fun setTopGear(v: Float) {
        sp.edit().putFloat("topGearRpmPerKmh", v).apply()
        _topGear.value = v
    }

    private val _accent = MutableStateFlow(sp.getInt("accent", DEFAULT_ACCENT))
    /** The accent colour, as ARGB: red unless the person picks another. */
    val accent: StateFlow<Int> = _accent

    fun setAccent(argb: Int) {
        val c = argb or 0xFF000000.toInt()
        sp.edit().putInt("accent", c).apply()
        _accent.value = c
    }

    private val _spoken = MutableStateFlow(sp.getBoolean("spokenDirections", true))
    /** Directions read out ("In 300 metres, turn right"); the mute button on Home silences them for a while. */
    val spoken: StateFlow<Boolean> = _spoken

    fun setSpoken(v: Boolean) {
        sp.edit().putBoolean("spokenDirections", v).apply()
        _spoken.value = v
    }

    private val _googleKey = MutableStateFlow(sp.getString("googleKey", null) ?: "")
    /** The driver's own Google key, for Eircodes. Blank: Eircodes go to Google Maps and come back with Share. */
    val googleKey: StateFlow<String> = _googleKey

    fun setGoogleKey(v: String) {
        val clean = v.trim()
        sp.edit().putString("googleKey", clean).apply()
        _googleKey.value = clean
    }


    private val _wifiOnly = MutableStateFlow(sp.getBoolean("mapWifiOnly", true))
    /** Saved map areas download only on Wi-Fi. */
    val wifiOnly: StateFlow<Boolean> = _wifiOnly

    fun setWifiOnly(v: Boolean) {
        sp.edit().putBoolean("mapWifiOnly", v).apply()
        _wifiOnly.value = v
    }

    companion object {
        /**
         * Suggested for the TT's 2.0 TDI (350 Nm from 1,750 to 2,500 rpm): change up at 2,000, or
         * later in the low gears so the next one doesn't land under 1,500; change down under 1,100,
         * where a diesel judders and the dual-mass flywheel takes the strain. Up to 8 gears.
         */
        /**
         * Where to change gear for economy, gear by gear. A diesel pulls from low down (the TT's 2.0 TDI
         * has its 350 Nm from 1,750 rpm): change up early, but each change landing no lower than about
         * 1,300 rpm in the next gear, and down under 1,100, where it starts to judder and the dual-mass
         * flywheel takes the strain. A petrol needs more revs: up around 2,500 to 3,000, down under 1,400.
         */
        val SHIFT_UP_DIESEL = listOf(2400, 2000, 1900, 1800, 1800, 1800, 1800, 1800)
        val SHIFT_DOWN_DIESEL = listOf(0, 1100, 1100, 1100, 1100, 1100, 1100, 1100)
        val SHIFT_UP_PETROL = listOf(3000, 2800, 2600, 2500, 2500, 2500, 2500, 2500)
        val SHIFT_DOWN_PETROL = listOf(0, 1400, 1400, 1400, 1400, 1400, 1400, 1400)
        fun shiftUpDefault(diesel: Boolean) = if (diesel) SHIFT_UP_DIESEL else SHIFT_UP_PETROL
        fun shiftDownDefault(diesel: Boolean) = if (diesel) SHIFT_DOWN_DIESEL else SHIFT_DOWN_PETROL
        const val SHIFT_POINT_STEP = 100
        const val SHIFT_UP_MIN = 1200
        const val SHIFT_UP_MAX = 4500
        const val SHIFT_DOWN_MIN = 800
        const val SHIFT_DOWN_MAX = 2500
        val DEFAULT_ACCENT = 0xFFFF2D40.toInt()
        const val SPEED_WARN_DEFAULT = 150
        const val SPEED_WARN_MIN = 50
        const val SPEED_WARN_MAX = 250
        const val SPEED_WARN_STEP = 10
        const val SHIFT_WARN_DEFAULT = 2500
        const val SHIFT_WARN_MIN = 1500
        const val SHIFT_WARN_MAX = 6000
        const val SHIFT_WARN_STEP = 100
        const val SLOTS = 4
        val DEFAULT_ICONS = listOf("android_auto", "waze", "music", "video")
    }
}
