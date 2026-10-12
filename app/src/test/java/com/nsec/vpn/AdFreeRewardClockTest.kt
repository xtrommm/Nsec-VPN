package com.nsec.vpn

import com.nsec.vpn.data.monetization.AdFreeRewardClock
import org.junit.Assert.*
import org.junit.Test

class AdFreeRewardClockTest {
    @Test fun activatesImmediatelyAndExpiresAfterThirtyMinutes() {
        val wall = 1_000_000L
        val elapsed = 50_000L
        val duration = 30 * 60_000L
        assertTrue(AdFreeRewardClock.active(wall, wall + duration, elapsed + duration, 2, 2, wall, elapsed))
        assertTrue(AdFreeRewardClock.active(wall, wall + duration, elapsed + duration, 2, 2, wall + 1000, elapsed + 1000))
        assertFalse(AdFreeRewardClock.active(wall, wall + duration, elapsed + duration, 2, 2, wall + duration, elapsed + duration))
    }
    @Test fun failsClosedOnClockRollbackOrReboot() {
        assertFalse(AdFreeRewardClock.active(1000, 10000, 12000, 2, 2, 999, 5000))
        assertFalse(AdFreeRewardClock.active(1000, 10000, 12000, 2, 3, 2000, 100))
        assertFalse(AdFreeRewardClock.active(1000, 10000, 12000, 2, 2, 2000, 12000))
    }
}
