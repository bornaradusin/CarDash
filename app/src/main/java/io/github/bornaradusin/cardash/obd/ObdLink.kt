package io.github.bornaradusin.cardash.obd

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

/**
 * The ELM327 dongle on the radio's USB port. Finds it, asks Android for it once, sets it up and
 * reads the car over and over: RPM and speed every round, the rest in turn. Unplugging it, or the
 * ignition going off, just makes it wait and try again.
 */
class ObdLink(private val context: Context) {

    private val usb = context.getSystemService(UsbManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var asked = false
    /** Set from Settings: matches the air-flow fuel figure to the car's. */
    @Volatile var fuelFactor = 1f

    /**
     * Diesel: fuel per injection in mg = [dieselQ0] + [dieselQ1] × engine load %. A diesel's air
     * doesn't follow its fuel (it runs lean, leaner still at idle), but what's injected follows the
     * load. Starting values are for a 2.0 TDI (about 57 mg flat out, 6 mg at idle); matching to
     * the car at idle sets Q0, over a drive Q1.
     */
    @Volatile var diesel = false
    @Volatile var dieselQ0 = DIESEL_Q0
    @Volatile var dieselQ1 = DIESEL_Q1
    private var pedalMin = 100f
    private var pedalMax = 80f
    @Volatile private var scanRequested = false
    /** CAN cars: each question ends with "1", so the dongle answers as soon as the engine has (no waiting for others). */
    private var oneAnswer = false
    /** CAN cars that take several readings in one question (up to what fits one CAN frame). */
    private var together = false

    private val _state = MutableStateFlow(ObdState())
    val state: StateFlow<ObdState> = _state

    /** Starts reading (again); safe to call any number of times. */
    fun start() {
        if (job?.isActive == true) return
        job = scope.launch { run() }
    }

    /** Reads the engine's fault codes on the next round. */
    fun scanFaults() {
        scanRequested = true
        _state.update { it.copy(scanning = true) }
    }

    /** Asks Android again, for when the person said no the first time. */
    fun askAgain() {
        asked = false
    }

    private suspend fun run() {
        while (coroutineContext.isActive) {
            val driver = UsbSerialProber.getDefaultProber().findAllDrivers(usb).firstOrNull()
            if (driver == null) {
                _state.update { ObdState(link = Link.NO_DONGLE, faults = it.faults) }
                delay(3_000)
                continue
            }
            if (!usb.hasPermission(driver.device)) {
                _state.update { it.copy(link = Link.NEEDS_PERMISSION) }
                if (!asked) {
                    asked = true
                    requestPermission(driver.device)
                }
                delay(2_000)
                continue
            }
            val connection = usb.openDevice(driver.device)
            if (connection == null) {
                _state.update { it.copy(link = Link.ERROR, problem = "Couldn't open the dongle") }
                delay(3_000)
                continue
            }
            val port = driver.ports.first()
            try {
                port.open(connection)
                _state.update { it.copy(link = Link.CONNECTING, problem = null) }
                log("Opened ${driver.javaClass.simpleName.removeSuffix("Driver")} (USB ${"%04x".format(driver.device.vendorId)}:${"%04x".format(driver.device.productId)})")
                val elm = connect(port) ?: run {
                    _state.update { it.copy(link = Link.ERROR, problem = "The dongle didn't answer at any speed") }
                    null
                }
                if (elm != null) poll(elm)
            } catch (e: Exception) {
                log("Error: ${e.message ?: e.javaClass.simpleName}")
                _state.update { it.copy(link = Link.ERROR, problem = e.message ?: e.javaClass.simpleName) }
            } finally {
                runCatching { port.close() }
            }
            delay(2_000)
        }
    }

    /**
     * Finds the dongle's speed and sets it up. USB ELM327s use 38400 most often, clones anything from
     * 9600 to 500000; some don't call themselves ELM, so any sensible answer to ATI counts.
     */
    private suspend fun connect(port: UsbSerialPort): Elm327? {
        for (baud in intArrayOf(38_400, 115_200, 9_600, 57_600, 230_400, 500_000)) {
            if (!coroutineContext.isActive) return null
            log("Trying $baud baud")
            port.setParameters(baud, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            runCatching { port.setDTR(true); port.setRTS(true) }
            delay(200)
            val elm = Elm327(port, ::log)
            // A bare return first, in case the dongle is half-way through something
            elm.send("", 600)
            val reset = elm.send("ATZ", 3_000)
            delay(300)
            val id = elm.send("ATI", 1_500)
            val name = listOf(id, reset).flatMap { it.lines() }.map { it.trim() }
                .firstOrNull { line ->
                    line.length >= 3 && line.all { c -> c.code in 32..126 } && !line.startsWith("AT") && line.any { c -> c.isLetter() }
                }
            if (name == null) continue
            log("Dongle found at $baud baud: $name")
            // ATAT2: the dongle's quickest timing, learnt from how fast the car answers
            listOf("ATE0", "ATL0", "ATS0", "ATH0", "ATAT2", "ATSP0").forEach { elm.send(it) }
            _state.update { it.copy(adapter = name, baud = baud) }
            return elm
        }
        return null
    }

    private fun log(line: String) {
        val stamp = java.time.LocalTime.now().withNano(0)
        _state.update { it.copy(log = (it.log + "$stamp $line").takeLast(60)) }
    }

    private suspend fun poll(elm: Elm327) {
        // Finds the car's protocol; slow the first time, and fails while the ignition is off
        if (!wake(elm)) return
        // Boost, air flow and load come round more often: they change fastest
        val slow = intArrayOf(Pids.MAP, Pids.MAF, Pids.LOAD, Pids.COOLANT, Pids.MAP, Pids.MAF, Pids.LOAD, Pids.INTAKE, Pids.FUEL_RATE, Pids.OIL, Pids.BARO, Pids.AMBIENT, VOLTS)
        val misses = mutableMapOf<Int, Int>()
        var next = 0
        var round = 0
        var silent = 0
        var count = 0
        var since = System.currentTimeMillis()
        var lastMil = 0L
        var lastRound = System.currentTimeMillis()
        while (coroutineContext.isActive) {
            var r = _state.value.readings.copy(fuelFactor = fuelFactor, dieselLph = null)
            var answered = false
            if (together) {
                // RPM and speed every round in one question; then one group of the rest, in turn
                val main = askTogether(elm, intArrayOf(Pids.RPM, Pids.SPEED))
                main.forEach { (pid, v) -> r = set(r, pid, v); count++ }
                answered = main.isNotEmpty()
                val group = GROUPS[round % GROUPS.size].filter { (misses[it] ?: 0) < 3 }.toIntArray()
                round++
                if (group.isNotEmpty()) {
                    val got = askTogether(elm, group)
                    group.forEach { pid ->
                        val v = got[pid]
                        if (v == null) misses[pid] = (misses[pid] ?: 0) + 1 else {
                            misses[pid] = 0
                            r = set(r, pid, v)
                            count++
                        }
                    }
                }
                if (round % 12 == 0) Pids.volts(elm.send("ATRV"))?.let { r = set(r, VOLTS, it); count++ }
            } else {
                for (pid in intArrayOf(Pids.RPM, Pids.SPEED, Pids.PEDAL)) {
                    // The pedal is skipped on cars that don't report it
                    if (pid == Pids.PEDAL && (misses[pid] ?: 0) >= 3) continue
                    val v = read(elm, pid)
                    if (v != null) {
                        answered = true
                        r = set(r, pid, v)
                        count++
                        misses[pid] = 0
                    } else if (pid == Pids.PEDAL) misses[pid] = (misses[pid] ?: 0) + 1
                }
                // One of the slower readings each round, skipping ones the car keeps not answering
                for (attempt in slow.indices) {
                    val pid = slow[next]
                    next = (next + 1) % slow.size
                    if ((misses[pid] ?: 0) >= 3) continue
                    val v = if (pid == VOLTS) Pids.volts(elm.send("ATRV")) else read(elm, pid)
                    if (v == null) misses[pid] = (misses[pid] ?: 0) + 1 else {
                        misses[pid] = 0
                        r = set(r, pid, v)
                        count++
                    }
                    break
                }
            }
            if (System.currentTimeMillis() - lastMil > 30_000) {
                lastMil = System.currentTimeMillis()
                Pids.data(elm.send("0101"), 0x01)?.let { d ->
                    r = r.copy(milOn = d[0] and 0x80 != 0, faultCount = d[0] and 0x7F)
                }
            }
            if (scanRequested) {
                scanRequested = false
                val can = (_state.value.protocol ?: "").contains("CAN", ignoreCase = true)
                val codes = Pids.faults(elm.send("03", 5_000), can)
                _state.update { it.copy(faults = codes, scanning = false) }
            }

            val now = System.currentTimeMillis()
            // The trip: distance from speed over the time since the last round
            val dt = (now - lastRound).coerceIn(0, 5_000)
            lastRound = now
            val kmh = r.speedKmh
            // Diesel fuel from load and RPM: 4 cylinders, one injection each per 2 turns
            val rpmNow = r.rpm
            val loadNow = r.loadPct
            if (diesel && rpmNow != null && loadNow != null) {
                val mg = (dieselQ0 + dieselQ1 * loadNow).coerceAtLeast(0f)
                val gPerS = rpmNow / 30f * mg / 1000f
                r = r.copy(dieselLph = gPerS * 3600f / DIESEL_G_PER_L)
            }
            val trip = _state.value.trip.let { t ->
                if (!answered || kmh == null) t else t.copy(
                    meters = t.meters + kmh / 3.6 * dt / 1000.0,
                    drivingMs = t.drivingMs + (if (kmh > 2) dt else 0L),
                    maxKmh = maxOf(t.maxKmh, kmh),
                    fuelL = t.fuelL + (r.fuelLph ?: 0f) * dt / 3_600_000.0,
                    maxBoostBar = maxOf(t.maxBoostBar, r.boostBar ?: 0f),
                    maxRpm = maxOf(t.maxRpm, r.rpm ?: 0f),
                    injections = t.injections + (rpmNow ?: 0f) / 30.0 * dt / 1_000_000.0,
                    injectionsLoad = t.injectionsLoad + (rpmNow ?: 0f) / 30.0 * (loadNow ?: 0f) * dt / 1_000_000.0,
                )
            }
            val rate = if (now - since >= 2_000) {
                val rps = count * 1000f / (now - since)
                count = 0
                since = now
                rps
            } else _state.value.rate
            silent = if (answered) 0 else silent + 1
            _state.update {
                it.copy(
                    link = if (silent >= 5) Link.NO_CAR else Link.CONNECTED,
                    readings = r,
                    rate = rate,
                    trip = trip,
                )
            }
            if (silent >= 5) {
                // Ignition off: check now and then, without hammering the car
                delay(3_000)
                if (!wake(elm)) return
                silent = 0
                // Readings missed while the engine was off get another chance
                misses.clear()
            }
        }
    }

    /** Asks the car what it supports, which also makes the dongle find its protocol. */
    private suspend fun wake(elm: Elm327): Boolean {
        repeat(Int.MAX_VALUE) {
            if (!coroutineContext.isActive) return false
            val answer = elm.send("0100", 8_000)
            if (Pids.data(answer, 0x00) != null) {
                val protocol = elm.send("ATDP").lines().map { it.trim() }.firstOrNull { it.isNotEmpty() }
                _state.update { it.copy(link = Link.CONNECTED, protocol = protocol) }
                // Faster reading where the car and the dongle can do it (CAN cars, like Torque's "faster communication")
                val can = protocol?.let { it.contains("CAN", ignoreCase = true) || it.contains("15765") } == true
                oneAnswer = can && Pids.data(elm.send("010C1"), Pids.RPM) != null
                together = oneAnswer && Pids.multi(elm.send("010C0D1")).let { Pids.RPM in it && Pids.SPEED in it }
                log(
                    when {
                        together -> "Fast reading on: several readings per question"
                        oneAnswer -> "Fast reading on: one answer per question"
                        else -> "Fast reading off: the car or the dongle doesn't do it"
                    },
                )
                if (_state.value.faults == null) scanFaults()
                return true
            }
            if (answer.isBlank() || "?" == answer.trim()) return false
            _state.update { it.copy(link = Link.NO_CAR) }
            delay(5_000)
        }
        return false
    }

    private fun read(elm: Elm327, pid: Int): Float? =
        Pids.data(elm.send("01%02X".format(pid) + if (oneAnswer) "1" else ""), pid)?.let { Pids.value(pid, it) }

    /** Several readings in one question (CAN cars); only the ones the car answered come back. */
    private fun askTogether(elm: Elm327, pids: IntArray): Map<Int, Float> {
        val got = Pids.multi(elm.send("01" + pids.joinToString("") { "%02X".format(it) } + "1"))
        return got.mapNotNull { (pid, d) -> Pids.value(pid, d)?.let { pid to it } }.toMap()
    }

    private fun set(r: Readings, pid: Int, v: Float): Readings = when (pid) {
        Pids.RPM -> r.copy(rpm = v)
        Pids.SPEED -> r.copy(speedKmh = v)
        Pids.COOLANT -> r.copy(coolantC = v)
        Pids.INTAKE -> r.copy(intakeC = v)
        Pids.OIL -> r.copy(oilC = v)
        Pids.AMBIENT -> r.copy(ambientC = v)
        Pids.MAP -> r.copy(mapKpa = v)
        Pids.BARO -> r.copy(baroKpa = v)
        Pids.THROTTLE -> r.copy(throttlePct = v)
        Pids.MAF -> r.copy(mafGs = v)
        Pids.TIMING -> r.copy(timingDeg = v)
        Pids.FUEL_RATE -> r.copy(fuelRateLph = v)
        Pids.PEDAL -> {
            // The sensor doesn't read 0..100 %: the lowest seen is foot off, and flat out is at least 80 %
            pedalMin = minOf(pedalMin, v)
            pedalMax = maxOf(pedalMax, v, 80f)
            r.copy(pedalPct = ((v - pedalMin) / (pedalMax - pedalMin) * 100f).coerceIn(0f, 100f))
        }
        Pids.LOAD -> r.copy(loadPct = v)
        Pids.FUEL -> r.copy(fuelPct = v)
        VOLTS -> r.copy(volts = v)
        else -> r
    }

    private fun requestPermission(device: android.hardware.usb.UsbDevice) {
        val action = context.packageName + ".USB_PERMISSION"
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                runCatching { context.unregisterReceiver(this) }
                // Allowed: the next round opens it. Refused: shown in Settings with a way to ask again.
            }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(action), ContextCompat.RECEIVER_NOT_EXPORTED)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        val pending = PendingIntent.getBroadcast(context, 0, Intent(action).setPackage(context.packageName), flags)
        usb.requestPermission(device, pending)
    }

    companion object {
        const val DIESEL_Q0 = -9.8f
        const val DIESEL_Q1 = 0.668f
        /** Grams in a litre of diesel. */
        const val DIESEL_G_PER_L = 832f

        /** Not a PID: the dongle's own voltage reading (ATRV). */
        private const val VOLTS = -1

        /**
         * With several readings per question: the second question of each round, in turn. Each fits one
         * CAN frame; pedal, boost and load come round most often, as they change fastest.
         */
        private val GROUPS = listOf(
            intArrayOf(Pids.PEDAL, Pids.MAP, Pids.LOAD),
            intArrayOf(Pids.MAF, Pids.MAP),
            intArrayOf(Pids.PEDAL, Pids.MAP, Pids.LOAD),
            intArrayOf(Pids.COOLANT, Pids.INTAKE, Pids.BARO),
            intArrayOf(Pids.PEDAL, Pids.MAP, Pids.LOAD),
            intArrayOf(Pids.MAF, Pids.FUEL_RATE),
            intArrayOf(Pids.PEDAL, Pids.MAP, Pids.LOAD),
            intArrayOf(Pids.OIL, Pids.AMBIENT),
        )
    }
}
