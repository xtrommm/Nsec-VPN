package com.nsec.vpn.data.monetization

import android.app.Activity
import android.content.Context
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** UMP is authoritative; no locally cached boolean can authorize ad requests. */
class AdConsentManager(context: Context, private val ads: AdMobManager) {
    private val information = UserMessagingPlatform.getConsentInformation(context.applicationContext)
    private val _privacyOptionsRequired = MutableStateFlow(false)
    val privacyOptionsRequired = _privacyOptionsRequired.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private var requesting = false

    fun request(activity: Activity) {
        if (requesting || activity.isFinishing || activity.isDestroyed) return
        requesting = true
        _error.value = null
        try {
            information.requestConsentInfoUpdate(activity, ConsentRequestParameters.Builder().build(), {
                updatePermission()
                if (activity.isFinishing || activity.isDestroyed) { requesting = false; return@requestConsentInfoUpdate }
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { error ->
                    requesting = false
                    if (error != null) _error.value = "Privacy Choices Could Not Be Loaded. Try Again In Settings."
                    updatePermission()
                    preload(activity)
                }
            }, {
                requesting = false
                _error.value = "Privacy Choices Could Not Be Updated. Ads Remain Subject To Your Existing Consent."
                updatePermission()
                preload(activity)
            })
            // UMP may authorize requests using consent from the previous launch.
            updatePermission()
            preload(activity)
        } catch (_: Exception) {
            requesting = false
            ads.applyConsent(false)
            _error.value = "Privacy Choices Are Unavailable. VPN Access Is Unaffected."
        }
    }

    fun showPrivacyOptions(activity: Activity) {
        if (!_privacyOptionsRequired.value) { request(activity); return }
        ads.applyConsent(false) // Hide/destroy existing ads while choices are changing.
        try {
            UserMessagingPlatform.showPrivacyOptionsForm(activity) { error ->
                _error.value = if (error == null) null else "Privacy Choices Could Not Be Saved. Please Retry."
                updatePermission()
                preload(activity)
            }
        } catch (_: Exception) {
            _error.value = "Privacy Choices Are Unavailable. Please Retry."
        }
    }

    private fun updatePermission() {
        _privacyOptionsRequired.value = information.privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
        ads.applyConsent(information.canRequestAds())
    }
    private fun preload(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) return
        ads.preloadInterstitial(activity)
        ads.preloadRewarded(activity)
    }
}
