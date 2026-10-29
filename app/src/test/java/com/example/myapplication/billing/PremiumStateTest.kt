package com.example.myapplication.billing

import org.junit.Assert.*
import org.junit.Test

class PremiumStateTest {
    @Test fun returningPaidUserKeepsAccessDuringAnOfflineOwnershipCheck() {
        val cached = PremiumState(owned = true, loaded = true, checking = true)
        val afterTimeout = cached.copy(owned = reconcilePremium(cached.owned, null), checking = false)
        assertTrue(afterTimeout.owned)
        assertFalse(afterTimeout.allowsAds)
    }

    @Test fun successfulOwnershipQueryRevokesMissingPurchaseButAnOutageDoesNot() {
        val cached = PremiumState(owned = true, loaded = true)
        assertTrue(reconcilePremium(cached.owned, null))
        val revoked = cached.copy(owned = reconcilePremium(cached.owned, false))
        assertFalse(revoked.owned)
        assertTrue(revoked.allowsAds)
    }

    @Test fun paidPurchaseImmediatelyRemovesAdsWhilePendingPaymentDoesNotGrantAccess() {
        val free = PremiumState(loaded = true)
        val pending = free.copy(owned = reconcilePremium(free.owned, false))
        assertFalse(pending.owned)
        val confirmed = pending.copy(owned = reconcilePremium(pending.owned, true))
        assertTrue(confirmed.owned)
        assertFalse(confirmed.allowsAds)
    }

    @Test fun noAdIsRequestedBeforeCacheAndStoreCheckFinish() {
        val unknown = PremiumState()
        assertFalse(unknown.allowsAds)
        val checking = unknown.copy(loaded = true, checking = true)
        assertFalse(checking.allowsAds)
        assertTrue(checking.copy(checking = false).allowsAds)
    }

    @Test fun repeatTapsAndPendingPaymentCannotStartAnotherPurchase() {
        val ready = BillingUiState(loading = false, price = "PKR 1,500")
        assertTrue(ready.canPurchase)
        val buying = ready.copy(action = BillingAction.PURCHASE)
        assertFalse(buying.canPurchase)
        val pending = buying.copy(action = null, pending = true)
        assertFalse(pending.canPurchase)
        assertTrue(pending.copy(pending = false).canPurchase)
    }

    @Test fun unavailableOrLoadingPriceAndRestoreDisableCheckout() {
        val unavailable = BillingUiState(loading = false)
        assertFalse(unavailable.canPurchase)
        val loading = unavailable.copy(loading = true, price = "€6.99")
        assertFalse(loading.canPurchase)
        val restoring = loading.copy(loading = false, action = BillingAction.RESTORE)
        assertFalse(restoring.canPurchase)
    }
}
