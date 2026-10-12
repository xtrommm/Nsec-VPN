package com.nsec.vpn

import com.nsec.vpn.data.network.VpnSpeedTest
import org.junit.Assert.*
import org.junit.Test

class VpnSpeedTestTest {
    @Test fun measuresActualCounterDifferencesAndElapsedTime() {
        assertEquals(8f, VpnSpeedTest.megabitsPerSecond(100, 1_000_100, 1000)!!, 0.001f)
        assertEquals(4f, VpnSpeedTest.megabitsPerSecond(100, 1_000_100, 2000)!!, 0.001f)
        assertEquals(0f, VpnSpeedTest.megabitsPerSecond(100, 100, 1000)!!, 0.001f)
        assertNull(VpnSpeedTest.megabitsPerSecond(100, 0, 1000))
        assertNull(VpnSpeedTest.megabitsPerSecond(0, 100, 0))
    }
    @Test fun jitterRequiresMultipleRealSamples() {
        assertEquals(15L, VpnSpeedTest.jitter(listOf(30, 40, 60)))
        assertEquals(0L, VpnSpeedTest.jitter(listOf(30, 30, 30)))
        assertNull(VpnSpeedTest.jitter(emptyList()))
        assertNull(VpnSpeedTest.jitter(listOf(30)))
    }
}
