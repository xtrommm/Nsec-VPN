package com.nsec.vpn.data.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import com.nsec.vpn.core.network.ServiceLocator
import com.nsec.vpn.data.api.NsecVpnApi
import com.nsec.vpn.data.api.VerifyPurchaseRequest
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class PlayBillingManager(private val context: Context, private val api: NsecVpnApi) {
    private val _isPurchasing = MutableStateFlow(false)
    val isPurchasing = _isPurchasing.asStateFlow()

    /** Query owned subscriptions only: this path never launches a purchase/charge UI. */
    suspend fun restorePurchases() = withContext(Dispatchers.Main) {
        check(!_isPurchasing.value) { "Another Purchase Operation Is In Progress." }
        _isPurchasing.value = true
        var client: BillingClient? = null
        try {
            val billing = BillingClient.newBuilder(context).setListener { _, _ -> }
                .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()).build()
            client = billing
            withTimeout(15_000) {
                suspendCancellableCoroutine<Unit> { continuation ->
                    billing.startConnection(object : BillingClientStateListener {
                        override fun onBillingSetupFinished(result: BillingResult) {
                            if (!continuation.isActive) return
                            if (result.responseCode == BillingClient.BillingResponseCode.OK) continuation.resume(Unit)
                            else continuation.resumeWithException(IllegalStateException("Google Play Billing Is Unavailable."))
                        }
                        override fun onBillingServiceDisconnected() {
                            if (continuation.isActive) continuation.resumeWithException(IllegalStateException("Google Play Billing Disconnected."))
                        }
                    })
                }
            }
            val purchases = withTimeout(15_000) {
                suspendCancellableCoroutine<List<Purchase>> { continuation ->
                    billing.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()) { result, items ->
                        if (!continuation.isActive) return@queryPurchasesAsync
                        if (result.responseCode == BillingClient.BillingResponseCode.OK) continuation.resume(items)
                        else continuation.resumeWithException(IllegalStateException("Could Not Read Existing Purchases."))
                    }
                }
            }
            val token = ServiceLocator.getInstance(context).keyStoreManager.getAccessToken()
                ?: error("Restore Your Device Session Before Restoring Purchases.")
            val receipts = purchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                .flatMap { purchase -> purchase.products.map { product -> OwnedReceipt(product, purchase.purchaseToken) } }
            withTimeout(45_000) {
                restoreOwnedReceipts(receipts) { receipt ->
                    val response = withContext(Dispatchers.IO) {
                        api.verifyPurchase("Bearer $token", VerifyPurchaseRequest(context.packageName, receipt.productId, receipt.token))
                    }
                    if (response.code() == 409) error("This Purchase Belongs To Another Device ID. Use Device VIP Recovery First.")
                    if (!response.isSuccessful || response.body()?.success != true) error("The Server Could Not Verify Your Purchase. Please Retry.")
                }
            }
        } finally { try { client?.endConnection() } finally { _isPurchasing.value = false } }
    }
    suspend fun launchPurchase(activity: Activity, productId: String, onSuccess: () -> Unit, onError: (String) -> Unit) = withContext(Dispatchers.Main) {
        if (_isPurchasing.value) return@withContext
        _isPurchasing.value = true
        val purchaseResult = CompletableDeferred<List<Purchase>>()
        var closingClient: BillingClient? = null
        try {
        val client = BillingClient.newBuilder(context).setListener { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) purchaseResult.complete(purchases)
            else purchaseResult.completeExceptionally(IllegalStateException("Purchase was cancelled or could not be completed"))
        }.enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()).build()
        closingClient = client
            withTimeout(15000) {
                suspendCancellableCoroutine<Unit> { continuation ->
                    client.startConnection(object : BillingClientStateListener {
                        override fun onBillingSetupFinished(result: BillingResult) {
                            if (!continuation.isActive) return
                            if (result.responseCode == BillingClient.BillingResponseCode.OK) continuation.resume(Unit)
                            else continuation.resumeWithException(IllegalStateException("Google Play billing is unavailable"))
                        }
                        override fun onBillingServiceDisconnected() {
                            if (continuation.isActive) continuation.resumeWithException(IllegalStateException("Google Play billing disconnected"))
                        }
                    })
                }
            }
            val existing = withTimeout(15000) { suspendCancellableCoroutine<List<Purchase>> { continuation ->
                client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()) { result, purchases ->
                    if (!continuation.isActive) return@queryPurchasesAsync
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) continuation.resume(purchases)
                    else continuation.resumeWithException(IllegalStateException("Couldn't check existing purchases"))
                }
            }
            }
            val purchases = if (existing.any { productId in it.products && it.purchaseState == Purchase.PurchaseState.PURCHASED }) existing else {
                val details = withTimeout(15000) { suspendCancellableCoroutine<ProductDetails> { continuation ->
                    val params = QueryProductDetailsParams.newBuilder().setProductList(listOf(
                        QueryProductDetailsParams.Product.newBuilder().setProductId(productId).setProductType(BillingClient.ProductType.SUBS).build())).build()
                    client.queryProductDetailsAsync(params) { result, products ->
                        if (!continuation.isActive) return@queryProductDetailsAsync
                        val product = products.productDetailsList.firstOrNull { it.productId == productId }
                        if (result.responseCode == BillingClient.BillingResponseCode.OK && product != null) continuation.resume(product)
                        else continuation.resumeWithException(IllegalStateException("Subscription is not available in Google Play"))
                    }
                } }
                val offer = details.subscriptionOfferDetails?.filter { it.offerId == null }?.singleOrNull()
                    ?: throw IllegalStateException("Configure one base plan for this subscription in Play Console")
                val params = BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(details).setOfferToken(offer.offerToken).build())).build()
                val result = client.launchBillingFlow(activity, params)
                if (result.responseCode != BillingClient.BillingResponseCode.OK) throw IllegalStateException("Couldn't launch Google Play purchase")
                withTimeout(300000) { purchaseResult.await() }
            }
            val purchase = purchases.firstOrNull { productId in it.products && it.purchaseState == Purchase.PurchaseState.PURCHASED }
                ?: throw IllegalStateException("Purchase is pending; premium stays locked until payment is confirmed")
            val token = ServiceLocator.getInstance(context).keyStoreManager.getAccessToken()
                ?: throw IllegalStateException("Sign in to the device session before verifying your purchase")
            val verified = withContext(Dispatchers.IO) { api.verifyPurchase("Bearer $token", VerifyPurchaseRequest(context.packageName, productId, purchase.purchaseToken)) }
            if (!verified.isSuccessful || verified.body()?.success != true) throw IllegalStateException("Backend could not verify the purchase; entitlement was not granted")
            onSuccess()
        } catch (_: TimeoutCancellationException) { onError("Purchase Verification Timed Out. Please Retry.") }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { onError(error.message ?: "Purchase verification failed") }
        finally { try { closingClient?.endConnection() } finally { _isPurchasing.value = false } }
    }
}

data class OwnedReceipt(val productId: String, val token: String) {
    override fun toString(): String = "OwnedReceipt(productId=$productId, token=[REDACTED])"
}
suspend fun restoreOwnedReceipts(receipts: List<OwnedReceipt>, verify: suspend (OwnedReceipt) -> Unit) {
    check(receipts.isNotEmpty()) { "No Purchased Subscriptions Were Found In This Google Play Account." }
    for (receipt in receipts.distinct()) verify(receipt)
}
