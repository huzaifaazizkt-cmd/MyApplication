package com.example.myapplication.navigation

import androidx.lifecycle.Lifecycle
import androidx.navigation.NavController

/** An outgoing paywall must never navigate again after its entry has been removed. */
internal class PremiumExitGuard(private val entryId: String) {
    private var closed = false

    fun close(
        currentEntryId: String?,
        initialized: Boolean,
        returnToApp: () -> Unit,
        continueSetup: () -> Unit
    ) {
        if (closed || currentEntryId != entryId) return
        closed = true
        if (initialized) returnToApp() else continueSetup()
    }
}

internal fun NavController.openPremium(returnTab: Int? = null) {
    val origin = currentBackStackEntry ?: return
    if (origin.destination.route == "premium" || !origin.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
    if (returnTab != null) {
        val app = if (origin.destination.route == "appList") origin
            else runCatching { getBackStackEntry("appList") }.getOrNull()
        app?.savedStateHandle?.set("premiumReturnTab", returnTab)
    }
    navigate("premium") {
        launchSingleTop = true
        if (origin.destination.route == "onboardingScreen") {
            popUpTo("onboardingScreen") { inclusive = true }
        }
    }
}
