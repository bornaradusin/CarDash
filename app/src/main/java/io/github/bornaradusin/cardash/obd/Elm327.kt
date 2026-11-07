package io.github.bornaradusin.cardash.obd

import com.hoho.android.usbserial.driver.UsbSerialPort

/** Sends one command to the ELM327 and waits for its ">" prompt. */
class Elm327(private val port: UsbSerialPort, private val log: ((String) -> Unit)? = null) {

    private val buf = ByteArray(256)

    /** The answer without the prompt, or "" when the dongle didn't finish in time. */
    fun send(command: String, timeoutMs: Long = 1_500): String {
        // Drop anything left over from an earlier answer
        runCatching { while (port.read(buf, 20) > 0) Unit }
        port.write((command + "\r").toByteArray(Charsets.US_ASCII), 500)
        val out = StringBuilder()
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            val n = port.read(buf, 100)
            if (n > 0) {
                out.append(String(buf, 0, n, Charsets.US_ASCII))
                if ('>' in out) break
            }
        }
        val answer = out.toString().substringBefore('>').replace("\u0000", "").trim()
        // Logged while setting up (AT commands), not for every reading
        if (log != null && ((command.startsWith("AT") && command != "ATRV") || command.isEmpty() || command == "0100" || command == "03")) {
            log.invoke("> ${command.ifEmpty { "(return)" }}  < ${answer.replace("\r", " | ").replace("\n", " ").ifEmpty { if ('>' in out) "(empty)" else "(no answer)" }}")
        }
        // Some clones finish without a prompt: what they did send still counts
        return answer
    }
}
