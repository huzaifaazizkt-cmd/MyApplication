package com.example.myapplication.Design.screens

import android.content.Context
import android.util.Log

import androidx.activity.compose.BackHandler

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController

import com.example.myapplication.R
import com.example.myapplication.viewmodel.RemoteConfigViewModel

import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError


private const val TAG = "AppLockAdMob"

private const val TEST_BANNER_AD_UNIT_ID =
    "ca-app-pub-3940256099942544/9214589741"


@Composable
fun MainScreen(
    context: Context,
    onIntruderClick: () -> Unit,
    onLanguageClick: () -> Unit,
    onResetPasswordClick: () -> Unit,
    openSettings: Boolean = false,
    navController: NavController
) {

    /*
     * ============================================================
     * REMOTE CONFIG
     * ============================================================
     */

    val remoteConfigViewModel: RemoteConfigViewModel =
        viewModel()

    val showBannerAd =
        remoteConfigViewModel.showBannerAd

    LaunchedEffect(Unit) {

        Log.d(
            TAG,
            "Loading Remote Config for banner"
        )

        remoteConfigViewModel
            .loadConfigWithBanner()
    }


    /*
     * ============================================================
     * SAVED STATE
     * ============================================================
     */

    val savedStateHandle =
        navController.currentBackStackEntry
            ?.savedStateHandle


    /*
     * ============================================================
     * SELECTED TAB
     *
     * 0 = Apps Lock
     * 1 = Vault
     * 2 = Settings
     * ============================================================
     */

    var selectedTab by remember {

        mutableIntStateOf(

            if (openSettings) {
                2
            } else {
                0
            }
        )
    }


    /*
     * ============================================================
     * VAULT PREVIEW STATE
     *
     * IMPORTANT:
     *
     * Ye state sirf banner ko hide/show karne ke liye hai.
     *
     * Hum VaultScreen ko dobara create nahi karenge.
     *
     * Is se preview ki local state reset nahi hogi.
     * ============================================================
     */

    var isVaultPreviewOpen by remember {

        mutableStateOf(false)
    }


    /*
     * ============================================================
     * SETTINGS PREVIOUS TAB
     * ============================================================
     */

    var settingsPreviousTab by remember {

        mutableIntStateOf(

            savedStateHandle
                ?.get<Int>("settingsPreviousTab")
                ?: 0
        )
    }


    /*
     * ============================================================
     * NAVIGATION FLAGS
     * ============================================================
     */

    val forceAppList =
        savedStateHandle
            ?.get<Boolean>("forceAppList")
            ?: false


    val returnToSettings =
        savedStateHandle
            ?.get<Boolean>("returnToSettings")
            ?: false


    /*
     * ============================================================
     * HANDLE SAVED NAVIGATION STATE
     * ============================================================
     */

    LaunchedEffect(
        openSettings,
        forceAppList,
        returnToSettings
    ) {

        when {

            /*
             * Force App List
             */

            forceAppList -> {

                selectedTab = 0

                settingsPreviousTab = 0

                savedStateHandle?.set(
                    "settingsPreviousTab",
                    0
                )

                savedStateHandle?.remove<Boolean>(
                    "forceAppList"
                )

                savedStateHandle?.remove<Boolean>(
                    "openSettings"
                )

                savedStateHandle?.remove<Boolean>(
                    "returnToSettings"
                )
            }


            /*
             * Return to Settings
             */

            returnToSettings -> {

                selectedTab = 2

                savedStateHandle?.remove<Boolean>(
                    "returnToSettings"
                )
            }


            /*
             * Open Settings
             */

            openSettings -> {

                selectedTab = 2

                savedStateHandle?.remove<Boolean>(
                    "openSettings"
                )
            }
        }
    }


    /*
     * ============================================================
     * TAB SELECTION
     * ============================================================
     */

    fun selectTab(tab: Int) {

        if (tab == selectedTab) {
            return
        }


        /*
         * Save previous tab before Settings.
         */

        if (
            tab == 2 &&
            (selectedTab == 0 || selectedTab == 1)
        ) {

            settingsPreviousTab =
                selectedTab

            savedStateHandle?.set(
                "settingsPreviousTab",
                selectedTab
            )
        }


        /*
         * If user changes tab,
         * preview state should not remain active.
         */

        isVaultPreviewOpen = false

        selectedTab = tab
    }


    /*
     * ============================================================
     * BACK HANDLER
     * ============================================================
     *
     * IMPORTANT:
     *
     * VaultScreen ka apna BackHandler preview ko close karega.
     *
     * Yahan hum preview ko forcibly recreate/dispose nahi karenge.
     * ============================================================
     */

    BackHandler {

        /*
         * Agar Vault preview open hai,
         * VaultScreen ka BackHandler handle karega.
         *
         * Yahan direct VaultScreen ko remove nahi karna.
         */

        if (isVaultPreviewOpen) {

            return@BackHandler
        }


        when (selectedTab) {

            /*
             * SETTINGS
             */

            2 -> {

                val previousTab =
                    savedStateHandle
                        ?.get<Int>("settingsPreviousTab")
                        ?: settingsPreviousTab

                selectedTab =
                    if (previousTab == 1) {
                        1
                    } else {
                        0
                    }
            }


            /*
             * VAULT
             */

            1 -> {

                selectedTab = 0

                savedStateHandle?.set(
                    "settingsPreviousTab",
                    0
                )
            }


            /*
             * APPS LOCK
             */

            0 -> {

                navController.navigate(
                    "exit"
                )
            }
        }
    }


    /*
     * ============================================================
     * MAIN CONTENT
     * ============================================================
     *
     * IMPORTANT FIX:
     *
     * Pehle code mein:
     *
     * if (isVaultPreviewOpen) {
     *     VaultScreen(...)
     * } else {
     *     VaultScreen(...)
     * }
     *
     * Is wajah se VaultScreen recreate ho sakta tha.
     *
     * Ab VaultScreen sirf EK baar isi content tree mein rahega.
     *
     * Is se previewImages aur previewImageIndex ki state
     * destroy nahi hogi.
     * ============================================================
     */

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {

        /*
         * ========================================================
         * MAIN SCREEN CONTENT
         * ========================================================
         */

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {

            when (selectedTab) {

                /*
                 * ==================================================
                 * APPS LOCK
                 * ==================================================
                 */

                0 -> {

                    AppListScreen(
                        context = context
                    )
                }


                /*
                 * ==================================================
                 * VAULT
                 * ==================================================
                 *
                 * IMPORTANT:
                 *
                 * VaultScreen ko yahan hamesha same composition
                 * mein rakha gaya hai.
                 *
                 * Preview open hone par sirf callback state
                 * update hogi.
                 *
                 * VaultScreen recreate nahi hoga.
                 * ==================================================
                 */

                1 -> {

                    VaultScreen(

                        onPreviewStateChange = { isOpen ->

                            isVaultPreviewOpen =
                                isOpen
                        }
                    )
                }


                /*
                 * ==================================================
                 * SETTINGS
                 * ==================================================
                 */

                2 -> {

                    SettingsScreen(

                        onIntruderClick =
                            onIntruderClick,

                        onLanguageClick =
                            onLanguageClick,

                        onResetPasswordClick =
                            onResetPasswordClick
                    )
                }
            }
        }


        /*
         * ============================================================
         * BANNER AD
         * ============================================================
         *
         * IMPORTANT FIX:
         *
         * Preview open hone par banner bilkul compose nahi hoga.
         *
         * Is se AdView preview ke upar/neeche interfere nahi karega.
         *
         * Preview close hone ke baad banner dobara aa jayega.
         *
         * Remote Config ka existing showBannerAd behavior same rahega.
         * ============================================================
         */

        if (
            showBannerAd &&
            !isVaultPreviewOpen
        ) {

            AdaptiveBannerAd(
                context = context,
                modifier = Modifier
                    .fillMaxWidth()
            )
        }


        /*
         * ============================================================
         * BOTTOM NAVIGATION
         * ============================================================
         *
         * Preview open hone par bottom navigation bhi hide hai,
         * taake image ko complete available area mile.
         *
         * Preview close hone ke baad same navigation wapas aa jayegi.
         * ============================================================
         */

        if (!isVaultPreviewOpen) {

            AppLockBottomNavigation(

                selectedTab = selectedTab,

                onTabSelected = { tab ->

                    selectTab(tab)
                },

                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(
                        WindowInsets.navigationBars
                    )
            )
        }
    }
}

@Composable
private fun AdaptiveBannerAd(
    context: Context,
    modifier: Modifier = Modifier
) {


    val adView = remember {

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

        val adWidthDp =
            screenWidthDp.coerceAtLeast(1)


        adView.setAdSize(

            AdSize
                .getCurrentOrientationAnchoredAdaptiveBannerAdSize(
                    context,
                    adWidthDp
                )
        )


        adView.adListener =
            object : AdListener() {

                override fun onAdLoaded() {

                    Log.d(
                        TAG,
                        "Adaptive banner loaded"
                    )
                }


                override fun onAdFailedToLoad(
                    adError: LoadAdError
                ) {

                    Log.e(
                        TAG,
                        "Adaptive banner failed: " +
                                "code=${adError.code}, " +
                                "message=${adError.message}"
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


        Log.d(
            TAG,
            "Loading adaptive banner"
        )

        adView.loadAd(
            AdRequest.Builder().build()
        )


        onDispose {

            Log.d(
                TAG,
                "Destroying adaptive banner"
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

@Composable
private fun AppLockBottomNavigation(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {

    Row(

        modifier = modifier
            .fillMaxWidth()
            .height(74.dp)
            .background(Color.White)
            .padding(
                horizontal = 28.dp,
                vertical = 10.dp
            ),

        horizontalArrangement =
            Arrangement.SpaceBetween,

        verticalAlignment =
            Alignment.CenterVertically
    ) {

        BottomNavigationItem(

            selected =
                selectedTab == 0,

            icon =
                R.drawable.group6,

            text =
                stringResource(
                    R.string.apps_lock
                ),

            onClick = {

                onTabSelected(0)
            }
        )

        BottomNavigationItem(

            selected =
                selectedTab == 1,

            icon =
                R.drawable.group8,

            text =
                stringResource(
                    R.string.vault
                ),

            onClick = {

                onTabSelected(1)
            }
        )


        BottomNavigationItem(

            selected =
                selectedTab == 2,

            icon =
                R.drawable.group7,

            text =
                stringResource(
                    R.string.settings
                ),

            onClick = {

                onTabSelected(2)
            }
        )
    }
}




@Composable
private fun BottomNavigationItem(
    selected: Boolean,
    icon: Int,
    text: String,
    onClick: () -> Unit
) {

    val blueColor =
        Color(0xFF2196F3)

    val grayColor =
        Color(0xFFBDBDBD)


    Box(

        modifier = Modifier
            .height(38.dp)

            .clickable(

                indication = null,

                interactionSource =
                    remember {
                        MutableInteractionSource()
                    }

            ) {

                onClick()
            }

            .background(

                color =
                    if (selected) {
                        blueColor
                    } else {
                        Color.Transparent
                    },

                shape =
                    RoundedCornerShape(
                        22.dp
                    )
            )

            .padding(

                horizontal =
                    if (selected) {
                        12.dp
                    } else {
                        5.dp
                    }
            ),

        contentAlignment =
            Alignment.Center
    ) {

        Row(

            verticalAlignment =
                Alignment.CenterVertically
        ) {


            Image(

                painter =
                    painterResource(
                        id = icon
                    ),

                contentDescription =
                    text,

                colorFilter =
                    ColorFilter.tint(

                        if (selected) {
                            Color.White
                        } else {
                            grayColor
                        }
                    ),

                modifier = Modifier
                    .size(22.dp)
                    .alpha(if (selected) { 1f } else { 0.9f })
            )

            Spacer(modifier = Modifier.width(5.dp))

            Text(
                text = text,
                color =
                    if (selected) { Color.White } else { grayColor },
                fontSize = 12.sp
            )
        }
    }
}