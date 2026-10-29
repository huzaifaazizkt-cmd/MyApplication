
package com.example.myapplication.Design.screens

import android.content.Context
import android.widget.Toast

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController

import com.example.myapplication.R
import com.example.myapplication.navigation.openPremium
import com.example.myapplication.viewmodel.AppListViewModel
import com.example.myapplication.data.AppListItem
import com.example.myapplication.data.AppIcons
import com.example.myapplication.data.filterAppList
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.Lifecycle
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity



@Composable
fun AppListScreen(
    context: Context,
    navController: NavController
) {

    val appContext = context.applicationContext
    val model: AppListViewModel = viewModel()
    val ui by model.state.collectAsStateWithLifecycle()
    val access by model.access.collectAsStateWithLifecycle()
    val showPermissions by model.showPermissions.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var searchText by rememberSaveable { mutableStateOf("") }
    val unlockedScroll = rememberLazyListState()
    val lockedScroll = rememberLazyListState()
    val lockedApps = ui.locked
    val filteredApps = remember(ui.apps, lockedApps, selectedTab, searchText) {
        filterAppList(ui.apps, lockedApps, selectedTab == 1, searchText)
    }
    DisposableEffect(lifecycle, model) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) model.refresh()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(model) {
        model.messages.collect { Toast.makeText(appContext, it, Toast.LENGTH_SHORT).show() }
    }
    access?.let { if (showPermissions) PermissionRequiredDialog(it) { model.dismissPermissions() } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F7F7))
            .statusBarsPadding()
    ) {

        // App Lock title and PRO card.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 20.dp,
                    end = 16.dp,
                    top = 15.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Image(
                painter = painterResource(R.drawable.applock),
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = stringResource(R.string.app_lock),
                color = Color.Black,
                fontSize = 20.sp
            )

            Spacer(modifier = Modifier.weight(1f))

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(30.dp))
                    .background(Color(0xFF0396FF))

                    .clickable {
                        navController.openPremium(returnTab = 0)
                    }
                    .padding(
                        horizontal = 12.dp,
                        vertical = 9.dp
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {

                Image(
                    painter = painterResource(R.drawable.pro),
                    contentDescription = "Pro",
                    modifier = Modifier.size(20.dp)
                )

                Spacer(modifier = Modifier.width(6.dp))

                Text(
                    text = "PRO",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color(0xFFF7F7F7),
            contentColor = Color(0xFF0396FF),
            modifier = Modifier.padding(top = 18.dp),
            indicator = { tabPositions ->
                if (selectedTab < tabPositions.size) {
                    TabRowDefaults.Indicator(
                        modifier = Modifier.tabIndicatorOffset(
                            tabPositions[selectedTab]
                        ),
                        color = Color(0xFF0396FF),
                        height = 2.dp
                    )
                }
            }
        ) {

            Tab(
                selected = selectedTab == 0,
                onClick = {
                    selectedTab = 0
                },
                modifier = Modifier.height(42.dp)
            ) {

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {

                    Image(
                        painter = painterResource(R.drawable.unlock),
                        contentDescription = stringResource(
                            R.string.unlocked
                        ),
                        colorFilter = ColorFilter.tint(
                            if (selectedTab == 0) {
                                Color(0xFF0396FF)
                            } else {
                                Color(0xFFBDBDBD)
                            }
                        ),
                        modifier = Modifier.size(18.dp)
                    )

                    Spacer(modifier = Modifier.width(5.dp))

                    Text(
                        text = stringResource(R.string.unlocked),
                        color = if (selectedTab == 0) {
                            Color(0xFF0396FF)
                        } else {
                            Color(0xFFBDBDBD)
                        },
                        fontSize = 16.sp
                    )
                }
            }

            Tab(
                selected = selectedTab == 1,
                onClick = {
                    selectedTab = 1
                },
                modifier = Modifier.height(42.dp)
            ) {

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {

                    Image(
                        painter = painterResource(R.drawable.locked),
                        contentDescription = stringResource(
                            R.string.locked
                        ),
                        colorFilter = ColorFilter.tint(
                            if (selectedTab == 1) {
                                Color(0xFF0396FF)
                            } else {
                                Color(0xFFBDBDBD)
                            }
                        ),
                        modifier = Modifier.size(18.dp)
                    )

                    Spacer(modifier = Modifier.width(5.dp))

                    Text(
                        text = stringResource(R.string.locked),
                        color = if (selectedTab == 1) {
                            Color(0xFF0396FF)
                        } else {
                            Color(0xFFBDBDBD)
                        },
                        fontSize = 16.sp
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 14.dp,
                    end = 14.dp,
                    top = 15.dp
                )
                .height(39.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xFFF7F7F7))
                .border(
                    width = 2.dp,
                    color = Color.White,
                    shape = RoundedCornerShape(22.dp)
                )
        ) {

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = 12.dp,
                        end = 12.dp
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {

                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = stringResource(
                        R.string.search
                    ),
                    tint = Color(0xFFBDBDBD),
                    modifier = Modifier.size(19.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                BasicTextField(
                    value = searchText,
                    onValueChange = {
                        searchText = it
                    },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = Color(0xFF555555),
                        fontSize = 14.sp
                    ),
                    cursorBrush = SolidColor(
                        Color(0xFF0396FF)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    decorationBox = { innerTextField ->

                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.CenterStart
                        ) {

                            if (searchText.isEmpty()) {
                                Text(
                                    text = stringResource(
                                        R.string.search
                                    ),
                                    color = Color(0xFFBDBDBD),
                                    fontSize = 14.sp
                                )
                            }

                            innerTextField()
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (ui.protectionEnabled && access?.ready == false && lockedApps.isNotEmpty()) {
            Text(stringResource(R.string.protection_service_required), color = Color(0xFF666666), fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 18.dp).clickable { model.requestPermissions() })
        }

        Text(
            text = stringResource(R.string.general),
            color = Color(0xFF878585),
            fontSize = 14.sp,
            modifier = Modifier.padding(
                start = 18.dp,
                top = 7.dp,
                bottom = 3.dp
            )
        )

        Spacer(modifier = Modifier.height(18.dp))

        if (filteredApps.isEmpty()) {

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {

                Image(
                    painter = painterResource(
                        R.drawable.applistlock
                    ),
                    contentDescription = null,
                    modifier = Modifier.size(90.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = stringResource(if (ui.loading) R.string.loading else if (ui.failed) R.string.apps_load_failed else R.string.apps_empty),
                    color = Color(0xFF878585),
                    fontSize = 15.sp,
                    modifier = Modifier.clickable(enabled = ui.failed) { model.refresh(force = true) }
                )
            }

        } else {

            LazyColumn(
                state = if (selectedTab == 0) unlockedScroll else lockedScroll,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(
                    start = 14.dp,
                    end = 14.dp,
                    top = 4.dp,
                    bottom = 16.dp
                )
            ) {

                items(filteredApps, key = { it.packageName }, contentType = { "app" }) { appItem ->
                    AppListRow(appItem, appItem.packageName in lockedApps) {
                        model.setLocked(appItem.packageName, appItem.packageName !in lockedApps)
                    }
                }
            }
        }
    }
}

@Composable
private fun AppListRow(item: AppListItem, locked: Boolean, onToggle: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val pixels = with(LocalDensity.current) { 32.dp.roundToPx().coerceIn(32, 192) }
    val bitmap by produceState<android.graphics.Bitmap?>(null, item.packageName, item.iconVersion, pixels) {
        value = AppIcons.load(context, item, pixels)
    }
    val icon = remember(bitmap) { bitmap?.asImageBitmap() }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 5.dp)
    ) {
        Row(Modifier.fillMaxWidth().height(58.dp).padding(start = 12.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) Image(icon, null, Modifier.size(32.dp)) else Spacer(Modifier.size(32.dp))
            Spacer(Modifier.width(18.dp))
            Text(item.appName, Modifier.weight(1f), color = Color(0xFF555555), fontSize = 15.sp,
                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            IconButton(onClick = onToggle, modifier = Modifier.size(40.dp)) {
                Image(painterResource(if (locked) R.drawable.locked else R.drawable.unlock),
                    stringResource(if (locked) R.string.unlock_app else R.string.lock_app, item.appName),
                    modifier = Modifier.size(21.dp))
            }
        }
    }
}
