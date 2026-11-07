package io.github.bornaradusin.cardash.obd

/** Reading the dongle's answers. Pure functions, so they're easy to check. */
object Pids {

    /** The data bytes of a mode 01 answer for [pid] ("41 0C 1A F8" → [0x1A, 0xF8]), or null. */
    fun data(response: String, pid: Int): IntArray? {
        val want = "41" + "%02X".format(pid)
        response.uppercase().split('\r', '\n').forEach { raw ->
            val line = raw.replace(" ", "").trim()
            val i = line.indexOf(want)
            if (i < 0) return@forEach
            val hex = line.substring(i + want.length)
            if (hex.length < 2 || hex.length % 2 != 0 || !hex.all { it in "0123456789ABCDEF" }) return@forEach
            return IntArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16) }
        }
        return null
    }

    /** The value a mode 01 answer stands for, in the units [Readings] uses. */
    fun value(pid: Int, d: IntArray): Float? {
        val a = d.getOrNull(0)?.toFloat() ?: return null
        val b = d.getOrNull(1)?.toFloat()
        return when (pid) {
            RPM -> b?.let { (256 * a + it) / 4f }
            MAF -> b?.let { (256 * a + it) / 100f }
            FUEL_RATE -> b?.let { (256 * a + it) / 20f }
            TIMING -> a / 2f - 64f
            SPEED, MAP, BARO -> a
            COOLANT, INTAKE, OIL, AMBIENT -> a - 40
            THROTTLE, LOAD, FUEL, PEDAL -> a * 100f / 255f
            else -> null
        }
    }

    /** How many data bytes each reading has in a mode 01 answer. */
    private val LENGTH = mapOf(
        LOAD to 1, COOLANT to 1, MAP to 1, RPM to 2, SPEED to 1, INTAKE to 1, THROTTLE to 1, FUEL to 1,
        BARO to 1, AMBIENT to 1, OIL to 1, PEDAL to 1, MAF to 2, TIMING to 1, FUEL_RATE to 2,
    )

    /** Whether these readings fit one CAN frame when asked for together (7 bytes: 41, then each PID and its data). */
    fun fitsOneFrame(pids: IntArray): Boolean = 1 + pids.sumOf { 1 + (LENGTH[it] ?: 4) } <= 7

    /**
     * Several readings from one answer ("41 0C 1A F8 0D 32" → RPM and speed). Readings the car doesn't
     * have are left out of its answer; a longer answer comes as numbered frames ("0: …", "1: …").
     */
    fun multi(response: String): Map<Int, IntArray> {
        val lines = response.uppercase().split('\r', '\n').map { it.replace(" ", "").trim() }.filter { it.isNotEmpty() }
        val frames = lines.filter { ':' in it }.map { it.substringAfter(':') }
        val hex = (if (frames.isNotEmpty()) frames.joinToString("") else lines.firstOrNull { it.startsWith("41") }) ?: return emptyMap()
        if (!hex.all { it in "0123456789ABCDEF" }) return emptyMap()
        val start = hex.indexOf("41")
        if (start < 0) return emptyMap()
        val out = HashMap<Int, IntArray>()
        var p = start + 2
        while (p + 2 <= hex.length) {
            val pid = hex.substring(p, p + 2).toInt(16)
            val n = LENGTH[pid] ?: break
            if (p + 2 + n * 2 > hex.length) break
            out[pid] = IntArray(n) { hex.substring(p + 2 + it * 2, p + 4 + it * 2).toInt(16) }
            p += 2 + n * 2
        }
        return out
    }

    /** Volts from the dongle's ATRV answer ("12.6V"). */
    fun volts(response: String): Float? =
        Regex("(\\d{1,2}\\.\\d)\\s*V").find(response.uppercase())?.groupValues?.get(1)?.toFloatOrNull()

    /**
     * Fault codes from a mode 03 answer ("43 01 33 00 00 00 00" → P0133). On CAN the first byte after
     * 43 is the number of codes; on older protocols the codes start straight away.
     */
    fun faults(response: String, can: Boolean): List<String> {
        val codes = mutableListOf<String>()
        response.uppercase().split('\r', '\n').forEach { raw ->
            var line = raw.replace(" ", "").trim()
            // A multi-frame answer starts each line with its number ("0:", "1:")
            line = line.substringAfter(':')
            val i = line.indexOf("43")
            if (i < 0) return@forEach
            var hex = line.substring(i + 2)
            if (can && hex.length >= 2) hex = hex.substring(2)
            hex.chunked(4).filter { it.length == 4 && it != "0000" && it.all { c -> c in "0123456789ABCDEF" } }
                .forEach { codes += code(it) }
        }
        return codes.distinct()
    }

    private fun code(hex: String): String {
        val first = hex[0].digitToInt(16)
        val letter = "PCBU"[first shr 2]
        return letter + (first and 3).toString() + hex.substring(1)
    }

    const val LOAD = 0x04
    const val COOLANT = 0x05
    const val MAP = 0x0B
    const val RPM = 0x0C
    const val SPEED = 0x0D
    const val INTAKE = 0x0F
    const val THROTTLE = 0x11
    const val FUEL = 0x2F
    const val BARO = 0x33
    const val AMBIENT = 0x46
    const val OIL = 0x5C
    /** Accelerator pedal position D: reads about 15 % with the foot off on most cars. */
    const val PEDAL = 0x49
    /** Air flow into the engine, g/s: what fuel use is worked out from. */
    const val MAF = 0x10
    /** Ignition timing advance, degrees before top dead centre. */
    const val TIMING = 0x0E
    /** The engine's own fuel use, L/h: only newer cars report it. */
    const val FUEL_RATE = 0x5E
}
