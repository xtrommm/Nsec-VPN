package com.nsec.vpn

import com.nsec.vpn.ui.components.RewardOffer
import org.junit.Assert.*
import org.junit.Test

class RewardOfferTest {
    @Test fun configuredFortyFiveMinutesShowsBothBenefits() {
        val offer = RewardOffer.from(true, true, 45, "FREE", false)
        assertTrue(offer.available); assertEquals(45, offer.minutes)
        assertEquals("45 minutes of VIP", offer.title)
        assertTrue(offer.detail.contains("ad-free")); assertTrue(offer.detail.contains("VIP servers"))
    }
    @Test fun displayedDurationFollowsBackendInsteadOfInventingFortyFive() {
        assertEquals("30 minutes of VIP", RewardOffer.from(true, true, 30, "FREE", false).title)
    }
    @Test fun unavailablePendingAndPaidStatesCannotOfferDuplicateRewards() {
        assertFalse(RewardOffer.from(false, true, 45, "FREE", false).available)
        assertFalse(RewardOffer.from(true, false, 45, "FREE", false).available)
        assertFalse(RewardOffer.from(true, true, 45, "VIP", false).available)
        assertFalse(RewardOffer.from(true, true, 45, "FREE", true).available)
    }
}
