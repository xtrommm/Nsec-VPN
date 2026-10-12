package com.nsec.vpn.data.model

import java.time.Instant

/** Resolves live display data without changing the established WireGuard configuration. */
object ServerStatus {
    const val STALE_AFTER_MILLIS = 90_000L
    fun current(servers: List<Server>, connectedId: String?, selected: Server?): Server? {
        val id = connectedId ?: selected?.id
        return servers.firstOrNull { it.id == id } ?: selected?.takeIf { it.id == id }
    }
    fun loadText(server: Server?, now: Long): String {
        val stamp = try { server?.loadUpdatedAt?.let { Instant.parse(it).toEpochMilli() } }
            catch (_: Exception) { null }
        return if (server != null && stamp != null && server.currentLoad in 0..100 &&
            now - stamp in -60_000L..STALE_AFTER_MILLIS) "${server.currentLoad}%" else "Unavailable"
    }
}
