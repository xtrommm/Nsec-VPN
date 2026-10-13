package com.nsec.vpn

import com.nsec.vpn.vpn.ConnectionEligibility
import com.nsec.vpn.vpn.EndpointPolicy
import com.nsec.vpn.vpn.TrafficCounterSampler
import java.net.InetAddress
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ConnectionPolicyRegressionTest {
    @Test
    fun dualStackHostnameUsesItsVerifiedIpv4ButRejectsPrivateOrMismatchedIpv4() {
        val good = listOf(InetAddress.getByName("47.85.109.170"), InetAddress.getByName("2606:4700:4700::1111"))
        EndpointPolicy.validate(good, "47.85.109.170")
        for (addresses in listOf(listOf(InetAddress.getByName("172.30.148.192")), listOf(InetAddress.getByName("1.1.1.1")), good + InetAddress.getByName("127.0.0.1"))) {
            try { EndpointPolicy.validate(addresses, "47.85.109.170"); fail("Unsafe endpoint must fail") } catch (_: IllegalArgumentException) { }
        }
    }
    @Test
    fun firstCounterSampleDoesNotInflateRateAndResetsDoNotProduceNegativeSpeeds() {
        val sampler = TrafficCounterSampler()
        assertEquals(0f, sampler.sample(100, 5_000_000, 2_000_000).downloadMbps, 0f)
        val measured = sampler.sample(1100, 5_100_000, 2_050_000)
        assertEquals(0.8f, measured.downloadMbps, 0.001f)
        assertEquals(0.4f, measured.uploadMbps, 0.001f)
        assertEquals(0f, sampler.sample(2100, 0, 0).downloadMbps, 0f)
    }
    @Test
    fun configurationEligibilityCanBeRecheckedAfterPermissionReturns() {
        assertNull(ConnectionEligibility.blockedReason(true, false, false, 2, 1))
        assertNotNull(ConnectionEligibility.blockedReason(false, false, false, 1, 1))
        assertNotNull(ConnectionEligibility.blockedReason(true, true, false, 1, 1))
        assertNotNull(ConnectionEligibility.blockedReason(true, false, true, 2, 1))
        assertNull(ConnectionEligibility.blockedReason(true, false, true, 2, 2))
    }
}
