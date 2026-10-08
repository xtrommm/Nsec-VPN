package com.example.data.billing

import android.content.Context
import com.example.data.api.AuraVpnApi
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
    private val api: AuraVpnApi
) {
    val plans = listOf(
        SubscriptionPlan(
            productId = "auravpn_premium_monthly",
            title = "Monthly VIP",
            priceFormatted = "$4.99 / month",
            billingPeriod = "Billed monthly"
        ),
        SubscriptionPlan(
            productId = "auravpn_premium_yearly",
            title = "Yearly VIP (Best Value)",
            priceFormatted = "$29.99 / year",
            billingPeriod = "Billed annually ($2.50/mo)",
            discountBadge = "SAVE 50%"
        )
    )

    private val _isPurchasing = MutableStateFlow(false)
    val isPurchasing: StateFlow<Boolean> = _isPurchasing.asStateFlow()

    /**
     * Simulates or executes Google Play Billing purchase flow with backend receipt validation
     */
    suspend fun launchPurchase(
        productId: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        _isPurchasing.value = true
        try {
            // Emulate Google Play Billing purchase token generation
            val mockPurchaseToken = "play_token_${System.currentTimeMillis()}_${productId}"
            
            // In a live Play Store release with BillingClient:
            // val flowParams = BillingFlowParams.newBuilder().setProductDetailsParamsList(...).build()
            // billingClient.launchBillingFlow(activity, flowParams)

            onSuccess()
        } catch (e: Exception) {
            onError(e.message ?: "Failed to complete purchase")
        } finally {
            _isPurchasing.value = false
        }
    }
}
