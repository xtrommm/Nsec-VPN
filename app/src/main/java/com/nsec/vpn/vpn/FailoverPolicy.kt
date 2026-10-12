package com.nsec.vpn.vpn

import com.nsec.vpn.data.model.Server

object FailoverPolicy {
    fun next(initial: Server, visited: Set<String>, servers: List<Server>, tier: String, maxAttempts: Int): Server? {
        if (visited.size >= maxAttempts) return null
        return servers.filter { it.id !in visited && it.countryCode == initial.countryCode && it.city == initial.city &&
            (!it.isPremiumOnly || tier != "FREE") }.minByOrNull { it.currentLoad }
    }
}
