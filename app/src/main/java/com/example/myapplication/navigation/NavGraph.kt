package com.example.myapplication.navigation

import android.content.Context
import android.net.Uri

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember

import androidx.fragment.app.FragmentActivity

import androidx.navigation.NavType
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

import com.example.myapplication.AppPermissionFlow
import com.example.myapplication.Design.screens.ExitScreen
import com.example.myapplication.Design.screens.HomeScreen
import com.example.myapplication.Design.screens.IntruderCamera
import com.example.myapplication.Design.screens.IntruderScreen
import com.example.myapplication.Design.screens.LanguagesScreen
import com.example.myapplication.Design.screens.MainScreen
import com.example.myapplication.Design.screens.OnboardingScreen
import com.example.myapplication.Design.screens.PinConfirmScreen
import com.example.myapplication.Design.screens.PinCreateScreen
import com.example.myapplication.Design.screens.PremiumScreen
import com.example.myapplication.Design.screens.StartScreen
import com.example.myapplication.Design.screens.UnlockScreen
import com.example.myapplication.Design.screens.WelcomeScreen
import com.example.myapplication.Design.screens.showFingerprintPrompt
import com.example.myapplication.data.DataStoreManager
import com.example.myapplication.security.AuthSession
import com.example.myapplication.billing.PremiumRepository
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import com.example.myapplication.R

@Composable
fun NavGraph(
    context: Context,
    startDestination: String = "startScreen"
) {
    val appContext = context.applicationContext
    val dataStore = DataStoreManager(appContext)
    val premium by remember(appContext) { PremiumRepository.get(appContext) }.state.collectAsState()

    // MainActivity AppCompatActivity hai, yani FragmentActivity bhi hai
    val activity = context as? FragmentActivity

    val intruderCamera = remember(activity) {
        activity?.let {
            IntruderCamera(it, DataStoreManager(appContext))
        }
    }

    val appInitialized by dataStore
        .getAppInitialized()
        .collectAsState(initial = null)

    if (appInitialized == null) {
        return
    }

    val authenticated by AuthSession.isAuthenticated.collectAsState()
    val resetExpiry by AuthSession.resetAuthorization.collectAsState()
    val navController = rememberNavController()
    val graphStart = remember(startDestination) {
        when {
            startDestination == "resetCreate" && AuthSession.canReset -> "resetCreate"
            appInitialized == true && startDestination == "permissionGate" -> "startScreen"
            else -> startDestination
        }
    }
    val entry by navController.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: graphStart
    val needsAuthentication = appInitialized == true && !authenticated && route != "startScreen" &&
        resetExpiry <= android.os.SystemClock.elapsedRealtime()
    val checkingPremium = appInitialized == true && premium.checking && route !in setOf("startScreen", "premium")

    Box(Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            modifier = if (needsAuthentication || checkingPremium) Modifier.clearAndSetSemantics {} else Modifier,
            startDestination = graphStart
        ) {

            composable("startScreen") {
                StartScreen(
                    navController = navController,
                    appInitialized = appInitialized == true
                )
            }

            // Redirect restored/legacy entries; authentication has a single presentation below.
            composable("unlockScreen") {
                LaunchedEffect(Unit) {
                    navController.navigate("appList") {
                        popUpTo("unlockScreen") { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }

            composable("welcomeScreen") {
                WelcomeScreen(
                    navController = navController
                )
            }

            composable("languagesSetup") {
                LanguagesScreen(
                    onBackClick = null,
                    onLanguageSelected = {
                        navController.navigate("onboardingScreen") {
                            popUpTo("languagesSetup") {
                                inclusive = true
                            }
                        }
                    }
                )
            }

            composable("onboardingScreen") {
                OnboardingScreen(
                    navController = navController
                )
            }

            composable("premium") { premiumEntry ->
                val exit = remember(premiumEntry.id) { PremiumExitGuard(premiumEntry.id) }
                fun closePremium(openApps: Boolean) {
                    exit.close(
                        currentEntryId = navController.currentBackStackEntry?.id,
                        initialized = appInitialized == true,
                        returnToApp = {
                            val appEntry = runCatching { navController.getBackStackEntry("appList") }.getOrNull()
                            if (appEntry != null) {
                                val returnTab = if (openApps) 0 else appEntry.savedStateHandle.get<Int>("premiumReturnTab") ?: 0
                                appEntry.savedStateHandle["openSettings"] = returnTab == 2
                                appEntry.savedStateHandle["forceAppList"] = returnTab == 0
                                if (openApps) appEntry.savedStateHandle["returnToSettings"] = false
                                navController.popBackStack("appList", false)
                            } else {
                                navController.navigate("appList") {
                                    popUpTo(navController.graph.id)
                                    launchSingleTop = true
                                }
                            }
                        },
                        continueSetup = {
                            navController.navigate("create") {
                                popUpTo("premium") { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    )
                }
                PremiumScreen(
                    paywallId = premiumEntry.id,
                    setupComplete = appInitialized == true,
                    onClose = { closePremium(false) },
                    onPremiumGranted = { closePremium(true) }
                )
            }

            composable("create") {
                PinCreateScreen(
                    onNext = { type, value ->
                        navController.navigate(
                            "confirm/${Uri.encode(type)}/${Uri.encode(value)}"
                        )
                    }
                )
            }

            composable(
                route = "confirm/{type}/{value}",
                arguments = listOf(
                    navArgument("type") {
                        type = NavType.StringType
                    },
                    navArgument("value") {
                        type = NavType.StringType
                    }
                )
            ) { backStackEntry ->

                val type = backStackEntry.arguments
                    ?.getString("type") ?: "pin"

                val value = Uri.decode(
                    backStackEntry.arguments
                        ?.getString("value") ?: ""
                )

                PinConfirmScreen(
                    navController = navController,
                    context = context,
                    type = type,
                    value = value,
                    isReset = false,
                    permissionOnly = false
                )
            }

            // Incomplete setup ke liye dedicated permission screen.
            // Yahan PIN/Pattern confirmation UI nahi dikhai jayegi.
            composable("permissionGate") {

                val pendingType =
                    AppPermissionFlow.getPendingType(appContext) ?: "pin"

                PinConfirmScreen(
                    navController = navController,
                    context = context,
                    type = pendingType,
                    value = "",
                    isReset = false,
                    permissionOnly = true
                )
            }

            composable("resetCreate") {
                if (!AuthSession.canReset) return@composable
                PinCreateScreen(
                    onNext = { type, value ->
                        navController.navigate(
                            "resetConfirm/${Uri.encode(type)}/${Uri.encode(value)}"
                        )
                    }
                )
            }

            composable(
                route = "resetConfirm/{type}/{value}",
                arguments = listOf(
                    navArgument("type") {
                        type = NavType.StringType
                    },
                    navArgument("value") {
                        type = NavType.StringType
                    }
                )
            ) { backStackEntry ->

                val type = backStackEntry.arguments
                    ?.getString("type") ?: "pin"

                val value = Uri.decode(
                    backStackEntry.arguments
                        ?.getString("value") ?: ""
                )

                PinConfirmScreen(
                    navController = navController,
                    context = context,
                    type = type,
                    value = value,
                    isReset = true,
                    permissionOnly = false
                )
            }

            composable("home") {
                HomeScreen(
                    navController = navController
                )
            }

            composable("appList") {
                val currentEntry =
                    navController.currentBackStackEntry

                val openSettings =
                    currentEntry
                        ?.savedStateHandle
                        ?.get<Boolean>("openSettings")
                        ?: false

                MainScreen(
                    context = context,

                    onIntruderClick = {
                        if (!premium.owned) {
                            navController.openPremium(returnTab = 2)
                        } else {
                        currentEntry
                            ?.savedStateHandle
                            ?.set("returnToSettings", true)

                        navController.navigate("intruder")
                        }
                    },

                    onLanguageClick = {
                        currentEntry
                            ?.savedStateHandle
                            ?.set("returnToSettings", true)

                        navController.navigate("languages")
                    },

                    onResetPasswordClick = {
                        navController.navigate("resetCreate")
                    },

                    openSettings = openSettings,
                    navController = navController
                )
            }

            composable("languages") {
                LanguagesScreen(
                    onBackClick = {
                        navController.popBackStack()
                    },
                    onLanguageSelected = null
                )
            }

            composable("intruder") {
                IntruderScreen(
                    onUpgradeClick = { navController.openPremium(returnTab = 2) },
                    onBackClick = {
                        navController.popBackStack()
                    }
                )
            }

            composable("exit") {
                ExitScreen(
                    navController = navController
                )
            }
        }
        if (checkingPremium && !needsAuthentication) {
            androidx.activity.compose.BackHandler { activity?.moveTaskToBack(true) }
            Box(Modifier.fillMaxSize().background(Color.White).clickable(
                interactionSource = remember { MutableInteractionSource() }, indication = null
            ) {}, contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color(0xFF0396FF))
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.billing_checking))
                }
            }
        }
        if (needsAuthentication) {
            androidx.activity.compose.BackHandler { activity?.moveTaskToBack(true) }
            // Consume empty-space taps and hide protected semantics while preserving activity result registrations.
            Box(Modifier.fillMaxSize().background(Color.White).clickable(
                interactionSource = remember { MutableInteractionSource() }, indication = null
            ) {}) {
                UnlockScreen(
                    onUnlockSuccess = { intruderCamera?.cancelCapture(); AuthSession.unlock() },
                    onFingerprintRequest = {
                        intruderCamera?.cancelCapture()
                        activity?.let { showFingerprintPrompt(it) { intruderCamera?.cancelCapture(); AuthSession.unlock() } }
                    },
                    onIntruderCapture = { intruderCamera?.capture() },
                    onForgotPasswordSuccess = { intruderCamera?.cancelCapture(); navController.navigate("resetCreate") }
                )
            }
        }
    }
}
