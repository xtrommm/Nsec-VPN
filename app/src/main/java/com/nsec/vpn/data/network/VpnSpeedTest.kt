package com.nsec.vpn.data.network

import kotlin.math.abs
import kotlin.math.roundToLong

object VpnSpeedTest {
    fun megabitsPerSecond(previous: Long, current: Long, elapsedMs: Long): Float? {
        if (previous < 0 || current < previous || elapsedMs <= 0) return null
        return ((current - previous).toDouble() * 8 / elapsedMs / 1000).toFloat()
    }

    fun jitter(samples: List<Long>): Long? = if (samples.size < 2) null else
        samples.zipWithNext { a, b -> abs(b - a).toDouble() }.average().roundToLong()

}
