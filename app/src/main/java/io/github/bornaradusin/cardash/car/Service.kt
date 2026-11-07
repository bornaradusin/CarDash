package io.github.bornaradusin.cardash.car

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.roundToInt

/** What an item is: the usual ones start with their usual interval; CUSTOM is one the person added. */
enum class ServiceKind(val title: String, val everyKm: Int?, val everyMonths: Int?) {
    OIL("Engine oil", 15_000, 12),
    AIR_FILTER("Air filter", 30_000, 24),
    CABIN_FILTER("Cabin filter", 15_000, 12),
    SPARK_PLUGS("Spark plugs", 60_000, 48),
    FUEL_FILTER("Fuel filter", 60_000, 48),
    BRAKE_FLUID("Brake fluid", null, 24),
    CUSTOM("", null, null),
}

/**
 * One thing to service: how often (km, months or both, whichever comes first) and when it was last
 * done. Null = not set.
 */
data class ServiceItem(
    val id: String,
    val kind: ServiceKind,
    val name: String,
    val everyKm: Int?,
    val everyMonths: Int?,
    val lastKm: Int?,
    /** The day it was last done, as LocalDate.toEpochDay(). */
    val lastDay: Long?,
) {
    val lastDate: LocalDate? get() = lastDay?.let(LocalDate::ofEpochDay)
}

enum class Urgency { UNSET, OK, SOON, OVERDUE }

/** Where an item stands now. */
data class ServiceStatus(
    val item: ServiceItem,
    val nextKm: Int?,
    /** Km until it's due (negative = overdue); null when the odometer or the last km isn't known. */
    val kmLeft: Int?,
    val nextDate: LocalDate?,
    /** Days until it's due (negative = overdue). */
    val daysLeft: Long?,
    /** How much of the interval has gone, 0..1 (more when overdue): the further of km and time. */
    val used: Float,
    val urgency: Urgency,
    /** The km comes first (else the date). */
    val byKm: Boolean,
) {
    val due: Boolean get() = urgency == Urgency.SOON || urgency == Urgency.OVERDUE
}

/** Everything the Service page shows. */
data class ServiceState(
    val items: List<ServiceItem> = emptyList(),
    /** The car's odometer: from the CAN box when it gives one, else typed in and counted on from OBD speed. */
    val carKm: Double? = null,
    /** [carKm] is the CAN box's odometer, live. */
    val fromCan: Boolean = false,
    /** A reminder pops up once an item is this close. */
    val kmBefore: Int = 1_000,
    val daysBefore: Int = 30,
    /** Goes up with each drive; [laterDrive] = the drive "Remind me later" was pressed on. */
    val drive: Int = 0,
    val laterDrive: Int? = null,
) {
    fun statuses(today: LocalDate = LocalDate.now()): List<ServiceStatus> =
        items.map { statusOf(it, carKm, today, kmBefore, daysBefore) }

    /** Most urgent first; items not set up yet last, in the order they were added. */
    fun sorted(today: LocalDate = LocalDate.now()): List<ServiceStatus> =
        statuses(today).sortedWith(compareBy<ServiceStatus> { order(it.urgency) }.thenByDescending { if (it.urgency == Urgency.UNSET) 0f else it.used })

    fun dueCount(today: LocalDate = LocalDate.now()): Int = statuses(today).count { it.due }

    private fun order(u: Urgency) = when (u) {
        Urgency.OVERDUE -> 0
        Urgency.SOON -> 1
        Urgency.OK -> 2
        Urgency.UNSET -> 3
    }
}

/** Where [item] stands at [carKm] on [today]. */
fun statusOf(item: ServiceItem, carKm: Double?, today: LocalDate, kmBefore: Int, daysBefore: Int): ServiceStatus {
    val nextKm = if (item.everyKm != null && item.lastKm != null) item.lastKm + item.everyKm else null
    val kmLeft = if (nextKm != null && carKm != null) (nextKm - carKm).roundToInt() else null
    val last = item.lastDate
    val nextDate = if (item.everyMonths != null && last != null) last.plusMonths(item.everyMonths.toLong()) else null
    val daysLeft = nextDate?.let { ChronoUnit.DAYS.between(today, it) }

    val usedKm = if (kmLeft != null && item.everyKm != null && item.everyKm > 0) 1f - kmLeft.toFloat() / item.everyKm else null
    val usedTime = if (last != null && nextDate != null) {
        val span = ChronoUnit.DAYS.between(last, nextDate).coerceAtLeast(1)
        1f - ChronoUnit.DAYS.between(today, nextDate).toFloat() / span
    } else null
    val byKm = when {
        usedKm == null -> false
        usedTime == null -> true
        else -> usedKm >= usedTime
    }
    val urgency = when {
        kmLeft == null && daysLeft == null -> Urgency.UNSET
        (kmLeft != null && kmLeft < 0) || (daysLeft != null && daysLeft < 0) -> Urgency.OVERDUE
        (kmLeft != null && kmLeft <= kmBefore) || (daysLeft != null && daysLeft <= daysBefore) -> Urgency.SOON
        else -> Urgency.OK
    }
    return ServiceStatus(item, nextKm, kmLeft, nextDate, daysLeft, maxOf(usedKm ?: 0f, usedTime ?: 0f), urgency, byKm)
}

/**
 * The service items and the car's odometer, kept on the radio. The odometer follows the CAN box's
 * (the car's own kilometres); without one it carries on from the last known or typed reading, adding
 * the distance worked out from OBD speed.
 */
class ServiceBook(context: Context, diesel: Boolean) {
    private val sp = context.getSharedPreferences("service", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(
        ServiceState(
            items = sp.getString("items", null)?.let(::decode) ?: defaults(diesel),
            carKm = sp.getString("carKm", null)?.toDoubleOrNull(),
            kmBefore = sp.getInt("kmBefore", 1_000),
            daysBefore = sp.getInt("daysBefore", 30),
        ),
    )
    val state: StateFlow<ServiceState> = _state

    private var savedKm: Double? = _state.value.carKm
    private var lastMeters = 0.0

    init {
        if (!sp.contains("items")) saveItems()
    }

    /** Adds the item, or replaces the one with its id. */
    fun save(item: ServiceItem) {
        _state.update { s ->
            val i = s.items.indexOfFirst { it.id == item.id }
            s.copy(items = if (i < 0) s.items + item else s.items.toMutableList().also { it[i] = item })
        }
        saveItems()
    }

    fun remove(id: String) {
        _state.update { s -> s.copy(items = s.items.filterNot { it.id == id }) }
        saveItems()
    }

    /** Done today, at the odometer now. */
    fun done(id: String) {
        val km = _state.value.carKm?.roundToInt()
        _state.update { s ->
            s.copy(items = s.items.map { if (it.id == id) it.copy(lastKm = km, lastDay = LocalDate.now().toEpochDay()) else it })
        }
        saveItems()
    }

    fun setKmBefore(km: Int) {
        val v = km.coerceIn(KM_BEFORE_MIN, KM_BEFORE_MAX)
        sp.edit().putInt("kmBefore", v).apply()
        _state.update { it.copy(kmBefore = v) }
    }

    fun setDaysBefore(days: Int) {
        val v = days.coerceIn(DAYS_BEFORE_MIN, DAYS_BEFORE_MAX)
        sp.edit().putInt("daysBefore", v).apply()
        _state.update { it.copy(daysBefore = v) }
    }

    /** The odometer as typed from the dashboard, for when the CAN box doesn't give it. */
    fun setCarKm(km: Double) {
        if (km < 0 || km > 3_000_000) return
        _state.update { it.copy(carKm = km) }
        saveKm(force = true)
    }

    /** The CAN box's odometer, or null when it isn't giving one. */
    fun onCanOdometer(km: Float?) {
        if (km == null) {
            _state.update { it.copy(fromCan = false) }
            return
        }
        _state.update { it.copy(carKm = km.toDouble(), fromCan = true) }
        saveKm()
    }

    /** This drive's distance from OBD speed: carries the odometer on while the CAN box gives none. */
    fun onDriven(meters: Double) {
        val delta = meters - lastMeters
        lastMeters = meters
        // A new count (CarDash restarted) or a jump that can't be one round of driving
        if (delta <= 0.0 || delta > 5_000.0) return
        val s = _state.value
        if (s.fromCan || s.carKm == null) return
        _state.update { it.copy(carKm = (it.carKm ?: return@update it) + delta / 1000.0) }
        saveKm()
    }

    /** A new drive: "Remind me later" from the last one no longer holds. */
    fun newDrive() = _state.update { it.copy(drive = it.drive + 1) }

    /** Hides the reminder until the next drive. */
    fun later() = _state.update { it.copy(laterDrive = it.drive) }

    /** Diesel or petrol: spark plugs and the fuel filter swap over, as long as the person hasn't set them up. */
    fun setEngine(diesel: Boolean) {
        val from = if (diesel) ServiceKind.SPARK_PLUGS else ServiceKind.FUEL_FILTER
        val to = if (diesel) ServiceKind.FUEL_FILTER else ServiceKind.SPARK_PLUGS
        var changed = false
        _state.update { s ->
            s.copy(items = s.items.map { item ->
                val untouched = item.kind == from && item.lastKm == null && item.lastDay == null &&
                    item.name == from.title && item.everyKm == from.everyKm && item.everyMonths == from.everyMonths
                if (untouched) {
                    changed = true
                    item.copy(id = to.name, kind = to, name = to.title, everyKm = to.everyKm, everyMonths = to.everyMonths)
                } else item
            })
        }
        if (changed) saveItems()
    }

    private fun saveItems() {
        sp.edit().putString("items", encode(_state.value.items)).apply()
    }

    /** Written when it has moved a little, not on every reading. */
    private fun saveKm(force: Boolean = false) {
        val km = _state.value.carKm ?: return
        val last = savedKm
        if (!force && last != null && abs(km - last) < 0.2) return
        savedKm = km
        sp.edit().putString("carKm", km.toString()).apply()
    }

    companion object {
        const val KM_BEFORE_MIN = 250
        const val KM_BEFORE_MAX = 5_000
        const val KM_BEFORE_STEP = 250
        const val DAYS_BEFORE_MIN = 5
        const val DAYS_BEFORE_MAX = 90
        const val DAYS_BEFORE_STEP = 5

        /** The usual items, not set up yet: the person fills in when each was last done. */
        fun defaults(diesel: Boolean): List<ServiceItem> = listOf(
            ServiceKind.OIL,
            ServiceKind.AIR_FILTER,
            ServiceKind.CABIN_FILTER,
            if (diesel) ServiceKind.FUEL_FILTER else ServiceKind.SPARK_PLUGS,
            ServiceKind.BRAKE_FLUID,
        ).map { ServiceItem(it.name, it, it.title, it.everyKm, it.everyMonths, null, null) }

        fun newId(): String = "c" + System.currentTimeMillis()

        private fun encode(items: List<ServiceItem>): String = JSONArray().apply {
            items.forEach { i ->
                put(JSONObject().apply {
                    put("id", i.id)
                    put("kind", i.kind.name)
                    put("name", i.name)
                    i.everyKm?.let { put("everyKm", it) }
                    i.everyMonths?.let { put("everyMonths", it) }
                    i.lastKm?.let { put("lastKm", it) }
                    i.lastDay?.let { put("lastDay", it) }
                })
            }
        }.toString()

        private fun decode(s: String): List<ServiceItem>? = runCatching {
            val a = JSONArray(s)
            (0 until a.length()).map { n ->
                val o = a.getJSONObject(n)
                fun int(k: String): Int? = if (o.has(k)) o.getInt(k) else null
                ServiceItem(
                    id = o.getString("id"),
                    kind = runCatching { ServiceKind.valueOf(o.getString("kind")) }.getOrDefault(ServiceKind.CUSTOM),
                    name = o.optString("name"),
                    everyKm = int("everyKm"),
                    everyMonths = int("everyMonths"),
                    lastKm = int("lastKm"),
                    lastDay = if (o.has("lastDay")) o.getLong("lastDay") else null,
                )
            }
        }.getOrNull()
    }
}
