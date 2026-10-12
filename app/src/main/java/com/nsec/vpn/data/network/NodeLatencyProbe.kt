package com.nsec.vpn.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlin.math.roundToLong

/** ICMP round-trip time to the backend-provided node IP, outside the VPN like this app. */
object NodeLatencyProbe {
    fun validPublicIpv4(host: String?): Boolean {
        if (host == null || !Regex("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}").matches(host)) return false
        val a = host.split('.').map { it.toInt() }
        return a.all { it in 0..255 } && !(a[0] in setOf(0, 10, 127) || a[0] >= 224 ||
            (a[0] == 172 && a[1] in 16..31) || (a[0] == 192 && a[1] in setOf(0, 168)) ||
            (a[0] == 169 && a[1] == 254) || (a[0] == 100 && a[1] in 64..127) ||
            (a[0] == 198 && (a[1] in 18..19 || (a[1] == 51 && a[2] == 100))) ||
            (a[0] == 203 && a[1] == 0 && a[2] == 113))
    }

    fun parseReply(output: String): Long? {
        val reply = Regex("(?m)^.*bytes from .+time([=<])([0-9]+(?:\\.[0-9]+)?)\\s*ms").find(output)
            ?: return null
        val value = reply.groupValues[2].toDoubleOrNull() ?: return null
        if (!value.isFinite() || value < 0 || value > 3000) return null
        return value.roundToLong().coerceAtLeast(1)
    }

    suspend fun measure(host: String?): Long? = withContext(Dispatchers.IO) {
        if (!validPublicIpv4(host)) return@withContext null
        val process = try {
            ProcessBuilder("/system/bin/ping", "-n", "-c", "1", "-W", "2", host!!)
                .redirectErrorStream(true).start()
        } catch (_: java.io.IOException) { return@withContext null }
        try {
            val deadline = System.nanoTime() + 3_000_000_000L
            while (System.nanoTime() < deadline) {
                coroutineContext.ensureActive()
                val exit = try { process.exitValue() } catch (_: IllegalThreadStateException) { null }
                if (exit != null) {
                    return@withContext if (exit == 0) process.inputStream.bufferedReader().use {
                        parseReply(it.readText())
                    } else null
                }
                delay(25)
            }
            null
        } finally {
            process.destroy()
            process.inputStream.close()
            process.outputStream.close()
            process.errorStream.close()
        }
    }
}
