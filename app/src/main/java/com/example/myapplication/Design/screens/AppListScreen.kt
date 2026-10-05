package com.example.myapplication.Design.screens

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text

import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.R
import androidx.core.graphics.drawable.toBitmap


import com.example.myapplication.data.DataStoreManager

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext


data class AppItem(
    val applicationInfo: ApplicationInfo,
    val appName: String,
    val searchName: String,
    val iconBitmap: ImageBitmap?
)


object AppListCache {

    @Volatile
    private var apps: List<AppItem>? = null

    private val preloadMutex = Mutex()

    fun getApps(): List<AppItem>? {
        return apps
    }

    suspend fun preload(context: Context) {

        if (apps != null) {
            return
        }

        preloadMutex.withLock {

            if (apps != null) {
                return@withLock
            }

            try {

                val appContext =
                    context.applicationContext

                val result =
                    withContext(Dispatchers.IO) {

                        val pm =
                            appContext.packageManager

                        val launcherIntent =
                            Intent(Intent.ACTION_MAIN).apply {
                                addCategory(
                                    Intent.CATEGORY_LAUNCHER
                                )
                            }

                        val launcherApps =
                            pm.queryIntentActivities(
                                launcherIntent,
                                PackageManager.MATCH_ALL
                            )

                        launcherApps.mapNotNull { resolveInfo ->

                            try {

                                val applicationInfo =
                                    resolveInfo.activityInfo
                                        ?.applicationInfo
                                        ?: return@mapNotNull null

                                val packageName =
                                    applicationInfo.packageName

                                if (
                                    packageName ==
                                    appContext.packageName
                                ) {
                                    return@mapNotNull null
                                }

                                val appName =
                                    resolveInfo
                                        .loadLabel(pm)
                                        .toString()

                                val iconBitmap =
                                    try {

                                        pm.getApplicationIcon(
                                            applicationInfo
                                        )
                                            .toBitmap(
                                                64,
                                                64
                                            )
                                            .asImageBitmap()

                                    } catch (
                                        e: Exception
                                    ) {

                                        null
                                    }

                                AppItem(
                                    applicationInfo =
                                        applicationInfo,
                                    appName =
                                        appName,
                                    searchName =
                                        appName.lowercase(),
                                    iconBitmap =
                                        iconBitmap
                                )

                            } catch (
                                e: Exception
                            ) {

                                null
                            }
                        }
                            .distinctBy {
                                it.applicationInfo
                                    .packageName
                            }
                            .sortedBy {
                                it.searchName
                            }
                    }

                apps = result

            } catch (
                e: Exception
            ) {

                e.printStackTrace()
            }
        }
    }

    fun updateIcon(
        packageName: String,
        icon: ImageBitmap
    ) {

        val currentApps =
            apps ?: return

        apps =
            currentApps.map { appItem ->

                if (
                    appItem.applicationInfo
                        .packageName ==
                    packageName
                ) {

                    appItem.copy(
                        iconBitmap = icon
                    )

                } else {

                    appItem
                }
            }
    }

    fun clear() {
        apps = null
    }
}


@Composable
fun AppListScreen(
    context: Context
) {

    val appContext =
        remember {
            context.applicationContext
        }

    val scope =
        rememberCoroutineScope()

    val dataStore =
        remember {
            DataStoreManager(appContext)
        }

    var selectedTab by remember {
        mutableStateOf(0)
    }

    var searchText by remember {
        mutableStateOf("")
    }

    var apps by remember {

        mutableStateOf(
            AppListCache.getApps()
                ?: emptyList()
        )
    }

    LaunchedEffect(Unit) {

        val cachedApps =
            AppListCache.getApps()

        if (cachedApps != null) {

            apps = cachedApps

        } else {

            AppListCache.preload(
                appContext
            )

            AppListCache.getApps()
                ?.let { loadedApps ->

                    apps = loadedApps
                }
        }
    }

    val lockedApps by
    dataStore.lockedAppsFlow.collectAsState(
        initial = emptySet()
    )

    val normalizedSearch =
        remember(searchText) {

            searchText
                .trim()
                .lowercase()
        }

    val filteredApps =
        remember(
            apps,
            lockedApps,
            selectedTab,
            normalizedSearch
        ) {

            val tabApps =

                if (selectedTab == 0) {

                    apps.filter { appItem ->

                        !lockedApps.contains(
                            appItem.applicationInfo
                                .packageName
                        )
                    }

                } else {

                    apps.filter { appItem ->

                        lockedApps.contains(
                            appItem.applicationInfo
                                .packageName
                        )
                    }
                }

            if (
                normalizedSearch.isEmpty()
            ) {

                tabApps

            } else {

                tabApps.filter { appItem ->

                    appItem.searchName.contains(
                        normalizedSearch
                    )
                }
            }
        }


    /*
     * =========================================================
     * MAIN SCREEN
     * =========================================================
     *
     * statusBarsPadding() added here.
     *
     * This moves the complete AppList content below
     * the Android status bar.
     *
     * =========================================================
     */

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Color(0xFFF7F7F7)
            )
            .statusBarsPadding()
    ) {

        /*
         * =====================================================
         * TOP TITLE
         * =====================================================
         */

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 20.dp,
                    top = 15.dp
                ),
            verticalAlignment =
                Alignment.CenterVertically
        ) {

            Image(
                painter =
                    painterResource(
                        R.drawable.applock
                    ),
                contentDescription = null,
                modifier =
                    Modifier.size(24.dp)
            )

            Spacer(
                modifier =
                    Modifier.width(8.dp)
            )

            Text(
                text =
                    stringResource(
                        R.string.app_lock
                    ),
                color =
                    Color.Black,
                fontSize =
                    20.sp
            )
        }


        /*
         * =====================================================
         * TABS
         * =====================================================
         */

        TabRow(
            selectedTabIndex =
                selectedTab,

            containerColor =
                Color(0xFFF7F7F7),

            contentColor =
                Color(0xFF0396FF),

            modifier =
                Modifier.padding(
                    top = 18.dp
                ),

            indicator = { tabPositions ->

                if (
                    selectedTab <
                    tabPositions.size
                ) {

                    TabRowDefaults.Indicator(

                        modifier =
                            Modifier.tabIndicatorOffset(
                                tabPositions[
                                    selectedTab
                                ]
                            ),

                        color =
                            Color(0xFF0396FF),

                        height =
                            2.dp
                    )
                }
            }
        ) {


            /*
             * =================================================
             * UNLOCKED TAB
             * =================================================
             */

            Tab(
                selected =
                    selectedTab == 0,

                onClick = {
                    selectedTab = 0
                },

                modifier =
                    Modifier.height(42.dp)
            ) {

                Row(
                    verticalAlignment =
                        Alignment.CenterVertically,

                    horizontalArrangement =
                        Arrangement.Center
                ) {

                    Image(
                        painter =
                            painterResource(
                                R.drawable.unlock
                            ),

                        contentDescription =
                            stringResource(
                                R.string.unlocked
                            ),

                        colorFilter =
                            ColorFilter.tint(

                                if (
                                    selectedTab == 0
                                ) {

                                    Color(
                                        0xFF0396FF
                                    )

                                } else {

                                    Color(
                                        0xFFBDBDBD
                                    )
                                }
                            ),

                        modifier =
                            Modifier.size(18.dp)
                    )

                    Spacer(
                        modifier =
                            Modifier.width(5.dp)
                    )

                    Text(
                        text =
                            stringResource(
                                R.string.unlocked
                            ),

                        color =

                            if (
                                selectedTab == 0
                            ) {

                                Color(
                                    0xFF0396FF
                                )

                            } else {

                                Color(
                                    0xFFBDBDBD
                                )
                            },

                        fontSize =
                            16.sp
                    )
                }
            }


            /*
             * =================================================
             * LOCKED TAB
             * =================================================
             */

            Tab(
                selected =
                    selectedTab == 1,

                onClick = {
                    selectedTab = 1
                },

                modifier =
                    Modifier.height(42.dp)
            ) {

                Row(
                    verticalAlignment =
                        Alignment.CenterVertically,

                    horizontalArrangement =
                        Arrangement.Center
                ) {

                    Image(
                        painter =
                            painterResource(
                                R.drawable.locked
                            ),

                        contentDescription =
                            stringResource(
                                R.string.locked
                            ),

                        colorFilter =
                            ColorFilter.tint(

                                if (
                                    selectedTab == 1
                                ) {

                                    Color(
                                        0xFF0396FF
                                    )

                                } else {

                                    Color(
                                        0xFFBDBDBD
                                    )
                                }
                            ),

                        modifier =
                            Modifier.size(18.dp)
                    )

                    Spacer(
                        modifier =
                            Modifier.width(5.dp)
                    )

                    Text(
                        text =
                            stringResource(
                                R.string.locked
                            ),

                        color =

                            if (
                                selectedTab == 1
                            ) {

                                Color(
                                    0xFF0396FF
                                )

                            } else {

                                Color(
                                    0xFFBDBDBD
                                )
                            },

                        fontSize =
                            16.sp
                    )
                }
            }
        }


        /*
         * =====================================================
         * SEARCH BAR
         * =====================================================
         */

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 14.dp,
                    end = 14.dp,
                    top = 15.dp
                )
                .height(39.dp)
                .clip(
                    RoundedCornerShape(
                        22.dp
                    )
                )
                .background(
                    Color(0xFFF7F7F7)
                )
                .border(
                    width = 2.dp,
                    color = Color.White,
                    shape =
                        RoundedCornerShape(
                            22.dp
                        )
                )
        ) {

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = 12.dp,
                        end = 12.dp
                    ),

                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Icon(
                    imageVector =
                        Icons.Default.Search,

                    contentDescription =
                        stringResource(
                            R.string.search
                        ),

                    tint =
                        Color(0xFFBDBDBD),

                    modifier =
                        Modifier.size(19.dp)
                )

                Spacer(
                    modifier =
                        Modifier.width(8.dp)
                )

                BasicTextField(
                    value =
                        searchText,

                    onValueChange = {
                        searchText = it
                    },

                    singleLine = true,

                    textStyle =
                        TextStyle(
                            color =
                                Color(0xFF555555),
                            fontSize =
                                14.sp
                        ),

                    cursorBrush =
                        SolidColor(
                            Color(0xFF0396FF)
                        ),

                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxHeight(),

                    decorationBox = {
                            innerTextField ->

                        Box(
                            modifier =
                                Modifier.fillMaxSize(),

                            contentAlignment =
                                Alignment.CenterStart
                        ) {

                            if (
                                searchText.isEmpty()
                            ) {

                                Text(
                                    text =
                                        stringResource(
                                            R.string.search
                                        ),

                                    color =
                                        Color(
                                            0xFFBDBDBD
                                        ),

                                    fontSize =
                                        14.sp
                                )
                            }

                            innerTextField()
                        }
                    }
                )
            }
        }


        Spacer(
            modifier =
                Modifier.height(10.dp)
        )


        /*
         * =====================================================
         * GENERAL TITLE
         * =====================================================
         */

        Text(
            text =
                stringResource(
                    R.string.general
                ),

            color =
                Color(0xFF878585),

            fontSize =
                14.sp,

            modifier =
                Modifier.padding(
                    start = 18.dp,
                    top = 7.dp,
                    bottom = 3.dp
                )
        )


        Spacer(
            modifier =
                Modifier.height(18.dp)
        )


        /*
         * =====================================================
         * EMPTY LOCKED APP SCREEN
         * =====================================================
         */

        if (
            selectedTab == 1 &&
            filteredApps.isEmpty()
        ) {

            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .navigationBarsPadding(),

                horizontalAlignment =
                    Alignment.CenterHorizontally,

                verticalArrangement =
                    Arrangement.Center
            ) {

                Image(
                    painter =
                        painterResource(
                            R.drawable.applistlock
                        ),

                    contentDescription =
                        null,

                    modifier =
                        Modifier.size(90.dp)
                )

                Spacer(
                    modifier =
                        Modifier.height(12.dp)
                )

                Text(
                    text =
                        "No locked app found",

                    color =
                        Color(0xFF878585),

                    fontSize =
                        15.sp
                )
            }

        } else {


            /*
             * =================================================
             * APP LIST
             * =================================================
             */

            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),

                verticalArrangement =
                    Arrangement.spacedBy(
                        10.dp
                    ),

                contentPadding =
                    PaddingValues(
                        start = 14.dp,
                        end = 14.dp,
                        top = 4.dp,
                        bottom = 16.dp
                    )
            ) {

                items(
                    items =
                        filteredApps,

                    key = { appItem ->

                        appItem.applicationInfo
                            .packageName
                    }
                ) { appItem ->

                    val appName =
                        appItem.appName

                    val packageName =
                        appItem.applicationInfo
                            .packageName

                    val isLocked =
                        lockedApps.contains(
                            packageName
                        )

                    val cardShape =
                        RoundedCornerShape(
                            12.dp
                        )


                    /*
                     * =========================================
                     * APP CARD
                     * =========================================
                     */

                    Card(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .shadow(
                                    elevation = 5.dp,
                                    shape =
                                        cardShape,
                                    clip = false,

                                    ambientColor =
                                        Color.Black.copy(
                                            alpha = 0.10f
                                        ),

                                    spotColor =
                                        Color.Black.copy(
                                            alpha = 0.10f
                                        )
                                )
                                .clip(
                                    cardShape
                                ),

                        shape =
                            cardShape,

                        colors =
                            CardDefaults.cardColors(
                                containerColor =
                                    Color.White
                            ),

                        elevation =
                            CardDefaults.cardElevation(
                                defaultElevation =
                                    2.dp,

                                pressedElevation =
                                    1.dp
                            )
                    ) {

                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(58.dp)
                                    .padding(
                                        start = 12.dp,
                                        end = 10.dp
                                    ),

                            verticalAlignment =
                                Alignment.CenterVertically
                        ) {


                            /*
                             * =================================
                             * APP ICON
                             * =================================
                             */

                            if (
                                appItem.iconBitmap != null
                            ) {

                                Image(
                                    bitmap =
                                        appItem.iconBitmap,

                                    contentDescription =
                                        null,

                                    modifier =
                                        Modifier.size(
                                            32.dp
                                        )
                                )

                            } else {

                                Spacer(
                                    modifier =
                                        Modifier.size(
                                            32.dp
                                        )
                                )
                            }


                            Spacer(
                                modifier =
                                    Modifier.width(
                                        18.dp
                                    )
                            )


                            /*
                             * =================================
                             * APP NAME
                             * =================================
                             */

                            Text(
                                text =
                                    appName,

                                modifier =
                                    Modifier.weight(
                                        1f
                                    ),

                                color =
                                    Color(0xFF555555),

                                fontSize =
                                    15.sp,

                                maxLines =
                                    1
                            )


                            /*
                             * =================================
                             * LOCK / UNLOCK BUTTON
                             * =================================
                             */

                            IconButton(
                                onClick = {

                                    if (isLocked) {

                                        scope.launch {

                                            dataStore
                                                .removeLockedApp(
                                                    packageName
                                                )
                                        }

                                    } else {

                                        scope.launch {

                                            dataStore
                                                .saveLockedApp(
                                                    packageName
                                                )
                                        }
                                    }
                                },

                                modifier =
                                    Modifier.size(
                                        30.dp
                                    )
                            ) {

                                Image(
                                    painter =
                                        painterResource(

                                            id =
                                                if (
                                                    isLocked
                                                ) {

                                                    R.drawable.locked

                                                } else {

                                                    R.drawable.unlock
                                                }
                                        ),

                                    contentDescription =

                                        if (
                                            isLocked
                                        ) {

                                            stringResource(
                                                R.string.unlock_app,
                                                appName
                                            )

                                        } else {

                                            stringResource(
                                                R.string.lock_app,
                                                appName
                                            )
                                        },

                                    modifier =
                                        Modifier.size(
                                            21.dp
                                        )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}