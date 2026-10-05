package com.example.myapplication

import android.app.Activity
import android.content.Context
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
import kotlinx.coroutines.launch

class Billing(
    private val context: Context
) {

    private var activity: Activity? = null

    var billingClient: BillingClient? = null
        private set

    private val dataStoreManager =
        DataStoreManager(context.applicationContext)

    // Current lifetime price
    var lifetimePrice: String = "$6.99"
        private set

    // =========================================================
    // PURCHASE UPDATE LISTENER
    // =========================================================

    private val purchasesUpdatedListener =
        PurchasesUpdatedListener { billingResult, purchases ->

            when (billingResult.responseCode) {

                BillingClient.BillingResponseCode.OK -> {

                    if (!purchases.isNullOrEmpty()) {

                        purchases.forEach { purchase ->
                            handlePurchase(purchase)
                        }

                    } else {

                        Log.d(
                            "BILLING",
                            "Purchase result OK but purchase list is empty"
                        )
                    }
                }

                BillingClient.BillingResponseCode.USER_CANCELED -> {

                    Toast.makeText(
                        context,
                        "Purchase cancelled",
                        Toast.LENGTH_SHORT
                    ).show()

                    Log.d(
                        "BILLING",
                        "User cancelled purchase"
                    )
                }

                else -> {

                    Log.e(
                        "BILLING",
                        "Purchase failed: " +
                                "${billingResult.responseCode} - " +
                                billingResult.debugMessage
                    )

                    Toast.makeText(
                        context,
                        "Purchase failed",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

    // =========================================================
    // CREATE BILLING CLIENT
    // =========================================================

    private fun createBillingClient() {

        if (billingClient != null) {
            return
        }

        val pendingPurchasesParams =
            PendingPurchasesParams
                .newBuilder()
                .enableOneTimeProducts()
                .build()

        billingClient =
            BillingClient
                .newBuilder(context)
                .setListener(purchasesUpdatedListener)
                .enablePendingPurchases(
                    pendingPurchasesParams
                )
                .enableAutoServiceReconnection()
                .build()
    }

    // =========================================================
    // CONNECT BILLING
    // =========================================================

    private fun connectBilling(
        onConnected: () -> Unit
    ) {

        createBillingClient()

        val client = billingClient
            ?: return

        if (client.isReady) {
            onConnected()
            return
        }

        client.startConnection(
            object : BillingClientStateListener {

                override fun onBillingSetupFinished(
                    billingResult: BillingResult
                ) {

                    if (
                        billingResult.responseCode ==
                        BillingClient.BillingResponseCode.OK
                    ) {

                        Log.d(
                            "BILLING",
                            "Google Play Billing connected"
                        )

                        onConnected()

                    } else {

                        Log.e(
                            "BILLING",
                            "Billing connection failed: " +
                                    "${billingResult.responseCode} - " +
                                    billingResult.debugMessage
                        )

                        Toast.makeText(
                            context,
                            "Unable to connect to Google Play",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                override fun onBillingServiceDisconnected() {

                    Log.w(
                        "BILLING",
                        "Billing service disconnected"
                    )
                }
            }
        )
    }

    // =========================================================
    // GET LIFETIME PRICE
    // =========================================================

    fun lifetimeprice(
        onPriceLoaded: ((String) -> Unit)? = null
    ) {

        connectBilling {

            queryLifetimeProductDetails { productDetails ->

                val oneTimeDetails =
                    productDetails.oneTimePurchaseOfferDetails
                        ?: return@queryLifetimeProductDetails

                lifetimePrice =
                    oneTimeDetails.formattedPrice

                Log.d(
                    "BILLING",
                    "Lifetime price = $lifetimePrice"
                )

                onPriceLoaded?.invoke(
                    lifetimePrice
                )
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

        if (
            client == null ||
            !client.isReady
        ) {

            Toast.makeText(
                context,
                "Billing is not ready",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        val productId =
            context.getString(
                R.string.lifetime
            )

        val product =
            QueryProductDetailsParams.Product
                .newBuilder()
                .setProductId(productId)
                .setProductType(
                    BillingClient.ProductType.INAPP
                )
                .build()

        val queryParams =
            QueryProductDetailsParams
                .newBuilder()
                .setProductList(
                    listOf(product)
                )
                .build()

        client.queryProductDetailsAsync(
            queryParams
        ) { billingResult, result ->

            if (
                billingResult.responseCode !=
                BillingClient.BillingResponseCode.OK
            ) {

                Log.e(
                    "BILLING",
                    "Product query failed: " +
                            "${billingResult.responseCode} - " +
                            billingResult.debugMessage
                )

                Toast.makeText(
                    context,
                    "Unable to load lifetime product",
                    Toast.LENGTH_SHORT
                ).show()

                return@queryProductDetailsAsync
            }

            val productDetails =
                result.productDetailsList.firstOrNull()

            if (productDetails == null) {

                Log.e(
                    "BILLING",
                    "Lifetime product not found"
                )

                Toast.makeText(
                    context,
                    "Lifetime product not found",
                    Toast.LENGTH_SHORT
                ).show()

                return@queryProductDetailsAsync
            }

            Log.d(
                "BILLING",
                "Lifetime product found: " +
                        productDetails.productId
            )

            onProductFound(
                productDetails
            )
        }
    }

    // =========================================================
    // START LIFETIME PURCHASE
    // =========================================================

    fun initPurchaselifetime(
        activity: Activity
    ) {

        this.activity = activity

        connectBilling {

            queryPurchaselifetime()
        }
    }

    // =========================================================
    // LAUNCH LIFETIME PURCHASE
    // =========================================================

    private fun queryPurchaselifetime() {

        val currentActivity =
            activity

        if (currentActivity == null) {

            Log.e(
                "BILLING",
                "Activity is null"
            )

            return
        }

        queryLifetimeProductDetails { productDetails ->

            val client =
                billingClient

            if (
                client == null ||
                !client.isReady
            ) {

                Toast.makeText(
                    context,
                    "Billing is not ready",
                    Toast.LENGTH_SHORT
                ).show()

                return@queryLifetimeProductDetails
            }

            val productDetailsParams =
                BillingFlowParams.ProductDetailsParams
                    .newBuilder()
                    .setProductDetails(
                        productDetails
                    )
                    .build()

            val billingFlowParams =
                BillingFlowParams
                    .newBuilder()
                    .setProductDetailsParamsList(
                        listOf(
                            productDetailsParams
                        )
                    )
                    .build()

            val result =
                client.launchBillingFlow(
                    currentActivity,
                    billingFlowParams
                )

            Log.d(
                "BILLING",
                "Launch billing result: " +
                        result.responseCode
            )
        }
    }

    // =========================================================
    // UNLOCK PREMIUM
    // =========================================================

    private fun unlockPremium() {

        // In-memory
        AppClass.isnotpro = false

        // Permanent
        CoroutineScope(
            Dispatchers.IO
        ).launch {

            try {

                dataStoreManager.savePremium(
                    true
                )

                Log.d(
                    "BILLING",
                    "Premium saved in DataStore"
                )

            } catch (e: Exception) {

                Log.e(
                    "BILLING",
                    "Failed to save premium",
                    e
                )
            }
        }
    }

    // =========================================================
    // HANDLE PURCHASE
    // =========================================================

    private fun handlePurchase(
        purchase: Purchase
    ) {

        val lifetimeProductId =
            context.getString(
                R.string.lifetime
            )

        Log.d(
            "BILLING",
            "Purchase received: ${purchase.products}"
        )

        if (
            !purchase.products.contains(
                lifetimeProductId
            )
        ) {

            Log.w(
                "BILLING",
                "Unknown product: ${purchase.products}"
            )

            return
        }

        when (
            purchase.purchaseState
        ) {

            Purchase.PurchaseState.PURCHASED -> {

                unlockPremium()

                if (
                    !purchase.isAcknowledged
                ) {

                    acknowledgePurchase(
                        purchase
                    )

                } else {

                    Toast.makeText(
                        context,
                        "Lifetime Premium activated",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            Purchase.PurchaseState.PENDING -> {

                Toast.makeText(
                    context,
                    "Purchase is pending",
                    Toast.LENGTH_SHORT
                ).show()

                Log.d(
                    "BILLING",
                    "Lifetime purchase pending"
                )
            }

            Purchase.PurchaseState.UNSPECIFIED_STATE -> {

                Log.w(
                    "BILLING",
                    "Purchase state unspecified"
                )
            }
        }
    }

    // =========================================================
    // ACKNOWLEDGE PURCHASE
    // =========================================================

    private fun acknowledgePurchase(
        purchase: Purchase
    ) {

        val client =
            billingClient

        if (
            client == null ||
            !client.isReady
        ) {

            Log.e(
                "BILLING",
                "Billing not ready for acknowledgement"
            )

            return
        }

        val params =
            AcknowledgePurchaseParams
                .newBuilder()
                .setPurchaseToken(
                    purchase.purchaseToken
                )
                .build()

        client.acknowledgePurchase(
            params
        ) { billingResult ->

            if (
                billingResult.responseCode ==
                BillingClient.BillingResponseCode.OK
            ) {

                unlockPremium()

                Log.d(
                    "BILLING",
                    "Lifetime purchase acknowledged"
                )

            } else {

                Log.e(
                    "BILLING",
                    "Acknowledge failed: " +
                            "${billingResult.responseCode} - " +
                            billingResult.debugMessage
                )
            }
        }
    }

    // =========================================================
    // RESTORE PURCHASE
    // =========================================================

    fun restorePurchases(
        activity: Activity
    ) {

        this.activity = activity

        connectBilling {

            queryLifetimeForRestore()
        }
    }

    // =========================================================
    // QUERY PURCHASES
    // =========================================================

    private fun queryLifetimeForRestore() {

        val client =
            billingClient

        if (
            client == null ||
            !client.isReady
        ) {

            Toast.makeText(
                context,
                "Billing is not ready",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        val params =
            QueryPurchasesParams
                .newBuilder()
                .setProductType(
                    BillingClient.ProductType.INAPP
                )
                .build()

        client.queryPurchasesAsync(
            params
        ) { billingResult, purchases ->

            if (
                billingResult.responseCode !=
                BillingClient.BillingResponseCode.OK
            ) {

                Log.e(
                    "BILLING_RESTORE",
                    "Restore query failed: " +
                            "${billingResult.responseCode} - " +
                            billingResult.debugMessage
                )

                Toast.makeText(
                    context,
                    "Unable to restore purchase",
                    Toast.LENGTH_SHORT
                ).show()

                return@queryPurchasesAsync
            }

            val lifetimeProductId =
                context.getString(
                    R.string.lifetime
                )

            var lifetimeFound = false

            for (purchase in purchases) {

                Log.d(
                    "BILLING_RESTORE",
                    "Purchase=${purchase.products}, " +
                            "state=${purchase.purchaseState}, " +
                            "acknowledged=${purchase.isAcknowledged}"
                )

                if (
                    purchase.products.contains(
                        lifetimeProductId
                    ) &&
                    purchase.purchaseState ==
                    Purchase.PurchaseState.PURCHASED
                ) {

                    lifetimeFound = true

                    unlockPremium()

                    if (
                        !purchase.isAcknowledged
                    ) {

                        acknowledgeRestoredPurchase(
                            purchase
                        )
                    }
                }
            }

            if (lifetimeFound) {

                Toast.makeText(
                    context,
                    "Lifetime Premium restored successfully",
                    Toast.LENGTH_SHORT
                ).show()

                Log.d(
                    "BILLING_RESTORE",
                    "Lifetime restore SUCCESS"
                )

            } else {

                Toast.makeText(
                    context,
                    "No previous purchase found",
                    Toast.LENGTH_LONG
                ).show()

                Log.d(
                    "BILLING_RESTORE",
                    "No lifetime purchase found"
                )
            }
        }
    }

    // =========================================================
    // ACKNOWLEDGE RESTORED PURCHASE
    // =========================================================

    private fun acknowledgeRestoredPurchase(
        purchase: Purchase
    ) {

        val client =
            billingClient

        if (
            client == null ||
            !client.isReady
        ) {
            return
        }

        val params =
            AcknowledgePurchaseParams
                .newBuilder()
                .setPurchaseToken(
                    purchase.purchaseToken
                )
                .build()

        client.acknowledgePurchase(
            params
        ) { billingResult ->

            if (
                billingResult.responseCode ==
                BillingClient.BillingResponseCode.OK
            ) {

                unlockPremium()

                Log.d(
                    "BILLING_RESTORE",
                    "Restored purchase acknowledged"
                )

            } else {

                Log.e(
                    "BILLING_RESTORE",
                    "Restore acknowledgement failed: " +
                            "${billingResult.responseCode} - " +
                            billingResult.debugMessage
                )
            }
        }
    }

    // =========================================================
    // END CONNECTION
    // =========================================================

    fun endConnection() {

        billingClient?.endConnection()

        billingClient = null
    }
}