package com.example.myapplication.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class StoreFailure(val code: Int) : Exception("Play Billing response $code")
internal data class LifetimeProduct(val details: ProductDetails, val token: String?, val price: String)

/** Google Play transport only; entitlement and presentation state belong to PremiumRepository. */
internal class PlayBillingStore(
    private val context: Context,
    private val productId: String,
    private val onPurchasesUpdated: (Int, List<Purchase>) -> Unit
) {
    private var client: BillingClient? = null
    private val connection = Mutex()

    private suspend fun connected(): BillingClient = connection.withLock {
        client?.takeIf { it.isReady }?.let { return@withLock it }
        val billing = client ?: BillingClient.newBuilder(context)
            .setListener { result, purchases -> onPurchasesUpdated(result.responseCode, purchases.orEmpty()) }
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .enableAutoServiceReconnection()
            .build().also { client = it }
        try {
            withTimeout(6_000) {
                suspendCancellableCoroutine<Unit> { continuation ->
                    billing.startConnection(object : BillingClientStateListener {
                        override fun onBillingSetupFinished(result: BillingResult) {
                            if (!continuation.isActive) return
                            if (result.responseCode == BillingClient.BillingResponseCode.OK) continuation.resume(Unit)
                            else continuation.resumeWithException(StoreFailure(result.responseCode))
                        }
                        override fun onBillingServiceDisconnected() = Unit
                    })
                }
            }
            billing
        } catch (error: Exception) {
            if (!billing.isReady) {
                billing.endConnection()
                if (client === billing) client = null
            }
            throw error
        }
    }

    suspend fun ownedPurchases(): List<Purchase> {
        val billing = connected()
        val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
        return withTimeout(6_000) {
            suspendCancellableCoroutine { continuation ->
                billing.queryPurchasesAsync(params) { result, purchases ->
                    if (!continuation.isActive) return@queryPurchasesAsync
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) continuation.resume(purchases)
                    else continuation.resumeWithException(StoreFailure(result.responseCode))
                }
            }
        }
    }

    suspend fun lifetimeProduct(): LifetimeProduct {
        val billing = connected()
        val requested = QueryProductDetailsParams.Product.newBuilder().setProductId(productId)
            .setProductType(BillingClient.ProductType.INAPP).build()
        val params = QueryProductDetailsParams.newBuilder().setProductList(listOf(requested)).build()
        val details: ProductDetails = withTimeout(6_000) {
            suspendCancellableCoroutine { continuation ->
                billing.queryProductDetailsAsync(params) { result, products ->
                    if (!continuation.isActive) return@queryProductDetailsAsync
                    val product = products.productDetailsList.firstOrNull { it.productId == productId }
                    when {
                        result.responseCode != BillingClient.BillingResponseCode.OK ->
                            continuation.resumeWithException(StoreFailure(result.responseCode))
                        product == null -> continuation.resumeWithException(StoreFailure(BillingClient.BillingResponseCode.ITEM_UNAVAILABLE))
                        else -> continuation.resume(product)
                    }
                }
            }
        }
        // This SKU grants lifetime access, so a rental option must never be selected.
        val offer = details.oneTimePurchaseOfferDetailsList.orEmpty().firstOrNull { it.rentalDetails == null }
            ?: details.oneTimePurchaseOfferDetails?.takeIf { it.rentalDetails == null }
            ?: throw StoreFailure(BillingClient.BillingResponseCode.ITEM_UNAVAILABLE)
        return LifetimeProduct(details, offer.offerToken?.takeIf { it.isNotBlank() }, offer.formattedPrice)
    }

    fun launch(activity: Activity, product: LifetimeProduct): Int {
        val billing = client?.takeIf { it.isReady }
            ?: throw StoreFailure(BillingClient.BillingResponseCode.SERVICE_DISCONNECTED)
        val details = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(product.details)
        product.token?.let(details::setOfferToken)
        return billing.launchBillingFlow(activity, BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(details.build())).build()).responseCode
    }

    suspend fun acknowledge(token: String) {
        val billing = connected()
        withTimeout(6_000) {
            suspendCancellableCoroutine<Unit> { continuation ->
                billing.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(token).build()) { result ->
                    if (!continuation.isActive) return@acknowledgePurchase
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) continuation.resume(Unit)
                    else continuation.resumeWithException(StoreFailure(result.responseCode))
                }
            }
        }
    }
}
