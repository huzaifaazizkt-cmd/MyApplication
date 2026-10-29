package com.example.myapplication.Design.screens

import com.example.myapplication.billing.PremiumRepository

import android.app.Activity
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.myapplication.data.DataStoreManager
import kotlin.coroutines.resume

private const val STARTUP_BUDGET_MS = 3_500L

@Composable
fun StartScreen(navController: NavController, appInitialized: Boolean) {
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var resumed by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var active by remember { mutableStateOf(true) }
    var completed by remember { mutableStateOf(false) }
    var ready by remember { mutableStateOf(false) }
    var isAdFlowActive by remember { mutableStateOf(false) }
    var adAttempted by rememberSaveable { mutableStateOf(false) }
    var interstitial by remember { mutableStateOf<InterstitialAd?>(null) }
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.loadingbar))
    val progress by animateLottieCompositionAsState(composition = composition, iterations = 1)
    val initialized by rememberUpdatedState(appInitialized)
    val premiumRepository = remember(context) { PremiumRepository.get(context) }
    val premiumState by premiumRepository.state.collectAsState()
    fun navigate() {
        if (!active || completed) return
        completed = true
        navController.navigate(if (initialized) "appList" else "welcomeScreen") {
            popUpTo("startScreen") { inclusive = true }
            launchSingleTop = true
        }
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            resumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycle.addObserver(observer)
        onDispose { active = false; lifecycle.removeObserver(observer); interstitial = null }
    }
    LaunchedEffect(Unit) {
        if (adAttempted) { ready = true; return@LaunchedEffect }
        // Offline, no-fill and slow callbacks cannot hold startup indefinitely.
        delay(700)
        try {
            if (appInitialized) {
                withTimeoutOrNull(8_500L) { premiumRepository.refreshOwnership().join() }
            }
            withTimeoutOrNull(STARTUP_BUDGET_MS) {
                if (!appInitialized || !premiumRepository.state.value.allowsAds) return@withTimeoutOrNull
                val showAd = suspendCancellableCoroutine<Boolean> { continuation ->
                    RemoteConfigManager().fetchConfig { show, _ ->
                        if (continuation.isActive) continuation.resume(show)
                    }
                }
                if (showAd) interstitial = suspendCancellableCoroutine<InterstitialAd?> { continuation ->
                    InterstitialAd.load(context, AdConfig.INTERSTITIAL_AD_UNIT_ID, AdRequest.Builder().build(),
                        object : InterstitialAdLoadCallback() {
                            override fun onAdLoaded(ad: InterstitialAd) {
                                if (continuation.isActive) continuation.resume(ad)
                            }
                            override fun onAdFailedToLoad(error: LoadAdError) {
                                if (continuation.isActive) continuation.resume(null)
                            }
                        })
                }
            }
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            Log.w("StartScreen", "Startup ad unavailable", error)
        }
        ready = true
    }
    LaunchedEffect(ready, resumed, premiumState.owned, premiumState.checking) {
        if (!ready || !resumed || completed || isAdFlowActive) return@LaunchedEffect
        val ad = interstitial.takeIf { premiumRepository.state.value.allowsAds }
        if (ad == null || activity == null) { navigate(); return@LaunchedEffect }
        interstitial = null
        adAttempted = true
        isAdFlowActive = true
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() { isAdFlowActive = false; navigate() }
            override fun onAdFailedToShowFullScreenContent(error: AdError) { isAdFlowActive = false; navigate() }
        }
        try { ad.show(activity) } catch (error: Exception) { isAdFlowActive = false; navigate() }
    }

    // =========================================================================
    // RESPONSIVE UI
    // =========================================================================

    if (!isAdFlowActive) {

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Color.White.copy(
                        alpha = 0.9f
                    )
                )
        ) {

            /*
             * We use the actual available Compose size here.
             *
             * No screen-width calculations.
             * No hardcoded 100.dp left/right margins.
             *
             * This makes the layout work consistently on:
             * Samsung
             * Infinix
             * itel
             * small phones
             * large phones
             * different DPI
             * different aspect ratios
             */

            val screenWidth = maxWidth
            val screenHeight = maxHeight

            /*
             * Responsive logo size.
             *
             * Normal phones:
             * approximately 240-270dp
             *
             * Small phones:
             * automatically smaller
             *
             * Large phones:
             * limited to 270dp so it does not become huge.
             */
            val logoSize =
                when {

                    screenHeight < 620.dp ->
                        205.dp

                    screenHeight < 700.dp ->
                        230.dp

                    else ->
                        270.dp
                }

            /*
             * Responsive title size.
             */
            val titleSize =
                when {

                    screenWidth < 340.dp ->
                        30.sp

                    screenWidth < 380.dp ->
                        32.sp

                    else ->
                        34.sp
                }

            /*
             * Responsive description size.
             */
            val descriptionSize =
                when {

                    screenWidth < 340.dp ->
                        12.sp

                    else ->
                        13.sp
                }

            // -----------------------------------------------------------------
            // TOP / CENTER CONTENT
            // -----------------------------------------------------------------

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(
                        start = 24.dp,
                        end = 24.dp
                    )
                    .align(
                        Alignment.TopCenter
                    ),

                horizontalAlignment =
                    Alignment.CenterHorizontally,

                verticalArrangement =
                    Arrangement.Top
            ) {

                Spacer(
                    modifier = Modifier
                        .height(
                            when {
                                screenHeight < 620.dp ->
                                    16.dp

                                screenHeight < 700.dp ->
                                    26.dp

                                else ->
                                    38.dp
                            }
                        )
                )

                // -------------------------------------------------------------
                // LOGO
                // -------------------------------------------------------------

                Image(
                    painter = painterResource(
                        id = R.drawable.startscreen
                    ),

                    contentDescription =
                        "App Lock",

                    modifier = Modifier
                        .sizeIn(
                            minWidth = 160.dp,
                            minHeight = 160.dp,
                            maxWidth = logoSize,
                            maxHeight = logoSize
                        )
                )

                Spacer(
                    modifier = Modifier
                        .height(
                            when {
                                screenHeight < 620.dp ->
                                    4.dp

                                else ->
                                    8.dp
                            }
                        )
                )

                // -------------------------------------------------------------
                // APP NAME
                // -------------------------------------------------------------

                Text(
                    text = buildAnnotatedString {

                        withStyle(
                            style = SpanStyle(
                                color =
                                    Color(0xFF333333),

                                fontWeight =
                                    FontWeight.Bold
                            )
                        ) {
                            append("App ")
                        }

                        withStyle(
                            style = SpanStyle(
                                color =
                                    Color(0xFF2196F3),

                                fontWeight =
                                    FontWeight.Bold
                            )
                        ) {
                            append("Lock")
                        }
                    },

                    fontSize = titleSize,

                    textAlign =
                        TextAlign.Center,

                    maxLines = 1
                )

                Spacer(
                    modifier = Modifier
                        .height(
                            when {
                                screenHeight < 620.dp ->
                                    6.dp

                                else ->
                                    9.dp
                            }
                        )
                )

                // -------------------------------------------------------------
                // DESCRIPTION
                // -------------------------------------------------------------

                Text(
                    text =
                        "Secure your apps. Protect your privacy.",

                    fontSize =
                        descriptionSize,

                    color =
                        Color.Gray,

                    textAlign =
                        TextAlign.Center,

                    maxLines = 1,

                    modifier = Modifier
                        .fillMaxWidth()
                )
            }

            // -----------------------------------------------------------------
            // BOTTOM LOADING AREA
            // -----------------------------------------------------------------

            Column(
                modifier = Modifier
                    .align(
                        Alignment.BottomCenter
                    )
                    .fillMaxWidth()
                    .windowInsetsPadding(
                        WindowInsets.navigationBars
                    )
                    .padding(
                        start = 32.dp,
                        end = 32.dp,
                        bottom = 20.dp
                    ),

                horizontalAlignment =
                    Alignment.CenterHorizontally
            ) {

                /*
                 * Loading animation does NOT use the full screen width.
                 *
                 * It uses a maximum width so it remains visually
                 * identical on wide and narrow phones.
                 */
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .sizeIn(
                            maxWidth = 260.dp
                        ),
                    contentAlignment =
                        Alignment.Center
                ) {

                    LottieAnimation(
                        composition = composition,

                        progress = {
                            progress
                        },

                        modifier = Modifier
                            .fillMaxWidth()
                            .height(25.dp)
                    )
                }

                Spacer(
                    modifier = Modifier
                        .height(10.dp)
                )

                Text(
                    text = when {
                        appInitialized && premiumState.checking -> stringResource(R.string.billing_checking)
                        premiumState.owned -> stringResource(R.string.billing_owned)
                        else -> "This action may contain ads"
                    },

                    fontSize =
                        14.sp,

                    color =
                        Color.Gray,

                    textAlign =
                        TextAlign.Center,

                    maxLines = 1,

                    modifier = Modifier
                        .fillMaxWidth()
                )
            }
        }
    }
}
