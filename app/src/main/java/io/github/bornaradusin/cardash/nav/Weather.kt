package io.github.bornaradusin.cardash.nav

import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** The weather where the car is, as a picture for the header. */
enum class Sky { CLEAR, PARTLY, CLOUDY, FOG, RAIN, SNOW, THUNDER }

data class WeatherNow(val sky: Sky, val day: Boolean)

/**
 * The weather where the car is, from Open-Meteo (free, no key): looked up when there's a GPS position,
 * then again every 20 minutes, or sooner once the car has gone 15 km. Nothing shows without a position
 * or the internet.
 */
class Weather(private val scope: CoroutineScope, private val here: () -> Pos?) {
    private val _now = MutableStateFlow<WeatherNow?>(null)
    val now: StateFlow<WeatherNow?> = _now

    private var at: Pos? = null
    private var gotAt = 0L
    private var started = false

    fun start() {
        if (started) return
        started = true
        scope.launch {
            while (true) {
                val p = here()
                val stale = System.currentTimeMillis() - gotAt > EVERY_MS
                val moved = p != null && at?.let { Geo.distance(it, p) > 15_000 } != false
                if (p != null && (stale || moved)) {
                    runCatching { fetch(p) }.getOrNull()?.let {
                        _now.value = it
                        at = p
                        gotAt = System.currentTimeMillis()
                    }
                }
                // Soon after a position first comes; then once a minute is enough to notice it's time
                delay(if (_now.value == null) 15_000 else 60_000)
            }
        }
    }

    private suspend fun fetch(p: Pos): WeatherNow? = withContext(Dispatchers.IO) {
        val url = String.format(
            Locale.US,
            "https://api.open-meteo.com/v1/forecast?latitude=%.3f&longitude=%.3f&current=weather_code,is_day",
            p.lat, p.lon,
        )
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 10_000
            c.readTimeout = 15_000
            c.setRequestProperty("User-Agent", "CarDash (github.com/bornaradusin/CarDash)")
            if (c.responseCode != 200) return@withContext null
            val cur = JSONObject(c.inputStream.bufferedReader().use { it.readText() }).getJSONObject("current")
            WeatherNow(skyOf(cur.getInt("weather_code")), cur.optInt("is_day", 1) == 1)
        } finally {
            c.disconnect()
        }
    }

    companion object {
        private const val EVERY_MS = 20 * 60_000L

        /** The WMO weather codes Open-Meteo gives, as one of the pictures. */
        fun skyOf(code: Int): Sky = when (code) {
            0, 1 -> Sky.CLEAR
            2 -> Sky.PARTLY
            3 -> Sky.CLOUDY
            45, 48 -> Sky.FOG
            in 51..67, in 80..82 -> Sky.RAIN
            in 71..77, 85, 86 -> Sky.SNOW
            in 95..99 -> Sky.THUNDER
            else -> Sky.CLOUDY
        }
    }
}
