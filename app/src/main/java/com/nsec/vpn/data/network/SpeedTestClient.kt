package com.nsec.vpn.data.network

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import com.nsec.vpn.data.model.VpnState
import com.nsec.vpn.vpn.WireGuardTunnelManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** IPC only. Network requests run under the helper's separately tunneled UID. */
object SpeedTestClient {
    const val HELPER_PACKAGE = "com.nsec.vpn.speedtest"
    data class Progress(val phase: String, val speed: Float, val ping: Double, val jitter: Double,
                        val download: Float, val upload: Float, val completed: Boolean = false,
                        val error: String? = null)

    fun measure(context: Context, manager: WireGuardTunnelManager): Flow<Progress> = callbackFlow {
        val node = manager.connectedNode.value
        require(manager.vpnState.value is VpnState.Connected && node != null) { "Connect to a VPN server first." }
        require(manager.isApplicationTunneled(HELPER_PACKAGE)) { "Include Nsec VPN Speed Helper in split tunneling and reconnect." }
        val ip = requireNotNull(node.publicIpv4) { "The connected node has no verified public IP." }
        var remote: Messenger? = null
        val responseHandler = Handler(Looper.getMainLooper()) { message ->
                if (manager.vpnState.value !is VpnState.Connected || manager.connectedNode.value != node) {
                    close(IllegalStateException("VPN changed or disconnected. Reconnect and retry.")); return@Handler true
                }
                val b = message.data
                trySend(Progress(b.getString("phase") ?: "Testing", b.getDouble("speed").toFloat(),
                    b.getDouble("ping"), b.getDouble("jitter"), b.getDouble("download").toFloat(),
                    b.getDouble("upload").toFloat(), message.what == 4, b.getString("error")))
                if (message.what == 4 || message.what == 5) close()
                true
        }
        val receiver = Messenger(responseHandler)
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                remote = Messenger(binder)
                try {
                    remote?.send(Message.obtain(null, 1).apply {
                        replyTo = receiver; data = Bundle().apply { putString("expectedIp", ip) }
                    })
                } catch (_: RemoteException) { close(IllegalStateException("The speed helper stopped. Retry the test.")) }
            }
            override fun onServiceDisconnected(name: ComponentName) { close(IllegalStateException("The speed helper disconnected.")) }
            override fun onNullBinding(name: ComponentName) { close(IllegalStateException("The speed helper could not start.")) }
            override fun onBindingDied(name: ComponentName) { close(IllegalStateException("The speed helper was updated. Retry the test.")) }
        }
        val intent = Intent().setComponent(ComponentName(HELPER_PACKAGE, "$HELPER_PACKAGE.SpeedTestService"))
        val bound = try { context.bindService(intent, connection, Context.BIND_AUTO_CREATE) }
        catch (_: SecurityException) { throw IllegalStateException("Install the speed helper signed with the same key as Nsec VPN.") }
        if (!bound) { close(IllegalStateException("Install Nsec VPN Speed Helper from the same build first.")) }
        awaitClose {
            responseHandler.removeCallbacksAndMessages(null)
            try { remote?.send(Message.obtain(null, 2)) } catch (_: RemoteException) { /* Already stopped. */ }
            if (bound) context.unbindService(connection)
        }
    }
}
