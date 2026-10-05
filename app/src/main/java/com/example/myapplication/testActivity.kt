package com.example.myapplication

import android.app.Activity
import android.os.Bundle
import android.util.Log

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size

import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.myapplication.ui.theme.MyApplicationTheme


import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

import kotlinx.coroutines.delay



private const val TAG = "AppLockAdMob"

private const val TEST_INTERSTITIAL_AD_UNIT_ID =
    "ca-app-pub-3940256099942544/1033173712"

private const val TEST_BANNER_AD_UNIT_ID =
    "ca-app-pub-3940256099942544/9214589741"

private const val TEST_DEVICE_ID =
    "E5CA263188C38AF6BD96C4C84E9600BB"


class testactivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        /*
         * ====================================================
         * ADMOB INITIALIZATION
         * ====================================================
         */

        try {

            val requestConfiguration =
                RequestConfiguration.Builder()
                    .setTestDeviceIds(
                        listOf(
                            TEST_DEVICE_ID
                        )
                    )
                    .build()

            MobileAds.setRequestConfiguration(
                requestConfiguration
            )

            Log.d(
                TAG,
                "TEST DEVICE CONFIGURED"
            )

            Log.d(
                TAG,
                "Test Device ID = $TEST_DEVICE_ID"
            )

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Failed to configure test device",
                e
            )
        }


        MobileAds.initialize(this) {

            Log.d(
                TAG,
                "AdMob initialized successfully"
            )
        }


        /*
         * ====================================================
         * COMPOSE
         * ====================================================
         */

        setContent {

           MyApplicationTheme {

                Scaffold(
                    modifier =
                        Modifier.fillMaxSize()
                ) { innerPadding ->

                    Greeting(
                        name = "Android",
                        modifier =
                            Modifier.padding(
                                innerPadding
                            )
                    )
                }
            }
        }
    }
}


/*
 * ============================================================
 * ADAPTIVE BANNER
 * ============================================================
 */

@Composable
fun AdaptiveBannerAdm(
    modifier: Modifier = Modifier
) {

    val context =
        LocalContext.current

    val adView =
        remember {
            AdView(context)
        }

    DisposableEffect(adView) {

        adView.adUnitId =
            TEST_BANNER_AD_UNIT_ID

        val displayMetrics =
            context.resources.displayMetrics

        val screenWidthDp =
            (
                    displayMetrics.widthPixels /
                            displayMetrics.density
                    ).toInt()

        val adSize =
            AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(
                context,
                screenWidthDp
            )

        adView.setAdSize(
            adSize
        )

        adView.adListener =
            object : AdListener() {

                override fun onAdLoaded() {

                    Log.d(
                        TAG,
                        "================================"
                    )

                    Log.d(
                        TAG,
                        "ADAPTIVE BANNER LOADED"
                    )

                    Log.d(
                        TAG,
                        "Ad Unit ID = $TEST_BANNER_AD_UNIT_ID"
                    )

                    Log.d(
                        TAG,
                        "Ad Size = ${adView.adSize}"
                    )

                    Log.d(
                        TAG,
                        "================================"
                    )
                }

                override fun onAdFailedToLoad(
                    adError: LoadAdError
                ) {

                    Log.e(
                        TAG,
                        "================================"
                    )

                    Log.e(
                        TAG,
                        "ADAPTIVE BANNER FAILED"
                    )

                    Log.e(
                        TAG,
                        "Error Code = ${adError.code}"
                    )

                    Log.e(
                        TAG,
                        "Error Message = ${adError.message}"
                    )

                    Log.e(
                        TAG,
                        "Error Domain = ${adError.domain}"
                    )

                    Log.e(
                        TAG,
                        "Response Info = ${adError.responseInfo}"
                    )

                    Log.e(
                        TAG,
                        "================================"
                    )
                }

                override fun onAdOpened() {

                    Log.d(
                        TAG,
                        "Adaptive banner opened"
                    )
                }

                override fun onAdClosed() {

                    Log.d(
                        TAG,
                        "Adaptive banner closed"
                    )
                }

                override fun onAdClicked() {

                    Log.d(
                        TAG,
                        "Adaptive banner clicked"
                    )
                }

                override fun onAdImpression() {

                    Log.d(
                        TAG,
                        "Adaptive banner impression"
                    )
                }
            }

        val adRequest =
            AdRequest.Builder()
                .build()

        Log.d(
            TAG,
            "Loading Adaptive Banner..."
        )

        Log.d(
            TAG,
            "Banner Ad Unit ID = $TEST_BANNER_AD_UNIT_ID"
        )

        adView.loadAd(
            adRequest
        )

        onDispose {

            Log.d(
                TAG,
                "Destroying Adaptive Banner..."
            )

            adView.destroy()
        }
    }

    AndroidView(
        factory = {
            adView
        },
        modifier = modifier
    )
}


/*
 * ============================================================
 * MAIN SCREEN
 * ============================================================
 */

@Composable
fun Greeting(
    name: String,
    modifier: Modifier = Modifier
) {

    val context =
        LocalContext.current

    val activity =
        context as? Activity


    /*
     * ========================================================
     * STATES
     * ========================================================
     */

    var isLoadingAd by remember {
        mutableStateOf(false)
    }

    var navigationDone by remember {
        mutableStateOf(false)
    }


    /*
     * ========================================================
     * GO TO MAIN SCREEN
     * ========================================================
     */

    fun continueToMainScreen() {

        if (navigationDone) {
            return
        }

        navigationDone = true

        Log.d(
            TAG,
            "Continuing to main screen"
        )
    }


    /*
     * ========================================================
     * INTERSTITIAL AD
     * ========================================================
     */

    if (isLoadingAd) {

        BackHandler {

        }

        LaunchedEffect(Unit) {

            Log.d(
                TAG,
                "Start Screen clicked"
            )

            Log.d(
                TAG,
                "Starting interstitial ad flow..."
            )

            val adRequest =
                AdRequest.Builder()
                    .build()

            Log.d(
                TAG,
                "Loading official Google test interstitial..."
            )

            Log.d(
                TAG,
                "Ad Unit ID = $TEST_INTERSTITIAL_AD_UNIT_ID"
            )


            InterstitialAd.load(
                context,
                TEST_INTERSTITIAL_AD_UNIT_ID,
                adRequest,

                object :
                    InterstitialAdLoadCallback() {

                    override fun onAdLoaded(
                        interstitialAd: InterstitialAd
                    ) {

                        if (navigationDone) {
                            return
                        }

                        Log.d(
                            TAG,
                            "================================"
                        )

                        Log.d(
                            TAG,
                            "INTERSTITIAL AD LOADED SUCCESSFULLY"
                        )

                        Log.d(
                            TAG,
                            "================================"
                        )


                        /*
                         * FULL SCREEN CALLBACK
                         */

                        interstitialAd.fullScreenContentCallback =
                            object :
                                FullScreenContentCallback() {

                                override fun onAdShowedFullScreenContent() {

                                    Log.d(
                                        TAG,
                                        "INTERSTITIAL AD SHOWN"
                                    )
                                }


                                override fun onAdDismissedFullScreenContent() {

                                    Log.d(
                                        TAG,
                                        "INTERSTITIAL AD CLOSED"
                                    )

                                    continueToMainScreen()
                                }


                                override fun onAdFailedToShowFullScreenContent(
                                    adError: AdError
                                ) {

                                    Log.e(
                                        TAG,
                                        "INTERSTITIAL AD FAILED TO SHOW"
                                    )

                                    Log.e(
                                        TAG,
                                        "Show Code = ${adError.code}"
                                    )

                                    Log.e(
                                        TAG,
                                        "Show Message = ${adError.message}"
                                    )

                                    Log.e(
                                        TAG,
                                        "Show Domain = ${adError.domain}"
                                    )

                                    continueToMainScreen()
                                }
                            }


                        /*
                         * SHOW AD
                         */

                        if (
                            activity != null &&
                            !activity.isFinishing &&
                            !activity.isDestroyed
                        ) {

                            try {

                                Log.d(
                                    TAG,
                                    "Showing interstitial ad..."
                                )

                                interstitialAd.show(
                                    activity
                                )

                            } catch (e: Exception) {

                                Log.e(
                                    TAG,
                                    "Exception while showing interstitial",
                                    e
                                )

                                continueToMainScreen()
                            }

                        } else {

                            Log.e(
                                TAG,
                                "Activity is not available"
                            )

                            continueToMainScreen()
                        }
                    }


                    /*
                     * AD FAILED
                     */

                    override fun onAdFailedToLoad(
                        loadAdError: LoadAdError
                    ) {

                        Log.e(
                            TAG,
                            "================================"
                        )

                        Log.e(
                            TAG,
                            "INTERSTITIAL AD FAILED TO LOAD"
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

                        Log.e(
                            TAG,
                            "ResponseInfo = ${loadAdError.responseInfo}"
                        )

                        Log.e(
                            TAG,
                            "================================"
                        )

                        continueToMainScreen()
                    }
                }
            )


            /*
             * =================================================
             * 20 SECOND SAFETY TIMEOUT
             * =================================================
             */

            delay(20_000)

            if (!navigationDone) {

                Log.e(
                    TAG,
                    "INTERSTITIAL AD LOAD TIMEOUT"
                )

                continueToMainScreen()
            }
        }


        /*
         * ====================================================
         * LOADING SCREEN
         * ====================================================
         */

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Color.White
                    ),

            contentAlignment =
                Alignment.Center
        ) {

            Column(
                horizontalAlignment =
                    Alignment.CenterHorizontally,

                verticalArrangement =
                    Arrangement.Center
            ) {

                CircularProgressIndicator(
                    modifier =
                        Modifier.size(
                            42.dp
                        ),

                    color =
                        Color(0xFF2196F3),

                    strokeWidth =
                        4.dp
                )

                Spacer(
                    modifier =
                        Modifier.height(
                            20.dp
                        )
                )

                Text(
                    text =
                        "Test Ad is Loading...",

                    fontSize =
                        17.sp,

                    color =
                        Color(0xFF333333)
                )
            }
        }

    } else {


        /*
         * ====================================================
         * NORMAL START SCREEN
         * ====================================================
         */

        Box(
            modifier =
                modifier
                    .fillMaxSize()
                    .background(
                        Color.White
                    )
        ) {


            /*
             * =================================================
             * CONTENT
             * =================================================
             */

            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(
                            horizontal = 20.dp
                        ),

                horizontalAlignment =
                    Alignment.CenterHorizontally,

                verticalArrangement =
                    Arrangement.Center
            ) {

                Text(
                    text =
                        "Hello $name!",

                    fontSize =
                        28.sp
                )

                Spacer(
                    modifier =
                        Modifier.height(
                            25.dp
                        )
                )


                /*
                 * START BUTTON
                 *
                 * IS BUTTON KO PRESS KARNE PAR
                 * INTERSTITIAL AD SHOW HOGI
                 */

                Button(
                    onClick = {

                        if (!isLoadingAd) {

                            Log.d(
                                TAG,
                                "START SCREEN BUTTON PRESSED"
                            )

                            isLoadingAd = true
                        }
                    },

                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(
                                52.dp
                            ),

                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor =
                                Color(0xFF2196F3)
                        )
                ) {

                    Text(
                        text =
                            "Start",

                        fontSize =
                            17.sp,

                        color =
                            Color.White
                    )
                }
            }


            /*
             * =================================================
             * ADAPTIVE BANNER
             * =================================================
             */

            Box(
                modifier =
                    Modifier
                        .align(
                            Alignment.BottomCenter
                        )
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(
                            start = 8.dp,
                            end = 8.dp,
                            bottom = 8.dp
                        )
            ) {

                AdaptiveBannerAdm(
                    modifier =
                        Modifier.fillMaxWidth()
                )
            }
        }
    }
}

