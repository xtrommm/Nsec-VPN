package com.nsec.vpn.ui.components

/** The offer comes from verified backend config, never a hardcoded marketing duration. */
data class RewardOffer(val minutes: Int?, val available: Boolean, val title: String, val detail: String) {
    companion object {
        fun from(loaded: Boolean, enabled: Boolean, minutes: Int, tier: String, pending: Boolean): RewardOffer = when {
            tier != "FREE" -> RewardOffer(null, false, "Your VIP Is Active", "Ad-Free Access And VIP Servers Are Unlocked.")
            pending -> RewardOffer(null, false, "Reward Completed", "Ad-Free Access Is Active. Your VIP Server Access Is Being Activated.")
            !loaded || minutes !in 1..1440 -> RewardOffer(null, false, "Your Next VIP Pass", "Loading The Current Reward Offer…")
            !enabled -> RewardOffer(null, false, "VIP, Your Way", "Rewarded Ads Are Unavailable Right Now. Paid Plans Are Still Available.")
            else -> RewardOffer(minutes, true, "$minutes Minutes Of VIP", "Watch A Rewarded Ad To Unlock Ad-Free Access And VIP Servers.")
        }
    }
}
