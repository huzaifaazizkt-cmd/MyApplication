package com.example.myapplication.billing

data class PremiumState(
    val owned: Boolean = false,
    val loaded: Boolean = false,
    val checking: Boolean = false
) {
    val allowsAds: Boolean get() = loaded && !checking && !owned
}

enum class BillingAction { PURCHASE, RESTORE }
enum class BillingMessage { UNAVAILABLE, PRODUCT_UNAVAILABLE, PURCHASE_FAILED, CANCELLED, PENDING, NOTHING_TO_RESTORE, RESTORE_FAILED, SAVE_FAILED }
data class BillingSuccess(val id: Long, val paywallId: String, val restored: Boolean)
data class BillingUiState(
    val loading: Boolean = true,
    val price: String? = null,
    val action: BillingAction? = null,
    val pending: Boolean = false,
    val message: BillingMessage? = null,
    val success: BillingSuccess? = null
) {
    val busy: Boolean get() = action != null
    val canPurchase: Boolean get() = !loading && !busy && !pending && price != null
}

/** Only a successful store response can change ownership; outages preserve the cached grant. */
internal fun reconcilePremium(cached: Boolean, purchased: Boolean?): Boolean =
    purchased ?: cached
