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
    private var pedalMin = 100f
    private var pedalMax = 80f
    @Volatile private var scanRequested = false

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
            listOf("ATE0", "ATL0", "ATS0", "ATH0", "ATAT1", "ATSP0").forEach { elm.send(it) }
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
        val slow = intArrayOf(Pids.MAP, Pids.MAF, Pids.LOAD, Pids.COOLANT, Pids.MAP, Pids.MAF, Pids.LOAD, Pids.INTAKE, Pids.TIMING, Pids.OIL, Pids.BARO, Pids.AMBIENT, VOLTS)
        val misses = mutableMapOf<Int, Int>()
        var next = 0
        var silent = 0
        var count = 0
        var since = System.currentTimeMillis()
        var lastMil = 0L
        var lastRound = System.currentTimeMillis()
        while (coroutineContext.isActive) {
            var r = _state.value.readings
            var answered = false
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
            val trip = _state.value.trip.let { t ->
                if (!answered || kmh == null) t else t.copy(
                    meters = t.meters + kmh / 3.6 * dt / 1000.0,
                    drivingMs = t.drivingMs + (if (kmh > 2) dt else 0L),
                    maxKmh = maxOf(t.maxKmh, kmh),
                    fuelL = t.fuelL + (r.fuelLph ?: 0f) * dt / 3_600_000.0,
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
        Pids.data(elm.send("01%02X".format(pid)), pid)?.let { Pids.value(pid, it) }

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

    private companion object {
        /** Not a PID: the dongle's own voltage reading (ATRV). */
        const val VOLTS = -1
    }
}
