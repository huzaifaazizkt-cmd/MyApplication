
package com.example.myapplication.Design.screens

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
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.compose.rememberNavController
import com.example.myapplication.billing.BillingAction
import com.example.myapplication.billing.BillingMessage
import com.example.myapplication.billing.PremiumRepository
import com.example.myapplication.security.AuthSession
import com.example.myapplication.R

@Composable
fun PremiumScreen(
    paywallId: String,
    setupComplete: Boolean,
    onClose: () -> Unit,
    onPremiumGranted: () -> Unit
) {
    BackHandler(onBack = onClose)
    val context = LocalContext.current
    val activity = LocalActivity.current
    val repository = remember(context) { PremiumRepository.get(context) }
    val billing by repository.ui.collectAsState()
    val premium by repository.state.collectAsState()
    val authenticated by AuthSession.isAuthenticated.collectAsState()
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val selectedPlan = true

    DisposableEffect(repository, paywallId) {
        repository.openPaywall(paywallId)
        onDispose { repository.closePaywall(paywallId) }
    }

    val blueColor = Color(0xFF0396FF)
    val darkText = Color(0xFF333333)
    val grayText = Color(0xFF777777)
    val success = billing.success?.takeIf { it.paywallId == paywallId && premium.owned }
    if (success != null && (!setupComplete || authenticated) && lifecycleState.isAtLeast(Lifecycle.State.RESUMED)) {
        fun continueAfterSuccess() {
            repository.consumeSuccess(success.id)
            onPremiumGranted()
        }
        AlertDialog(
            onDismissRequest = ::continueAfterSuccess,
            title = { Text(stringResource(if (success.restored) R.string.billing_restored_title else R.string.billing_congratulations)) },
            text = { Text(stringResource(if (success.restored) R.string.billing_restored_message else R.string.billing_premium_activated)) },
            confirmButton = {
                TextButton(onClick = ::continueAfterSuccess) { Text(stringResource(R.string.billing_continue), color = blueColor) }
            }
        )
    }

    PremiumLimitFontScale {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
        ) {
            val screenWidth = maxWidth
            val screenHeight = maxHeight

            val isSmallScreen = screenHeight < 700.dp
            val isVerySmallScreen = screenHeight < 620.dp

            val horizontalPadding = when {
                screenWidth < 340.dp -> 14.dp
                screenWidth < 380.dp -> 18.dp
                else -> 20.dp
            }

            val topPadding = when {
                isVerySmallScreen -> 8.dp
                isSmallScreen -> 14.dp
                else -> 20.dp
            }

            val premiumImageSize = when {
                screenWidth < 340.dp -> 100.dp
                screenWidth < 380.dp -> 112.dp
                else -> 125.dp
            }

            val titleSize = when {
                screenWidth < 340.dp -> 24.sp
                screenWidth < 380.dp -> 27.sp
                else -> 30.sp
            }

            val descriptionSize = when {
                screenWidth < 340.dp -> 11.sp
                else -> 12.sp
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(
                            start = horizontalPadding,
                            end = horizontalPadding,
                            top = topPadding
                        )
                ) {
                    // CLOSE BUTTON
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Button(
                            onClick = { repository.restore(paywallId) },
                            enabled = !billing.loading && !billing.busy,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = blueColor,
                                contentColor = Color.White,
                                disabledContainerColor = blueColor.copy(alpha = 0.6f),
                                disabledContentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text(
                                stringResource(
                                    if (billing.action == BillingAction.RESTORE)
                                        R.string.billing_restoring else R.string.billing_restore
                                ),
                                fontSize = 10.sp
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() }
                                ) { onClose() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.cross),
                                contentDescription = stringResource(R.string.premium_close),
                                tint = Color(0xFF777777),
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(
                                when {
                                    isVerySmallScreen -> 105.dp
                                    isSmallScreen -> 120.dp
                                    else -> 140.dp
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(
                                id = R.drawable.`in`
                            ),
                            contentDescription = stringResource(
                                R.string.premium_image_description
                            ),
                            modifier = Modifier.sizeIn(
                                minWidth = 85.dp,
                                minHeight = 85.dp,
                                maxWidth = premiumImageSize,
                                maxHeight = premiumImageSize
                            )
                        )
                    }

                    Spacer(
                        modifier = Modifier.height(
                            when {
                                isVerySmallScreen -> 8.dp
                                isSmallScreen -> 12.dp
                                else -> 20.dp
                            }
                        )
                    )

                    val unlockText = stringResource(
                        R.string.premium_unlock
                    )
                    val allFeaturesText = stringResource(
                        R.string.premium_all_features
                    )

                    val titleText = buildAnnotatedString {
                        withStyle(SpanStyle(color = darkText)) {
                            append(unlockText)
                        }
                        append(" ")
                        withStyle(SpanStyle(color = blueColor)) {
                            append(allFeaturesText)
                        }
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = titleText,
                            fontSize = titleSize,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text(
                            text = stringResource(
                                R.string.premium_forever
                            ),
                            color = darkText,
                            fontSize = titleSize,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(
                        modifier = Modifier.height(
                            if (isVerySmallScreen) 5.dp else 8.dp
                        )
                    )

                    Text(
                        text = stringResource(
                            R.string.premium_description
                        ),
                        color = grayText,
                        fontSize = descriptionSize,
                        lineHeight = if (isVerySmallScreen) {
                            14.sp
                        } else {
                            16.sp
                        },
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = if (screenWidth < 340.dp) {
                                    4.dp
                                } else {
                                    20.dp
                                }
                            )
                    )

                    Spacer(
                        modifier = Modifier.height(
                            if (isVerySmallScreen) 10.dp else 18.dp
                        )
                    )

                    Text(
                        text = stringResource(
                            R.string.premium_benefits
                        ),
                        color = darkText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(
                        modifier = Modifier.height(
                            if (isVerySmallScreen) 8.dp else 12.dp
                        )
                    )

                    PremiumBenefitRow(
                        icon = R.drawable.selfie,
                        text = stringResource(
                            R.string.premium_intruder_selfie
                        )
                    )

                    Spacer(
                        modifier = Modifier.height(
                            if (isVerySmallScreen) 5.dp else 8.dp
                        )
                    )

                    PremiumBenefitRow(
                        icon = R.drawable.noads,
                        text = stringResource(
                            R.string.premium_no_ads
                        )
                    )

                    Spacer(
                        modifier = Modifier.height(
                            if (isVerySmallScreen) 5.dp else 8.dp
                        )
                    )

                    PremiumBenefitRow(
                        icon = R.drawable.priority,
                        text = stringResource(
                            R.string.premium_priority_support
                        )
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // ONE TIME PURCHASE
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 76.dp)
                            .border(
                                width = 1.dp,
                                color = blueColor,
                                shape = RoundedCornerShape(16.dp)
                            )
                    ) {
                        // BEST VALUE
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(end = 100.dp)
                                .widthIn(max = 120.dp)
                                .heightIn(min = 17.dp)
                                .background(
                                    color = blueColor,
                                    shape = RoundedCornerShape(
                                        bottomStart = 3.dp,
                                        bottomEnd = 3.dp
                                    )
                                )
                                .padding(horizontal = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.premium_best_value
                                ),
                                color = Color.White,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // PURCHASE ROW
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    start = 12.dp,
                                    end = 12.dp,
                                    top = 20.dp,
                                    bottom = 10.dp
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // RADIO BUTTON
                            Box(
                                modifier = Modifier
                                    .size(15.dp)
                                    .border(
                                        width = 1.3.dp,
                                        color = blueColor,
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (selectedPlan) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .background(
                                                color = blueColor,
                                                shape = CircleShape
                                            )
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            // PLAN DETAILS
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = stringResource(
                                        R.string.premium_one_time_purchase
                                    ),
                                    color = darkText,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Spacer(modifier = Modifier.height(2.dp))

                                Text(
                                    text = stringResource(
                                        R.string.premium_lifetime_access
                                    ),
                                    color = Color(0xFF888888),
                                    fontSize = 12.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // PRICE
                            Column(
                                modifier = Modifier.widthIn(max = 100.dp),
                                horizontalAlignment = Alignment.End
                            ) {
                                if (billing.loading) CircularProgressIndicator(modifier = Modifier.size(18.dp), color = blueColor, strokeWidth = 2.dp)
                                else Text(
                                    text = if (premium.owned) stringResource(R.string.billing_owned)
                                        else billing.price ?: stringResource(R.string.billing_price_unavailable),
                                    color = blueColor,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )

                                Spacer(modifier = Modifier.height(2.dp))

                                Text(
                                    text = stringResource(
                                        R.string.premium_one_time_payment
                                    ),
                                    color = Color(0xFF888888),
                                    fontSize = 11.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.End
                                )
                            }
                        }
                    }

                    Spacer(
                        modifier = Modifier.height(
                            if (isVerySmallScreen) 8.dp else 12.dp
                        )
                    )

                    // NO SUBSCRIPTION
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(
                                id = R.drawable.shield
                            ),
                            contentDescription = stringResource(
                                R.string.premium_shield_description
                            ),
                            modifier = Modifier.size(16.dp)
                        )

                        Spacer(modifier = Modifier.width(7.dp))

                        Text(
                            text = stringResource(
                                R.string.premium_no_subscription
                            ),
                            color = Color(0xFF777777),
                            fontSize = 10.sp,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }

                    Spacer(
                        modifier = Modifier.height(
                            if (isVerySmallScreen) 8.dp else 12.dp
                        )
                    )

                    // UNLOCK BUTTON
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(
                                min = if (isVerySmallScreen) {
                                    46.dp
                                } else {
                                    50.dp
                                }
                            )
                            .clip(RoundedCornerShape(13.dp))
                            .background(if (billing.loading || billing.busy || billing.pending) blueColor.copy(alpha = 0.6f) else blueColor)
                            .clickable(enabled = !billing.loading && !billing.busy && !billing.pending) {
                                when {
                                    premium.owned -> onPremiumGranted()
                                    billing.price == null -> repository.openPaywall(paywallId)
                                    else -> activity?.let { repository.purchase(it, paywallId) }
                                }
                            }
                            .padding(
                                horizontal = 12.dp,
                                vertical = 8.dp
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (billing.loading || billing.busy) {
                                CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(
                                text = stringResource(when {
                                    billing.loading -> R.string.billing_loading
                                    billing.action == BillingAction.RESTORE -> R.string.billing_restoring
                                    billing.busy -> R.string.billing_processing
                                    billing.pending -> R.string.billing_pending_short
                                    premium.owned -> R.string.billing_continue
                                    billing.price == null -> R.string.billing_retry
                                    else -> R.string.premium_unlock_button
                                }),
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    billing.message?.let { message ->
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(message.textResource()), color = grayText, fontSize = 12.sp,
                            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    }

                    Spacer(
                        modifier = Modifier.height(
                            if (isVerySmallScreen) 8.dp else 12.dp
                        )
                    )

                    // TERMS OF USE AND PRIVACY POLICY
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(
                                R.string.premium_terms_of_use
                            ),
                            color = Color(0xFF555555),
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Start,
                            modifier = Modifier
                                .weight(1f)
                                .clickable(
                                    indication = null,
                                    interactionSource = remember {
                                        MutableInteractionSource()
                                    }
                                ) { SettingsActions.open(context, "terms_of_use") }
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = stringResource(
                                R.string.premium_privacy_policy
                            ),
                            color = Color(0xFF555555),
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.End,
                            modifier = Modifier
                                .weight(1f)
                                .clickable(
                                    indication = null,
                                    interactionSource = remember {
                                        MutableInteractionSource()
                                    }
                                ) { SettingsActions.open(context, "privacy_policy") }
                        )
                    }

                    Spacer(
                        modifier = Modifier.height(
                            if (isVerySmallScreen) 6.dp else 10.dp
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun PremiumBenefitRow(
    icon: Int,
    text: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(
                    color = Color(0xFFF2F7FF),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = icon),
                contentDescription = text,
                modifier = Modifier.size(27.dp)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = text,
            color = Color(0xFF444444),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

private fun BillingMessage.textResource(): Int = when (this) {
    BillingMessage.UNAVAILABLE -> R.string.billing_unavailable
    BillingMessage.PRODUCT_UNAVAILABLE -> R.string.billing_product_unavailable
    BillingMessage.PURCHASE_FAILED -> R.string.billing_purchase_failed
    BillingMessage.CANCELLED -> R.string.billing_cancelled
    BillingMessage.PENDING -> R.string.billing_pending
    BillingMessage.NOTHING_TO_RESTORE -> R.string.billing_nothing_to_restore
    BillingMessage.RESTORE_FAILED -> R.string.billing_restore_failed
    BillingMessage.SAVE_FAILED -> R.string.billing_save_failed
}

@Composable
private fun PremiumLimitFontScale(
    maxScale: Float = 1.15f,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current

    CompositionLocalProvider(
        LocalDensity provides Density(
            density = density.density,
            fontScale = density.fontScale.coerceAtMost(maxScale)
        )
    ) {
        content()
    }
}
@Preview(
    showBackground = true,
    showSystemUi = true,
    device = "spec:width=393dp,height=873dp,dpi=440"
)
@Composable
private fun PremiumScreenPreview() {
    PremiumScreen(
        paywallId = "preview",
        setupComplete = true,
        onClose = {},
        onPremiumGranted = {}
    )
}