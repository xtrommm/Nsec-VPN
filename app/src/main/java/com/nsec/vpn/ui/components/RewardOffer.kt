package com.nsec.vpn.ui.components

/** The offer comes from verified backend config, never a hardcoded marketing duration. */
data class RewardOffer(val minutes: Int?, val available: Boolean, val title: String, val detail: String) {
    companion object {
        fun from(loaded: Boolean, enabled: Boolean, minutes: Int, tier: String, pending: Boolean): RewardOffer = when {
            tier != "FREE" -> RewardOffer(null, false, "Your VIP is active", "Ad-free access and VIP servers are unlocked.")
            pending -> RewardOffer(null, false, "Reward completed", "Ad-free access is active. Your VIP server access is being activated.")
            !loaded || minutes !in 1..1440 -> RewardOffer(null, false, "Your next VIP pass", "Loading the current reward offer…")
            !enabled -> RewardOffer(null, false, "VIP, your way", "Rewarded ads are unavailable right now. Paid plans are still available.")
            else -> RewardOffer(minutes, true, "$minutes minutes of VIP", "Watch a rewarded ad to unlock ad-free access and VIP servers.")
        }
    }
}
