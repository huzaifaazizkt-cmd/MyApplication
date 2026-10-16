package com.example.myapplication.navigation

import android.content.Context
import android.net.Uri

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

import androidx.fragment.app.FragmentActivity

import androidx.navigation.NavType
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

@Composable
fun NavGraph(
    context: Context,
    startDestination: String = "startScreen"
) {
    val appContext = context.applicationContext
    val dataStore = DataStoreManager(appContext)

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

    // DataStore load hone tak splash jaisa background dikhayen (white/black flash na aaye)
    if (appInitialized == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFEEF1F6))
        )
        return
    }

    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None }
    ) {

        composable("startScreen") {
            StartScreen(
                navController = navController,
                appInitialized = appInitialized == true
            )
        }

        composable("unlockScreen") {
            UnlockScreen(
                onUnlockSuccess = {
                    navController.navigate("appList") {
                        popUpTo("unlockScreen") {
                            inclusive = true
                        }
                        launchSingleTop = true
                    }
                },
                onForgotPasswordSuccess = {
                    navController.navigate("resetCreate") {
                        popUpTo("unlockScreen") {
                            inclusive = true
                        }
                    }
                },
                onFingerprintRequest = {
                    activity?.let { act ->
                        showFingerprintPrompt(act) {
                            navController.navigate("appList") {
                                popUpTo("unlockScreen") {
                                    inclusive = true
                                }
                                launchSingleTop = true
                            }
                        }
                    }
                },
                onIntruderCapture = { _ ->
                    intruderCamera?.capture()
                }
            )
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

        composable("premium") {
            PremiumScreen(
                navController = navController
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

            val pendingValue =
                AppPermissionFlow.getPendingValue(appContext) ?: ""

            PinConfirmScreen(
                navController = navController,
                context = context,
                type = pendingType,
                value = pendingValue,
                isReset = false,
                permissionOnly = true
            )
        }

        composable("resetCreate") {
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
                    currentEntry
                        ?.savedStateHandle
                        ?.set("returnToSettings", true)

                    navController.navigate("intruder")
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
}