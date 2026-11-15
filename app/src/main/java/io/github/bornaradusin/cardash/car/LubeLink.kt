package io.github.bornaradusin.cardash.car

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/** How CarDash reaches LubeLogger, and what it does there. Everything is off until it's set up. */
data class LubeSetup(
    val server: String = "",
    val apiKey: String = "",
    val vehicleId: Int? = null,
    val vehicleName: String = "",
    /** Sends the CAN box's odometer, once a day, at the start of the first drive. */
    val sendOdometer: Boolean = false,
) {
    val connected: Boolean get() = server.isNotBlank() && apiKey.isNotBlank() && vehicleId != null
}

data class LubeState(
    val setup: LubeSetup = LubeSetup(),
    /** The last reading sent, and when. */
    val sentKm: Int? = null,
    val sentAt: Long = 0,
    /** Readings and records waiting for the radio to be online. */
    val waiting: Int = 0,
    /** What went wrong the last time, until something works again. */
    val problem: String? = null,
)

class LubeException(message: String) : Exception(message)

/**
 * The car's LubeLogger: CarDash puts the dashboard's exact kilometres there, and can share its
 * service items with it, so CarTracker (and anything else reading LubeLogger) follows the car itself.
 */
class LubeLink(context: Context, private val scope: CoroutineScope, private val service: ServiceBook) {

    private val sp = context.getSharedPreferences("lubelogger", Context.MODE_PRIVATE)
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val sending = Mutex()

    private val _state = MutableStateFlow(
        LubeState(
            setup = LubeSetup(
                server = sp.getString("server", "").orEmpty(),
                apiKey = sp.getString("apiKey", "").orEmpty(),
                vehicleId = sp.getInt("vehicleId", -1).takeIf { it >= 0 },
                vehicleName = sp.getString("vehicleName", "").orEmpty(),
                sendOdometer = sp.getBoolean("sendOdometer", false),
            ),
            sentKm = sp.getInt("sentKm", -1).takeIf { it >= 0 },
            sentAt = sp.getLong("sentAt", 0L),
            waiting = queue().length(),
        ),
    )
    val state: StateFlow<LubeState> = _state

    /** The CAN box's odometer now, and whether this is the first reading of a drive. */
    private var canKm: Float? = null
    private var readingDue = true

    fun start() {
        scope.launch {
            while (true) {
                runCatching { flush() }
                runCatching { syncService() }
                // Often while something waits, else now and then for records added elsewhere
                delay(if (_state.value.waiting > 0) RETRY_MS else SYNC_MS)
            }
        }
    }

    fun save(setup: LubeSetup) {
        sp.edit()
            .putString("server", setup.server)
            .putString("apiKey", setup.apiKey)
            .putInt("vehicleId", setup.vehicleId ?: -1)
            .putString("vehicleName", setup.vehicleName)
            .putBoolean("sendOdometer", setup.sendOdometer)
            .apply()
        val wasSending = _state.value.setup.let { it.connected && it.sendOdometer }
        _state.update { it.copy(setup = setup, problem = null) }
        // Just turned on: send today's reading now rather than at the next drive
        if (!wasSending && setup.connected && setup.sendOdometer) {
            readingDue = true
            canKm?.let { onCanOdometer(it) }
        }
        scope.launch {
            runCatching { flush() }
            runCatching { syncService() }
        }
    }

    fun disconnect() {
        save(LubeSetup())
        service.replaceAll(emptyList())
        sp.edit().putString("queue", "[]").apply()
        _state.update { it.copy(waiting = 0) }
    }

    /** A new drive: its first odometer reading is the one the car was parked at. */
    fun newDrive() {
        readingDue = true
        canKm?.let { onCanOdometer(it) }
        // Items done or changed since: read them again
        scope.launch { runCatching { syncService() } }
    }

    /** The CAN box's odometer; null when it isn't giving one. Only the CAN box is exact enough to send. */
    fun onCanOdometer(km: Float?) {
        canKm = km
        val setup = _state.value.setup
        // The reading stays due until it can be sent: setting LubeLogger up mid-drive sends it then
        if (km == null || !readingDue || !setup.connected || !setup.sendOdometer) return
        readingDue = false
        val reading = km.roundToInt()
        val s = _state.value
        val today = LocalDate.now()
        val sentToday = s.sentAt > 0 && Instant.ofEpochMilli(s.sentAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate() == today
        // Once a day is plenty, and not at all while the car hasn't moved
        if (sentToday || reading == s.sentKm) return
        enqueue(JSONObject().put("type", "odometer").put("km", reading).put("at", System.currentTimeMillis()))
    }

    /** The vehicles the key can see, to pick the car from. */
    suspend fun vehicles(server: String, apiKey: String): List<Pair<Int, String>> {
        val array = JSONArray(call(server, apiKey, "GET", "/api/vehicles", null, null))
        return (0 until array.length()).mapNotNull { i ->
            val v = array.optJSONObject(i) ?: return@mapNotNull null
            val id = number(v.opt("id"))?.toInt() ?: return@mapNotNull null
            val name = listOf(v.optString("year").takeIf { it != "0" }.orEmpty(), v.optString("make"), v.optString("model"))
                .filter { it.isNotBlank() }.joinToString(" ")
            val plate = v.optString("licensePlate")
            id to listOf(name, plate).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "Vehicle $id" }
        }
    }

    // ---- sending ----

    private fun queue(): JSONArray = runCatching { JSONArray(sp.getString("queue", "[]")) }.getOrDefault(JSONArray())

    private fun enqueue(entry: JSONObject) {
        val q = queue().put(entry)
        sp.edit().putString("queue", q.toString()).apply()
        _state.update { it.copy(waiting = q.length()) }
        scope.launch { runCatching { flush() } }
    }

    /** Sends what's waiting, oldest first; stops at the first that can't go, to try again later. */
    private suspend fun flush() = sending.withLock {
        val setup = _state.value.setup
        if (!setup.connected) return@withLock
        var q = queue()
        while (q.length() > 0) {
            val entry = q.getJSONObject(0)
            try {
                send(setup, entry)
            } catch (e: LubeException) {
                _state.update { it.copy(problem = e.message) }
                return@withLock
            }
            q = queue().also { it.remove(0) }
            sp.edit().putString("queue", q.toString()).apply()
            if (entry.optString("type") == "odometer") {
                val km = entry.getInt("km")
                val at = entry.getLong("at")
                sp.edit().putInt("sentKm", km).putLong("sentAt", at).apply()
                _state.update { it.copy(sentKm = km, sentAt = at) }
            }
            _state.update { it.copy(waiting = q.length(), problem = null) }
        }
    }

    private suspend fun send(setup: LubeSetup, entry: JSONObject) {
        val vehicle = setup.vehicleId ?: return
        val at = Instant.ofEpochMilli(entry.optLong("at", System.currentTimeMillis()))
        when (entry.optString("type")) {
            "odometer" -> {
                val fields = linkedMapOf(
                    "date" to at.atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString(),
                    "odometer" to entry.getInt("km").toString(),
                    // The exact time, so CarTracker can line the reading up with the tracker's own count
                    "notes" to "$NOTE_PREFIX $at",
                )
                call(setup.server, setup.apiKey, "POST", "/api/vehicle/odometerrecords/add", vehicle, fields)
            }
        }
    }

    // ---- service items from LubeLogger ----

    /**
     * The service items as CarTracker keeps them in LubeLogger: one reminder each, with the interval
     * and the last time in its notes. A service, repair or upgrade record with an item's name in its
     * description, newer than that, counts as the item done then.
     */
    suspend fun syncService() {
        val setup = _state.value.setup
        val vehicle = setup.vehicleId
        if (!setup.connected || vehicle == null) return
        val reminders = JSONArray(call(setup.server, setup.apiKey, "GET", "/api/vehicle/reminders", vehicle, null))
        val records = listOf("servicerecords", "repairrecords", "upgraderecords").flatMap { path ->
            val array = JSONArray(call(setup.server, setup.apiKey, "GET", "/api/vehicle/$path", vehicle, null))
            (0 until array.length()).mapNotNull { i ->
                val r = array.optJSONObject(i) ?: return@mapNotNull null
                val date = date(r.optString("date")) ?: return@mapNotNull null
                Triple(r.optString("description"), date, number(r.opt("odometer"))?.roundToInt()?.takeIf { it > 0 })
            }
        }
        val items = (0 until reminders.length()).mapNotNull { i ->
            val r = reminders.optJSONObject(i) ?: return@mapNotNull null
            val m = MARK.find(r.optString("notes")) ?: return@mapNotNull null
            val (everyKm, everyMonths, lastKm, lastDay) = m.destructured
            val name = r.optString("description").trim()
            var item = ServiceItem(
                id = "r" + (number(r.opt("id"))?.toInt() ?: i),
                kind = ServiceKind.entries.firstOrNull { it != ServiceKind.CUSTOM && it.title.equals(name, ignoreCase = true) } ?: ServiceKind.CUSTOM,
                name = name,
                everyKm = everyKm.toIntOrNull(),
                everyMonths = everyMonths.toIntOrNull(),
                lastKm = lastKm.toIntOrNull(),
                lastDay = runCatching { LocalDate.parse(lastDay).toEpochDay() }.getOrNull(),
            )
            if (name.length >= 3) {
                val newest = records.filter { it.first.contains(name, ignoreCase = true) }.maxByOrNull { it.second }
                if (newest != null && (item.lastDay == null || newest.second.toEpochDay() >= item.lastDay!!)) {
                    item = item.copy(lastDay = newest.second.toEpochDay(), lastKm = newest.third ?: item.lastKm)
                }
            }
            item
        }
        service.replaceAll(items)
        _state.update { it.copy(problem = null) }
    }

    // ---- talking to LubeLogger ----

    private suspend fun call(server: String, apiKey: String, method: String, path: String, vehicleId: Int?, fields: Map<String, String>?): String =
        withContext(Dispatchers.IO) {
            val base = baseUrl(server) ?: throw LubeException("That doesn't look like a server address.")
            val url = base.newBuilder().encodedPath(base.encodedPath.trimEnd('/') + path).apply {
                if (vehicleId != null) addQueryParameter("vehicleId", vehicleId.toString())
            }.build()
            val body = fields?.let { f -> FormBody.Builder().apply { f.forEach { (k, v) -> add(k, v) } }.build() }
            val request = Request.Builder().url(url)
                .header("Accept", "application/json")
                .header("culture-invariant", "true")
                .header("x-api-key", apiKey.trim())
                .method(method, body)
                .build()
            try {
                client.newCall(request).execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    when {
                        response.code == 401 || response.code == 403 ->
                            throw LubeException(if (method == "GET") "LubeLogger didn't accept the key." else "The key can't add records: it needs the Editor role.")
                        response.code == 404 -> throw LubeException("No LubeLogger found at that address.")
                        !response.isSuccessful -> throw LubeException("LubeLogger answered with an error (${response.code}).")
                    }
                    if (method != "GET") {
                        val answer = runCatching { JSONObject(text) }.getOrNull()
                        if (answer != null && answer.has("success") && !answer.optBoolean("success", true)) {
                            throw LubeException(answer.optString("message").ifBlank { "LubeLogger didn't save it." })
                        }
                    }
                    text
                }
            } catch (e: LubeException) {
                throw e
            } catch (e: IOException) {
                throw LubeException("Couldn't reach LubeLogger: the radio may be offline.")
            }
        }

    private fun baseUrl(server: String): HttpUrl? {
        val trimmed = server.trim().trimEnd('/')
        if (trimmed.isEmpty()) return null
        return (if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "https://$trimmed").toHttpUrlOrNull()
    }

    companion object {
        /** The line in a reminder's notes that makes it a service item; CarTracker writes it. */
        private val MARK = Regex("""\[service item: every=(\d*)km,(\d*)mo; last=(\d*)km,([0-9-]*)\]""")

        /** The start of the note on each reading; CarTracker looks for it. */
        const val NOTE_PREFIX = "CarDash odometer"

        private const val RETRY_MS = 2 * 60_000L
        private const val SYNC_MS = 6 * 60 * 60_000L

        private val datePatterns = listOf("M/d/yyyy", "d/M/yyyy", "d.M.yyyy", "d-M-yyyy").map { DateTimeFormatter.ofPattern(it) }

        fun date(raw: String): LocalDate? {
            val s = raw.trim()
            if (s.isEmpty()) return null
            runCatching { LocalDate.parse(s.take(10)) }.getOrNull()?.let { return it }
            val day = s.substringBefore(' ').substringBefore('T')
            datePatterns.forEach { p -> runCatching { LocalDate.parse(day, p) }.getOrNull()?.let { return it } }
            return null
        }

        fun number(raw: Any?): Double? = when (raw) {
            is Number -> raw.toDouble()
            is String -> raw.filter { it.isDigit() || it == '.' || it == '-' }.toDoubleOrNull()
            else -> null
        }
    }
}
