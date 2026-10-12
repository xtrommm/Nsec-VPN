package com.nsec.vpn.data.model

data class Server(
    val id: String,
    val name: String,
    val countryCode: String,
    val countryName: String,
    val city: String,
    val flagEmoji: String,
    val currentLoad: Int,
    val isPremiumOnly: Boolean,
    val isRecommended: Boolean = false,
    val pingMs: Long? = null,
    val isFavorite: Boolean = false,
    val totalNodes: Int = 1,
    val publicIpv4: String? = null,
    val loadUpdatedAt: String? = null
)

data class TunnelConfig(
    val serverPublicKey: String,
    val serverEndpoint: String,
    val clientAddress: String,
    val dnsServers: List<String> = listOf("1.1.1.1", "1.0.0.1"),
    val allowedIps: List<String> = listOf("0.0.0.0/0"),
    val persistentKeepalive: Int = 25,
    val mtu: Int = 1420,
    val nodeId: String? = null,
    val nodeName: String? = null,
    val locationName: String? = null,
    val publicIpv4: String? = null
)

sealed class VpnState {
    object Disconnected : VpnState()
    object Connecting : VpnState()
    object Connected : VpnState()
    object Disconnecting : VpnState()
    data class Error(val message: String) : VpnState()
}

data class VpnStats(
    val connectionDurationSeconds: Long = 0,
    val downloadSpeedMbps: Float = 0f,
    val uploadSpeedMbps: Float = 0f,
    val totalBytesReceived: Long = 0,
    val totalBytesSent: Long = 0
) {
    val formattedDuration: String
        get() {
            val hours = connectionDurationSeconds / 3600
            val minutes = (connectionDurationSeconds % 3600) / 60
            val seconds = connectionDurationSeconds % 60
            return if (hours > 0) {
                String.format(java.util.Locale.ROOT, "%02d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format(java.util.Locale.ROOT, "%02d:%02d", minutes, seconds)
            }
        }
}

enum class DnsProvider(val displayName: String, val primary: String, val secondary: String) {
    AUTOMATIC("Automatic (Server DNS)", "", ""),
    CUSTOM("Custom DNS", "", ""),
    CLOUDFLARE("Cloudflare (Fast & Private)", "1.1.1.1", "1.0.0.1"),
    GOOGLE("Google Public DNS", "8.8.8.8", "8.8.4.4"),
    ADGUARD("AdGuard (Ad-Blocking DNS)", "94.140.14.14", "94.140.15.15"),
    QUAD9("Quad9 (Security Filtered)", "9.9.9.9", "149.112.112.112")
}

enum class ThemeMode(val displayName: String) {
    SYSTEM("System Default"),
    LIGHT("Light Theme"),
    DARK("Dark Theme")
}

