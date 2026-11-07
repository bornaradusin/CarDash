package io.github.bornaradusin.cardash.car

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** One value the CAN box sent: its update code and what came with it. */
data class CanValue(val code: Int, val ints: List<Int>, val floats: List<Float>, val strings: List<String>, val at: Long) {
    fun describe(): String = buildList {
        if (ints.isNotEmpty()) add(ints.joinToString(", "))
        if (floats.isNotEmpty()) add(floats.joinToString(", "))
        if (strings.isNotEmpty()) add(strings.joinToString(", ") { "\"$it\"" })
    }.joinToString("  ").ifEmpty { "(empty)" }
}

data class CanState(val status: String = "Not connected", val values: Map<Int, CanValue> = emptyMap())

/**
 * The radio's CAN box, through the radio's own service (com.syu.ms, the one its Vehicle settings app
 * uses). Talks to it the way that app does: asks for the CAN bus module and signs up for its update
 * codes; the service sends each value it knows straight away, then again whenever it changes.
 *
 * Which code holds what (oil temperature, odometer) depends on the car, so every code is listed in
 * Settings and the person picks the right ones.
 */
class CanBox(private val context: Context) {

    private val _state = MutableStateFlow(CanState())
    val state: StateFlow<CanState> = _state
    private var bound = false

    private val callback = object : Binder() {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code == INTERFACE_TRANSACTION) {
                reply?.writeString(CALLBACK)
                return true
            }
            if (code != 1) return super.onTransact(code, data, reply, flags)
            runCatching {
                data.enforceInterface(CALLBACK)
                val update = data.readInt()
                val ints = data.createIntArray()?.toList().orEmpty()
                val floats = data.createFloatArray()?.toList().orEmpty()
                val strings = data.createStringArray()?.filterNotNull().orEmpty()
                val v = CanValue(update, ints, floats, strings, System.currentTimeMillis())
                _state.update { it.copy(values = it.values + (update to v)) }
            }
            reply?.writeNoException()
            return true
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder) {
            Thread {
                val module = runCatching { remoteModule(service, CANBUS) }.getOrNull()
                if (module == null) {
                    _state.update { it.copy(status = "The radio's CAN service answered, but has no CAN box module") }
                    return@Thread
                }
                var ok = 0
                for (code in 0..MAX_CODE) {
                    if (runCatching { register(module, code) }.isSuccess) ok++
                }
                _state.update { it.copy(status = if (ok > 0) "Connected to the radio's CAN box" else "The CAN box refused every code") }
            }.start()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            _state.update { it.copy(status = "The radio's CAN service stopped; reconnecting…") }
        }
    }

    fun start() {
        if (bound) return
        val intent = Intent().setClassName(PACKAGE, SERVICE)
        bound = runCatching { context.bindService(intent, connection, Context.BIND_AUTO_CREATE) }.getOrDefault(false)
        if (!bound) _state.update { it.copy(status = "This radio has no CAN service CarDash can reach") }
        else _state.update { it.copy(status = "Connecting…") }
    }

    /** IRemoteToolkit.getRemoteModule(code), transaction 1. */
    private fun remoteModule(toolkit: IBinder, module: Int): IBinder? {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(TOOLKIT)
            data.writeInt(module)
            toolkit.transact(1, data, reply, 0)
            reply.readException()
            return reply.readStrongBinder()
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    /** IRemoteModule.register(callback, updateCode, 1), transaction 3. */
    private fun register(module: IBinder, update: Int) {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(MODULE)
            data.writeStrongBinder(callback)
            data.writeInt(update)
            data.writeInt(1)
            module.transact(3, data, reply, 0)
            reply.readException()
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    companion object {
        const val PACKAGE = "com.syu.ms"
        private const val SERVICE = "app.ToolkitService"
        private const val TOOLKIT = "com.syu.ipc.IRemoteToolkit"
        private const val MODULE = "com.syu.ipc.IRemoteModule"
        private const val CALLBACK = "com.syu.ipc.IModuleCallback"
        private const val CANBUS = 7
        /** Codes signed up for: car info sits well inside this on every profile seen. */
        private const val MAX_CODE = 400

        /** A value read from a code: the [index]th whole number it sent. */
        fun read(values: Map<Int, CanValue>, code: Int, index: Int): Int? = values[code]?.ints?.getOrNull(index)
    }
}

/** What a CAN box value can be used as on the dashboard. */
enum class CanRole(val title: String, val unit: String) {
    OIL("Oil temperature", "°C"),
    ODOMETER("Odometer", "km"),
    RANGE("Range", "km"),
    FUEL_LEVEL("Fuel level", "L"),
    FUEL_AVG("Average fuel use", "L/100"),
    COOLANT("Coolant", "°C"),
    OUTSIDE("Outside", "°C"),
    BATTERY("Battery", "V"),
}

/** How a raw CAN number becomes the real value. */
enum class CanScale(val title: String, val apply: (Int) -> Float) {
    AS_IS("As it comes", { it.toFloat() }),
    DIV10("÷ 10", { it / 10f }),
    DIV100("÷ 100", { it / 100f }),
    DIV2("÷ 2", { it / 2f }),
    MINUS40("− 40", { it - 40f }),
    DIV10_MINUS40("÷ 10, − 40", { it / 10f - 40f }),
}

/** Which code, which number in it, and how to scale it. */
data class CanPick(val code: Int, val index: Int, val scale: CanScale) {
    fun encode() = "$code:$index:${scale.name}"

    companion object {
        fun decode(s: String?): CanPick? {
            val p = s?.split(':') ?: return null
            if (p.size != 3) return null
            return CanPick(p[0].toIntOrNull() ?: return null, p[1].toIntOrNull() ?: return null,
                runCatching { CanScale.valueOf(p[2]) }.getOrNull() ?: return null)
        }
    }
}

/** The dashboard values from the CAN box, from the person's picks. */
fun canValues(values: Map<Int, CanValue>, picks: Map<CanRole, CanPick>): Map<CanRole, Float> =
    picks.mapNotNull { (role, pick) -> CanBox.read(values, pick.code, pick.index)?.let { role to pick.scale.apply(it) } }.toMap()
