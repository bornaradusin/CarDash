package io.github.bornaradusin.cardash.nav

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.net.URLEncoder
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Somewhere to go: a name, the line under it, and where it is. */
data class Place(val name: String, val detail: String, val pos: Pos) {
    fun toJson(): JSONObject = JSONObject().put("name", name).put("detail", detail).put("lat", pos.lat).put("lon", pos.lon)

    companion object {
        fun fromJson(o: JSONObject?): Place? = o?.let {
            runCatching { Place(it.getString("name"), it.optString("detail"), Pos(it.getDouble("lat"), it.getDouble("lon"))) }.getOrNull()
        }
    }
}

/**
 * Finding places (Photon, the OpenStreetMap search from Komoot: free, no key), and the places kept on
 * the radio: Home, Work and the last few destinations.
 */
class Places(context: Context) {
    private val sp = context.getSharedPreferences("places", Context.MODE_PRIVATE)

    private val _home = MutableStateFlow(Place.fromJson(sp.getString("home", null)?.let { JSONObject(it) }))
    val home: StateFlow<Place?> = _home
    private val _work = MutableStateFlow(Place.fromJson(sp.getString("work", null)?.let { JSONObject(it) }))
    val work: StateFlow<Place?> = _work
    private val _recent = MutableStateFlow(readRecent())
    val recent: StateFlow<List<Place>> = _recent

    fun setHome(p: Place) {
        sp.edit().putString("home", p.toJson().toString()).apply()
        _home.value = p
    }

    fun setWork(p: Place) {
        sp.edit().putString("work", p.toJson().toString()).apply()
        _work.value = p
    }

    /** A destination just driven to goes to the top of Recent. */
    fun used(p: Place) {
        val list = (listOf(p) + _recent.value.filterNot { Geo.distance(it.pos, p.pos) < 30 }).take(RECENT)
        sp.edit().putString("recent", JSONArray(list.map { it.toJson() }).toString()).apply()
        _recent.value = list
    }

    fun forget(p: Place) {
        val list = _recent.value.filterNot { it == p }
        sp.edit().putString("recent", JSONArray(list.map { it.toJson() }).toString()).apply()
        _recent.value = list
    }

    private fun readRecent(): List<Place> = runCatching {
        val a = JSONArray(sp.getString("recent", "[]"))
        (0 until a.length()).mapNotNull { Place.fromJson(a.optJSONObject(it)) }
    }.getOrDefault(emptyList())

    companion object {
        private const val RECENT = 6
        private const val PHOTON = "https://photon.komoot.io"
        const val GOOGLE_MAPS = "com.google.android.apps.maps"

        /** An Irish postcode such as V94 X2H4 or D6W XK32. The free map search doesn't know these. */
        private val EIRCODE = Regex("^[A-Za-z]\\d[\\dWw]\\s?[0-9A-Za-z]{4}$")

        fun isEircode(q: String) = EIRCODE.matches(q.trim())

        /** Places matching what's typed, nearest to [near] first. */
        suspend fun search(q: String, near: Pos?): List<Place> {
            val bias = near?.let { String.format(Locale.US, "&lat=%.5f&lon=%.5f", it.lat, it.lon) } ?: ""
            val url = "$PHOTON/api/?q=${URLEncoder.encode(q.trim(), "UTF-8")}&limit=8&lang=en$bias"
            val features = JSONObject(Http.get(url)).optJSONArray("features") ?: return emptyList()
            val out = (0 until features.length()).mapNotNull { place(features.optJSONObject(it)) }
                .distinctBy { "${it.name}|${it.detail}" }
            return if (near == null) out else out.sortedBy { Geo.distance(near, it.pos) }
        }

        /** The street at a place, for the map when there's no route. */
        suspend fun streetAt(p: Pos): String? {
            val url = String.format(Locale.US, "%s/reverse?lat=%.6f&lon=%.6f&lang=en&limit=1", PHOTON, p.lat, p.lon)
            val f = JSONObject(Http.get(url)).optJSONArray("features")?.optJSONObject(0) ?: return null
            val pr = f.optJSONObject("properties") ?: return null
            return pr.optString("street").ifEmpty { pr.optString("name") }.ifEmpty { null }
        }

        private fun place(f: JSONObject?): Place? {
            f ?: return null
            val c = f.optJSONObject("geometry")?.optJSONArray("coordinates") ?: return null
            val pr = f.optJSONObject("properties") ?: return null
            fun s(k: String) = pr.optString(k).trim().ifEmpty { null }
            val street = listOfNotNull(s("housenumber"), s("street")).joinToString(" ").ifEmpty { null }
            val name = s("name") ?: street ?: s("city") ?: return null
            val town = s("city") ?: s("town") ?: s("village") ?: s("district")
            val detail = listOfNotNull(street?.takeIf { it != name }, town?.takeIf { it != name }, s("county")?.takeIf { it != town })
                .distinct().take(2).joinToString(", ").ifEmpty { s("country") ?: "" }
            return Place(name, detail, Pos(c.getDouble(1), c.getDouble(0)))
        }

        /** Google Maps, navigating to whatever was typed (an Eircode, an address the free search doesn't know). */
        fun directionsInGoogleMaps(context: Context, q: String) {
            val nav = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=" + Uri.encode(q.trim())))
                .setPackage(GOOGLE_MAPS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val any = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(q.trim()))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(nav) }.onFailure { runCatching { context.startActivity(any) } }
        }
    }
}
