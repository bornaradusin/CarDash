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
 * Google's short link is followed one hop at a time until a hop shows where the place is: the pin
 * (!3d…!4d…), the end of a directions link, a q=lat,lon or the map's middle (@lat,lon). A shared
 * business usually lands on maps?q=Name,+Address&ftid=…, which has no spot in it, so that page is
 * read for where Google puts the pin. Google's cookie page (Ireland and the rest of the EU) is
 * skipped. If no spot turns up, the name and address are searched; if that finds nothing either,
 * [Found.words] is what to put in the search box so the driver can pick.
 */
object SharedPlace {
    class Found(val place: Place?, val words: String?)

    private val LINK = Regex("""(https?://|geo:)\S+""")
    private val PIN = Regex("""!3d(-?\d{1,2}\.\d+)!4d(-?\d{1,3}\.\d+)""")
    private val DIR_POINT = Regex("""!1d(-?\d{1,3}\.\d+)!2d(-?\d{1,2}\.\d+)""")
    private val PAIR = Regex("""(-?\d{1,2}\.\d{3,})\s*,\s*(-?\d{1,3}\.\d{3,})""")
    private val AT = Regex("""@(-?\d{1,2}\.\d+),(-?\d{1,3}\.\d+)""")
    private val PARAM = Regex("""[?&](?:q|query|daddr|destination|ll|center)=([^&#]+)""")
    private val WORDS_PARAM = Regex("""[?&](?:q|query|daddr|destination)=([^&#]+)""")
    private val NAMED = Regex("""/maps/(?:place|search)/([^/@?]+)""")
    // In Google Maps' page: the pin on the little map picture, then where the map is centred
    private val MARKER = Regex("""markers=(-?\d{1,2}\.\d+)(?:%2C|,)(-?\d{1,3}\.\d+)""")
    private val CENTER = Regex("""center=(-?\d{1,2}\.\d+)(?:%2C|,)(-?\d{1,3}\.\d+)""")
    private val APP_STATE = Regex("""APP_INITIALIZATION_STATE=\[\[\[[\d.]+,(-?\d{1,3}\.\d+),(-?\d{1,2}\.\d+)\]""")
    private val MAPS_LINK = Regex("""https://(?:www\.google\.[a-z.]+/maps|maps\.google\.[a-z.]+)[^"'\s<\\]*""")
    private const val AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"
    // Choices already made on Google's cookie page, so it isn't shown instead of the map
    private const val CONSENT = "SOCS=CAESEwgDEgk0ODE3Nzk3MjQaAmVuIAEaBgiA_LyaBg; CONSENT=YES+cb"

    /** The place in what was shared, nearest to [near] when it has to be searched. */
    suspend fun from(text: String, googleKey: String, near: Pos?): Found = withContext(Dispatchers.IO) {
        val link = LINK.find(text)?.value?.trimEnd(')', '.', ',', '"', '\'')
        // Lines that aren't the link: Google Maps sometimes sends the name and the address with it
        val lines = text.lines().map { it.replace(link ?: "\u0000", "").trim() }.filter { it.isNotEmpty() }
        val lineName = lines.firstOrNull()
        val lineAddress = lines.drop(1).joinToString(", ").ifEmpty { null }

        var pos: Pos? = null
        var linkWords: String? = null
        if (link != null) {
            var url: String? = link
            val seen = mutableSetOf<String>()
            var hops = 0
            while (pos == null && hops++ < 8) {
                val here = url ?: break
                if (!seen.add(here)) break
                val plain = decode(here)
                pos = spotIn(plain)
                linkWords = linkWords ?: wordsIn(plain)
                if (pos != null || !here.startsWith("http")) break
                // Google's cookie page: go straight on to where it was sending us
                if (hostOf(here).startsWith("consent.")) {
                    url = Regex("""[?&]continue=([^&]+)""").find(here)?.groupValues?.get(1)?.let { decode(it) }
                    continue
                }
                val step = runCatching { hop(here) }.getOrNull() ?: break
                url = step.next
                val page = step.page
                if (page != null) {
                    pos = spotInPage(page, sure = isOnePlace(plain))
                    if (pos == null) url = MAPS_LINK.find(page)?.value?.replace("&amp;", "&")?.takeIf { it !in seen }
                }
            }
            if (pos == null && link.startsWith("geo:")) linkWords = linkWords ?: wordsIn(decode(link))
        }
        // A link's words are "Name, Address"; a share's lines are the name, then the address
        val name = linkWords?.substringBefore(',')?.trim()?.takeIf { PAIR.matchEntire(it) == null } ?: lineName
        val address = linkWords?.substringAfter(',', "")?.trim()?.ifEmpty { null } ?: lineAddress
        if (pos != null) return@withContext Found(Place(name ?: "Shared place", address ?: "From Google Maps", pos), linkWords)
        val words = linkWords ?: listOfNotNull(lineName, lineAddress).joinToString(", ").ifEmpty { null }
        val found = words?.let { lookUp(it, address, name, googleKey, near) }
        Found(found?.let { f -> f.copy(name = name ?: f.name) }, words)
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
        NAMED.find(u)?.let { m ->
            PAIR.matchEntire(m.groupValues[1].replace('+', ' ').trim())?.let { return pos(it.groupValues[1], it.groupValues[2]) }
        }
        AT.find(u)?.let { return pos(it.groupValues[1], it.groupValues[2]) }
        return null
    }

    /** The name and address a link carries (q=Lock+Doctor,+Main+St or /maps/place/Lock+Doctor), not a lat,lon. */
    private fun wordsIn(u: String): String? {
        val raw = WORDS_PARAM.find(u)?.groupValues?.get(1) ?: NAMED.find(u)?.groupValues?.get(1) ?: return null
        val w = raw.replace('+', ' ').replace(Regex("""\s+"""), " ").trim()
        return w.takeIf { it.length >= 3 && PAIR.find(it) == null && !it.startsWith("loc:") }
    }

    /** A link to one place (rather than a search), so the map's middle is that place. */
    private fun isOnePlace(u: String) = "ftid=" in u || "cid=" in u || "/maps/place/" in u || Regex("""!1s0x[0-9a-f]+:0x""").containsMatchIn(u)

    /** Where Google Maps' page puts the pin. The map's middle only counts for a link to one place. */
    private fun spotInPage(page: String, sure: Boolean): Pos? {
        MARKER.find(page)?.let { m -> pos(m.groupValues[1], m.groupValues[2])?.let { return it } }
        PIN.find(page)?.let { m -> pos(m.groupValues[1], m.groupValues[2])?.let { return it } }
        if (!sure) return null
        APP_STATE.find(page)?.let { m -> pos(m.groupValues[2], m.groupValues[1])?.let { return it } }
        CENTER.find(page)?.let { m -> pos(m.groupValues[1], m.groupValues[2])?.let { return it } }
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

    private class Hop(val next: String?, val page: String?)

    /** Where a link sends you next, or the page it answers with. */
    private fun hop(url: String): Hop {
        val c = URL(url).openConnection() as HttpURLConnection
        return try {
            c.instanceFollowRedirects = false
            c.connectTimeout = 10_000
            c.readTimeout = 15_000
            c.setRequestProperty("User-Agent", AGENT)
            c.setRequestProperty("Accept-Language", "en-IE,en;q=0.9")
            if ("google." in hostOf(url)) c.setRequestProperty("Cookie", CONSENT)
            if (c.responseCode in 300..399) Hop(c.getHeaderField("Location")?.let { URL(URL(url), it).toString() }, null)
            else Hop(null, c.inputStream.bufferedReader().use { it.readText() }.take(1_500_000))
        } finally {
            c.disconnect()
        }
    }

    private fun hostOf(url: String) = runCatching { URL(url).host.lowercase() }.getOrDefault("")

    private fun decode(u: String): String = runCatching { URLDecoder.decode(u.replace("+", "%2B"), "UTF-8") }.getOrDefault(u)

    /**
     * Searches the shared words: the Eircode with Google first (exact), then the whole lot, then the
     * address on its own and the name on its own (the free search doesn't like long addresses).
     */
    private suspend fun lookUp(words: String, address: String?, name: String?, googleKey: String, near: Pos?): Place? {
        val key = googleKey.trim()
        val parts = words.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        val eircode = parts.firstOrNull { Places.isEircode(it) }
        if (key.isNotEmpty()) {
            eircode?.let { e -> runCatching { Places.google(e, key).firstOrNull() }.getOrNull()?.let { return it } }
            runCatching { Places.google(words, key).firstOrNull() }.getOrNull()?.let { return it }
        }
        val noEircode = parts.filterNot { Places.isEircode(it) }
        val tries = listOfNotNull(
            noEircode.joinToString(", "),
            address?.split(',')?.map { it.trim() }?.filterNot { Places.isEircode(it) || it.isEmpty() }?.joinToString(", "),
            // The street and town without the house or unit number
            noEircode.drop(1).takeLast(3).joinToString(", "),
            noEircode.drop(1).takeLast(2).joinToString(", "),
            name,
        ).filter { it.length >= 3 }.distinct()
        for (q in tries) runCatching { Places.search(q, near).firstOrNull() }.getOrNull()?.let { return it }
        return null
    }
}
