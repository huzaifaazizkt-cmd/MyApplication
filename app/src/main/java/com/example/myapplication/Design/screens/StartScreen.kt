package com.example.myapplication.Design.screens

import android.app.Activity
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.example.myapplication.Ads.AdConfig
import com.example.myapplication.Firebase.RemoteConfigManager
import com.example.myapplication.R
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import kotlinx.coroutines.delay

private const val TAG = "StartScreenAdMob"

@Composable
fun StartScreen(
    navController: NavController,
    appInitialized: Boolean
) {
    val context = LocalContext.current
    val activity = context as? Activity

    val composition by rememberLottieComposition(
        LottieCompositionSpec.RawRes(R.raw.loadingbar)
    )

    var splashFinished by remember { mutableStateOf(false) }
    var navigationDone by remember { mutableStateOf(false) }
    var isAdFlowActive by remember { mutableStateOf(false) }
    var interstitialAd by remember { mutableStateOf<InterstitialAd?>(null) }
    var adLoaded by remember { mutableStateOf(false) }
    var adFailed by remember { mutableStateOf(false) }
    var remoteConfigLoaded by remember { mutableStateOf(false) }
    var showInterstitialAd by remember { mutableStateOf(false) }

    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = 1
    )

    LaunchedEffect(appInitialized) {
        if (!appInitialized) {
            remoteConfigLoaded = true
            return@LaunchedEffect
        }
    }

    LaunchedEffect(appInitialized) {
        if (!appInitialized) return@LaunchedEffect

        Log.d(TAG, "Fetching Remote Config...")

        val remoteConfigManager = RemoteConfigManager()

        remoteConfigManager.fetchConfig { remoteInterstitial, _ ->
            Log.d(
                TAG,
                "Remote Config interstitial = $remoteInterstitial"
            )

            showInterstitialAd = remoteInterstitial
            remoteConfigLoaded = true
        }
    }

    LaunchedEffect(
        remoteConfigLoaded,
        showInterstitialAd,
        appInitialized
    ) {
        if (!appInitialized) return@LaunchedEffect
        if (!remoteConfigLoaded) return@LaunchedEffect

        if (!showInterstitialAd) {
            Log.d(TAG, "REMOTE CONFIG = FALSE")
            Log.d(TAG, "INTERSTITIAL WILL NOT BE SHOWN")
            return@LaunchedEffect
        }

        if (activity == null) {
            Log.e(TAG, "Activity is not available")
            adFailed = true
            return@LaunchedEffect
        }

        Log.d(TAG, "REMOTE CONFIG = TRUE")
        Log.d(TAG, "Loading interstitial...")

        val adRequest = AdRequest.Builder().build()

        InterstitialAd.load(
            context,
            AdConfig.INTERSTITIAL_AD_UNIT_ID,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    Log.d(TAG, "INTERSTITIAL AD LOADED")

                    interstitialAd = ad
                    adLoaded = true

                    ad.fullScreenContentCallback =
                        object : FullScreenContentCallback() {
                            override fun onAdShowedFullScreenContent() {
                                Log.d(TAG, "INTERSTITIAL AD SHOWN")
                            }

                            override fun onAdDismissedFullScreenContent() {
                                Log.d(TAG, "INTERSTITIAL AD CLOSED")

                                interstitialAd = null

                                if (!navigationDone) {
                                    navigationDone = true
                                    isAdFlowActive = false

                                    navController.navigate(
                                        "unlockScreen"
                                    ) {
                                        popUpTo("startScreen") {
                                            inclusive = true
                                        }

                                        launchSingleTop = true
                                    }
                                }
                            }

                            override fun onAdFailedToShowFullScreenContent(
                                adError: AdError
                            ) {
                                Log.e(
                                    TAG,
                                    "INTERSTITIAL SHOW FAILED"
                                )

                                Log.e(
                                    TAG,
                                    "Code = ${adError.code}"
                                )

                                Log.e(
                                    TAG,
                                    "Message = ${adError.message}"
                                )

                                interstitialAd = null

                                if (!navigationDone) {
                                    navigationDone = true
                                    isAdFlowActive = false

                                    navController.navigate(
                                        "unlockScreen"
                                    ) {
                                        popUpTo("startScreen") {
                                            inclusive = true
                                        }

                                        launchSingleTop = true
                                    }
                                }
                            }
                        }
                }

                override fun onAdFailedToLoad(
                    loadAdError: LoadAdError
                ) {
                    Log.e(
                        TAG,
                        "INTERSTITIAL LOAD FAILED"
                    )

                    Log.e(
                        TAG,
                        "Code = ${loadAdError.code}"
                    )

                    Log.e(
                        TAG,
                        "Message = ${loadAdError.message}"
                    )

                    Log.e(
                        TAG,
                        "Domain = ${loadAdError.domain}"
                    )

                    interstitialAd = null
                    adFailed = true
                }
            }
        )
    }

    LaunchedEffect(composition, progress) {
        if (composition != null && progress >= 1f) {
            splashFinished = true
            Log.d(TAG, "SPLASH FINISHED")
        }
    }

    LaunchedEffect(
        splashFinished,
        remoteConfigLoaded,
        showInterstitialAd,
        adLoaded,
        adFailed,
        appInitialized
    ) {
        if (!splashFinished || navigationDone) {
            return@LaunchedEffect
        }

        if (!appInitialized) {
            navigationDone = true

            Log.d(
                TAG,
                "FIRST LAUNCH -> WELCOME"
            )

            navController.navigate("welcomeScreen") {
                popUpTo("startScreen") {
                    inclusive = true
                }
            }

            return@LaunchedEffect
        }

        if (!remoteConfigLoaded) {
            return@LaunchedEffect
        }

        if (!showInterstitialAd) {
            navigationDone = true

            Log.d(
                TAG,
                "REMOTE CONFIG FALSE -> NO AD"
            )

            Log.d(
                TAG,
                "GOING DIRECTLY TO UNLOCK"
            )

            navController.navigate("unlockScreen") {
                popUpTo("startScreen") {
                    inclusive = true
                }

                launchSingleTop = true
            }

            return@LaunchedEffect
        }

        if (adFailed) {
            navigationDone = true

            Log.d(
                TAG,
                "AD FAILED -> UNLOCK"
            )

            navController.navigate("unlockScreen") {
                popUpTo("startScreen") {
                    inclusive = true
                }

                launchSingleTop = true
            }

            return@LaunchedEffect
        }

        if (
            adLoaded &&
            interstitialAd != null &&
            activity != null
        ) {
            Log.d(
                TAG,
                "STARTING AD FLOW"
            )

            isAdFlowActive = true

            try {
                interstitialAd?.show(activity)
            } catch (e: Exception) {
                Log.e(
                    TAG,
                    "Exception while showing ad",
                    e
                )

                interstitialAd = null

                if (!navigationDone) {
                    navigationDone = true
                    isAdFlowActive = false

                    navController.navigate(
                        "unlockScreen"
                    ) {
                        popUpTo("startScreen") {
                            inclusive = true
                        }

                        launchSingleTop = true
                    }
                }
            }
        }
    }

    LaunchedEffect(appInitialized) {
        if (!appInitialized) return@LaunchedEffect

        delay(20_000L)

        if (
            !navigationDone &&
            !adLoaded &&
            remoteConfigLoaded
        ) {
            Log.e(
                TAG,
                "INTERSTITIAL TIMEOUT"
            )

            navigationDone = true
            isAdFlowActive = false

            navController.navigate(
                "unlockScreen"
            ) {
                popUpTo("startScreen") {
                    inclusive = true
                }

                launchSingleTop = true
            }
        }
    }

    if (!isAdFlowActive) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Color.White.copy(alpha = 0.9f)
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painter = painterResource(
                        id = R.drawable.startscreen
                    ),
                    contentDescription = "App Lock",
                    modifier = Modifier.size(270.dp)
                )

                Spacer(
                    modifier = Modifier.height(10.dp)
                )

                Text(
                    text = buildAnnotatedString {
                        withStyle(
                            style = SpanStyle(
                                color = Color(0xFF333333),
                                fontWeight = FontWeight.Bold
                            )
                        ) {
                            append("App ")
                        }

                        withStyle(
                            style = SpanStyle(
                                color = Color(0xFF2196F3),
                                fontWeight = FontWeight.Bold
                            )
                        ) {
                            append("Lock")
                        }
                    },
                    fontSize = 34.sp
                )

                Spacer(
                    modifier = Modifier.height(9.dp)
                )

                Text(
                    text = "Secure your apps. Protect your privacy.",
                    fontSize = 13.sp,
                    color = Color.Gray
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .windowInsetsPadding(
                        WindowInsets.navigationBars
                    )
                    .padding(
                        start = 100.dp,
                        end = 100.dp,
                        bottom = 20.dp
                    ),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                LottieAnimation(
                    composition = composition,
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(25.dp)
                )

                Spacer(
                    modifier = Modifier.height(10.dp)
                )

                Text(
                    text = "This action may contain ads",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
            }
        }
    }
}