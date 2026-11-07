package io.github.bornaradusin.cardash

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

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

    private val _fuelFactor = MutableStateFlow(sp.getFloat("fuelFactor", 1f))
    /** Scales the air-flow fuel figure to match the car's own. */
    val fuelFactor: StateFlow<Float> = _fuelFactor

    fun setFuelFactor(v: Float) {
        val f = v.coerceIn(0.05f, 3f)
        sp.edit().putFloat("fuelFactor", f).apply()
        _fuelFactor.value = f
    }

    private val _fullScreen = MutableStateFlow(sp.getBoolean("fullScreen", true))
    /** Hides the radio's own bars (a swipe from the edge brings them back). */
    val fullScreen: StateFlow<Boolean> = _fullScreen

    fun setFullScreen(v: Boolean) {
        sp.edit().putBoolean("fullScreen", v).apply()
        _fullScreen.value = v
    }

    companion object {
        const val SLOTS = 4
        val DEFAULT_ICONS = listOf("android_auto", "waze", "music", "video")
    }
}
