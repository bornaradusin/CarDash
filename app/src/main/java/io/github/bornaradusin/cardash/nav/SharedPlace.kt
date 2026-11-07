package io.github.bornaradusin.cardash.nav

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A place shared to CarDash: Google Maps' Share → CarDash (some text with a maps.app.goo.gl link in
 * it), any other Google Maps link, or a geo: link from another app ("Open in maps").
 *
 * Google's short link is followed one hop at a time (without opening the pages) until a hop shows
 * where the place is: the pin (!3d…!4d…), the end of a directions link, a q=lat,lon or the map's
 * middle (@lat,lon). If no link gives a spot, the name and address that came with it are searched.
 */
object SharedPlace {
    private val LINK = Regex("""(https?://|geo:)\S+""")
    private val PIN = Regex("""!3d(-?\d{1,2}\.\d+)!4d(-?\d{1,3}\.\d+)""")
    private val DIR_POINT = Regex("""!1d(-?\d{1,3}\.\d+)!2d(-?\d{1,2}\.\d+)""")
    private val PAIR = Regex("""(-?\d{1,2}\.\d{3,})\s*,\s*(-?\d{1,3}\.\d{3,})""")
    private val AT = Regex("""@(-?\d{1,2}\.\d+),(-?\d{1,3}\.\d+)""")
    private val PARAM = Regex("""[?&](?:q|query|daddr|destination|ll|center)=([^&#]+)""")
    private val PLACE_NAME = Regex("""/maps/place/([^/@?]+)""")
    private const val AGENT = "Mozilla/5.0 (X11; Linux x86_64) CarDash"

    /** The place in what was shared, or null if there's none to be found. */
    suspend fun from(text: String, googleKey: String): Place? = withContext(Dispatchers.IO) {
        val link = LINK.find(text)?.value?.trimEnd(')', '.', ',', '"', '\'')
        // Lines that aren't the link: Google Maps sends the name and the address with it
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() && (link == null || !it.contains(link)) }
        val name = lines.firstOrNull()
        val address = lines.drop(1).joinToString(", ").ifEmpty { null }

        var pos: Pos? = null
        var linkName: String? = null
        if (link != null) {
            var url: String? = link
            var hops = 0
            while (pos == null && hops++ < 6) {
                val here = url ?: break
                val plain = decode(here)
                pos = spotIn(plain)
                linkName = linkName ?: PLACE_NAME.find(plain)?.groupValues?.get(1)?.replace('+', ' ')?.trim()
                url = if (pos == null && here.startsWith("http")) runCatching { next(here) }.getOrNull() else null
            }
            if (pos == null && link.startsWith("geo:")) {
                // geo:0,0?q=Some address
                PARAM.find(decode(link))?.groupValues?.get(1)?.replace('+', ' ')?.let { q -> return@withContext lookUp(q, googleKey) }
            }
        }
        val title = linkName?.takeIf { PAIR.matchEntire(it) == null } ?: name
        if (pos != null) return@withContext Place(title ?: "Shared place", address ?: "From Google Maps", pos)
        // No spot in the link: search the address (or the name) that came with it
        val q = listOfNotNull(address, name, linkName).firstOrNull() ?: return@withContext null
        lookUp(q, googleKey)?.let { found -> found.copy(name = title ?: found.name) }
    }

    /** Where a link puts the place, if it says. */
    private fun spotIn(u: String): Pos? {
        PIN.findAll(u).lastOrNull()?.let { return pos(it.groupValues[1], it.groupValues[2]) }
        if ("/maps/dir/" in u) {
            DIR_POINT.findAll(u).lastOrNull()?.let { return pos(it.groupValues[2], it.groupValues[1]) }
            // /maps/dir/Here/52.66,-8.63/@…: the last stop
            u.substringAfter("/maps/dir/").substringBefore("/@").substringBefore("/data=").split('/')
                .lastOrNull { it.isNotBlank() }?.let { stop -> PAIR.matchEntire(stop.trim())?.let { return pos(it.groupValues[1], it.groupValues[2]) } }
        }
        PARAM.findAll(u).forEach { m -> PAIR.find(m.groupValues[1])?.let { return pos(it.groupValues[1], it.groupValues[2]) } }
        if (u.startsWith("geo:")) PAIR.find(u.substringBefore('?'))?.let { m -> pos(m.groupValues[1], m.groupValues[2])?.let { return it } }
        Regex("""/maps/(?:place|search)/([^/?]+)""").find(u)?.let { m ->
            PAIR.matchEntire(m.groupValues[1].trim())?.let { return pos(it.groupValues[1], it.groupValues[2]) }
        }
        AT.find(u)?.let { return pos(it.groupValues[1], it.groupValues[2]) }
        return null
    }

    private fun pos(lat: String, lon: String): Pos? {
        val a = lat.toDoubleOrNull() ?: return null
        val b = lon.toDoubleOrNull() ?: return null
        // geo:0,0 means "no spot, search the words"
        if (a == 0.0 && b == 0.0) return null
        if (a !in -90.0..90.0 || b !in -180.0..180.0) return null
        return Pos(a, b)
    }

    /** Where a link sends you next, without opening the page. */
    private fun next(url: String): String? {
        val c = URL(url).openConnection() as HttpURLConnection
        return try {
            c.instanceFollowRedirects = false
            c.connectTimeout = 10_000
            c.readTimeout = 10_000
            c.setRequestProperty("User-Agent", AGENT)
            if (c.responseCode in 300..399) c.getHeaderField("Location")?.let { URL(URL(url), it).toString() }
            else {
                // Some short links answer with a page that points on: take the Google Maps link in it
                val page = c.inputStream.bufferedReader().use { it.readText() }.take(400_000)
                Regex("""https://www\.google\.[a-z.]+/maps[^"'\s<\\]*""").find(page)?.value
                    ?.takeIf { it != url }
            }
        } finally {
            c.disconnect()
        }
    }

    private fun decode(u: String): String = runCatching { URLDecoder.decode(u.replace("+", "%2B"), "UTF-8") }.getOrDefault(u)

    /** An address or name: Google if there's a key (Eircodes too), else the free search. */
    private suspend fun lookUp(q: String, googleKey: String): Place? {
        if (googleKey.isNotBlank()) runCatching { Places.google(q, googleKey).firstOrNull() }.getOrNull()?.let { return it }
        return runCatching { Places.search(q, null).firstOrNull() }.getOrNull()
    }
}
