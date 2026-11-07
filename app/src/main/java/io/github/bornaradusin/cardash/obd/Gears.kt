package io.github.bornaradusin.cardash.obd

import android.content.Context
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Which gear the car is in, worked out: the car doesn't report it, but in each gear the engine turns
 * a fixed number of rpm per km/h, whatever the speed. CarDash learns those figures by watching: every
 * time rpm per km/h holds steady for a moment (in gear, clutch up), that figure gets a count. The
 * counts pile up around one figure per gear, and once there's a pile for every gear (the number of
 * gears is set in Settings) the highest figure is 1st and the lowest is top. Kept between drives.
 *
 * Between gears, with the clutch down or coasting, the figure matches no gear and the gear shows "–".
 */
class Gears(context: Context) {
    private val sp = context.getSharedPreferences("gears", Context.MODE_PRIVATE)

    /** Counts per small band of rpm per km/h, on a log scale so each band is the same few per cent wide. */
    private val bins = IntArray(BINS)
    private var steadyRatio = 0f
    private var steadySince = 0L
    private var lastLook = 0L
    private var lastSave = 0L
    private var dirty = false

    /** rpm per km/h of each gear, 1st first; empty until every gear has been found. */
    private var ratios: List<Float> = emptyList()

    private val _gear = MutableStateFlow<Int?>(null)
    /** The gear the car is in now, or null (stopped, between gears, clutch down, or not learnt yet). */
    val gear: StateFlow<Int?> = _gear

    private val _ratios = MutableStateFlow<List<Float>>(emptyList())
    /** rpm per km/h of each gear, 1st first, once they've all been found (for the change up / down signs). */
    val learnt: StateFlow<List<Float>> = _ratios

    private val _found = MutableStateFlow(0)
    /** How many of the gears have been found so far. */
    val found: StateFlow<Int> = _found

    private val _count = MutableStateFlow(sp.getInt("count", DEFAULT_COUNT))
    /** How many forward gears the gearbox has. */
    val count: StateFlow<Int> = _count

    init {
        sp.getString("bins", null)?.split(',')?.forEachIndexed { i, v -> if (i < BINS) bins[i] = v.toIntOrNull() ?: 0 }
        look()
    }

    /** Each new reading from the car. */
    @Synchronized
    fun feed(rpm: Float?, kmh: Float?) {
        val now = System.currentTimeMillis()
        if (rpm == null || kmh == null || kmh < 3f) {
            _gear.value = null
            steadySince = 0L
            saveIfDue(now)
            return
        }
        val ratio = rpm / kmh
        _gear.value = gearFor(ratio)
        // Learning: driving (not crawling, not idling) with the figure holding steady
        if (kmh < 8f || rpm < 900f || ratio !in MIN_RATIO..MAX_RATIO) {
            steadySince = 0L
            return
        }
        if (steadySince == 0L || abs(ratio - steadyRatio) / steadyRatio > 0.025f) {
            steadyRatio = ratio
            steadySince = now
            return
        }
        if (now - steadySince < 1_200L) return
        val b = bin(ratio)
        if (bins[b] < 1_000_000) bins[b]++
        dirty = true
        if (now - lastLook > 10_000L) look()
        saveIfDue(now)
    }

    @Synchronized
    fun setCount(n: Int) {
        val c = n.coerceIn(MIN_COUNT, MAX_COUNT)
        sp.edit().putInt("count", c).apply()
        _count.value = c
        look()
    }

    /** Starts learning again from nothing (after changing the gearbox, say). */
    @Synchronized
    fun forget() {
        bins.fill(0)
        sp.edit().remove("bins").apply()
        lastSave = System.currentTimeMillis()
        dirty = false
        look()
    }

    private fun gearFor(ratio: Float): Int? {
        val rs = ratios
        if (rs.isEmpty()) return null
        var best = -1
        var bestD = Float.MAX_VALUE
        rs.forEachIndexed { i, r ->
            val d = abs(ln(ratio / r))
            if (d < bestD) {
                bestD = d
                best = i
            }
        }
        return if (bestD < 0.06f) best + 1 else null
    }

    /** Finds the piles: the strongest peaks, far enough apart to be different gears. */
    private fun look() {
        lastLook = System.currentTimeMillis()
        val total = bins.sum()
        val want = _count.value
        if (total < 40) {
            ratios = emptyList()
            _ratios.value = ratios
            _found.value = 0
            return
        }
        val smooth = IntArray(BINS) { i -> (if (i > 0) bins[i - 1] else 0) + 2 * bins[i] + (if (i < BINS - 1) bins[i + 1] else 0) }
        val floor = maxOf(40, (total * 4 * 0.004f).toInt())
        val peaks = (0 until BINS).filter { i ->
            smooth[i] >= floor &&
                (i == 0 || smooth[i] >= smooth[i - 1]) &&
                (i == BINS - 1 || smooth[i] > smooth[i + 1])
        }.sortedByDescending { smooth[it] }
        val taken = ArrayList<Int>()
        for (p in peaks) {
            if (taken.none { abs(it - p) < MIN_GAP_BINS }) taken.add(p)
            if (taken.size == want) break
        }
        _found.value = taken.size
        ratios = if (taken.size == want) taken.map { ratioOf(it) }.sortedDescending() else emptyList()
        _ratios.value = ratios
    }

    private fun saveIfDue(now: Long) {
        if (!dirty || now - lastSave < 30_000L) return
        sp.edit().putString("bins", bins.joinToString(",")).apply()
        lastSave = now
        dirty = false
    }

    private fun bin(ratio: Float): Int =
        ((ln(ratio) - LN_MIN) / (LN_MAX - LN_MIN) * BINS).toInt().coerceIn(0, BINS - 1)

    private fun ratioOf(bin: Int): Float = exp(LN_MIN + (bin + 0.5f) * (LN_MAX - LN_MIN) / BINS)

    companion object {
        const val DEFAULT_COUNT = 6
        const val MIN_COUNT = 4
        const val MAX_COUNT = 8
        private const val BINS = 120
        private const val MIN_RATIO = 10f
        private const val MAX_RATIO = 250f
        private val LN_MIN = ln(MIN_RATIO)
        private val LN_MAX = ln(MAX_RATIO)
        /** Gears are at least ~11 % apart: neighbouring peaks closer than that are one gear. */
        private const val MIN_GAP_BINS = 4
    }
}
