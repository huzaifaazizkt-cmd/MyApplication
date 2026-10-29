package com.example.myapplication.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.Purchase
import com.example.myapplication.R
import com.example.myapplication.data.DataStoreManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One process-wide owner. Paywall disposal never disconnects a purchase in progress. */
class PremiumRepository private constructor(context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val dataStore = DataStoreManager(context)
    private val productId = context.getString(R.string.lifetime)
    private val store = PlayBillingStore(context, productId) { code, _ ->
        scope.launch { onStoreResult(code) }
    }
    private val entitlement = MutableStateFlow(PremiumState())
    val state = entitlement.asStateFlow()
    private val presentation = MutableStateFlow(BillingUiState())
    val ui = presentation.asStateFlow()
    private val cached = CompletableDeferred<Unit>()
    private val ownership = Mutex()
    private val acknowledging = mutableSetOf<String>()
    private var refresh: Job? = null
    private var paywallLoad: Job? = null
    private var recovery: Job? = null
    private var activePaywall: String? = null
    private var request: Request? = null
    private var serial = 0L
    private var loadSerial = 0L
    private var confirmedOwnership: Boolean? = null
    private data class Request(val id: Long, val paywallId: String, val action: BillingAction, var pending: Boolean = false)
    private data class Ownership(val owned: Boolean, val pending: Boolean)

    init {
        scope.launch {
            dataStore.getPremium().retryWhen { error, _ ->
                Log.w(TAG, "Premium cache unavailable", error)
                delay(1_000)
                true
            }.collect { owned ->
                entitlement.value = entitlement.value.copy(owned = confirmedOwnership ?: owned, loaded = true)
                if (!cached.isCompleted) cached.complete(Unit)
            }
        }
    }

    fun onForeground(initialized: Boolean) {
        if (!initialized && request == null) return
        val check = refreshOwnership()
        recovery?.cancel()
        recovery = scope.launch {
            check.join()
            val checkout = request ?: return@launch
            if (checkout.action != BillingAction.PURCHASE || checkout.pending) return@launch
            // Covers a checkout dismissed without a listener callback, including process/UI races.
            delay(1_500)
            if (request !== checkout) return@launch
            complete(checkout, restore = false)
        }
    }

    fun refreshOwnership(): Job {
        refresh?.takeIf { it.isActive }?.let { return it }
        entitlement.value = entitlement.value.copy(checking = true)
        return scope.launch {
            try {
                withTimeout(8_000) {
                    val result = synchronizeOwnership()
                    request?.let { reconcileRequest(it, result, final = false) }
                }
            } catch (error: Exception) {
                if (error is CancellationException && error !is TimeoutCancellationException) throw error
                // A store outage is not evidence that an existing lifetime purchase was revoked.
                Log.w(TAG, "Ownership check unavailable; retaining cached access")
            } finally {
                entitlement.value = entitlement.value.copy(checking = false)
            }
        }.also { refresh = it }
    }

    fun openPaywall(id: String) {
        activePaywall = id
        if (presentation.value.busy) return
        val generation = ++loadSerial
        paywallLoad?.cancel()
        presentation.value = presentation.value.copy(loading = true, price = null, message = null,
            success = presentation.value.success?.takeIf { it.paywallId == id })
        paywallLoad = scope.launch {
            try {
                withTimeout(10_000) {
                    synchronizeOwnership()
                    if (!state.value.owned) {
                        val price = store.lifetimeProduct().price
                        if (activePaywall == id && generation == loadSerial) presentation.value = presentation.value.copy(price = price)
                    }
                }
            } catch (error: Exception) {
                if (error is CancellationException && error !is TimeoutCancellationException) throw error
                if (activePaywall == id && generation == loadSerial) presentation.value = presentation.value.copy(message = when {
                    error is PremiumSaveFailure -> BillingMessage.SAVE_FAILED
                    error is StoreFailure && error.code == BillingResponseCode.ITEM_UNAVAILABLE -> BillingMessage.PRODUCT_UNAVAILABLE
                    else -> BillingMessage.UNAVAILABLE
                })
            } finally {
                if (generation == loadSerial) presentation.value = presentation.value.copy(loading = false)
            }
        }
    }

    fun closePaywall(id: String) {
        if (activePaywall != id) return
        activePaywall = null
        loadSerial++
        // Keep purchase processing alive; only the price-loading job belongs to this UI.
        paywallLoad?.cancel()
        presentation.value = presentation.value.copy(loading = false)
    }

    fun purchase(activity: Activity, id: String) {
        if (activePaywall != id || !ui.value.canPurchase || state.value.owned) return
        val checkout = Request(++serial, id, BillingAction.PURCHASE)
        request = checkout
        presentation.value = presentation.value.copy(action = BillingAction.PURCHASE, message = null)
        scope.launch {
            try {
                val product = withTimeout(10_000) { store.lifetimeProduct() }
                if (request !== checkout || activePaywall != id || activity.isFinishing || activity.isDestroyed ||
                    (activity as? LifecycleOwner)?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == false) {
                    finish(checkout, BillingMessage.CANCELLED)
                    return@launch
                }
                presentation.value = presentation.value.copy(price = product.price)
                val code = store.launch(activity, product)
                if (code != BillingResponseCode.OK) onStoreResult(code)
            } catch (error: Exception) {
                if (error is CancellationException && error !is TimeoutCancellationException) throw error
                if (error is StoreFailure && error.code == BillingResponseCode.ITEM_UNAVAILABLE) {
                    presentation.value = presentation.value.copy(price = null)
                    finish(checkout, BillingMessage.PRODUCT_UNAVAILABLE)
                } else finish(checkout, BillingMessage.PURCHASE_FAILED)
            }
        }
    }

    fun restore(id: String) {
        if (activePaywall != id || ui.value.busy || ui.value.loading) return
        val checkout = Request(++serial, id, BillingAction.RESTORE)
        request = checkout
        presentation.value = presentation.value.copy(action = BillingAction.RESTORE, message = null)
        scope.launch { complete(checkout, restore = true) }
    }

    fun consumeSuccess(id: Long) {
        if (ui.value.success?.id == id) presentation.value = presentation.value.copy(success = null)
    }

    private suspend fun onStoreResult(code: Int) {
        val checkout = request
        when (code) {
            BillingResponseCode.OK -> {
                if (checkout != null) complete(checkout, restore = false)
                else refreshOwnership()
            }
            BillingResponseCode.ITEM_ALREADY_OWNED -> {
                if (checkout != null) complete(checkout, restore = true) else refreshOwnership()
            }
            BillingResponseCode.USER_CANCELED -> checkout?.let { finish(it, BillingMessage.CANCELLED) }
            else -> checkout?.let { finish(it, BillingMessage.PURCHASE_FAILED) }
        }
    }

    private suspend fun complete(checkout: Request, restore: Boolean) {
        try {
            withTimeout(10_000) {
                var result = synchronizeOwnership()
                // A just-completed payment can reach the listener before the ownership query catches up.
                if (!restore) for (wait in listOf(400L, 1_000L)) {
                    if (result.owned || result.pending || request !== checkout) break
                    delay(wait)
                    result = synchronizeOwnership()
                }
                reconcileRequest(checkout, result, final = true, restore = restore)
            }
        } catch (error: Exception) {
            if (error is CancellationException && error !is TimeoutCancellationException) throw error
            finish(checkout, if (error is PremiumSaveFailure) BillingMessage.SAVE_FAILED
                else if (restore) BillingMessage.RESTORE_FAILED else BillingMessage.PURCHASE_FAILED)
        }
    }

    private suspend fun synchronizeOwnership(): Ownership = ownership.withLock {
        cached.await()
        val purchases = store.ownedPurchases().filter { productId in it.products }
        val paid = purchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED && it.purchaseToken.isNotBlank() }
        val result = Ownership(paid.isNotEmpty(), purchases.any { it.purchaseState == Purchase.PurchaseState.PENDING })
        val next = reconcilePremium(state.value.owned, result.owned)
        // Once Play confirms ownership, finishing fulfillment must survive paywall disposal.
        withContext(NonCancellable) {
            if (!next) {
                // Confirmed revocation takes effect even if the device cannot save its cache.
                confirmedOwnership = false
                entitlement.value = entitlement.value.copy(owned = false)
            }
            try {
                withTimeout(2_000) { dataStore.savePremium(next) }
            } catch (error: Exception) {
                if (error is CancellationException && error !is TimeoutCancellationException) throw error
                throw PremiumSaveFailure(error)
            }
            confirmedOwnership = next
            entitlement.value = entitlement.value.copy(owned = next, loaded = true)
            presentation.value = presentation.value.copy(pending = result.pending && !next)
            // Fulfillment is saved before acknowledgment. Tokens remain recoverable through owned-purchase queries.
            paid.filterNot { it.isAcknowledged }.forEach { acknowledge(it.purchaseToken) }
        }
        result
    }

    private fun reconcileRequest(checkout: Request, result: Ownership, final: Boolean, restore: Boolean = false) {
        if (request !== checkout) return
        when {
            result.owned -> {
                request = null
                presentation.value = presentation.value.copy(action = null, pending = false, message = null,
                    success = if (activePaywall == checkout.paywallId)
                        BillingSuccess(checkout.id, checkout.paywallId, restore || checkout.action == BillingAction.RESTORE) else null)
            }
            result.pending -> {
                checkout.pending = true
                presentation.value = presentation.value.copy(action = null, pending = true, message = BillingMessage.PENDING)
            }
            checkout.pending || final -> finish(checkout, when {
                checkout.action == BillingAction.RESTORE -> BillingMessage.NOTHING_TO_RESTORE
                checkout.pending -> BillingMessage.CANCELLED
                else -> BillingMessage.PURCHASE_FAILED
            })
        }
    }

    private fun finish(checkout: Request, message: BillingMessage) {
        if (request !== checkout) return
        request = null
        presentation.value = presentation.value.copy(action = null, pending = false, message = message)
    }

    private fun acknowledge(token: String) {
        if (!acknowledging.add(token)) return
        scope.launch {
            try {
                repeat(6) { attempt ->
                    try { store.acknowledge(token); return@launch }
                    catch (error: Exception) {
                        if (error is CancellationException && error !is TimeoutCancellationException) throw error
                        if (error is StoreFailure && error.code in setOf(BillingResponseCode.ITEM_NOT_OWNED,
                                BillingResponseCode.DEVELOPER_ERROR)) { refreshOwnership(); return@launch }
                        if (attempt < 5) delay(2_000L shl attempt)
                    }
                }
                Log.w(TAG, "Acknowledgment deferred until the next ownership check")
            } finally { acknowledging.remove(token) }
        }
    }

    private class PremiumSaveFailure(cause: Throwable) : Exception(cause)
    companion object {
        private const val TAG = "PremiumBilling"
        @Volatile private var instance: PremiumRepository? = null
        fun get(context: Context): PremiumRepository = instance ?: synchronized(this) {
            instance ?: PremiumRepository(context.applicationContext).also { instance = it }
        }
    }
}
