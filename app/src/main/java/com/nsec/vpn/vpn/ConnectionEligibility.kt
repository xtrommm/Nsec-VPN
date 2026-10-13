package com.nsec.vpn.vpn

object ConnectionEligibility {
    fun blockedReason(loaded: Boolean, maintenance: Boolean, forcedUpdate: Boolean, minimumVersion: Int, currentVersion: Int): String? = when {
        !loaded -> "App Settings Are Still Loading. Please Retry Shortly."
        maintenance -> "VPN Maintenance Is In Progress. Please Try Again Later."
        forcedUpdate && currentVersion < minimumVersion -> "Update Nsec VPN Before Connecting."
        else -> null
    }
}
