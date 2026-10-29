package com.example.myapplication.service

import org.junit.Assert.*
import org.junit.Test

class UsageEventCursorTest {
    private fun entry(pkg: String, at: Long, activity: String = "Main") = ForegroundEntry(pkg, activity, at)

    @Test fun overlappingQueriesDoNotReplayOldDepartures() {
        val cursor = UsageEventCursor()
        val first = entry("private", 100)
        val home = entry("home", 200)
        assertEquals(listOf(first, home), cursor.consume(listOf(first, home)))
        assertTrue(cursor.consume(listOf(first, home)).isEmpty())
        assertEquals(home, cursor.current)
    }
    @Test fun multipleAppsAtTheSameMillisecondAreNotLost() {
        val cursor = UsageEventCursor()
        val a = entry("a", 100)
        val b = entry("b", 100)
        val c = entry("c", 100)
        assertEquals(listOf(a, b), cursor.consume(listOf(a, b)))
        assertEquals(listOf(c), cursor.consume(listOf(a, b, c)))
        assertTrue(cursor.consume(listOf(a, b, c)).isEmpty())
    }
    @Test fun emptyPollKeepsTheCurrentAppWithoutInventingADeparture() {
        val cursor = UsageEventCursor()
        val foreground = entry("private", 100)
        cursor.consume(listOf(foreground))
        assertTrue(cursor.consume(emptyList()).isEmpty())
        assertEquals(foreground, cursor.current)
    }
    @Test fun oldEventsCannotReplaceANewerHomeEntry() {
        val cursor = UsageEventCursor()
        cursor.consume(listOf(entry("home", 300)))
        assertTrue(cursor.consume(listOf(entry("private", 100), entry("private", 200))).isEmpty())
        assertEquals("home", cursor.current?.packageName)
    }
    @Test fun quickHomeAndReturnWithinOnePollStillRelocksImmediately() {
        val policy = RelockPolicy { 1_000 }
        policy.authenticate("private")
        val cursor = UsageEventCursor()
        cursor.consume(listOf(entry("private", 100), entry("home", 200), entry("private", 300)))
            .forEach { policy.onForeground(if (it.packageName == "home") null else it.packageName) }
        assertEquals("private", cursor.current?.packageName)
        assertFalse(policy.isAuthenticated("private"))
    }
    @Test fun changingActivitiesWithinTheSameAppDoesNotRevokeItsGrant() {
        val policy = RelockPolicy { 1_000 }
        policy.authenticate("private")
        val cursor = UsageEventCursor()
        cursor.consume(listOf(entry("private", 100, "List"), entry("private", 200, "Details")))
            .forEach { policy.onForeground(it.packageName) }
        assertTrue(policy.isAuthenticated("private"))
    }
    @Test fun wallClockResetAcceptsNewEventsAndRetainsOwnerUntilOneArrives() {
        val cursor = UsageEventCursor()
        cursor.consume(listOf(entry("a", 10_000)))
        cursor.reset()
        assertEquals("a", cursor.current?.packageName)
        assertEquals(listOf(entry("home", 100)), cursor.consume(listOf(entry("home", 100))))
        assertEquals("home", cursor.current?.packageName)
    }
}
