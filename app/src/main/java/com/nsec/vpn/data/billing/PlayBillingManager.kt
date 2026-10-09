package com.nsec.vpn.data.billing

import android.content.Context
import com.nsec.vpn.data.api.NsecVpnApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class SubscriptionPlan(
    val productId: String,
    val title: String,
    val priceFormatted: String,
    val billingPeriod: String,
    val discountBadge: String? = null
)

class PlayBillingManager(
    private val context: Context,
    private val api: NsecVpnApi
) {
    val plans = listOf(
        SubscriptionPlan(
            productId = "nsecvpn_premium_monthly",
            title = "Monthly VIP",
            priceFormatted = "$4.99 / month",
            billingPeriod = "Billed monthly"
        ),
        SubscriptionPlan(
            productId = "nsecvpn_premium_yearly",
            title = "Yearly VIP (Best Value)",
            priceFormatted = "$29.99 / year",
            billingPeriod = "Billed annually ($2.50/mo)",
            discountBadge = "SAVE 50%"
        )
    )

    private val _isPurchasing = MutableStateFlow(false)
    val isPurchasing: StateFlow<Boolean> = _isPurchasing.asStateFlow()

    suspend fun launchPurchase(
        productId: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        _isPurchasing.value = true
        try {
            val mockPurchaseToken = "play_token_${System.currentTimeMillis()}_${productId}"
            onSuccess()
        } catch (e: Exception) {
            onError(e.message ?: "Failed to complete purchase")
        } finally {
            _isPurchasing.value = false
        }
    }
}
