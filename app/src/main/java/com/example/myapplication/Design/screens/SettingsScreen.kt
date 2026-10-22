
package com.example.myapplication.Design.screens

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.example.myapplication.R
import com.example.myapplication.data.DataStoreManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val PRIVACY_POLICY_URL = "https://YOUR_PRIVACY_POLICY_URL"

private data class SettingsItemData(
    val key: String,
    val titleRes: Int,
    val iconRes: Int
)

@Composable
fun SettingsScreen(
    onIntruderClick: () -> Unit,
    onLanguageClick: () -> Unit,
    onResetPasswordClick: () -> Unit,
    onPremiumClick: () -> Unit = {}
) {
    LimitFontScale {
        SettingsScreenContent(
            onIntruderClick = onIntruderClick,
            onLanguageClick = onLanguageClick,
            onResetPasswordClick = onResetPasswordClick,
            onPremiumClick = onPremiumClick
        )
    }
}

@Composable
private fun SettingsScreenContent(
    onIntruderClick: () -> Unit,
    onLanguageClick: () -> Unit,
    onResetPasswordClick: () -> Unit,
    onPremiumClick: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity

    DisposableEffect(activity) {
        val previousSoftInputMode =
            activity?.window?.attributes?.softInputMode

        activity?.window?.setSoftInputMode(
            android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        )

        onDispose {
            if (previousSoftInputMode != null) {
                activity?.window?.setSoftInputMode(previousSoftInputMode)
            }
        }
    }

    val dataStore = remember(context) {
        DataStoreManager(context)
    }

    val scope = rememberCoroutineScope()

    var hideRecentJob by remember { mutableStateOf<Job?>(null) }
    var appProtectionEnabled by remember { mutableStateOf(false) }
    var expandedItem by remember { mutableStateOf<String?>(null) }
    var hideFromRecents by remember { mutableStateOf(false) }

    var showRelockDialog by remember { mutableStateOf(false) }
    var relockOption by remember { mutableStateOf("relock_after_quitting") }
    var tempRelockOption by remember { mutableStateOf("relock_after_quitting") }

    var showDelayDialog by remember { mutableStateOf(false) }
    var delayOption by remember { mutableStateOf("never") }
    var tempDelayOption by remember { mutableStateOf("never") }

    var showSecurityQuestionDialog by remember { mutableStateOf(false) }
    var securityQuestion by remember { mutableStateOf("") }
    var securityAnswer by remember { mutableStateOf("") }
    var securityDropdownExpanded by remember { mutableStateOf(false) }

    val settingsItems = listOf(
        SettingsItemData("languages", R.string.languages, R.drawable.language),
        SettingsItemData("lock_setting", R.string.lock_setting, R.drawable.settingicon),
        SettingsItemData("intruder", R.string.intruder, R.drawable.intruder),
        SettingsItemData("hide_settings", R.string.hide_settings, R.drawable.hideicon),
        SettingsItemData("privacy_policy", R.string.privacy_policy, R.drawable.privacy),
        SettingsItemData("share", R.string.share, R.drawable.share),
        SettingsItemData("rate_us", R.string.rate_us, R.drawable.rateus)
    )

    LaunchedEffect(dataStore) {
        appProtectionEnabled =
            dataStore.getAppProtectionEnabled().first()

        hideFromRecents =
            dataStore.getHideFromRecents().first()

        relockOption =
            dataStore.getRelockOption().first()

        tempRelockOption = relockOption

        delayOption = when (dataStore.getRelockDelay().first()) {
            "five_seconds", "5_seconds" -> "five_seconds"
            "ten_seconds", "10_seconds" -> "ten_seconds"
            "thirty_seconds", "30_seconds" -> "thirty_seconds"
            "one_minute", "1_minute" -> "one_minute"
            "two_minutes", "2_minutes" -> "two_minutes"
            "five_minutes", "5_minutes" -> "five_minutes"
            else -> "never"
        }

        tempDelayOption = delayOption
    }

    val scrollState = rememberScrollState()

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F7F7))
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
        ) {
            Text(
                text = stringResource(R.string.settings),
                modifier = Modifier.padding(
                    start = 20.dp,
                    end = 20.dp,
                    top = 15.dp
                ),
                color = Color(0xFF333333),
                fontSize = 22.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(22.dp))

            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(
                        start = 14.dp,
                        end = 14.dp,
                        bottom = 25.dp
                    )
            ) {
                PremiumPromoCard(
                    onClick = onPremiumClick
                )

                Spacer(Modifier.height(12.dp))

                AppProtectionCard(
                    enabled = appProtectionEnabled,
                    onEnabledChange = { enabled ->
                        appProtectionEnabled = enabled
                        scope.launch {
                            dataStore.saveAppProtectionEnabled(enabled)
                        }
                    }
                )

                Spacer(Modifier.height(12.dp))

                settingsItems.forEach { item ->
                    val title = stringResource(item.titleRes)

                    SettingsItem(
                        itemKey = item.key,
                        title = title,
                        iconRes = item.iconRes,
                        expanded = expandedItem == item.key,
                        hideFromRecents = hideFromRecents,
                        onHideFromRecentsChange = { enabled ->
                            hideFromRecents = enabled

                            hideRecentJob?.cancel()
                            hideRecentJob = null

                            scope.launch {
                                dataStore.saveHideFromRecents(enabled)
                            }

                            if (enabled) {
                                hideRecentJob = scope.launch {
                                    delay(2_000L)
                                    (context as? Activity)?.finishAndRemoveTask()
                                    hideRecentJob = null
                                }
                            }
                        },
                        onRelockClick = {
                            tempRelockOption = relockOption
                            showRelockDialog = true
                        },
                        onDelayClick = {
                            tempDelayOption = delayOption
                            showDelayDialog = true
                        },
                        delayOption = delayOption,
                        relockOption = relockOption,
                        onSecurityQuestionClick = {
                            scope.launch {
                                securityQuestion =
                                    dataStore.getSecurityQuestion()
                                        .first()
                                        .orEmpty()

                                securityAnswer =
                                    dataStore.getSecurityAnswer()
                                        .first()
                                        .orEmpty()

                                securityDropdownExpanded = false
                                showSecurityQuestionDialog = true
                            }
                        },
                        onCardClick = {
                            when (item.key) {
                                "languages" -> onLanguageClick()

                                "lock_setting", "hide_settings" -> {
                                    expandedItem =
                                        if (expandedItem == item.key) null
                                        else item.key
                                }

                                "intruder" -> onIntruderClick()

                                "rate_us" -> openPlayStore(context)

                                "share" -> shareApp(context)

                                "privacy_policy" -> openPrivacyPolicy(context)
                            }
                        },
                        onResetPasswordClick = onResetPasswordClick
                    )

                    Spacer(Modifier.height(12.dp))
                }
            }
        }

        if (showRelockDialog) {
            RelockOptionDialog(
                selectedOption = tempRelockOption,
                onOptionSelected = { tempRelockOption = it },
                onCancel = {
                    tempRelockOption = relockOption
                    showRelockDialog = false
                },
                onConfirm = {
                    relockOption = tempRelockOption
                    showRelockDialog = false

                    scope.launch {
                        dataStore.saveRelockOption(tempRelockOption)
                    }
                }
            )
        }

        if (showDelayDialog) {
            DelayToRelockDialog(
                selectedOption = tempDelayOption,
                onOptionSelected = { tempDelayOption = it },
                onCancel = {
                    tempDelayOption = delayOption
                    showDelayDialog = false
                },
                onConfirm = {
                    delayOption = tempDelayOption
                    showDelayDialog = false

                    scope.launch {
                        dataStore.saveRelockDelay(tempDelayOption)
                    }
                }
            )
        }

        if (showSecurityQuestionDialog) {
            SecurityQuestionSettingsDialog(
                selectedQuestion = securityQuestion,
                answer = securityAnswer,
                dropdownExpanded = securityDropdownExpanded,
                onDropdownClick = {
                    securityDropdownExpanded = !securityDropdownExpanded
                },
                onQuestionSelected = {
                    securityQuestion = it
                    securityDropdownExpanded = false
                },
                onAnswerChanged = { securityAnswer = it },
                onCancel = {
                    securityDropdownExpanded = false
                    showSecurityQuestionDialog = false
                },
                onSave = {
                    val question = securityQuestion.trim()
                    val answer = securityAnswer.trim()

                    if (question.isNotEmpty() && answer.isNotEmpty()) {
                        scope.launch {
                            dataStore.saveSecurityQuestion(question)
                            dataStore.saveSecurityAnswer(answer)

                            securityDropdownExpanded = false
                            showSecurityQuestionDialog = false
                        }
                    }
                }
            )
        }
    }
}

private fun openPlayStore(context: Context) {
    val packageName = context.packageName

    try {
        context.startActivity(
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("market://details?id=$packageName")
            )
        )
    } catch (e: ActivityNotFoundException) {
        try {
            context.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
                )
            )
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(
                context,
                "Unable to open Play Store.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}

private fun shareApp(context: Context) {
    val packageName = context.packageName
    val playStoreUrl =
        "https://play.google.com/store/apps/details?id=$packageName"

    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "AppLock")
        putExtra(
            Intent.EXTRA_TEXT,
            "Check out AppLock!\n$playStoreUrl"
        )
    }

    try {
        context.startActivity(
            Intent.createChooser(shareIntent, "Share AppLock")
        )
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(
            context,
            "Unable to share the app.",
            Toast.LENGTH_SHORT
        ).show()
    }
}

private fun openPrivacyPolicy(context: Context) {
    try {
        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://www.google.com")
        )
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(
            context,
            "Unable to open Privacy Policy.",
            Toast.LENGTH_SHORT
        ).show()
    }
}
@Composable
private fun PremiumPromoCard(
    onClick: () -> Unit
) {
    val blue = Color(0xFF0396FF)
    val outerShape = RoundedCornerShape(14.dp)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 4.dp,
                shape = outerShape,
                clip = false
            )
            .clip(outerShape)
            .clickable(
                interactionSource = remember {
                    MutableInteractionSource()
                },
                indication = ripple(bounded = true)
            ) {
                onClick()
            },
        shape = outerShape,
        colors = CardDefaults.cardColors(
            containerColor = blue
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 14.dp,
                    end = 14.dp,
                    top = 13.dp,
                    bottom = 12.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "Unlock All Features",
                    color = Color.White,
                    fontSize = 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(5.dp))

                Text(
                    text = "Remove ads & unlock all features",
                    color = Color.White,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(13.dp))

                Row(
                    modifier = Modifier
                        .width(145.dp)
                        .height(29.dp)
                        .background(
                            Color.White,
                            RoundedCornerShape(25.dp)
                        )
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Image(
                        painter = painterResource(R.drawable.kingblue),
                        contentDescription = "Get Premium",
                        modifier = Modifier.size(22.dp),
                        contentScale = ContentScale.Fit
                    )

                    Spacer(Modifier.width(8.dp))

                    Text(
                        text = "Get Premium",
                        color = blue,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            Image(
                painter = painterResource(R.drawable.king),
                contentDescription = "Premium King",
                modifier = Modifier.size(128.dp),
                contentScale = ContentScale.Fit
            )
        }
    }
}

@Composable
private fun AppProtectionCard(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit
) {
    val cardShape = RoundedCornerShape(12.dp)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 5.dp,
                shape = cardShape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.10f),
                spotColor = Color.Black.copy(alpha = 0.10f)
            )
            .clip(cardShape),
        shape = cardShape,
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp,
            pressedElevation = 1.dp
        )
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .padding(
                    start = 8.dp,
                    end = 10.dp,
                    top = 6.dp,
                    bottom = 6.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(R.drawable.enable),
                contentDescription = stringResource(
                    R.string.enable_app_protection
                ),
                modifier = Modifier.size(38.dp),
                contentScale = ContentScale.FillBounds
            )

            Spacer(Modifier.width(18.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.enable_app_protection),
                    color = Color(0xFF333333),
                    fontSize = 15.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(1.dp))

                Text(
                    text = if (enabled) {
                        stringResource(R.string.status_enabled)
                    } else {
                        stringResource(R.string.status_disabled)
                    },
                    color = if (enabled) {
                        Color(0xFF0396FF)
                    } else {
                        Color(0xFF999999)
                    },
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Switch(
                checked = enabled,
                onCheckedChange = onEnabledChange,
                modifier = Modifier.scale(0.72f),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color(0xFF0396FF),
                    uncheckedThumbColor = Color(0xFFAAAAAA),
                    uncheckedTrackColor = Color(0xFFE3E3E3),
                    uncheckedBorderColor = Color.Transparent,
                    checkedBorderColor = Color.Transparent
                )
            )
        }
    }
}

@Composable
private fun SettingsItem(
    itemKey: String,
    title: String,
    iconRes: Int,
    expanded: Boolean,
    hideFromRecents: Boolean,
    onHideFromRecentsChange: (Boolean) -> Unit,
    onRelockClick: () -> Unit,
    onDelayClick: () -> Unit,
    delayOption: String,
    relockOption: String,
    onSecurityQuestionClick: () -> Unit,
    onCardClick: () -> Unit,
    onResetPasswordClick: () -> Unit
) {
    val expandable =
        itemKey == "lock_setting" || itemKey == "hide_settings"

    val cardShape = RoundedCornerShape(12.dp)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 5.dp,
                shape = cardShape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.10f),
                spotColor = Color.Black.copy(alpha = 0.10f)
            )
            .clip(cardShape)
            .clickable(
                interactionSource = remember {
                    MutableInteractionSource()
                },
                indication = ripple(bounded = true)
            ) {
                onCardClick()
            },
        shape = cardShape,
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp,
            pressedElevation = 1.dp
        )
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .padding(
                        start = 8.dp,
                        end = 10.dp,
                        top = 6.dp,
                        bottom = 6.dp
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(iconRes),
                    contentDescription = title,
                    modifier = Modifier.size(38.dp),
                    contentScale = ContentScale.FillBounds
                )

                Spacer(Modifier.width(18.dp))

                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    color = Color(0xFF333333),
                    fontSize = 15.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.width(8.dp))

                Image(
                    painter = painterResource(
                        if (expandable) {
                            if (expanded) R.drawable.uparrow
                            else R.drawable.downicon
                        } else {
                            R.drawable.sidearrow
                        }
                    ),
                    contentDescription = null,
                    modifier = Modifier.size(
                        if (expandable) 12.dp else 14.dp
                    ),
                    contentScale = ContentScale.Fit
                )
            }

            if (expanded && itemKey == "lock_setting") {
                LockSettingExpandedContent(
                    onRelockClick = onRelockClick,
                    onDelayClick = onDelayClick,
                    delayOption = delayOption,
                    relockOption = relockOption,
                    onResetPasswordClick = onResetPasswordClick,
                    onSecurityQuestionClick = onSecurityQuestionClick
                )
            }

            if (expanded && itemKey == "hide_settings") {
                HideSettingsExpandedContent(
                    hideFromRecents = hideFromRecents,
                    onHideFromRecentsChange = onHideFromRecentsChange
                )
            }
        }
    }
}

@Composable
private fun HideSettingsExpandedContent(
    hideFromRecents: Boolean,
    onHideFromRecentsChange: (Boolean) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(
                start = 12.dp,
                end = 10.dp,
                top = 8.dp,
                bottom = 8.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(R.drawable.eyehide),
            contentDescription = stringResource(
                R.string.hide_from_recent_screen
            ),
            modifier = Modifier.size(22.dp),
            contentScale = ContentScale.Fit
        )

        Spacer(Modifier.width(13.dp))

        Text(
            text = stringResource(
                R.string.apps_hide_from_recent_screen
            ),
            color = Color(0xFF444444),
            fontSize = 14.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        Switch(
            checked = hideFromRecents,
            onCheckedChange = onHideFromRecentsChange,
            modifier = Modifier.scale(0.72f),
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF9C27B0),
                uncheckedThumbColor = Color(0xFFAAAAAA),
                uncheckedTrackColor = Color(0xFFE3E3E3),
                uncheckedBorderColor = Color.Transparent,
                checkedBorderColor = Color.Transparent
            )
        )
    }
}

@Composable
private fun LockSettingExpandedContent(
    onRelockClick: () -> Unit,
    onDelayClick: () -> Unit,
    delayOption: String,
    relockOption: String,
    onResetPasswordClick: () -> Unit,
    onSecurityQuestionClick: () -> Unit
) {
    val context = LocalContext.current
    val dataStore = remember(context) {
        DataStoreManager(context)
    }

    val scope = rememberCoroutineScope()

    var fingerprintEnabled by remember { mutableStateOf(false) }
    var vibrationEnabled by remember { mutableStateOf(false) }
    var hideTrackEnabled by remember { mutableStateOf(false) }

    LaunchedEffect(dataStore) {
        fingerprintEnabled =
            dataStore.getFingerprintEnabled().first()

        vibrationEnabled =
            dataStore.getVibrationEnabled().first()

        hideTrackEnabled =
            dataStore.getHideTrackEnabled().first()
    }

    Column(Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.password),
            color = Color(0xFF888888),
            fontSize = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(
                start = 10.dp,
                end = 10.dp,
                top = 6.dp,
                bottom = 6.dp
            )
        )

        LockSettingRow(
            R.drawable.redlock,
            stringResource(R.string.reset_password),
            stringResource(R.string.pattern),
            onResetPasswordClick
        )

        SettingDivider()

        LockSettingRow(
            R.drawable.security,
            stringResource(R.string.security_settings),
            stringResource(R.string.set_security_email),
            onSecurityQuestionClick
        )

        SettingDivider()

        LockSettingSwitchRow(
            R.drawable.fingerprint,
            stringResource(R.string.fingerprint_lock),
            stringResource(R.string.use_fingerprint_to_unlock),
            fingerprintEnabled
        ) {
            fingerprintEnabled = it
            scope.launch {
                dataStore.saveFingerprintEnabled(it)
            }
        }

        Text(
            text = stringResource(R.string.unlock),
            color = Color(0xFF888888),
            fontSize = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(
                start = 10.dp,
                end = 10.dp,
                top = 8.dp,
                bottom = 6.dp
            )
        )

        LockSettingSwitchRow(
            R.drawable.vibration,
            stringResource(R.string.vibration),
            null,
            vibrationEnabled
        ) {
            vibrationEnabled = it
            scope.launch {
                dataStore.saveVibrationEnabled(it)
            }
        }

        SettingDivider()

        LockSettingSwitchRow(
            R.drawable.track,
            stringResource(R.string.hide_track),
            stringResource(R.string.hide_track_description),
            hideTrackEnabled
        ) {
            hideTrackEnabled = it
            scope.launch {
                dataStore.saveHideTrackEnabled(it)
            }
        }

        SettingDivider()

        LockSettingRow(
            R.drawable.relock,
            stringResource(R.string.relock_option),
            getRelockText(relockOption),
            onRelockClick
        )

        SettingDivider()

        LockSettingRow(
            R.drawable.delay,
            stringResource(R.string.delay_to_relock),
            getDelayText(delayOption),
            onDelayClick
        )

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun LockSettingRow(
    iconRes: Int,
    title: String,
    subtitle: String?,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(
                start = 10.dp,
                end = 14.dp,
                top = 8.dp,
                bottom = 8.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.width(28.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(iconRes),
                contentDescription = title,
                modifier = Modifier.size(19.dp),
                contentScale = ContentScale.Fit
            )
        }

        Spacer(Modifier.width(8.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color(0xFF333333),
                fontSize = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (!subtitle.isNullOrEmpty()) {
                Spacer(Modifier.height(2.dp))

                Text(
                    text = subtitle,
                    color = Color(0xFF666666),
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun LockSettingSwitchRow(
    iconRes: Int,
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(
                start = 10.dp,
                end = 12.dp,
                top = 7.dp,
                bottom = 7.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.width(28.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(iconRes),
                contentDescription = title,
                modifier = Modifier.size(19.dp),
                contentScale = ContentScale.Fit
            )
        }

        Spacer(Modifier.width(8.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color(0xFF333333),
                fontSize = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (!subtitle.isNullOrEmpty()) {
                Spacer(Modifier.height(2.dp))

                Text(
                    text = subtitle,
                    color = Color(0xFF666666),
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.scale(0.72f),
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFFF45656),
                uncheckedThumbColor = Color(0xFFAAAAAA),
                uncheckedTrackColor = Color(0xFFE3E3E3),
                uncheckedBorderColor = Color.Transparent,
                checkedBorderColor = Color.Transparent
            )
        )
    }
}

@Composable
private fun SecurityQuestionSettingsDialog(
    selectedQuestion: String,
    answer: String,
    dropdownExpanded: Boolean,
    onDropdownClick: () -> Unit,
    onQuestionSelected: (String) -> Unit,
    onAnswerChanged: (String) -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit
) {
    val questions = listOf(
        stringResource(R.string.security_question_name),
        stringResource(R.string.security_question_father),
        stringResource(R.string.security_question_pet),
        stringResource(R.string.security_question_job)
    )

    val config = LocalConfiguration.current
    val dialogWidth =
        (config.screenWidthDp.dp * 0.90f).coerceAtMost(420.dp)
    val dialogMaxHeight = config.screenHeightDp.dp * 0.90f
    val fieldWidth = dialogWidth - 50.dp

    val density = LocalDensity.current
    val popupOffset = with(density) {
        IntOffset(0, 44.dp.roundToPx())
    }

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            Column(
                Modifier
                    .width(dialogWidth)
                    .heightIn(max = dialogMaxHeight)
                    .background(Color.White, RoundedCornerShape(26.dp))
                    .verticalScroll(rememberScrollState())
                    .padding(
                        start = 25.dp,
                        end = 25.dp,
                        top = 30.dp,
                        bottom = 30.dp
                    )
            ) {
                Text(
                    text = stringResource(R.string.security_questions),
                    color = Color(0xFF333333),
                    fontSize = 19.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = stringResource(
                        R.string.security_question_description
                    ),
                    color = Color(0xFFBDBDBD),
                    fontSize = 11.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(28.dp))

                Text(
                    text = stringResource(
                        R.string.select_security_questions
                    ),
                    color = Color(0xFF333333),
                    fontSize = 14.sp
                )

                Spacer(Modifier.height(8.dp))

                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .background(
                                Color(0xFFF8F8F8),
                                RoundedCornerShape(15.dp)
                            )
                            .clickable(
                                indication = ripple(bounded = true),
                                interactionSource = remember {
                                    MutableInteractionSource()
                                }
                            ) {
                                onDropdownClick()
                            }
                            .padding(
                                start = 18.dp,
                                end = 14.dp
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (selectedQuestion.isEmpty()) {
                                stringResource(
                                    R.string.select_security_question
                                )
                            } else {
                                selectedQuestion
                            },
                            color = if (selectedQuestion.isEmpty()) {
                                Color(0xFFBDBDBD)
                            } else {
                                Color(0xFF333333)
                            },
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        Icon(
                            imageVector = Icons.Outlined.KeyboardArrowDown,
                            contentDescription = null,
                            tint = Color(0xFF8F8F8F),
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    if (dropdownExpanded) {
                        Popup(
                            alignment = Alignment.TopStart,
                            offset = popupOffset,
                            onDismissRequest = onDropdownClick,
                            properties = PopupProperties(
                                focusable = true
                            )
                        ) {
                            Column(
                                Modifier
                                    .width(fieldWidth)
                                    .background(
                                        Color.White,
                                        RoundedCornerShape(15.dp)
                                    )
                                    .border(
                                        1.dp,
                                        Color(0xFFE5E5E5),
                                        RoundedCornerShape(15.dp)
                                    )
                            ) {
                                questions.forEachIndexed { index, question ->
                                    Text(
                                        text = question,
                                        color = Color(0xFF333333),
                                        fontSize = 13.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable(
                                                indication = ripple(
                                                    bounded = true
                                                ),
                                                interactionSource = remember {
                                                    MutableInteractionSource()
                                                }
                                            ) {
                                                onQuestionSelected(question)
                                            }
                                            .padding(
                                                horizontal = 18.dp,
                                                vertical = 14.dp
                                            )
                                    )

                                    if (index < questions.lastIndex) {
                                        Box(
                                            Modifier
                                                .fillMaxWidth()
                                                .height(1.dp)
                                                .background(Color(0xFFF0F0F0))
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(23.dp))

                Text(
                    text = stringResource(
                        R.string.enter_security_answer
                    ),
                    color = Color(0xFF333333),
                    fontSize = 14.sp
                )

                Spacer(Modifier.height(8.dp))

                BasicTextField(
                    value = answer,
                    onValueChange = onAnswerChanged,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .background(
                            Color(0xFFF8F8F8),
                            RoundedCornerShape(15.dp)
                        )
                        .padding(horizontal = 15.dp),
                    singleLine = true,
                    textStyle = TextStyle(
                        color = Color(0xFF333333),
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    ),
                    decorationBox = { innerTextField ->
                        Box(
                            Modifier.fillMaxSize(),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (answer.isEmpty()) {
                                Text(
                                    text = stringResource(
                                        R.string.enter_your_answer
                                    ),
                                    color = Color(0xFFBDBDBD),
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            innerTextField()
                        }
                    }
                )

                Spacer(Modifier.height(30.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.cancel),
                        color = Color(0xFF818181),
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .clickable(
                                indication = ripple(bounded = true),
                                interactionSource = remember {
                                    MutableInteractionSource()
                                }
                            ) {
                                onCancel()
                            }
                            .padding(
                                horizontal = 16.dp,
                                vertical = 10.dp
                            )
                    )

                    Spacer(Modifier.width(8.dp))

                    val saveEnabled =
                        selectedQuestion.isNotEmpty() &&
                                answer.trim().isNotEmpty()

                    Text(
                        text = stringResource(R.string.save),
                        color = if (saveEnabled) {
                            Color(0xFF2196F3)
                        } else {
                            Color(0xFF90CAF9)
                        },
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .clickable(
                                enabled = saveEnabled,
                                indication = ripple(bounded = true),
                                interactionSource = remember {
                                    MutableInteractionSource()
                                }
                            ) {
                                onSave()
                            }
                            .padding(
                                horizontal = 16.dp,
                                vertical = 10.dp
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun getRelockText(relockOption: String): String {
    return when (relockOption) {
        "relock_after_screen_off" ->
            stringResource(R.string.relock_after_screen_off)

        "relock_after_quitting" ->
            stringResource(R.string.relock_after_quitting)

        else ->
            stringResource(R.string.relock_after_quitting)
    }
}

@Composable
private fun getDelayText(key: String): String {
    return when (key) {
        "five_seconds" -> stringResource(R.string.five_seconds)
        "ten_seconds" -> stringResource(R.string.ten_seconds)
        "thirty_seconds" -> stringResource(R.string.thirty_seconds)
        "one_minute" -> stringResource(R.string.one_minute)
        "two_minutes" -> stringResource(R.string.two_minutes)
        "five_minutes" -> stringResource(R.string.five_minutes)
        else -> stringResource(R.string.never)
    }
}

@Composable
private fun RelockOptionDialog(
    selectedOption: String,
    onOptionSelected: (String) -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    val config = LocalConfiguration.current
    val dialogWidth =
        (config.screenWidthDp.dp * 0.88f).coerceAtMost(320.dp)
    val dialogMaxHeight = config.screenHeightDp.dp * 0.85f

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            Modifier
                .width(dialogWidth)
                .heightIn(max = dialogMaxHeight)
                .background(Color.White, RoundedCornerShape(12.dp))
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(
                        start = 16.dp,
                        end = 16.dp,
                        top = 16.dp,
                        bottom = 8.dp
                    )
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 22.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.relock),
                        color = Color(0xFF333333),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.height(7.dp))

                RelockOptionRow(
                    text = stringResource(
                        R.string.relock_after_quitting_option
                    ),
                    selected = selectedOption == "relock_after_quitting"
                ) {
                    onOptionSelected("relock_after_quitting")
                }

                RelockOptionRow(
                    text = stringResource(
                        R.string.relock_after_screen_off
                    ),
                    selected = selectedOption == "relock_after_screen_off"
                ) {
                    onOptionSelected("relock_after_screen_off")
                }

                Spacer(Modifier.height(16.dp))

                DialogButtons(
                    onCancel = onCancel,
                    onConfirm = onConfirm
                )
            }
        }
    }
}

@Composable
private fun RelockOptionRow(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 38.dp)
            .clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            modifier = Modifier.size(28.dp),
            colors = RadioButtonDefaults.colors(
                selectedColor = Color(0xFF0396FF),
                unselectedColor = Color(0xFFBDBDBD)
            )
        )

        Spacer(Modifier.width(5.dp))

        Text(
            text = text,
            color = Color(0xFF444444),
            fontSize = 13.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 4.dp)
        )
    }
}

@Composable
private fun DelayToRelockDialog(
    selectedOption: String,
    onOptionSelected: (String) -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    val delayOptions = listOf(
        "never",
        "five_seconds",
        "ten_seconds",
        "thirty_seconds",
        "one_minute",
        "two_minutes",
        "five_minutes"
    )

    val config = LocalConfiguration.current
    val dialogWidth =
        (config.screenWidthDp.dp * 0.88f).coerceAtMost(320.dp)
    val dialogMaxHeight = config.screenHeightDp.dp * 0.85f

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            Modifier
                .width(dialogWidth)
                .heightIn(max = dialogMaxHeight)
                .background(Color.White, RoundedCornerShape(12.dp))
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(
                        start = 16.dp,
                        end = 16.dp,
                        top = 16.dp,
                        bottom = 8.dp
                    )
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 22.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.delay_to_relock),
                        color = Color(0xFF333333),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.height(7.dp))

                delayOptions.forEach { option ->
                    DelayOptionRow(
                        text = getDelayText(option),
                        selected = selectedOption == option
                    ) {
                        onOptionSelected(option)
                    }
                }

                Spacer(Modifier.height(16.dp))

                DialogButtons(
                    onCancel = onCancel,
                    onConfirm = onConfirm
                )
            }
        }
    }
}

@Composable
private fun DelayOptionRow(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 38.dp)
            .clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            modifier = Modifier.size(28.dp),
            colors = RadioButtonDefaults.colors(
                selectedColor = Color(0xFF0396FF),
                unselectedColor = Color(0xFFBDBDBD)
            )
        )

        Spacer(Modifier.width(5.dp))

        Text(
            text = text,
            color = Color(0xFF444444),
            fontSize = 13.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 4.dp)
        )
    }
}

@Composable
private fun DialogButtons(
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.cancel),
            color = Color(0xFF818181),
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f, fill = false)
                .clickable { onCancel() }
                .padding(horizontal = 10.dp, vertical = 6.dp)
        )

        Spacer(Modifier.width(4.dp))

        Text(
            text = stringResource(R.string.confirm),
            color = Color(0xFF0396FF),
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f, fill = false)
                .clickable { onConfirm() }
                .padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun SettingDivider() {
    HorizontalDivider(
        modifier = Modifier.fillMaxWidth(),
        thickness = 0.6.dp,
        color = Color(0xFFE8E8E8)
    )
}
