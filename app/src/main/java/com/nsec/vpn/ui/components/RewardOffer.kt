package com.nsec.vpn.ui.components

/** The offer comes from verified backend config, never a hardcoded marketing duration. */
data class RewardOffer(val minutes: Int?, val available: Boolean, val title: String, val detail: String, val loading: Boolean = false) {
    companion object {
        fun from(loaded: Boolean, enabled: Boolean, minutes: Int, tier: String, pending: Boolean, failed: Boolean = false, pendingDetail: String? = null): RewardOffer = when {
            tier != "FREE" -> RewardOffer(null, false, "Your VIP Is Active", "Ad-Free Access And VIP Servers Are Unlocked.")
            pending -> RewardOffer(null, false, if (pendingDetail == null) "Confirming Your Reward" else "Reward Verification Pending", pendingDetail ?: "Your Ad Is Complete. Your VIP Pass Activates When Verification Succeeds.")
            !loaded && failed -> RewardOffer(null, false, "Reward Offer Unavailable", "Check Your Connection. VIP Options Remain Available.")
            !loaded || minutes !in 1..1440 -> RewardOffer(null, false, "", "", loading = true)
            !enabled -> RewardOffer(null, false, "VIP, Your Way", "Rewarded Ads Are Unavailable Right Now. Paid Plans Are Still Available.")
            else -> RewardOffer(minutes, true, "$minutes Minutes Of VIP", "Watch A Rewarded Ad To Unlock Ad-Free Access And VIP Servers.")
        }
    }
}
