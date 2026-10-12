package com.nsec.vpn.data.model

import java.util.Locale

object LocationSelection {
    fun key(server: Server): String = "${server.countryCode.trim().uppercase(Locale.ROOT)}:${server.city.trim().lowercase(Locale.ROOT)}"
    fun sameLocation(a: Server, b: Server): Boolean = key(a) == key(b)
    fun score(server: Server): Long = (server.pingMs ?: 999L).coerceIn(0, 60000) + server.currentLoad.coerceIn(0, 100) * 2L
    fun ranked(servers: List<Server>, tier: String): List<Server> = servers
        .filter { !it.isPremiumOnly || tier != "FREE" }
        .sortedWith(compareBy<Server> { it.currentLoad >= 100 }.thenBy { score(it) }.thenBy { it.id })

    /** UI grouping only; retain raw nodes for measurements, routing and verified-node display. */
    fun group(servers: List<Server>, tier: String): List<Server> = servers.groupBy(::key).values.map { nodes ->
        val eligible = ranked(nodes, tier)
        val best = eligible.firstOrNull() ?: nodes.minBy { score(it) }
        val displayed = eligible.ifEmpty { nodes }
        best.copy(totalNodes = nodes.size, isPremiumOnly = nodes.all { it.isPremiumOnly },
            isFavorite = nodes.any { it.isFavorite }, isRecommended = nodes.any { it.isRecommended },
            currentLoad = displayed.map { it.currentLoad }.average().toInt())
    }
}
