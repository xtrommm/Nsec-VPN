package com.nsec.vpn.util

import java.util.Locale

object SpeedFormatter {
    fun formatSpeed(speedMbps: Float): String {
        if (speedMbps.isNaN() || speedMbps.isInfinite() || speedMbps <= 0f) {
            return "0.0 Mb/s"
        }
        return when {
            speedMbps >= 1000f -> String.format(Locale.US, "%.1f Gb/s", speedMbps / 1000f)
            speedMbps >= 0.1f -> String.format(Locale.US, "%.1f Mb/s", speedMbps)
            else -> String.format(Locale.US, "%.1f Kb/s", speedMbps * 1000f)
        }
    }

    fun formatTimer(seconds: Long): String {
        val s = seconds.coerceAtLeast(0)
        val hours = s / 3600
        val minutes = (s % 3600) / 60
        val secs = s % 60
        return if (hours > 0) {
            String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, secs)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, secs)
        }
    }
}
