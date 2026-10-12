package com.nsec.vpn

import com.nsec.vpn.data.model.Server
import com.nsec.vpn.vpn.FailoverPolicy
import org.junit.Assert.*
import org.junit.Test

class FailoverPolicyTest {
    private val initial = Server("one", "Test node", "US", "Test", "Test city", "US", 30, false)
    @Test fun timeoutSelectsAnotherEligibleNodeInTheSameLocation() {
        val other = initial.copy(id="two", currentLoad=10)
        val pool = listOf(initial, other, initial.copy(id="elsewhere", city="Other city", currentLoad=0),
            initial.copy(id="premium", isPremiumOnly=true, currentLoad=0))
        assertEquals("two", FailoverPolicy.next(initial, setOf("one"), pool, "FREE", 3)?.id)
    }
    @Test fun failureAttemptsAreCappedAndVisitedNodesNeverRepeat() {
        val pool = listOf(initial, initial.copy(id="two"), initial.copy(id="three"), initial.copy(id="four"))
        assertNull(FailoverPolicy.next(initial, setOf("one","two","three"), pool, "FREE", 3))
        assertNull(FailoverPolicy.next(initial, setOf("one"), listOf(initial), "FREE", 3))
    }
}
