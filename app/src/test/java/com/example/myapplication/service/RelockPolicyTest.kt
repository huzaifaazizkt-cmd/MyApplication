package com.example.myapplication.service

import org.junit.Assert.*
import org.junit.Test

class RelockPolicyTest {
    @Test fun delayedUsageBatchPreservesAReturnThatWasWithinGrace() {
        var now = 100L
        val policy = RelockPolicy { now }.apply { configure(RelockPolicy.Mode.AFTER_QUITTING, 5_000) }
        policy.authenticate("private")
        now = 10_000
        policy.onForeground(null, observedAt = 200)
        policy.onForeground("private", observedAt = 1_000)
        policy.expireGrants()
        assertTrue(policy.isAuthenticated("private"))
    }
    @Test fun delayedUsageBatchDoesNotGrantAReturnOutsideGrace() {
        var now = 100L
        val policy = RelockPolicy { now }.apply { configure(RelockPolicy.Mode.AFTER_QUITTING, 5_000) }
        policy.authenticate("private")
        now = 10_000
        policy.onForeground(null, observedAt = 200)
        policy.onForeground("private", observedAt = 6_000)
        assertFalse(policy.isAuthenticated("private"))
    }
    private var time = 100L
    private fun policy(mode: RelockPolicy.Mode = RelockPolicy.Mode.AFTER_QUITTING, delay: Long = 0) =
        RelockPolicy { time }.apply { configure(mode, delay) }

    @Test fun backWithoutAuthenticationNeverGrantsAccess() {
        val policy = policy(delay = 300_000)
        repeat(10) { policy.onForeground("private"); policy.onForeground(null) }
        assertFalse(policy.isAuthenticated("private"))
    }
    @Test fun quittingImmediatelyRevokesAccess() {
        val policy = policy()
        policy.authenticate("a"); policy.onForeground(null)
        assertFalse(policy.isAuthenticated("a"))
    }
    @Test fun returningWithinGraceCancelsExpiry() {
        val policy = policy(delay = 5_000)
        policy.authenticate("a"); policy.onForeground(null)
        time += 4_999; policy.onForeground("a")
        time += 60_000
        assertTrue(policy.isAuthenticated("a"))
        policy.onForeground(null); time += 5_000
        assertFalse(policy.isAuthenticated("a"))
    }
    @Test fun repeatedHomeEventsDoNotExtendGrace() {
        val policy = policy(delay = 5_000)
        policy.authenticate("a"); policy.onForeground(null)
        time += 4_000; repeat(10) { policy.onForeground(null) }
        time += 1_000; policy.onForeground("a")
        assertFalse(policy.isAuthenticated("a"))
    }
    @Test fun everyConfiguredDelayExpiresAtItsBoundary() {
        for (delay in listOf(0L, 5_000, 10_000, 30_000, 60_000, 120_000, 300_000)) {
            val policy = policy(delay = delay)
            policy.authenticate("a"); policy.onForeground("b")
            if (delay > 0) { time += delay - 1; assertTrue(policy.isAuthenticated("a")); time++ }
            assertFalse(policy.isAuthenticated("a"))
        }
    }
    @Test fun screenOffModeKeepsIndependentAppGrants() {
        val policy = policy(RelockPolicy.Mode.AFTER_SCREEN_OFF)
        policy.authenticate("a"); policy.onForeground("b"); policy.authenticate("b")
        policy.onForeground("a")
        assertTrue(policy.isAuthenticated("a")); assertTrue(policy.isAuthenticated("b"))
        policy.onScreenOff()
        assertFalse(policy.isAuthenticated("a")); assertFalse(policy.isAuthenticated("b"))
    }
    @Test fun screenOffGraceIsNotCancelledByReturning() {
        val policy = policy(RelockPolicy.Mode.AFTER_SCREEN_OFF, 5_000)
        policy.authenticate("a"); policy.onScreenOff()
        time += 4_000; policy.onForeground("a"); time += 1_000
        assertFalse(policy.isAuthenticated("a"))
    }
    @Test fun policyChangesAndDisablingClearExistingGrants() {
        val policy = policy()
        policy.authenticate("a"); policy.configure(RelockPolicy.Mode.AFTER_SCREEN_OFF, 0)
        assertFalse(policy.isAuthenticated("a"))
        policy.authenticate("a"); policy.clear(); assertFalse(policy.isAuthenticated("a"))
    }
    @Test fun unrelatedSettingsUpdatesDoNotRevokeAuthentication() {
        val policy = policy()
        policy.authenticate("a"); policy.configure(RelockPolicy.Mode.AFTER_QUITTING, 0)
        assertTrue(policy.isAuthenticated("a"))
        policy.retainPackages(setOf("b")); assertFalse(policy.isAuthenticated("a"))
    }
    @Test fun expiryCanBeEnforcedWithoutAnotherAppEvent() {
        val policy = policy(RelockPolicy.Mode.AFTER_SCREEN_OFF, 5_000)
        policy.authenticate("a")
        policy.authenticate("b")
        policy.onScreenOff()
        assertEquals(time + 5_000, policy.nextExpiry())
        time += 5_000
        policy.expireGrants()
        assertNull(policy.nextExpiry())
        assertFalse(policy.isAuthenticated("a"))
        assertFalse(policy.isAuthenticated("b"))
    }
}
