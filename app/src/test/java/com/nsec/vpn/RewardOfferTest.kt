package com.nsec.vpn

import com.nsec.vpn.ui.components.RewardOffer
import org.junit.Assert.*
import org.junit.Test

class RewardOfferTest {
    @Test fun configuredFortyFiveMinutesShowsBothBenefits() {
        val offer = RewardOffer.from(true, true, 45, "FREE", false)
        assertTrue(offer.available); assertEquals(45, offer.minutes)
        assertEquals("45 Minutes Of VIP", offer.title)
        assertTrue(offer.detail.contains("Ad-Free")); assertTrue(offer.detail.contains("VIP Servers"))
    }
    @Test fun displayedDurationFollowsBackendInsteadOfInventingFortyFive() {
        assertEquals("30 Minutes Of VIP", RewardOffer.from(true, true, 30, "FREE", false).title)
    }
    @Test fun unavailablePendingAndPaidStatesCannotOfferDuplicateRewards() {
        assertFalse(RewardOffer.from(false, true, 45, "FREE", false).available)
        assertFalse(RewardOffer.from(true, false, 45, "FREE", false).available)
        assertFalse(RewardOffer.from(true, true, 45, "VIP", false).available)
        assertFalse(RewardOffer.from(true, true, 45, "FREE", true).available)
    }
    @Test fun firstInstallIsLoadingWithoutAClaimedDurationAndFailureIsExplicit() {
        val loading = RewardOffer.from(false, false, 0, "FREE", false)
        assertTrue(loading.loading)
        assertNull(loading.minutes)
        assertEquals("", loading.title)
        val failed = RewardOffer.from(false, false, 0, "FREE", false, failed = true)
        assertFalse(failed.loading)
        assertFalse(failed.available)
        assertEquals("Reward Offer Unavailable", failed.title)
    }

}
