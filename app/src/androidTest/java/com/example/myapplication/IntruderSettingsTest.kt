package com.example.myapplication

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myapplication.data.DataStoreManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IntruderSettingsTest {
    @Test fun concurrentAttemptsAreAtomicAndNeverOptionDisablesCapture() = runBlocking {
        val store = DataStoreManager(InstrumentationRegistry.getInstrumentation().targetContext)
        val enabled = store.getIntruderEnabled().first()
        val threshold = store.getIntruderObservationAttempts().first()
        val attempts = store.getIntruderWrongAttempts().first()
        val premium = store.getPremium().first()
        try {
            store.savePremium(true)
            store.saveIntruderEnabled(true)
            store.saveIntruderObservationAttempts(3)
            store.resetIntruderWrongAttempts()
            val captures = coroutineScope {
                (1..30).map { async(Dispatchers.Default) { store.recordIntruderAttempt() } }.awaitAll()
            }
            assertEquals(10, captures.count { it })
            assertEquals(0, store.getIntruderWrongAttempts().first())
            store.saveIntruderObservationAttempts(0)
            repeat(10) { assertFalse(store.recordIntruderAttempt()) }
        } finally {
            store.savePremium(true)
            store.saveIntruderEnabled(enabled)
            store.saveIntruderObservationAttempts(threshold)
            store.savePremium(premium)
            store.saveIntruderWrongAttempts(attempts)
        }
    }

    @Test fun freeUsersCannotEnableOrAccumulateIntruderCaptureAttempts() = runBlocking {
        val store = DataStoreManager(InstrumentationRegistry.getInstrumentation().targetContext)
        val premium = store.getPremium().first()
        val enabled = store.getIntruderEnabled().first()
        val threshold = store.getIntruderObservationAttempts().first()
        val attempts = store.getIntruderWrongAttempts().first()
        try {
            store.savePremium(false)
            store.saveIntruderEnabled(true)
            store.saveIntruderObservationAttempts(3)
            assertFalse(store.getIntruderEnabled().first())
            repeat(10) { assertFalse(store.recordIntruderAttempt()) }
            assertEquals(0, store.getIntruderWrongAttempts().first())
            store.savePremium(true)
            store.saveIntruderEnabled(true)
            assertFalse(store.recordIntruderAttempt())
            store.savePremium(false)
            repeat(10) { assertFalse(store.recordIntruderAttempt()) }
            assertEquals(0, store.getIntruderWrongAttempts().first())
        } finally {
            store.savePremium(true)
            store.saveIntruderEnabled(enabled)
            store.saveIntruderObservationAttempts(threshold)
            store.savePremium(premium)
            store.saveIntruderWrongAttempts(attempts)
        }
    }
}
