package com.nsec.vpn.data.network

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.net.toUri
import com.nsec.vpn.vpn.WireGuardTunnelManager
import kotlin.math.abs
import kotlin.math.roundToLong

object VpnSpeedTest {
    fun megabitsPerSecond(previous: Long, current: Long, elapsedMs: Long): Float? {
        if (previous < 0 || current < previous || elapsedMs <= 0) return null
        return ((current - previous).toDouble() * 8 / elapsedMs / 1000).toFloat()
    }

    fun jitter(samples: List<Long>): Long? = if (samples.size < 2) null else
        samples.zipWithNext { a, b -> abs(b - a).toDouble() }.average().roundToLong()

    fun openBrowser(context: Context, manager: WireGuardTunnelManager): Boolean {
        if (manager.vpnState.value !is com.nsec.vpn.data.model.VpnState.Connected) return false
        val intent = Intent(Intent.ACTION_VIEW, "https://speed.cloudflare.com/".toUri())
            .addCategory(Intent.CATEGORY_BROWSABLE)
        @Suppress("DEPRECATION")
        val browsers = context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            .filter { it.activityInfo.exported && it.activityInfo.enabled &&
                manager.isApplicationTunneled(it.activityInfo.packageName) }
            .distinctBy { it.activityInfo.packageName }
        if (browsers.isEmpty()) return false
        val targets = browsers.map { Intent(intent).setPackage(it.activityInfo.packageName) }
        val chooser = Intent.createChooser(targets.first(), "Choose a browser included in the VPN")
        if (targets.size > 1) chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, targets.drop(1).toTypedArray())
        return try { context.startActivity(chooser); true }
        catch (_: android.content.ActivityNotFoundException) { false }
    }
}
