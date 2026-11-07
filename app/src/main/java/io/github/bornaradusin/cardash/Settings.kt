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

    private val _bigMap = MutableStateFlow(sp.getBoolean("bigMap", true))
    /** On a route, with the map showing, the map takes the RPM dial's place. */
    val bigMap: StateFlow<Boolean> = _bigMap

    fun setBigMap(v: Boolean) {
        sp.edit().putBoolean("bigMap", v).apply()
        _bigMap.value = v
    }

    private val _wifiOnly = MutableStateFlow(sp.getBoolean("mapWifiOnly", true))
    /** Saved map areas download only on Wi-Fi. */
    val wifiOnly: StateFlow<Boolean> = _wifiOnly

    fun setWifiOnly(v: Boolean) {
        sp.edit().putBoolean("mapWifiOnly", v).apply()
        _wifiOnly.value = v
    }

    companion object {
        val DEFAULT_ACCENT = 0xFFFF2D40.toInt()
        const val SPEED_WARN_DEFAULT = 150
        const val SPEED_WARN_MIN = 50
        const val SPEED_WARN_MAX = 250
        const val SPEED_WARN_STEP = 10
        const val SLOTS = 4
        val DEFAULT_ICONS = listOf("android_auto", "waze", "music", "video")
    }
}
