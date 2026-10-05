package com.example.myapplication

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object BillingManager {

    private const val PRODUCT_ID = "premium_lifetime"

    private lateinit var billingClient: BillingClient

    private var premiumProduct: ProductDetails? = null

    private val _isPremium =
        MutableStateFlow(false)

    val isPremium: StateFlow<Boolean> =
        _isPremium


    // =========================================================
    // INITIALIZE
    // =========================================================

    fun initialize(
        context: Context
    ) {

        if (::billingClient.isInitialized) {
            return
        }

        billingClient =
            BillingClient
                .newBuilder(context.applicationContext)
                .setListener { billingResult, purchases ->

                    if (
                        billingResult.responseCode ==
                        BillingClient.BillingResponseCode.OK
                    ) {

                        purchases?.forEach { purchase ->

                            handlePurchase(
                                purchase
                            )
                        }
                    }
                }

                // Billing Library 8+
                .enablePendingPurchases(
                    PendingPurchasesParams
                        .newBuilder()
                        .enableOneTimeProducts()
                        .build()
                )

                .build()

        connectBilling()
    }


    // =========================================================
    // CONNECT BILLING
    // =========================================================

    private fun connectBilling() {

        billingClient.startConnection(
            object : BillingClientStateListener {

                override fun onBillingSetupFinished(
                    billingResult: BillingResult
                ) {

                    if (
                        billingResult.responseCode ==
                        BillingClient.BillingResponseCode.OK
                    ) {

                        queryProduct()

                        queryExistingPurchases()
                    }
                }


                override fun onBillingServiceDisconnected() {

                    // Google Play Billing disconnected.
                    // Billing will be retried when initialize()
                    // is called again.
                }
            }
        )
    }


    // =========================================================
    // QUERY PRODUCT
    // =========================================================

    private fun queryProduct() {

        val product =
            QueryProductDetailsParams.Product
                .newBuilder()
                .setProductId(
                    PRODUCT_ID
                )
                .setProductType(
                    BillingClient.ProductType.INAPP
                )
                .build()


        val params =
            QueryProductDetailsParams
                .newBuilder()
                .setProductList(
                    listOf(product)
                )
                .build()


        billingClient.queryProductDetailsAsync(
            params
        ) { billingResult, productDetailsResult ->

            if (
                billingResult.responseCode ==
                BillingClient.BillingResponseCode.OK
            ) {

                premiumProduct =
                    productDetailsResult
                        .productDetailsList
                        .firstOrNull()
            }
        }
    }


    // =========================================================
    // LAUNCH PURCHASE
    // =========================================================

    fun launchPurchase(
        activity: Activity
    ) {

        if (!::billingClient.isInitialized) {
            return
        }

        val product =
            premiumProduct
                ?: return


        val productDetailsParams =
            BillingFlowParams
                .ProductDetailsParams
                .newBuilder()
                .setProductDetails(
                    product
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


        billingClient.launchBillingFlow(
            activity,
            billingFlowParams
        )
    }


    // =========================================================
    // HANDLE PURCHASE
    // =========================================================

    private fun handlePurchase(
        purchase: Purchase
    ) {

        // Only grant premium after successful purchase
        if (
            purchase.purchaseState ==
            Purchase.PurchaseState.PURCHASED
        ) {

            if (
                !purchase.isAcknowledged
            ) {

                val acknowledgeParams =
                    AcknowledgePurchaseParams
                        .newBuilder()
                        .setPurchaseToken(
                            purchase.purchaseToken
                        )
                        .build()


                billingClient.acknowledgePurchase(
                    acknowledgeParams
                ) { billingResult ->

                    if (
                        billingResult.responseCode ==
                        BillingClient.BillingResponseCode.OK
                    ) {

                        _isPremium.value =
                            true
                    }
                }

            } else {

                _isPremium.value =
                    true
            }
        }
    }


    // =========================================================
    // CHECK EXISTING PURCHASE
    // =========================================================

    private fun queryExistingPurchases() {

        val params =
            QueryPurchasesParams
                .newBuilder()
                .setProductType(
                    BillingClient.ProductType.INAPP
                )
                .build()


        billingClient.queryPurchasesAsync(
            params
        ) { billingResult, purchases ->

            if (
                billingResult.responseCode ==
                BillingClient.BillingResponseCode.OK
            ) {

                purchases.forEach { purchase ->

                    if (
                        purchase.products.contains(
                            PRODUCT_ID
                        )
                    ) {

                        handlePurchase(
                            purchase
                        )
                    }
                }
            }
        }
    }
}