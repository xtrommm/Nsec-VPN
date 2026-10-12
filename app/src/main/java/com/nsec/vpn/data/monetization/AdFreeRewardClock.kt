package com.nsec.vpn.data.monetization

object AdFreeRewardClock {
    fun active(grantedWallMs: Long, expiresWallMs: Long, expiresElapsedMs: Long,
               grantedBoot: Int, currentBoot: Int, wallMs: Long, elapsedMs: Long): Boolean =
        grantedBoot >= 0 && grantedBoot == currentBoot && grantedWallMs > 0 &&
            wallMs >= grantedWallMs && wallMs < expiresWallMs && elapsedMs < expiresElapsedMs
}
