package com.example.myapplication.Design.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.myapplication.R
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(
    navController: NavController
) {
    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { 3 }
    )

    val scope = rememberCoroutineScope()

    val images = listOf(
        R.drawable.onboarding0,
        R.drawable.onboarding1,
        R.drawable.onboarding2
    )

    val titles = listOf(
        R.string.onboarding_secure_your_apps,
        R.string.onboarding_quick_secure_access,
        R.string.onboarding_privacy_protected
    )

    val descriptions = listOf(
        R.string.onboarding_secure_your_apps_description,
        R.string.onboarding_quick_secure_access_description,
        R.string.onboarding_privacy_protected_description
    )

    LimitFontScale {

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
        ) {
            // ---------- Responsive values ----------
            val isCompact = maxHeight < 640.dp || maxWidth < 360.dp

            val imageSize = (maxHeight * 0.32f).coerceIn(140.dp, 280.dp)
            val topSpace = if (isCompact) 16.dp else 40.dp
            val gapAfterImage = if (isCompact) 18.dp else 30.dp
            val titleSize = if (isCompact) 21.sp else 25.sp
            val bodySize = if (isCompact) 13.sp else 14.sp
            val bodyLineHeight = if (isCompact) 18.sp else 20.sp
            val buttonTextSize = if (isCompact) 15.sp else 16.sp

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
                contentAlignment = Alignment.TopCenter
            ) {

                // Tablet par content phail na jaye
                Column(
                    modifier = Modifier
                        .widthIn(max = 480.dp)
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .padding(horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) { page ->

                        // Chhoti screen par scroll ho jaye
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Top
                        ) {

                            Spacer(modifier = Modifier.height(topSpace))

                            Image(
                                painter = painterResource(id = images[page]),
                                contentDescription = stringResource(
                                    R.string.onboarding_image_description,
                                    page + 1
                                ),
                                modifier = Modifier.size(imageSize)
                            )

                            Spacer(modifier = Modifier.height(gapAfterImage))

                            Text(
                                text = stringResource(titles[page]),
                                fontSize = titleSize,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF333333),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = stringResource(descriptions[page]),
                                fontSize = bodySize,
                                color = Color.Gray,
                                lineHeight = bodyLineHeight,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 5.dp)
                            )

                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    RowIndicator(
                        currentPage = pagerState.currentPage,
                        pageCount = 3
                    )

                    Spacer(modifier = Modifier.height(if (isCompact) 16.dp else 25.dp))

                    Button(
                        onClick = {
                            if (pagerState.currentPage < 2) {
                                scope.launch {
                                    pagerState.animateScrollToPage(
                                        pagerState.currentPage + 1
                                    )
                                }
                            } else {
                                navController.navigate("premium") {
                                    popUpTo("onboardingScreen") {
                                        inclusive = true
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2196F3)
                        )
                    ) {
                        Text(
                            text = if (pagerState.currentPage == 2) {
                                stringResource(R.string.onboarding_get_started)
                            } else {
                                stringResource(R.string.onboarding_next)
                            },
                            fontSize = buttonTextSize,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(if (isCompact) 16.dp else 25.dp))
                }
            }
        }
    }
}

@Composable
private fun RowIndicator(
    currentPage: Int,
    pageCount: Int
) {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(pageCount) { index ->
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(
                        width = if (currentPage == index) 22.dp else 8.dp,
                        height = 8.dp
                    )
                    .background(
                        color = if (currentPage == index) {
                            Color(0xFF2196F3)
                        } else {
                            Color(0xFFD0D0D0)
                        },
                        shape = RoundedCornerShape(10.dp)
                    )
            )
        }
    }
}