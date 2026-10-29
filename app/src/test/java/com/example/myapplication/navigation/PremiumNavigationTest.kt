package com.example.myapplication.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class PremiumNavigationTest {
    @Test
    fun rapidCloseAfterReturningToAppDoesNotStartPinSetup() {
        val guard = PremiumExitGuard("premium-1")
        val destinations = mutableListOf("appList", "premium-1")
        var returns = 0
        val close = {
            guard.close(
                currentEntryId = destinations.last(),
                initialized = true,
                returnToApp = { returns++; destinations.removeAt(destinations.lastIndex) },
                continueSetup = { destinations.add("create") }
            )
        }

        repeat(3) { close() }

        assertEquals(listOf("appList"), destinations)
        assertEquals(1, returns)
    }

    @Test
    fun repeatedCloseWhileTransitionIsPendingRunsOnlyOnce() {
        val guard = PremiumExitGuard("premium-1")
        var returns = 0
        var setupStarts = 0

        repeat(3) {
            guard.close("premium-1", true, { returns++ }, { setupStarts++ })
        }

        assertEquals(1, returns)
        assertEquals(0, setupStarts)
    }

    @Test
    fun firstTimeSetupStillContinuesToPinOnce() {
        val guard = PremiumExitGuard("onboarding-premium")
        var returns = 0
        var setupStarts = 0

        repeat(3) {
            guard.close("onboarding-premium", false, { returns++ }, { setupStarts++ })
        }

        assertEquals(0, returns)
        assertEquals(1, setupStarts)
    }

    @Test
    fun staleCallbackCannotCloseReopenedPremium() {
        val oldGuard = PremiumExitGuard("premium-1")
        val newGuard = PremiumExitGuard("premium-2")
        var oldReturns = 0
        var newReturns = 0
        var setupStarts = 0

        oldGuard.close("premium-2", true, { oldReturns++ }, { setupStarts++ })
        newGuard.close("premium-2", true, { newReturns++ }, { setupStarts++ })

        assertEquals(0, oldReturns)
        assertEquals(1, newReturns)
        assertEquals(0, setupStarts)
    }

    @Test
    fun missingOrDifferentCurrentEntryDoesNotConsumeValidClose() {
        val guard = PremiumExitGuard("premium-1")
        var returns = 0
        var setupStarts = 0

        listOf(null, "appList", "premium-1", "premium-1").forEach { current ->
            guard.close(current, true, { returns++ }, { setupStarts++ })
        }

        assertEquals(1, returns)
        assertEquals(0, setupStarts)
    }
}
