package com.example.myapplication.Design.screens

import android.app.Activity

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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

import com.example.myapplication.Billing
import com.example.myapplication.R


@Composable
fun PremiumScreen(
    navController: NavController
) {

    val context = LocalContext.current

    val activity = context as? Activity

    val billing = remember(context) {
        Billing(context)
    }

    var lifetimePrice by remember {
        mutableStateOf("$6.99")
    }

    LaunchedEffect(Unit) {

        billing.lifetimeprice { price ->

            lifetimePrice = price
        }
    }

    DisposableEffect(Unit) {

        onDispose {

            billing.endConnection()
        }
    }

    val blueColor =
        Color(0xFF2196F3)

    val darkText =
        Color(0xFF333333)

    val grayText =
        Color(0xFF777777)

    var selectedPlan by remember {
        mutableStateOf(true)
    }


    /*
     * =========================================================
     * PREMIUM SCREEN
     * =========================================================
     */

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {

        /*
         * =====================================================
         * TOP INSET ADDED HERE
         * =====================================================
         *
         * statusBarsPadding() keeps the complete Premium
         * content below the Android status bar.
         *
         * =====================================================
         */

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(
                    start = 20.dp,
                    end = 20.dp,
                    top = 30.dp
                )
        ) {

            /*
             * =================================================
             * CLOSE BUTTON
             * =================================================
             */

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(25.dp)
            ) {

                Icon(
                    painter = painterResource(
                        id = R.drawable.cross
                    ),
                    contentDescription =
                        stringResource(
                            R.string.premium_close
                        ),
                    tint = Color(0xFF777777),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(9.dp)
                        .clickable(
                            indication = null,
                            interactionSource =
                                remember {
                                    MutableInteractionSource()
                                }
                        ) {

                            navController.navigate(
                                "create"
                            ) {

                                popUpTo(
                                    "premium"
                                ) {
                                    inclusive = true
                                }
                            }
                        }
                )
            }


            /*
             * =================================================
             * PREMIUM IMAGE
             * =================================================
             */

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
                contentAlignment =
                    Alignment.Center
            ) {

                Image(
                    painter = painterResource(
                        id = R.drawable.`in`
                    ),
                    contentDescription =
                        stringResource(
                            R.string.premium_image_description
                        ),
                    modifier =
                        Modifier.size(125.dp)
                )
            }


            Spacer(
                modifier =
                    Modifier.height(20.dp)
            )


            /*
             * =================================================
             * PREMIUM TITLE
             * =================================================
             */

            Column(
                modifier =
                    Modifier.fillMaxWidth(),

                horizontalAlignment =
                    Alignment.CenterHorizontally
            ) {

                Row {

                    Text(
                        text =
                            stringResource(
                                R.string.premium_unlock
                            ),
                        color =
                            darkText,
                        fontSize =
                            26.sp,
                        fontWeight =
                            FontWeight.Bold
                    )

                    Spacer(
                        modifier =
                            Modifier.width(8.dp)
                    )

                    Text(
                        text =
                            stringResource(
                                R.string.premium_all_features
                            ),
                        color =
                            blueColor,
                        fontSize =
                            26.sp,
                        fontWeight =
                            FontWeight.Bold
                    )
                }

                Text(
                    text =
                        stringResource(
                            R.string.premium_forever
                        ),
                    color =
                        darkText,
                    fontSize =
                        26.sp,
                    fontWeight =
                        FontWeight.Bold
                )
            }


            Spacer(
                modifier =
                    Modifier.height(8.dp)
            )


            /*
             * =================================================
             * DESCRIPTION
             * =================================================
             */

            Text(
                text =
                    stringResource(
                        R.string.premium_description
                    ),
                color =
                    grayText,
                fontSize =
                    12.sp,
                lineHeight =
                    17.sp,
                modifier =
                    Modifier.padding(
                        start = 70.5.dp
                    ),
                textAlign =
                    TextAlign.Center
            )


            Spacer(
                modifier =
                    Modifier.height(20.dp)
            )


            /*
             * =================================================
             * BENEFITS TITLE
             * =================================================
             */

            Text(
                text =
                    stringResource(
                        R.string.premium_benefits
                    ),
                color =
                    darkText,
                fontSize =
                    14.sp,
                fontWeight =
                    FontWeight.Medium
            )


            Spacer(
                modifier =
                    Modifier.height(14.dp)
            )


            /*
             * =================================================
             * BENEFIT 1
             * =================================================
             */

            PremiumBenefitRow(
                icon =
                    R.drawable.hideinapp,
                text =
                    stringResource(
                        R.string.premium_hide_unlimited
                    )
            )


            Spacer(
                modifier =
                    Modifier.height(10.dp)
            )


            /*
             * =================================================
             * BENEFIT 2
             * =================================================
             */

            PremiumBenefitRow(
                icon =
                    R.drawable.selfie,
                text =
                    stringResource(
                        R.string.premium_intruder_selfie
                    )
            )


            Spacer(
                modifier =
                    Modifier.height(10.dp)
            )


            /*
             * =================================================
             * BENEFIT 3
             * =================================================
             */

            PremiumBenefitRow(
                icon =
                    R.drawable.noads,
                text =
                    stringResource(
                        R.string.premium_no_ads
                    )
            )


            Spacer(
                modifier =
                    Modifier.height(10.dp)
            )


            /*
             * =================================================
             * BENEFIT 4
             * =================================================
             */

            PremiumBenefitRow(
                icon =
                    R.drawable.priority,
                text =
                    stringResource(
                        R.string.premium_priority_support
                    )
            )


            Spacer(
                modifier =
                    Modifier.height(15.dp)
            )


            /*
             * =================================================
             * LIFETIME PLAN
             * =================================================
             */

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(76.dp)
                    .border(
                        width = 1.dp,
                        color = blueColor,
                        shape =
                            RoundedCornerShape(
                                16.dp
                            )
                    )
            ) {

                /*
                 * BEST VALUE
                 */

                Box(
                    modifier = Modifier
                        .align(
                            Alignment.TopEnd
                        )
                        .offset(
                            x = (-40).dp
                        )
                        .height(17.dp)
                        .background(
                            color =
                                blueColor,
                            shape =
                                RoundedCornerShape(
                                    bottomStart =
                                        3.dp
                                )
                        )
                        .padding(
                            horizontal = 8.dp
                        ),
                    contentAlignment =
                        Alignment.Center
                ) {

                    Text(
                        text =
                            stringResource(
                                R.string.premium_best_value
                            ),
                        color =
                            Color.White,
                        fontSize =
                            8.sp,
                        fontWeight =
                            FontWeight.Medium
                    )
                }


                Row(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(
                                start = 12.dp,
                                end = 12.dp,
                                top = 15.dp,
                                bottom = 8.dp
                            ),

                    verticalAlignment =
                        Alignment.CenterVertically
                ) {

                    /*
                     * RADIO BUTTON
                     */

                    Box(
                        modifier =
                            Modifier
                                .size(15.dp)
                                .border(
                                    width = 1.3.dp,
                                    color =
                                        blueColor,
                                    shape =
                                        CircleShape
                                ),

                        contentAlignment =
                            Alignment.Center
                    ) {

                        if (selectedPlan) {

                            Box(
                                modifier =
                                    Modifier
                                        .size(7.dp)
                                        .background(
                                            color =
                                                blueColor,
                                            shape =
                                                CircleShape
                                        )
                            )
                        }
                    }


                    Spacer(
                        modifier =
                            Modifier.width(15.dp)
                    )


                    /*
                     * PLAN TEXT
                     */

                    Column(
                        modifier =
                            Modifier.weight(1f)
                    ) {

                        Text(
                            text =
                                stringResource(
                                    R.string.premium_one_time_purchase
                                ),
                            color =
                                darkText,
                            fontSize =
                                16.sp,
                            fontWeight =
                                FontWeight.Medium
                        )

                        Spacer(
                            modifier =
                                Modifier.height(3.dp)
                        )

                        Text(
                            text =
                                stringResource(
                                    R.string.premium_lifetime_access
                                ),
                            color =
                                Color(0xFF888888),
                            fontSize =
                                13.sp
                        )
                    }


                    /*
                     * PRICE
                     */

                    Column(
                        horizontalAlignment =
                            Alignment.End
                    ) {

                        Text(
                            text =
                                lifetimePrice,
                            color =
                                blueColor,
                            fontSize =
                                15.sp,
                            fontWeight =
                                FontWeight.Bold
                        )

                        Spacer(
                            modifier =
                                Modifier.height(3.dp)
                        )

                        Text(
                            text =
                                stringResource(
                                    R.string.premium_one_time_payment
                                ),
                            color =
                                Color(0xFF888888),
                            fontSize =
                                13.sp
                        )
                    }
                }
            }


            Spacer(
                modifier =
                    Modifier.height(16.dp)
            )


            /*
             * =================================================
             * NO SUBSCRIPTION
             * =================================================
             */

            Row(
                modifier =
                    Modifier.fillMaxWidth(),

                horizontalArrangement =
                    Arrangement.Center,

                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Icon(
                    painter =
                        painterResource(
                            id = R.drawable.shield
                        ),
                    contentDescription =
                        stringResource(
                            R.string.premium_shield_description
                        )
                )

                Spacer(
                    modifier =
                        Modifier.width(8.dp)
                )

                Text(
                    text =
                        stringResource(
                            R.string.premium_no_subscription
                        ),
                    color =
                        Color(0xFF777777),
                    fontSize =
                        10.sp
                )
            }


            Spacer(
                modifier =
                    Modifier.height(16.dp)
            )


            /*
             * =================================================
             * UNLOCK BUTTON
             * =================================================
             */

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .clip(
                        RoundedCornerShape(
                            13.dp
                        )
                    )
                    .background(
                        blueColor
                    )
                    .clickable {

                        activity?.let {

                            billing.initPurchaselifetime(
                                it
                            )
                        }
                    },

                contentAlignment =
                    Alignment.Center
            ) {

                Text(
                    text =
                        stringResource(
                            R.string.premium_unlock_button
                        ),
                    color =
                        Color.White,
                    fontSize =
                        13.sp,
                    fontWeight =
                        FontWeight.Bold,
                    textAlign =
                        TextAlign.Center
                )
            }


            Spacer(
                modifier =
                    Modifier.height(14.dp)
            )


            /*
             * =================================================
             * TERMS / PRIVACY
             * ================================================= */

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding(),

                horizontalArrangement =
                    Arrangement.SpaceBetween,

                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Text(
                    text =
                        stringResource(
                            R.string.premium_terms_of_use
                        ),
                    color =
                        Color(0xFF555555),
                    fontSize =
                        10.sp,
                    modifier =
                        Modifier.clickable {

                        }
                )

                Text(
                    text =
                        stringResource(
                            R.string.premium_privacy_policy
                        ),
                    color =
                        Color(0xFF555555),
                    fontSize =
                        10.sp,
                    modifier =
                        Modifier.clickable {

                        }
                )
            }


            Spacer(
                modifier =
                    Modifier.height(10.dp)
            )
        }
    }
}


/*
 * =============================================================
 * PREMIUM BENEFIT ROW
 * =============================================================
 */

@Composable
private fun PremiumBenefitRow(
    icon: Int,
    text: String
) {

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(40.dp),

        verticalAlignment =
            Alignment.CenterVertically
    ) {

        Box(
            modifier =
                Modifier
                    .size(34.dp)
                    .background(
                        color =
                            Color(0xFFF2F7FF),
                        shape =
                            CircleShape
                    ),

            contentAlignment =
                Alignment.Center
        ) {

            Image(
                painter =
                    painterResource(
                        id = icon
                    ),
                contentDescription =
                    text,
                modifier =
                    Modifier.size(27.dp)
            )
        }

        Spacer(
            modifier =
                Modifier.width(8.dp)
        )

        Text(
            text =
                text,
            color =
                Color(0xFF444444),
            fontSize =
                14.sp,
            fontWeight =
                FontWeight.SemiBold
        )
    }
}