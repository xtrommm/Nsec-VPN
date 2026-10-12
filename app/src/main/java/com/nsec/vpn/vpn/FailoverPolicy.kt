package com.nsec.vpn.vpn

import com.nsec.vpn.data.model.Server
import com.nsec.vpn.data.model.LocationSelection

object FailoverPolicy {
    fun next(initial: Server, visited: Set<String>, servers: List<Server>, tier: String, maxAttempts: Int): Server? {
        if (visited.size >= maxAttempts) return null
        return LocationSelection.ranked(servers.filter { it.id !in visited && LocationSelection.sameLocation(it, initial) }, tier).firstOrNull()
    }
}
