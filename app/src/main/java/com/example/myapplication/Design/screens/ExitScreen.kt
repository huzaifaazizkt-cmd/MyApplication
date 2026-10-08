package com.example.myapplication.Design.screens

import android.app.Activity

import androidx.activity.compose.BackHandler

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.myapplication.R

@Composable
fun ExitScreen(
    navController: NavController
) {

    val context = LocalContext.current
    val activity = context as? Activity

    var selectedRating by remember {
        mutableIntStateOf(0)
    }

    BackHandler {
        navController.popBackStack()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {

        // =====================================================
        // TOP BAR
        // =====================================================

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .padding(horizontal = 16.dp),

            verticalAlignment = Alignment.CenterVertically
        ) {

            Icon(
                imageVector = Icons.Default.ArrowBack,

                contentDescription =
                    stringResource(
                        R.string.exit_screen_back
                    ),

                tint = Color(0xFF3D3D3D),

                modifier = Modifier
                    .size(28.dp)
                    .clickable(
                        indication = null,

                        interactionSource =
                            remember {
                                MutableInteractionSource()
                            }
                    ) {
                        navController.popBackStack()
                    }
            )

            Spacer(
                modifier = Modifier.size(18.dp)
            )

            Text(
                text =
                    stringResource(
                        R.string.exit_screen_title
                    ),

                fontSize = 24.sp,

                fontWeight =
                    FontWeight.Normal,

                color =
                    Color(0xFF3D3D3D)
            )
        }

        // =====================================================
        // CONTENT
        // =====================================================

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 15.dp),

            horizontalAlignment =
                Alignment.CenterHorizontally
        ) {

            Spacer(
                modifier = Modifier.height(40.dp)
            )

            // =================================================
            // EXIT IMAGE
            // =================================================

            Image(
                painter =
                    painterResource(
                        id = R.drawable.exitlock
                    ),

                contentDescription =
                    stringResource(
                        R.string.exit_image_description
                    ),

                modifier =
                    Modifier.size(200.dp)
            )

            Spacer(
                modifier = Modifier.height(25.dp)
            )

            // =================================================
            // EXIT TITLE
            // =================================================

            Text(
                text =
                    stringResource(
                        R.string.exit_app_lock_title
                    ),

                fontSize = 22.sp,

                fontWeight =
                    FontWeight.Bold,

                color =
                    Color(0xFF333333),

                textAlign =
                    TextAlign.Center
            )

            Spacer(
                modifier = Modifier.height(8.dp)
            )

            // =================================================
            // EXIT DESCRIPTION
            // =================================================

            Text(
                text =
                    stringResource(
                        R.string.exit_app_lock_description
                    ),

                fontSize = 13.sp,

                lineHeight = 17.sp,

                color =
                    Color(0xFF555555),

                textAlign =
                    TextAlign.Center
            )

            Spacer(
                modifier = Modifier.height(30.dp)
            )

            // =================================================
            // RATING
            // =================================================

            Row(
                horizontalArrangement =
                    Arrangement.Center,

                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                for (index in 1..5) {

                    Text(
                        text =
                            if (index <= selectedRating) {
                                "★"
                            } else {
                                "☆"
                            },

                        fontSize = 30.sp,

                        color =
                            if (index <= selectedRating) {
                                Color(0xFF2196F3)
                            } else {
                                Color(0xFF3D3D3D)
                            },

                        modifier = Modifier
                            .clickable(
                                indication = null,

                                interactionSource =
                                    remember {
                                        MutableInteractionSource()
                                    }
                            ) {

                                selectedRating =
                                    if (
                                        selectedRating == index
                                    ) {
                                        0
                                    } else {
                                        index
                                    }
                            }
                            .padding(
                                horizontal = 3.dp
                            )
                    )
                }
            }

            Spacer(
                modifier = Modifier.height(15.dp)
            )

            // =================================================
            // RATING DESCRIPTION
            // =================================================

            Text(
                text =
                    stringResource(
                        R.string.exit_rating_description
                    ),

                fontSize = 13.sp,

                lineHeight = 17.sp,

                color =
                    Color(0xFF555555),

                textAlign =
                    TextAlign.Center
            )

            Spacer(
                modifier = Modifier.height(26.dp)
            )

            // =================================================
            // STAY PROTECTED
            // =================================================

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(51.dp)
                    .clip(
                        RoundedCornerShape(14.dp)
                    )
                    .background(
                        Color(0xFF2196F3)
                    )
                    .clickable {
                        navController.popBackStack()
                    },

                contentAlignment =
                    Alignment.Center
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
                                id = R.drawable.stay
                            ),

                        contentDescription =
                            stringResource(
                                R.string
                                    .exit_stay_protected_description
                            ),

                        modifier =
                            Modifier.size(19.dp)
                    )

                    Spacer(
                        modifier =
                            Modifier.size(10.dp)
                    )

                    Text(
                        text =
                            stringResource(
                                R.string.exit_stay_protected
                            ),

                        fontSize = 15.sp,

                        fontWeight =
                            FontWeight.SemiBold,

                        color =
                            Color.White
                    )
                }
            }

            Spacer(
                modifier = Modifier.height(17.dp)
            )

            // =================================================
            // EXIT ANYWAY
            // =================================================

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(51.dp)
                    .clip(
                        RoundedCornerShape(14.dp)
                    )
                    .background(
                        Color.White
                    )
                    .border(
                        width = 1.dp,

                        color =
                            Color(0xFF2196F3),

                        shape =
                            RoundedCornerShape(14.dp)
                    )
                    .clickable {
                        activity?.finishAffinity()
                    },

                contentAlignment =
                    Alignment.Center
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
                                id = R.drawable.anyway
                            ),

                        contentDescription =
                            stringResource(
                                R.string
                                    .exit_anyway_description
                            ),

                        modifier =
                            Modifier.size(19.dp)
                    )

                    Spacer(
                        modifier =
                            Modifier.size(10.dp)
                    )

                    Text(
                        text =
                            stringResource(
                                R.string.exit_anyway
                            ),

                        fontSize = 15.sp,

                        fontWeight =
                            FontWeight.Medium,

                        color =
                            Color(0xFF2196F3)
                    )
                }
            }

            Spacer(
                modifier = Modifier.height(30.dp)
            )
        }
    }
}