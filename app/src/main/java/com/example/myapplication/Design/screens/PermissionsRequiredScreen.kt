package com.example.myapplication.Design.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.myapplication.R

// =============================================================
// PERMISSIONS REQUIRED SCREEN
// =============================================================

@Composable
fun PermissionsRequiredScreen(
    onDismiss: () -> Unit
) {

    Dialog(

        onDismissRequest = {
            onDismiss()
        },

        properties =
            DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
                usePlatformDefaultWidth = false
            )
    ) {

        // =====================================================
        // DARK BLUE BACKGROUND
        // =====================================================

        Box(

            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Color(0xFF0B3B59)
                    )
                    .padding(
                        horizontal = 15.dp,
                        vertical = 36.dp
                    ),

            contentAlignment =
                Alignment.Center
        ) {


            // =================================================
            // WHITE CARD
            // =================================================

            Column(

                modifier =
                    Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .background(
                            Color.White,
                            RoundedCornerShape(20.dp)
                        )
                        .padding(
                            start = 25.dp,
                            end = 25.dp,
                            top = 8.dp,
                            bottom = 20.dp
                        )
            ) {


                // =================================================
                // TITLE
                // =================================================

                Box(

                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                bottom = 25.dp
                            ),

                    contentAlignment =
                        Alignment.Center
                ) {

                    Text(

                        text =
                            stringResource(
                                R.string.permissions_required
                            ),

                        color =
                            Color(0xFF333333),

                        fontSize =
                            20.sp
                    )
                }


                // =================================================
                // SHOW OVER OTHER APPS
                // =================================================

                PermissionItem(

                    icon = {

                        Image(

                            painter =
                                painterResource(
                                    id = R.drawable.group1
                                ),

                            contentDescription =
                                stringResource(
                                    R.string.show_over_other_apps
                                ),

                            modifier =
                                Modifier.size(
                                    23.dp
                                ),

                            contentScale =
                                ContentScale.Fit
                        )
                    },

                    title =
                        stringResource(
                            R.string.show_over_other_apps
                        ),

                    description =
                        stringResource(
                            R.string.allow_lock_screen
                        ),

                    onAllowClick = {



                    }
                )


                PermissionDivider()


                // =================================================
                // DETECT LAUNCHED APP
                // =================================================

                PermissionItem(

                    icon = {

                        Image(

                            painter =
                                painterResource(
                                    id = R.drawable.group2
                                ),

                            contentDescription =
                                stringResource(
                                    R.string.detect_launched_app
                                ),

                            modifier =
                                Modifier.size(
                                    23.dp
                                ),

                            contentScale =
                                ContentScale.Fit
                        )
                    },

                    title =
                        stringResource(
                            R.string.detect_launched_app
                        ),

                    description =
                        stringResource(
                            R.string.detect_launched_description
                        ),

                    onAllowClick = {



                    }
                )


                PermissionDivider()


                // =================================================
                // AUTO START
                // =================================================

                PermissionItem(

                    icon = {

                        Image(

                            painter =
                                painterResource(
                                    id = R.drawable.group3
                                ),

                            contentDescription =
                                stringResource(
                                    R.string.auto_start
                                ),

                            modifier =
                                Modifier.size(
                                    23.dp
                                ),

                            contentScale =
                                ContentScale.Fit
                        )
                    },

                    title =
                        stringResource(
                            R.string.auto_start
                        ),

                    description =
                        stringResource(
                            R.string.keep_applock_running
                        ),

                    onAllowClick = {


                    }
                )


                PermissionDivider()


                // =================================================
                // BOTTOM TEXT
                // =================================================

                Text(

                    text =
                        stringResource(
                            R.string.permissions_work_properly
                        ),

                    color =
                        Color(0xFFBDBDBD),

                    fontSize =
                        13.sp,

                    lineHeight =
                        18.sp,

                    modifier =
                        Modifier.padding(
                            start = 12.dp
                        )
                )
            }
        }
    }
}


// =============================================================
// PERMISSION ITEM
// =============================================================

@Composable
private fun PermissionItem(

    icon:
    @Composable () -> Unit,

    title:
    String,

    description:
    String,

    onAllowClick:
        () -> Unit

) {

    Column(

        modifier =
            Modifier.fillMaxWidth()
    ) {

        Row(

            modifier =
                Modifier.fillMaxWidth(),

            verticalAlignment =
                Alignment.Top
        ) {


            // =================================================
            // ICON
            // =================================================

            Box(

                modifier =
                    Modifier
                        .width(30.dp)
                        .padding(
                            top = 4.dp
                        ),

                contentAlignment =
                    Alignment.Center
            ) {

                icon()
            }


            // =================================================
            // CONTENT
            // =================================================

            Column(

                modifier =
                    Modifier.weight(1f)
            ) {


                // =================================================
                // TITLE ROW
                // =================================================

                Row(

                    modifier =
                        Modifier.fillMaxWidth(),

                    verticalAlignment =
                        Alignment.CenterVertically
                ) {

                    Text(

                        text =
                            title,

                        color =
                            Color(0xFF333333),

                        fontSize =
                            15.sp,

                        modifier =
                            Modifier.weight(
                                1f
                            )
                    )


                    Icon(

                        imageVector =
                            Icons.Outlined.KeyboardArrowDown,

                        contentDescription =
                            null,

                        tint =
                            Color(0xFFB5B5B5),

                        modifier =
                            Modifier.size(
                                18.dp
                            )
                    )
                }


                Spacer(
                    modifier =
                        Modifier.height(
                            7.dp
                        )
                )


                // =================================================
                // DESCRIPTION + ALLOW
                // =================================================

                Row(

                    modifier =
                        Modifier.fillMaxWidth(),

                    verticalAlignment =
                        Alignment.Bottom
                ) {

                    Text(

                        text =
                            description,

                        color =
                            Color(0xFFBDBDBD),

                        fontSize =
                            13.sp,

                        lineHeight =
                            18.sp,

                        modifier =
                            Modifier.weight(
                                1f
                            )
                    )


                    // =================================================
                    // ALLOW BUTTON
                    // =================================================

                    Box(

                        modifier =
                            Modifier
                                .width(72.dp)
                                .height(40.dp)
                                .background(
                                    Color(0xFF2196F3),
                                    RoundedCornerShape(
                                        4.dp
                                    )
                                )
                                .clickable {

                                    onAllowClick()
                                },

                        contentAlignment =
                            Alignment.Center
                    ) {

                        Text(

                            text =
                                stringResource(
                                    R.string.allow
                                ),

                            color =
                                Color.White,

                            fontSize =
                                13.sp
                        )
                    }
                }
            }
        }
    }
}


// =============================================================
// PERMISSION DIVIDER
// =============================================================

@Composable
private fun PermissionDivider() {

    Spacer(
        modifier =
            Modifier.height(
                17.dp
            )
    )


    Box(

        modifier =
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    Color(0xFFE5E5E5)
                )
    )


    Spacer(
        modifier =
            Modifier.height(
                22.dp
            )
    )
}