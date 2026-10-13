package com.nsec.vpn.data.monetization

import android.os.Bundle
import com.google.ads.mediation.admob.AdMobAdapter
import com.google.android.gms.ads.AdRequest

/** No inferred consent or personalized-ad authorization is sent by this app. */
object AdRequests {
    fun create(): AdRequest = AdRequest.Builder()
        .addNetworkExtrasBundle(AdMobAdapter::class.java, Bundle().apply { putString("npa", "1") })
        .build()
}
