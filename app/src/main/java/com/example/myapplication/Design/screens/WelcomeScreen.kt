package com.example.myapplication.Design.screens

import android.app.Activity
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.myapplication.Ads.AdConfig
import com.example.myapplication.R
import com.example.myapplication.viewmodel.RemoteConfigViewModel
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.logEvent
import kotlinx.coroutines.delay

private const val TAG = "AppLockAdMob"

@Composable
fun WelcomeScreen(
    navController: NavController,
    remoteConfigViewModel: RemoteConfigViewModel = viewModel()
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val analytics = remember { FirebaseAnalytics.getInstance(context) }
    var isLoadingAd by remember { mutableStateOf(false) }
    var navigationDone by remember { mutableStateOf(false) }

    fun goToLanguages() {
        if (navigationDone) return
        navigationDone = true
        Log.d(TAG, "Navigating to languagesSetup")
        navController.navigate("languagesSetup") {
            popUpTo("welcomeScreen") { inclusive = true }
            launchSingleTop = true
        }
    }

    if (navigationDone) {
        BackHandler {}
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
        )
        return
    }

    if (isLoadingAd) {
        BackHandler {}
        LaunchedEffect(Unit) {
            Log.d(TAG, "Starting interstitial ad flow")
            analytics.logEvent("interstitial_ad_loading") {
                param("ad_type", "interstitial")
                param("screen", "welcome_screen")
            }

            val adRequest = AdRequest.Builder().build()
            var callbackFinished = false

            InterstitialAd.load(
                context,
                AdConfig.INTERSTITIAL_AD_UNIT_ID,
                adRequest,
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(interstitialAd: InterstitialAd) {
                        if (navigationDone || callbackFinished) return
                        callbackFinished = true
                        Log.d(TAG, "INTERSTITIAL AD LOADED")

                        analytics.logEvent("interstitial_ad_loaded") {
                            param("ad_type", "interstitial")
                            param("screen", "welcome_screen")
                        }

                        interstitialAd.fullScreenContentCallback = object : FullScreenContentCallback() {
                            override fun onAdShowedFullScreenContent() {
                                Log.d(TAG, "INTERSTITIAL AD SHOWN")
                                analytics.logEvent("interstitial_ad_shown") {
                                    param("ad_type", "interstitial")
                                    param("screen", "welcome_screen")
                                }
                            }

                            override fun onAdDismissedFullScreenContent() {
                                Log.d(TAG, "INTERSTITIAL AD CLOSED")
                                analytics.logEvent("interstitial_ad_closed") {
                                    param("ad_type", "interstitial")
                                    param("screen", "welcome_screen")
                                }
                                goToLanguages()
                            }

                            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                                Log.e(TAG, "INTERSTITIAL AD FAILED TO SHOW")
                                Log.e(TAG, "Code = ${adError.code}")
                                Log.e(TAG, "Message = ${adError.message}")

                                analytics.logEvent("interstitial_ad_show_failed") {
                                    param("ad_type", "interstitial")
                                    param("error_code", adError.code.toString())
                                    param("error_message", adError.message)
                                    param("screen", "welcome_screen")
                                }

                                goToLanguages()
                            }
                        }

                        if (activity != null && !activity.isFinishing && !activity.isDestroyed) {
                            try {
                                Log.d(TAG, "Showing interstitial ad")
                                interstitialAd.show(activity)
                            } catch (e: Exception) {
                                Log.e(TAG, "Exception while showing ad", e)
                                goToLanguages()
                            }
                        } else {
                            Log.e(TAG, "Activity unavailable")
                            goToLanguages()
                        }
                    }

                    override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                        if (navigationDone || callbackFinished) return
                        callbackFinished = true
                        Log.e(TAG, "INTERSTITIAL AD FAILED TO LOAD")
                        Log.e(TAG, "Code = ${loadAdError.code}")
                        Log.e(TAG, "Message = ${loadAdError.message}")
                        Log.e(TAG, "Domain = ${loadAdError.domain}")

                        analytics.logEvent("interstitial_ad_failed") {
                            param("ad_type", "interstitial")
                            param("error_code", loadAdError.code.toString())
                            param("error_message", loadAdError.message)
                            param("screen", "welcome_screen")
                        }

                        goToLanguages()
                    }
                }
            )

            delay(20_000)

            if (!navigationDone) {
                Log.e(TAG, "INTERSTITIAL AD TIMEOUT")
                analytics.logEvent("interstitial_ad_timeout") {
                    param("ad_type", "interstitial")
                    param("timeout_seconds", "20")
                    param("screen", "welcome_screen")
                }
                goToLanguages()
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(42.dp),
                    color = Color(0xFF2196F3),
                    strokeWidth = 4.dp
                )

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Test Ad is Loading...",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF333333)
                )
            }
        }
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White.copy(alpha = 0.9f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painter = painterResource(id = R.drawable.startscreen),
                    contentDescription = "App Lock",
                    modifier = Modifier.size(270.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

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

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Secure your apps. Protect your privacy.",
                    fontSize = 13.sp,
                    color = Color.Gray
                )
            }

            Button(
                onClick = {
                    if (!isLoadingAd && !navigationDone) {
                        Log.d(TAG, "Get Started clicked")
                        remoteConfigViewModel.loadConfig { showAd ->
                            if (navigationDone) return@loadConfig

                            if (showAd) {
                                Log.d(TAG, "Remote Config: Interstitial ENABLED")
                                isLoadingAd = true
                            } else {
                                Log.d(TAG, "Remote Config: Interstitial DISABLED")
                                goToLanguages()
                            }
                        }
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(
                        start = 14.dp,
                        end = 14.dp,
                        bottom = 18.dp
                    )
                    .height(51.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2196F3)
                )
            ) {
                Text(
                    text = "Get Started",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        }
    }
}