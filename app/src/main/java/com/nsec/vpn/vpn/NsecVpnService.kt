package com.nsec.vpn.vpn

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.nsec.vpn.MainActivity
import com.nsec.vpn.R
import com.nsec.vpn.data.model.VpnState
import kotlinx.coroutines.*

/** Notification companion only. GoBackend.VpnService owns the sole TUN and WireGuard engine. */
class NsecVpnService : Service() {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var serverName = ""
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "VPN connection", NotificationManager.IMPORTANCE_LOW))
        }
        scope.launch {
            WireGuardTunnelManager.getInstance(this@NsecVpnService).vpnState.collect { state ->
                if (state is VpnState.Connected || state is VpnState.Connecting) notifyState(state is VpnState.Connected)
            }
        }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISCONNECT) {
            WireGuardTunnelManager.getInstance(this).disconnect()
            stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
        } else {
            serverName = intent?.getStringExtra(EXTRA_SERVER_NAME).orEmpty()
            notifyState(WireGuardTunnelManager.getInstance(this).vpnState.value is VpnState.Connected)
        }
        return START_NOT_STICKY
    }
    private fun notifyState(connected: Boolean) {
        if (android.net.VpnService.prepare(this) != null) {
            WireGuardTunnelManager.getInstance(this).disconnect()
            stopSelf()
            return
        }
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val disconnect = PendingIntent.getService(this, 1, Intent(this, NsecVpnService::class.java).setAction(ACTION_DISCONNECT), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(this, CHANNEL_ID).setSmallIcon(R.drawable.ic_notification_small)
            .setContentTitle(if (connected) "Nsec VPN Connected" else "Nsec VPN Connecting")
            .setContentText(serverName).setContentIntent(open).setOngoing(true).addAction(0, "Disconnect", disconnect).build()
        if (Build.VERSION.SDK_INT >= 34) ServiceCompat.startForeground(this, 1001, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)
        else startForeground(1001, notification)
    }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
    companion object {
        const val ACTION_CONNECT = "com.nsec.vpn.ACTION_CONNECT"
        const val ACTION_DISCONNECT = "com.nsec.vpn.ACTION_DISCONNECT"
        const val EXTRA_SERVER_NAME = "extra_server_name"
        private const val CHANNEL_ID = "nsecvpn_status_channel"
    }
}
