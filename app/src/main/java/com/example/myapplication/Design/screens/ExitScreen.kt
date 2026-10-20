
package com.example.myapplication.Design.screens

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.myapplication.R

@Composable
fun ExitScreen(navController: NavController) {
    val context = LocalContext.current
    val activity = context as? Activity

    var selectedRating by remember { mutableIntStateOf(0) }
    var stayProtectedClicked by remember { mutableStateOf(false) }
    var exitAnywayClicked by remember { mutableStateOf(false) }
    var ratingActionStarted by remember { mutableStateOf(false) }

    fun openFeedbackOrReview(rating: Int) {
        if (ratingActionStarted) return
        ratingActionStarted = true

        try {
            if (rating in 1..3) {
                val emailIntent = Intent(
                    Intent.ACTION_SENDTO,
                    Uri.parse("mailto:YOUR_SUPPORT_EMAIL@gmail.com")
                ).apply {
                    putExtra(Intent.EXTRA_SUBJECT, "AppLock Feedback")
                    putExtra(
                        Intent.EXTRA_TEXT,
                        "My AppLock rating: $rating out of 5 stars.\n\nFeedback:"
                    )
                }

                try {
                    context.startActivity(emailIntent)
                } catch (e: ActivityNotFoundException) {
                    ratingActionStarted = false
                    Toast.makeText(
                        context,
                        "No email app is installed.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } else {
                val packageName = context.packageName
                val marketIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("market://details?id=$packageName")
                )

                try {
                    context.startActivity(marketIntent)
                } catch (e: ActivityNotFoundException) {
                    val webIntent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse(
                            "https://play.google.com/store/apps/details?id=$packageName"
                        )
                    )

                    try {
                        context.startActivity(webIntent)
                    } catch (e: ActivityNotFoundException) {
                        ratingActionStarted = false
                        Toast.makeText(
                            context,
                            "Unable to open the Play Store.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        } catch (e: Exception) {
            ratingActionStarted = false
            Toast.makeText(
                context,
                "Unable to open the selected app.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    BackHandler {
        if (!stayProtectedClicked && !exitAnywayClicked) {
            navController.popBackStack()
        }
    }

    val density = LocalDensity.current
    val limitedDensity = Density(
        density = density.density,
        fontScale = density.fontScale.coerceAtMost(1.15f)
    )

    CompositionLocalProvider(LocalDensity provides limitedDensity) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
        ) {
            val isCompact = maxWidth < 360.dp || maxHeight < 640.dp
            val imageSize = (maxHeight * 0.25f).coerceIn(120.dp, 220.dp)
            val topBarHeight = if (isCompact) 60.dp else 72.dp
            val screenTitleSize = if (isCompact) 20.sp else 24.sp
            val titleSize = if (isCompact) 19.sp else 22.sp
            val bodySize = if (isCompact) 12.sp else 13.sp
            val bodyLineHeight = if (isCompact) 16.sp else 17.sp
            val starSize = if (isCompact) 26.sp else 30.sp
            val buttonTextSize = if (isCompact) 14.sp else 15.sp
            val topSpace = if (isCompact) 16.dp else 40.dp
            val gapLarge = if (isCompact) 20.dp else 30.dp
            val gapMedium = if (isCompact) 18.dp else 26.dp
            val sidePadding = if (isCompact) 16.dp else 20.dp

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(topBarHeight)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = stringResource(R.string.exit_screen_back),
                        tint = Color(0xFF3D3D3D),
                        modifier = Modifier
                            .size(28.dp)
                            .clickable(
                                enabled = !stayProtectedClicked &&
                                        !exitAnywayClicked,
                                indication = null,
                                interactionSource = remember {
                                    MutableInteractionSource()
                                }
                            ) {
                                navController.popBackStack()
                            }
                    )

                    Spacer(modifier = Modifier.size(18.dp))

                    Text(
                        text = stringResource(R.string.exit_screen_title),
                        fontSize = screenTitleSize,
                        fontWeight = FontWeight.Normal,
                        color = Color(0xFF3D3D3D),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 480.dp)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .navigationBarsPadding()
                            .padding(horizontal = sidePadding),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(modifier = Modifier.height(topSpace))

                        Image(
                            painter = painterResource(id = R.drawable.exitlock),
                            contentDescription = stringResource(
                                R.string.exit_image_description
                            ),
                            modifier = Modifier.size(imageSize)
                        )

                        Spacer(
                            modifier = Modifier.height(
                                if (isCompact) 16.dp else 25.dp
                            )
                        )

                        Text(
                            text = stringResource(R.string.exit_app_lock_title),
                            fontSize = titleSize,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF333333),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = stringResource(
                                R.string.exit_app_lock_description
                            ),
                            fontSize = bodySize,
                            lineHeight = bodyLineHeight,
                            color = Color(0xFF555555),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(gapLarge))

                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            for (index in 1..5) {
                                Text(
                                    text = if (index <= selectedRating) "★" else "☆",
                                    fontSize = starSize,
                                    color = if (index <= selectedRating) {
                                        Color(0xFF2196F3)
                                    } else {
                                        Color(0xFF3D3D3D)
                                    },
                                    modifier = Modifier
                                        .clickable(
                                            enabled = !ratingActionStarted,
                                            indication = null,
                                            interactionSource = remember {
                                                MutableInteractionSource()
                                            }
                                        ) {
                                            selectedRating = index
                                            openFeedbackOrReview(index)
                                        }
                                        .padding(horizontal = 3.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(15.dp))

                        Text(
                            text = stringResource(
                                R.string.exit_rating_description
                            ),
                            fontSize = bodySize,
                            lineHeight = bodyLineHeight,
                            color = Color(0xFF555555),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(gapMedium))

                        // STAY PROTECTED
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 51.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF2196F3))
                                .clickable(
                                    enabled = !stayProtectedClicked &&
                                            !exitAnywayClicked,
                                    indication = null,
                                    interactionSource = remember {
                                        MutableInteractionSource()
                                    }
                                ) {
                                    stayProtectedClicked = true
                                    val navigated = navController.popBackStack()
                                    if (!navigated) {
                                        stayProtectedClicked = false
                                    }
                                }
                                .padding(
                                    horizontal = 12.dp,
                                    vertical = 10.dp
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.stay),
                                    contentDescription = stringResource(
                                        R.string.exit_stay_protected_description
                                    ),
                                    modifier = Modifier.size(19.dp)
                                )

                                Spacer(modifier = Modifier.size(10.dp))

                                Text(
                                    text = stringResource(
                                        R.string.exit_stay_protected
                                    ),
                                    fontSize = buttonTextSize,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(
                                        1f,
                                        fill = false
                                    )
                                )
                            }
                        }

                        Spacer(
                            modifier = Modifier.height(
                                if (isCompact) 12.dp else 17.dp
                            )
                        )

                        // EXIT ANYWAY
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 51.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.White)
                                .border(
                                    width = 1.dp,
                                    color = Color(0xFF2196F3),
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .clickable(
                                    enabled = !stayProtectedClicked &&
                                            !exitAnywayClicked,
                                    indication = null,
                                    interactionSource = remember {
                                        MutableInteractionSource()
                                    }
                                ) {
                                    exitAnywayClicked = true
                                    if (activity != null) {
                                        activity.finishAffinity()
                                    } else {
                                        exitAnywayClicked = false
                                    }
                                }
                                .padding(
                                    horizontal = 12.dp,
                                    vertical = 10.dp
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Image(
                                    painter = painterResource(
                                        id = R.drawable.anyway
                                    ),
                                    contentDescription = stringResource(
                                        R.string.exit_anyway_description
                                    ),
                                    modifier = Modifier.size(19.dp)
                                )

                                Spacer(modifier = Modifier.size(10.dp))

                                Text(
                                    text = stringResource(R.string.exit_anyway),
                                    fontSize = buttonTextSize,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF2196F3),
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(
                                        1f,
                                        fill = false
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(gapLarge))
                    }
                }
            }
        }
    }
}
