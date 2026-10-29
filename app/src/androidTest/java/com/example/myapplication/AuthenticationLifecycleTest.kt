package com.example.myapplication

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.geometry.Offset
import androidx.test.espresso.Espresso
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myapplication.data.DataStoreManager
import com.example.myapplication.security.AuthSession
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuthenticationLifecycleTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private var scenario: ActivityScenario<MainActivity>? = null
    private var initialized = false
    private var fingerprint = false
    private var premium = false
    private var authType = "pin"
    @Before fun prepare() = runBlocking {
        val store = DataStoreManager(context)
        initialized = store.getAppInitialized().first()
        fingerprint = store.getFingerprintEnabled().first()
        premium = store.getPremium().first()
        authType = store.getAuthType().first() ?: "pin"
        store.savePremium(true) // Keep ad/network callbacks out of authentication regression tests.
        store.saveAuthType("pin")
        store.saveFingerprintEnabled(false)
        store.saveAppInitialized(true)
        AuthSession.lock()
    }
    @After fun cleanup() {
        scenario?.close()
        runBlocking {
            DataStoreManager(context).saveAppInitialized(initialized)
            DataStoreManager(context).saveFingerprintEnabled(fingerprint)
            DataStoreManager(context).savePremium(premium)
            DataStoreManager(context).saveAuthType(authType)
        }
        AuthSession.lock()
    }
    private fun launch(reset: Boolean = false) {
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)
            .putExtra("openResetPassword", reset).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    private fun assertLocked() {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText(context.getString(R.string.enter_passcode)).fetchSemanticsNodes().isNotEmpty()
        }
        assertFalse(AuthSession.isAuthenticated.value)
        compose.onNodeWithText(context.getString(R.string.enter_passcode)).assertIsDisplayed()
    }
    @Test fun externalResetExtraDoesNotAuthorizePasswordChange() {
        launch(reset = true)
        assertLocked()
        assertFalse(AuthSession.hasResetAuthorization)
    }
    @Test fun backgroundReturnRequiresAuthentication() {
        launch()
        compose.runOnIdle { AuthSession.unlock() }
        scenario!!.moveToState(Lifecycle.State.CREATED)
        assertFalse(AuthSession.isAuthenticated.value)
        scenario!!.moveToState(Lifecycle.State.RESUMED)
        assertLocked()
    }
    @Test fun recreatedUnauthenticatedActivityStaysLocked() {
        launch(reset = true)
        scenario!!.recreate()
        assertLocked()
    }
    @Test fun authorizedRecoveryOpensResetButBackgroundingRevokesIt() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { AuthSession.authorizeReset() }
        launch(reset = true)
        val createPin = context.getString(R.string.create_pin, 4)
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText(createPin).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(createPin).assertIsDisplayed()
        scenario!!.moveToState(Lifecycle.State.CREATED)
        assertFalse(AuthSession.hasResetAuthorization)
        scenario!!.moveToState(Lifecycle.State.RESUMED)
        assertLocked()
    }
    @Test fun backgroundReturnDoesNotLeaveAnUnlockRouteBehindBack() {
        launch()
        assertLocked()
        compose.runOnIdle { AuthSession.unlock() }
        scenario!!.moveToState(Lifecycle.State.CREATED)
        scenario!!.moveToState(Lifecycle.State.RESUMED)
        assertLocked()
        compose.runOnIdle { AuthSession.unlock() }
        Espresso.pressBack()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText(context.getString(R.string.exit_screen_title)).fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(AuthSession.isAuthenticated.value)
        assertTrue(compose.onAllNodesWithText(context.getString(R.string.enter_passcode)).fetchSemanticsNodes().isEmpty())
    }
    @Test fun cancellingPatternDoesNotCountAsAnIntruderAttempt() {
        val store = DataStoreManager(context)
        val previous = runBlocking { Triple(store.getIntruderEnabled().first(),
            store.getIntruderObservationAttempts().first(), store.getIntruderWrongAttempts().first()) }
        try {
            runBlocking {
                store.saveAuthType("pattern")
                store.saveIntruderEnabled(true)
                store.saveIntruderObservationAttempts(3)
                store.resetIntruderWrongAttempts()
            }
            launch()
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText(context.getString(R.string.draw_pattern)).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithContentDescription(context.getString(R.string.pattern)).performTouchInput {
                down(Offset(width / 6f, height / 2f))
                moveTo(center)
                cancel()
            }
            compose.waitForIdle()
            assertEquals(0, runBlocking { store.getIntruderWrongAttempts().first() })
            assertTrue(compose.onAllNodesWithText(context.getString(R.string.checking_password)).fetchSemanticsNodes().isEmpty())
        } finally {
            runBlocking {
                store.saveIntruderEnabled(previous.first)
                store.saveIntruderObservationAttempts(previous.second)
                store.saveIntruderWrongAttempts(previous.third)
            }
        }
    }
}
