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
 * With one gear still missing, the others are numbered when it's clear which one it is: 1st turns over
 * 95 rpm per km/h and no other gear does, so with that pile found the missing one is top, and without
 * it, 1st. A gap too wide for neighbouring gears means the missing one is in between: none is shown.
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
    /** The gears that can be named now, as (gear, rpm per km/h): all of them, or all but one. */
    private var named: List<Pair<Int, Float>> = emptyList()

    private val _gear = MutableStateFlow<Int?>(null)
    /** The gear the car is in now, or null (stopped, between gears, clutch down, or not learnt yet). */
    val gear: StateFlow<Int?> = _gear

    private val _rolling = MutableStateFlow(false)
    /** Rolling with the engine at tickover (clutch down or out of gear): the revs have stopped
     * following the speed. No gear is shown and no change hint, as nothing is driving the wheels. */
    val rolling: StateFlow<Boolean> = _rolling

    /** The last couple of seconds of revs and speed, to tell whether one follows the other. */
    private val recent = ArrayDeque<Triple<Long, Float, Float>>()

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
            _rolling.value = false
            recent.clear()
            steadySince = 0L
            saveIfDue(now)
            return
        }
        val ratio = rpm / kmh
        val idle = isRolling(now, rpm, kmh)
        _rolling.value = idle
        _gear.value = if (idle) null else gearFor(ratio)
        if (idle) { steadySince = 0L; return }
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

    /**
     * In gear, the revs rise and fall with the speed (the same rpm per km/h). With the clutch down or
     * in neutral the engine sits at tickover while the car speeds up or slows down by itself: over the
     * last two seconds the speed changed (4 % or more) and the revs, at tickover, didn't go with it.
     * It holds until the revs follow the speed again or leave tickover.
     */
    private fun isRolling(now: Long, rpm: Float, kmh: Float): Boolean {
        recent.addLast(Triple(now, rpm, kmh))
        while (recent.isNotEmpty() && now - recent.first().first > WINDOW_MS) recent.removeFirst()
        if (rpm > IDLE_MAX) return false
        val first = recent.first()
        if (now - first.first < WINDOW_MS / 2) return _rolling.value
        val speedChange = ln(kmh / first.third)
        val revChange = ln(rpm / first.second)
        if (abs(speedChange) < 0.04f) return _rolling.value     // too little change to tell: as it was
        return abs(revChange - speedChange) > 0.5f * abs(speedChange)
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
        val gs = named
        if (gs.isEmpty()) return null
        var best = -1
        var bestD = Float.MAX_VALUE
        gs.forEach { (gear, r) ->
            val d = abs(ln(ratio / r))
            if (d < bestD) {
                bestD = d
                best = gear
            }
        }
        return if (bestD < 0.06f) best else null
    }

    /** The found piles numbered, highest first, when every gear but one is found and it's clear which. */
    private fun nameAllButOne(found: List<Float>, want: Int): List<Pair<Int, Float>> {
        if (found.size != want - 1 || found.isEmpty()) return emptyList()
        if (found.zipWithNext().any { (a, b) -> a / b > MAX_STEP }) return emptyList()   // one missing in between
        val first = if (found.first() >= FIRST_GEAR) 1 else 2
        return found.mapIndexed { i, r -> (first + i) to r }
    }

    /** Finds the piles: the strongest peaks, far enough apart to be different gears. */
    private fun look() {
        lastLook = System.currentTimeMillis()
        val total = bins.sum()
        val want = _count.value
        if (total < 40) {
            ratios = emptyList()
            named = emptyList()
            _ratios.value = ratios
            _found.value = 0
            return
        }
        val smooth = IntArray(BINS) { i -> (if (i > 0) bins[i - 1] else 0) + 2 * bins[i] + (if (i < BINS - 1) bins[i + 1] else 0) }
        // A pile needs a share of all the counts, but no more than a few pull-aways' worth: 1st is held
        // for seconds while 5th and 6th pile up for hours, and it was never found
        val floor = (total * 4 * 0.004f).toInt().coerceIn(MIN_FLOOR, MAX_FLOOR)
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
        val found = taken.map { ratioOf(it) }.sortedDescending()
        ratios = if (taken.size == want) found else emptyList()
        named = if (taken.size == want) found.mapIndexed { i, r -> (i + 1) to r } else nameAllButOne(found, want)
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
        /** Over this, the engine isn't at tickover (the TDI idles at about 800). */
        private const val IDLE_MAX = 1050f
        private const val WINDOW_MS = 2_000L
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
        /** The smallest pile that counts as a gear (in smoothed counts, 4 per sample), and the most it needs. */
        private const val MIN_FLOOR = 40
        private const val MAX_FLOOR = 120
        /** 1st turns over this many rpm per km/h (about 100-160 in cars), and 2nd stays under it. */
        private const val FIRST_GEAR = 95f
        /** Neighbouring gears are less than this far apart (1st to 2nd is the widest, about 1.6-1.9). */
        private const val MAX_STEP = 2.05f
    }
}
