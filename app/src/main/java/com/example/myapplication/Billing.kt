package com.example.myapplication

import android.app.Activity
import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import android.widget.Toast
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.example.myapplication.data.DataStoreManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

/**
 * Ek hi instance poori app mein: Billing.getInstance(context)
 */
class Billing(context: Context) {

    companion object {
        @Volatile
        private var INSTANCE: Billing? = null

        fun getInstance(context: Context): Billing =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Billing(context.applicationContext).also {
                    INSTANCE = it
                }
            }
    }

    // Hamesha application context (activity leak nahi hoga)
    private val appContext: Context = context.applicationContext

    private val isDebug =
        (appContext.applicationInfo.flags and
                ApplicationInfo.FLAG_DEBUGGABLE) != 0

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val dataStoreManager = DataStoreManager(appContext)

    var billingClient: BillingClient? = null
        private set

    // Price jab tak load na ho, khali rahe
    var lifetimePrice: String = ""
        private set

    // Purchase kamyab hone par ek baar emit hota hai (UI band karne ke liye)
    private val _purchaseSuccess = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val purchaseSuccess: SharedFlow<Unit> = _purchaseSuccess

    private fun logD(msg: String) {
        if (isDebug) Log.d("BILLING", msg)
    }

    private fun logE(msg: String) {
        if (isDebug) Log.e("BILLING", msg)
    }

    private fun toast(msg: String) {
        Toast.makeText(appContext, msg, Toast.LENGTH_SHORT).show()
    }

    private fun lifetimeId(): String = appContext.getString(R.string.lifetime)

    // =========================================================
    // PURCHASE UPDATE LISTENER
    // =========================================================

    private val purchasesUpdatedListener =
        PurchasesUpdatedListener { billingResult, purchases ->
            when (billingResult.responseCode) {
                BillingClient.BillingResponseCode.OK -> {
                    purchases?.forEach { handlePurchase(it, showToast = true) }
                }

                BillingClient.BillingResponseCode.USER_CANCELED -> {
                    toast("Purchase cancelled")
                }

                BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                    // Pehle se khareed chuka hai, to restore kar dein
                    syncPurchases(showToast = true)
                }

                else -> {
                    logE(
                        "Purchase failed: ${billingResult.responseCode} - " +
                                billingResult.debugMessage
                    )
                    toast("Purchase failed")
                }
            }
        }

    // =========================================================
    // CREATE / CONNECT
    // =========================================================

    private fun createBillingClient() {
        if (billingClient != null) return

        val pendingPurchasesParams =
            PendingPurchasesParams
                .newBuilder()
                .enableOneTimeProducts()
                .build()

        billingClient =
            BillingClient
                .newBuilder(appContext)
                .setListener(purchasesUpdatedListener)
                .enablePendingPurchases(pendingPurchasesParams)
                .enableAutoServiceReconnection()
                .build()
    }

    private fun connectBilling(
        showError: Boolean = true,
        onConnected: () -> Unit
    ) {
        createBillingClient()

        val client = billingClient ?: return

        if (client.isReady) {
            onConnected()
            return
        }

        client.startConnection(
            object : BillingClientStateListener {

                override fun onBillingSetupFinished(billingResult: BillingResult) {
                    if (billingResult.responseCode ==
                        BillingClient.BillingResponseCode.OK
                    ) {
                        logD("Google Play Billing connected")
                        onConnected()
                    } else {
                        logE(
                            "Billing connection failed: " +
                                    "${billingResult.responseCode} - " +
                                    billingResult.debugMessage
                        )
                        if (showError) toast("Unable to connect to Google Play")
                    }
                }

                override fun onBillingServiceDisconnected() {
                    // enableAutoServiceReconnection() khud reconnect karega
                    logD("Billing service disconnected")
                }
            }
        )
    }

    // =========================================================
    // GET LIFETIME PRICE
    // =========================================================

    fun lifetimeprice(onPriceLoaded: ((String) -> Unit)? = null) {
        connectBilling {
            queryLifetimeProductDetails { productDetails ->
                val oneTimeDetails =
                    productDetails.oneTimePurchaseOfferDetails
                        ?: return@queryLifetimeProductDetails

                lifetimePrice = oneTimeDetails.formattedPrice
                logD("Lifetime price = $lifetimePrice")
                onPriceLoaded?.invoke(lifetimePrice)
            }
        }
    }

    // =========================================================
    // QUERY LIFETIME PRODUCT
    // =========================================================

    private fun queryLifetimeProductDetails(
        onProductFound: (ProductDetails) -> Unit
    ) {
        val client = billingClient

        if (client == null || !client.isReady) {
            toast("Billing is not ready")
            return
        }

        val product =
            QueryProductDetailsParams.Product
                .newBuilder()
                .setProductId(lifetimeId())
                .setProductType(BillingClient.ProductType.INAPP)
                .build()

        val queryParams =
            QueryProductDetailsParams
                .newBuilder()
                .setProductList(listOf(product))
                .build()

        client.queryProductDetailsAsync(queryParams) { billingResult, result ->
            if (billingResult.responseCode !=
                BillingClient.BillingResponseCode.OK
            ) {
                logE(
                    "Product query failed: ${billingResult.responseCode} - " +
                            billingResult.debugMessage
                )
                toast("Unable to load lifetime product")
                return@queryProductDetailsAsync
            }

            val productDetails = result.productDetailsList.firstOrNull()

            if (productDetails == null) {
                logE("Lifetime product not found")
                toast("Lifetime product not found")
                return@queryProductDetailsAsync
            }

            onProductFound(productDetails)
        }
    }

    // =========================================================
    // START LIFETIME PURCHASE
    // =========================================================

    fun initPurchaselifetime(activity: Activity) {
        connectBilling {
            queryLifetimeProductDetails { productDetails ->
                val client = billingClient

                if (client == null || !client.isReady) {
                    toast("Billing is not ready")
                    return@queryLifetimeProductDetails
                }

                val productDetailsParams =
                    BillingFlowParams.ProductDetailsParams
                        .newBuilder()
                        .setProductDetails(productDetails)
                        .build()

                val billingFlowParams =
                    BillingFlowParams
                        .newBuilder()
                        .setProductDetailsParamsList(listOf(productDetailsParams))
                        .build()

                val result = client.launchBillingFlow(activity, billingFlowParams)
                logD("Launch billing result: ${result.responseCode}")
            }
        }
    }

    // =========================================================
    // UNLOCK / LOCK PREMIUM
    // =========================================================

    private fun unlockPremium() {
        // Sirf pehli baar (isnotpro true -> false) kaam karega,
        // is liye screen do baar pop nahi hogi
        if (!AppClass.isnotpro) return

        AppClass.isnotpro = false
        _purchaseSuccess.tryEmit(Unit)

        scope.launch {
            try {
                dataStoreManager.savePremium(true)
                logD("Premium saved in DataStore")
            } catch (e: Exception) {
                logE("Failed to save premium: ${e.message}")
            }
        }
    }

    private fun lockPremium() {
        AppClass.isnotpro = true

        scope.launch {
            try {
                dataStoreManager.savePremium(false)
            } catch (e: Exception) {
                logE("Failed to clear premium: ${e.message}")
            }
        }
    }

    // =========================================================
    // HANDLE PURCHASE (naya purchase aur restore dono yahan se)
    // =========================================================

    private fun handlePurchase(purchase: Purchase, showToast: Boolean) {
        if (!purchase.products.contains(lifetimeId())) {
            logD("Unknown product: ${purchase.products}")
            return
        }

        when (purchase.purchaseState) {
            Purchase.PurchaseState.PURCHASED -> {
                unlockPremium()

                if (!purchase.isAcknowledged) {
                    acknowledgePurchase(purchase)
                } else if (showToast) {
                    toast("Lifetime Premium activated")
                }
            }

            Purchase.PurchaseState.PENDING -> {
                if (showToast) toast("Purchase is pending")
            }

            else -> logD("Purchase state unspecified")
        }
    }

    private fun acknowledgePurchase(purchase: Purchase) {
        val client = billingClient

        if (client == null || !client.isReady) {
            logE("Billing not ready for acknowledgement")
            return
        }

        val params =
            AcknowledgePurchaseParams
                .newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()

        client.acknowledgePurchase(params) { billingResult ->
            if (billingResult.responseCode ==
                BillingClient.BillingResponseCode.OK
            ) {
                logD("Purchase acknowledged")
            } else {
                logE(
                    "Acknowledge failed: ${billingResult.responseCode} - " +
                            billingResult.debugMessage
                )
            }
        }
    }

    // =========================================================
    // RESTORE (button se, Toast ke sath)
    // =========================================================

    fun restorePurchases(@Suppress("UNUSED_PARAMETER") activity: Activity) {
        syncPurchases(showToast = true)
    }

    // =========================================================
    // SYNC (app start par silent: lagao MainActivity/Application mein)
    // =========================================================

    fun syncPurchases(showToast: Boolean = false) {
        connectBilling(showError = showToast) {
            val client = billingClient

            if (client == null || !client.isReady) {
                if (showToast) toast("Billing is not ready")
                return@connectBilling
            }

            val params =
                QueryPurchasesParams
                    .newBuilder()
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()

            client.queryPurchasesAsync(params) { billingResult, purchases ->
                if (billingResult.responseCode !=
                    BillingClient.BillingResponseCode.OK
                ) {
                    logE(
                        "Sync query failed: ${billingResult.responseCode} - " +
                                billingResult.debugMessage
                    )
                    if (showToast) toast("Unable to restore purchase")
                    return@queryPurchasesAsync
                }

                val lifetime = purchases.firstOrNull {
                    it.products.contains(lifetimeId()) &&
                            it.purchaseState == Purchase.PurchaseState.PURCHASED
                }

                if (lifetime != null) {
                    handlePurchase(lifetime, showToast = false)
                    if (showToast) toast("Lifetime Premium restored successfully")
                } else {
                    // Google Play ne OK bola aur purchase nahi hai
                    // (refund ya kabhi kharida hi nahi)
                    if (!AppClass.isnotpro) lockPremium()
                    if (showToast) toast("No previous purchase found")
                }
            }
        }
    }

    // =========================================================
    // END CONNECTION (sirf zaroorat par)
    // =========================================================

    fun endConnection() {
        billingClient?.endConnection()
        billingClient = null
    }
}