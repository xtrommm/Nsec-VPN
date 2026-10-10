package com.nsec.vpn.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.nsec.vpn.MainActivity
import com.nsec.vpn.R

class NsecVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> {
                val serverName = intent.getStringExtra(EXTRA_SERVER_NAME) ?: "Secure Tunnel"
                val dnsServers = intent.getStringArrayListExtra(EXTRA_DNS_SERVERS) ?: arrayListOf("1.1.1.1", "1.0.0.1")
                val excludedApps = intent.getStringArrayListExtra(EXTRA_EXCLUDED_APPS)

                establishTunnel(serverName, dnsServers, excludedApps)

                val notification = buildNotification(serverName)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ServiceCompat.startForeground(
                        this,
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            }
            ACTION_DISCONNECT -> {
                closeTunnel()
                WireGuardTunnelManager.getInstance(this).disconnect()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun establishTunnel(
        serverName: String,
        dnsServers: List<String>,
        excludedApps: List<String>?
    ) {
        try {
            val builder = Builder()
                .setSession("Nsec VPN: $serverName")
                .setMtu(1420)
                .addAddress("10.8.0.2", 32)
                .addRoute("0.0.0.0", 0)

            for (dns in dnsServers) {
                try {
                    builder.addDnsServer(dns)
                } catch (_: Throwable) {}
            }

            if (!excludedApps.isNullOrEmpty()) {
                for (pkg in excludedApps) {
                    try {
                        builder.addDisallowedApplication(pkg)
                    } catch (_: Throwable) {}
                }
            }

            vpnInterface?.close()
            vpnInterface = builder.establish()
            Log.i("NsecVpnService", "VPN TUN interface established successfully")
        } catch (e: Exception) {
            Log.e("NsecVpnService", "Could not establish VPN TUN interface: ${e.message}")
        }
    }

    private fun closeTunnel() {
        try {
            vpnInterface?.close()
        } catch (_: Throwable) {}
        vpnInterface = null
    }

    override fun onRevoke() {
        super.onRevoke()
        closeTunnel()
        WireGuardTunnelManager.getInstance(this).disconnect()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        closeTunnel()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun buildNotification(serverName: String): Notification {
        createNotificationChannel()

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpenApp = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val disconnectIntent = Intent(this, NsecVpnService::class.java).apply {
            action = ACTION_DISCONNECT
        }
        val pendingDisconnect = PendingIntent.getService(
            this, 1, disconnectIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Nsec VPN Connected")
            .setContentText("Secured via WireGuard: $serverName")
            .setSmallIcon(R.drawable.ic_notification_small)
            .setContentIntent(pendingOpenApp)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, "Disconnect", pendingDisconnect)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Nsec VPN Connection Status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows live WireGuard tunnel connection status"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    companion object {
        const val ACTION_CONNECT = "com.nsec.vpn.ACTION_CONNECT"
        const val ACTION_DISCONNECT = "com.nsec.vpn.ACTION_DISCONNECT"
        const val EXTRA_SERVER_NAME = "extra_server_name"
        const val EXTRA_DNS_SERVERS = "extra_dns_servers"
        const val EXTRA_EXCLUDED_APPS = "extra_excluded_apps"
        private const val CHANNEL_ID = "nsecvpn_status_channel"
        private const val NOTIFICATION_ID = 1001
    }
}
