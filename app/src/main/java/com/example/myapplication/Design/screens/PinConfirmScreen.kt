package com.example.myapplication.Design.screens

import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.example.myapplication.AppPermissionFlow
import com.example.myapplication.XiaomiPermissionHelper
import com.example.myapplication.Design.components.NumberPad
import com.example.myapplication.R
import com.example.myapplication.data.DataStoreManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.math.sqrt

private const val PERMISSION_DEBUG_TAG = "APPLOCK_PERMISSION_DEBUG"

@Composable
fun PinConfirmScreen(
    navController: NavController,
    context: Context,
    type: String,
    value: String,
    isReset: Boolean = false,
    permissionOnly: Boolean = false
) {
    val scope = rememberCoroutineScope()
    val appContext = remember { context.applicationContext }
    val dataStore = remember { DataStoreManager(appContext) }
    val lifecycleOwner = LocalLifecycleOwner.current


    var confirmPin by remember { mutableStateOf("") }
    var confirmPattern by remember { mutableStateOf<List<Int>>(emptyList()) }
    var error by remember { mutableStateOf("") }
    var patternError by remember { mutableStateOf(false) }
    var patternConfirmed by remember { mutableStateOf(false) }
    var clearErrorJob by remember { mutableStateOf<Job?>(null) }

    var showPermissionDialog by remember { mutableStateOf(false) }
    var showSecurityDialog by remember { mutableStateOf(false) }
    var isNavigatingToAppList by remember { mutableStateOf(false) }
    var savingCredential by remember { mutableStateOf(false) }

    var overlayAllowed by remember {
        mutableStateOf(Settings.canDrawOverlays(context))
    }
    var usageAllowed by remember {
        mutableStateOf(hasUsageAccess(context))
    }
    var popupAccess by remember {
        mutableStateOf(XiaomiPermissionHelper.backgroundPopupAccess(context))
    }

    var selectedQuestion by remember { mutableStateOf("") }
    var securityAnswer by remember { mutableStateOf("") }
    var securityDropdownExpanded by remember { mutableStateOf(false) }
    var securityAnswerError by remember { mutableStateOf(false) }
    var securityQuestionLocked by remember { mutableStateOf(false) }

    val isPattern = type.equals("pattern", ignoreCase = true)

    // ---------- Colors (PinCreateScreen jaise) ----------
    val backgroundColor = Color.White
    val primaryBlue = Color(0xFF0396FF)
    val darkText = Color(0xFF555555)
    val gray = Color(0xFFCCCCCC)
    val numberButtonColor = primaryBlue
    val patternDotColor = primaryBlue
    val errorColor = Color.Red

    val pinLength = value.length

    val passwordNotMatchText = stringResource(R.string.password_not_match)
    val enterCorrectPasswordText = stringResource(R.string.enter_correct_password)
    val resetSuccessText = stringResource(R.string.password_reset_success)
    val resetFailedText = stringResource(R.string.password_reset_failed)

    fun showResetToast() {
        val toastView = TextView(context).apply {
            text = resetSuccessText
            setTextColor(android.graphics.Color.rgb(0x7B, 0x7B, 0x7B))
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(28, 14, 28, 14)
            background = GradientDrawable().apply {
                setColor(android.graphics.Color.WHITE)
                cornerRadius = 30f
            }
            elevation = 6f
        }

        Toast(context).apply {
            duration = Toast.LENGTH_LONG
            view = toastView
            setGravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, 0, 80)
            show()
        }
    }

    fun checkPermissions() {
        Log.d(
            PERMISSION_DEBUG_TAG,
            "========== CHECK PERMISSIONS START =========="
        )

        val overlay = Settings.canDrawOverlays(context)
        val usage = hasUsageAccess(context)
        val popup = XiaomiPermissionHelper.backgroundPopupAccess(context)

        overlayAllowed = overlay
        usageAllowed = usage
        popupAccess = popup

        Log.d(
            PERMISSION_DEBUG_TAG,
            "CHECK_RESULT: overlay=$overlay, " +
                    "usage=$usage, " +
                    "popupRequired=${popup.required}, " +
                    "popupGranted=${popup.granted}, " +
                    "popupVerified=${popup.verified}, " +
                    "popupSatisfied=${popup.satisfied}, " +
                    "allAllowed=${overlay && usage && popup.satisfied}"
        )

        Log.d(
            PERMISSION_DEBUG_TAG,
            "========== CHECK PERMISSIONS END =========="
        )
    }

    fun allPermissionsAllowed(): Boolean {
        val result =
            overlayAllowed &&
                    usageAllowed &&
                    popupAccess.satisfied

        Log.d(
            PERMISSION_DEBUG_TAG,
            "ALL_PERMISSIONS_ALLOWED: result=$result, " +
                    "overlay=$overlayAllowed, " +
                    "usage=$usageAllowed, " +
                    "popupRequired=${popupAccess.required}, " +
                    "popupGranted=${popupAccess.granted}, " +
                    "popupVerified=${popupAccess.verified}, " +
                    "popupSatisfied=${popupAccess.satisfied}"
        )

        return result
    }

    fun openSecurityQuestion() {
        Log.w(
            PERMISSION_DEBUG_TAG,
            "OPEN_SECURITY_QUESTION: Opening Security Question dialog"
        )

        showPermissionDialog = false
        securityAnswer = ""
        securityAnswerError = false
        securityDropdownExpanded = false
        selectedQuestion = ""
        securityQuestionLocked = false
        showSecurityDialog = true
    }

    LaunchedEffect(permissionOnly) {
        Log.d(
            PERMISSION_DEBUG_TAG,
            "INITIAL_PERMISSION_FLOW: permissionOnly=$permissionOnly"
        )

        if (permissionOnly) {
            checkPermissions()

            if (allPermissionsAllowed()) {
                Log.w(
                    PERMISSION_DEBUG_TAG,
                    "INITIAL_PERMISSION_FLOW: Permissions already allowed"
                )
                openSecurityQuestion()
            } else {
                Log.w(
                    PERMISSION_DEBUG_TAG,
                    "INITIAL_PERMISSION_FLOW: Showing permission dialog"
                )
                showPermissionDialog = true
            }
        }
    }

    val currentShowPermissionDialog =
        rememberUpdatedState(showPermissionDialog)

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                Log.d(
                    PERMISSION_DEBUG_TAG,
                    "ON_RESUME: Returned to PinConfirmScreen"
                )

                val newOverlayAllowed =
                    Settings.canDrawOverlays(context)

                val newUsageAllowed =
                    hasUsageAccess(context)

                val newPopupAccess =
                    XiaomiPermissionHelper.backgroundPopupAccess(context)

                Log.d(
                    PERMISSION_DEBUG_TAG,
                    "ON_RESUME_STATUS: " +
                            "overlay=$newOverlayAllowed, " +
                            "usage=$newUsageAllowed, " +
                            "popupRequired=${newPopupAccess.required}, " +
                            "popupGranted=${newPopupAccess.granted}, " +
                            "popupVerified=${newPopupAccess.verified}, " +
                            "popupSatisfied=${newPopupAccess.satisfied}, " +
                            "dialogVisible=${currentShowPermissionDialog.value}"
                )

                overlayAllowed = newOverlayAllowed
                usageAllowed = newUsageAllowed
                popupAccess = newPopupAccess

                val allAllowed =
                    newOverlayAllowed &&
                            newUsageAllowed &&
                            newPopupAccess.satisfied

                Log.d(
                    PERMISSION_DEBUG_TAG,
                    "ON_RESUME_DECISION: allAllowed=$allAllowed"
                )

                if (
                    currentShowPermissionDialog.value &&
                    newOverlayAllowed &&
                    newUsageAllowed &&
                    newPopupAccess.satisfied
                ) {
                    Log.w(
                        PERMISSION_DEBUG_TAG,
                        "ON_RESUME_ACTION: Closing permission dialog " +
                                "and opening Security Question"
                    )

                    showPermissionDialog = false
                    openSecurityQuestion()
                } else {
                    Log.d(
                        PERMISSION_DEBUG_TAG,
                        "ON_RESUME_ACTION: Conditions not met; " +
                                "dialog remains unchanged"
                    )
                }
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(showPermissionDialog) {
        Log.d(
            PERMISSION_DEBUG_TAG,
            "EFFECT_DIALOG_CHANGED: showPermissionDialog=$showPermissionDialog"
        )

        if (showPermissionDialog) {
            checkPermissions()

            val allAllowed = allPermissionsAllowed()

            Log.d(
                PERMISSION_DEBUG_TAG,
                "EFFECT_DIALOG_CHECK: allAllowed=$allAllowed"
            )

            if (allAllowed) {
                Log.w(
                    PERMISSION_DEBUG_TAG,
                    "EFFECT_DIALOG_ACTION: Opening Security Question"
                )
                showPermissionDialog = false
                openSecurityQuestion()
            }
        }
    }

    LaunchedEffect(overlayAllowed, usageAllowed, popupAccess) {
        Log.d(
            PERMISSION_DEBUG_TAG,
            "EFFECT_PERMISSION_STATE: " +
                    "overlay=$overlayAllowed, " +
                    "usage=$usageAllowed, " +
                    "popupRequired=${popupAccess.required}, " +
                    "popupGranted=${popupAccess.granted}, " +
                    "popupVerified=${popupAccess.verified}, " +
                    "popupSatisfied=${popupAccess.satisfied}, " +
                    "dialogVisible=$showPermissionDialog"
        )

        if (
            showPermissionDialog &&
            overlayAllowed &&
            usageAllowed &&
            popupAccess.satisfied
        ) {
            Log.w(
                PERMISSION_DEBUG_TAG,
                "EFFECT_PERMISSION_ACTION: Opening Security Question"
            )

            showPermissionDialog = false
            openSecurityQuestion()
        }
    }

    fun showPatternError() {
        clearErrorJob?.cancel()
        patternError = true
        error = passwordNotMatchText

        clearErrorJob = scope.launch {
            delay(1500)
            confirmPattern = emptyList()
            patternError = false
            error = ""
            clearErrorJob = null
        }
    }

    fun finishReset() {
        if (!com.example.myapplication.security.AuthSession.canReset) return
        if (isNavigatingToAppList) return

        isNavigatingToAppList = true
        showSecurityDialog = false
        showPermissionDialog = false

        scope.launch(Dispatchers.IO) {
            try {
                dataStore.saveCredential(if (isPattern) "pattern" else "pin", value)

                withContext(Dispatchers.Main) {
                    if (com.example.myapplication.security.AuthSession.canReset &&
                        lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                        com.example.myapplication.security.AuthSession.finishReset()
                    } else com.example.myapplication.security.AuthSession.lock()
                    showResetToast()

                    if (!navController.popBackStack("appList", false)) {
                        navController.navigate("appList") {
                            launchSingleTop = true
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()

                withContext(Dispatchers.Main) {
                    isNavigatingToAppList = false
                    Toast.makeText(
                        context,
                        resetFailedText,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    fun continueSetup(credentialType: String) {
        if (isReset) {
            finishReset()
            return
        }
        if (savingCredential) return
        savingCredential = true
        checkPermissions()
        scope.launch {
            try {
                dataStore.saveCredential(credentialType, value)
                // Resume permissions only after the hashed credential has been persisted.
                AppPermissionFlow.savePendingSetup(appContext, credentialType)
                showPermissionDialog = true
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                Log.e(PERMISSION_DEBUG_TAG, "Unable to save credential", error)
                Toast.makeText(context, R.string.secure_settings_unavailable, Toast.LENGTH_LONG).show()
            } finally {
                savingCredential = false
            }
        }
    }

    fun continueWithPin() = continueSetup("pin")
    fun continueWithPattern() = continueSetup("pattern")







    fun goToAppList() {
        if (isNavigatingToAppList) return

        isNavigatingToAppList = true
        showSecurityDialog = false
        showPermissionDialog = false

        scope.launch {
            try {
                dataStore.saveAppInitialized(true)
                if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                    com.example.myapplication.security.AuthSession.unlock()
                }
                AppPermissionFlow.clearPendingSetup(appContext)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.e(PERMISSION_DEBUG_TAG, "Unable to complete setup", e)
                isNavigatingToAppList = false
                Toast.makeText(context, R.string.secure_settings_unavailable, Toast.LENGTH_LONG).show()
                return@launch
            }

            navController.navigate("appList") {
                if (permissionOnly) {
                    popUpTo("permissionGate") {
                        inclusive = true
                    }
                } else {
                    popUpTo("create") {
                        inclusive = true
                    }
                }

                launchSingleTop = true
            }
        }
    }

    // ============================================================
    // UI
    // ============================================================

    LimitFontScale {

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor)
        ) {
            // ---------- Responsive values ----------
            val isCompact = maxHeight < 700.dp || maxWidth < 360.dp

            val titleSize = if (isCompact) 21.sp else 25.sp
            val subTitleSize = if (isCompact) 16.sp else 18.sp
            val errorSize = if (isCompact) 13.sp else 15.sp
            val actionSize = if (isCompact) 17.sp else 20.sp
            val stepLineWidth = if (isCompact) 70.dp else 95.dp
            val gapAfterTitle = if (isCompact) 16.dp else 28.dp
            val gapAfterSteps = if (isCompact) 24.dp else 65.dp
            val gapAfterSubTitle = if (isCompact) 10.dp else 18.dp
            val gapBeforePattern = if (isCompact) 12.dp else 38.dp
            val gapBeforePad = if (isCompact) 14.dp else 50.dp
            val gapBeforeActions = if (isCompact) 20.dp else 55.dp
            val actionEndPadding = if (isCompact) 8.dp else 30.dp

            val gridSize = minOf(
                maxWidth - 80.dp,
                maxHeight * 0.40f,
                330.dp
            ).coerceAtLeast(200.dp)

            if (!permissionOnly) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 480.dp)
                            .fillMaxWidth()
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 30.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = if (isPattern) {
                                stringResource(R.string.confirm_pattern)
                            } else {
                                stringResource(R.string.confirm_passcode)
                            },
                            color = darkText,
                            fontSize = titleSize,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(Modifier.height(gapAfterTitle))

                        // ---------- STEP INDICATOR ----------
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Step 1 (done)

                            Box(
                                modifier = Modifier
                                    .size(25.dp)
                                    .border(2.dp, gray, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = "1", color = Color(0xFF999999), fontSize = 12.sp)
                            }

                            Box(
                                modifier = Modifier
                                    .width(stepLineWidth)
                                    .height(2.dp)
                                    .background(gray)
                            )

                            // Step 2 (current)
                            Box(
                                modifier = Modifier
                                    .size(31.dp)
                                    .background(
                                        Color(0xFFF2F2F2),
                                        CircleShape
                                    )
                                    .border(
                                        2.dp,
                                        primaryBlue,
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(21.dp)
                                        .background(primaryBlue, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "2",
                                        color = Color.White,
                                        fontSize = 15.sp
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(gapAfterSteps))

                        if (isPattern) {
                            Text(
                                text = stringResource(R.string.draw_pattern_again),
                                color = darkText,
                                fontSize = subTitleSize,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(Modifier.height(gapAfterSubTitle))

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (error.isNotEmpty()) {
                                    Text(
                                        text = error,
                                        color = Color.Red,
                                        fontSize = errorSize,
                                        textAlign = TextAlign.Center,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Spacer(Modifier.height(gapBeforePattern))

                            ConfirmPatternGrid(
                                modifier = Modifier.size(gridSize),
                                selectedDots = confirmPattern,
                                isError = patternError,
                                onPatternChanged = { dots ->
                                    confirmPattern = dots

                                    if (patternError) {
                                        clearErrorJob?.cancel()
                                        clearErrorJob = null
                                        patternError = false
                                        error = ""
                                    }
                                },
                                onPatternFinished = { pattern ->
                                    val originalPattern = value
                                        .split("-")
                                        .mapNotNull { it.toIntOrNull() }

                                    if (pattern.size < 4) {
                                        showPatternError()
                                        return@ConfirmPatternGrid
                                    }

                                    if (pattern != originalPattern) {
                                        showPatternError()
                                        return@ConfirmPatternGrid
                                    }

                                    clearErrorJob?.cancel()
                                    clearErrorJob = null
                                    patternError = false
                                    error = ""
                                    confirmPattern = pattern
                                    patternConfirmed = true
                                },
                                dotColor = patternDotColor,
                                errorColor = errorColor,
                                backgroundColor = backgroundColor
                            )

                            Spacer(Modifier.height(gapBeforeActions))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(end = actionEndPadding),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Text(
                                    text = stringResource(R.string.continue_text),
                                    color = if (patternConfirmed) {
                                        primaryBlue
                                    } else {
                                        Color(0xFFBDBDBD)
                                    },
                                    fontSize = actionSize,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .weight(1f, fill = false)
                                        .clickable(
                                            enabled = patternConfirmed,
                                            indication = null,
                                            interactionSource = remember {
                                                MutableInteractionSource()
                                            }
                                        ) {
                                            continueWithPattern()
                                        }
                                        .padding(10.dp)
                                )
                            }
                        } else {
                            Text(
                                text = stringResource(R.string.confirm_passcode),
                                color = darkText,
                                fontSize = subTitleSize,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(Modifier.height(gapAfterSubTitle))

                            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                repeat(pinLength) { index ->
                                    val isFilled = index < confirmPin.length

                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .background(
                                                color = if (isFilled) primaryBlue else Color.Transparent,
                                                shape = CircleShape
                                            )
                                            .border(
                                                width = 1.5.dp,
                                                color = if (isFilled) primaryBlue else gray,
                                                shape = CircleShape
                                            )
                                    )
                                }
                            }

                            Spacer(Modifier.height(15.dp))

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 22.dp)
                                    .padding(top = 5.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (error.isNotEmpty()) {
                                    Text(
                                        text = error,
                                        color = Color.Red,
                                        fontSize = errorSize,
                                        textAlign = TextAlign.Center,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Spacer(Modifier.height(gapBeforePad))

                            NumberPad(
                                onNumberClick = { number ->
                                    if (confirmPin.length < pinLength) {
                                        confirmPin += number
                                        error = ""
                                    }
                                },
                                onDelete = {
                                    if (confirmPin.isNotEmpty()) {
                                        confirmPin = confirmPin.dropLast(1)
                                        error = ""
                                    }
                                },
                                buttonColor = numberButtonColor
                            )

                            Spacer(Modifier.height(gapBeforeActions))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(end = actionEndPadding),
                                horizontalArrangement = Arrangement.spacedBy(
                                    12.dp,
                                    Alignment.End
                                )
                            ) {
                                Text(
                                    text = stringResource(R.string.reset),
                                    color = if (confirmPin.isEmpty()) {
                                        Color(0xFFBDBDBD)
                                    } else {
                                        primaryBlue
                                    },
                                    fontSize = actionSize,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .weight(1f, fill = false)
                                        .clickable(
                                            enabled = confirmPin.isNotEmpty(),
                                            indication = null,
                                            interactionSource = remember {
                                                MutableInteractionSource()
                                            }
                                        ) {
                                            confirmPin = ""
                                            error = ""
                                        }
                                        .padding(10.dp)
                                )

                                Text(
                                    text = stringResource(R.string.continue_text),
                                    color = if (confirmPin.length == pinLength) {
                                        primaryBlue
                                    } else {
                                        Color(0xFFBDBDBD)
                                    },
                                    fontSize = actionSize,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .weight(1f, fill = false)
                                        .clickable(
                                            enabled = confirmPin.length == pinLength,
                                            indication = null,
                                            interactionSource = remember {
                                                MutableInteractionSource()
                                            }
                                        ) {
                                            if (confirmPin != value) {
                                                error = enterCorrectPasswordText
                                                confirmPin = ""
                                                return@clickable
                                            }

                                            continueWithPin()
                                        }
                                        .padding(10.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (showPermissionDialog) {
                PermissionRequiredDialog(
                    access = com.example.myapplication.service.AppLockAccess(
                        usageAllowed, overlayAllowed, popupAccess.required, popupAccess.granted)
                )
            }

            if (showSecurityDialog) {
                SecurityQuestionDialog(
                    selectedQuestion = selectedQuestion,
                    answer = securityAnswer,
                    dropdownExpanded = securityDropdownExpanded,
                    isReset = isReset,
                    questionLocked = securityQuestionLocked,
                    answerError = securityAnswerError,
                    onDropdownClick = {
                        if (!securityQuestionLocked) {
                            securityDropdownExpanded = !securityDropdownExpanded
                        }
                    },
                    onQuestionSelected = { question ->
                        if (!securityQuestionLocked) {
                            selectedQuestion = question
                            securityDropdownExpanded = false
                            securityAnswerError = false
                        }
                    },
                    onAnswerChanged = { newAnswer ->
                        securityAnswer = newAnswer
                        securityAnswerError = false
                    },
                    onSkip = {
                        if (!isReset) goToAppList()
                    },
                    onSave = {
                        scope.launch {
                            try {
                                val question = selectedQuestion.trim()
                                val enteredAnswer = securityAnswer.trim()

                                if (question.isEmpty() || enteredAnswer.isEmpty()) {
                                    securityAnswerError = true
                                    return@launch
                                }

                                dataStore.saveSecurityQuestionAndAnswer(question, enteredAnswer)
                                securityAnswerError = false
                                goToAppList()
                            } catch (e: Exception) {
                                e.printStackTrace()
                                securityAnswerError = true
                            }
                        }
                    }
                )
            }
        }
    }
}

// ============================================================
// RESPONSIVE PERMISSION DIALOG
// ============================================================

@Composable
private fun SecurityQuestionDialog(
    selectedQuestion: String,
    answer: String,
    dropdownExpanded: Boolean,
    isReset: Boolean,
    questionLocked: Boolean,
    answerError: Boolean,
    onDropdownClick: () -> Unit,
    onQuestionSelected: (String) -> Unit,
    onAnswerChanged: (String) -> Unit,
    onSkip: () -> Unit,
    onSave: () -> Unit
) {
    val questions = listOf(
        stringResource(R.string.security_question_name),
        stringResource(R.string.security_question_father),
        stringResource(R.string.security_question_pet),
        stringResource(R.string.security_question_job)
    )

    val answerErrorText = stringResource(R.string.enter_correct_answer)

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        val dialogWindow =
            (LocalView.current.parent as? DialogWindowProvider)?.window

        DisposableEffect(dialogWindow) {
            dialogWindow?.setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
            )

            onDispose {
                dialogWindow?.setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                )
            }
        }

        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            val isCompact = maxWidth < 360.dp || maxHeight < 600.dp
            val dialogWidth = (maxWidth * 0.90f).coerceAtMost(420.dp)
            val dialogMaxHeight = maxHeight * 0.90f
            val hPad = if (isCompact) 18.dp else 25.dp
            val vPad = if (isCompact) 20.dp else 30.dp
            val fieldWidth = dialogWidth - hPad * 2

            val density = LocalDensity.current
            val popupOffset = with(density) {
                IntOffset(0, 44.dp.roundToPx())
            }

            Column(
                modifier = Modifier
                    .width(dialogWidth)
                    .heightIn(max = dialogMaxHeight)
                    .background(Color.White, RoundedCornerShape(26.dp))
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = hPad, vertical = vPad)
            ) {
                Text(
                    text = stringResource(R.string.security_questions),
                    color = Color(0xFF333333),
                    fontSize = if (isCompact) 17.sp else 19.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.security_question_description),
                    color = Color(0xFFBDBDBD),
                    fontSize = 11.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(if (isCompact) 18.dp else 28.dp))

                Text(
                    text = stringResource(R.string.select_security_questions),
                    color = Color(0xFF333333),
                    fontSize = 14.sp
                )

                Spacer(Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .background(
                                Color(0xFFF8F8F8),
                                RoundedCornerShape(15.dp)
                            )
                            .clickable(
                                enabled = !questionLocked,
                                indication = null,
                                interactionSource = remember {
                                    MutableInteractionSource()
                                }
                            ) {
                                if (!questionLocked) {
                                    onDropdownClick()
                                }
                            }
                            .padding(start = 18.dp, end = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (selectedQuestion.isEmpty()) {
                                stringResource(R.string.select_security_question)
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

                        if (!questionLocked) {
                            Icon(
                                imageVector = Icons.Outlined.KeyboardArrowDown,
                                contentDescription = stringResource(
                                    R.string.select_security_question
                                ),
                                tint = Color(0xFF8F8F8F),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    if (dropdownExpanded && !questionLocked) {
                        // Field ke bilkul neeche, field jitni chauri
                        Popup(
                            alignment = Alignment.TopStart,
                            offset = popupOffset,
                            onDismissRequest = { onDropdownClick() },
                            properties = PopupProperties(focusable = true)
                        ) {
                            Column(
                                modifier = Modifier
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
                                                indication = null,
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
                                            modifier = Modifier
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

                Spacer(Modifier.height(if (isCompact) 16.dp else 23.dp))

                Text(
                    text = stringResource(R.string.enter_security_answer),
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
                            modifier = Modifier.fillMaxSize(),
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

                if (answerError) {
                    Spacer(Modifier.height(5.dp))

                    Text(
                        text = answerErrorText,
                        color = Color.Red,
                        fontSize = 12.sp,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(if (isCompact) 20.dp else 30.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!isReset) {
                        Text(
                            text = stringResource(R.string.skip),
                            color = Color(0xFF2196F3),
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .clickable(
                                    indication = null,
                                    interactionSource = remember {
                                        MutableInteractionSource()
                                    }
                                ) {
                                    onSkip()
                                }
                                .padding(
                                    horizontal = 16.dp,
                                    vertical = 10.dp
                                )
                        )

                        Spacer(Modifier.width(8.dp))
                    }

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
                                indication = null,
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

// ============================================================
// HELPERS
// ============================================================

private fun hasUsageAccess(context: Context): Boolean =
    com.example.myapplication.service.AppLockPermissions.hasUsageAccess(context)

// ============================================================
// PATTERN GRID
// ============================================================

@Composable
private fun ConfirmPatternGrid(
    modifier: Modifier = Modifier,
    selectedDots: List<Int>,
    isError: Boolean,
    onPatternChanged: (List<Int>) -> Unit,
    onPatternFinished: (List<Int>) -> Unit,
    dotColor: Color = Color(0xFF0396FF),
    errorColor: Color = Color.Red,
    backgroundColor: Color = Color.White
) {
    val latestOnPatternChanged by rememberUpdatedState(onPatternChanged)
    val latestOnPatternFinished by rememberUpdatedState(onPatternFinished)

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                var currentDots = mutableListOf<Int>()
                var patternFinished = false

                detectDragGestures(
                    onDragStart = { offset ->
                        currentDots = mutableListOf()
                        patternFinished = false

                        val dot = findConfirmDot(
                            offset,
                            size.width.toFloat(),
                            size.height.toFloat()
                        )

                        if (dot != null) {
                            currentDots.add(dot)
                            latestOnPatternChanged(currentDots.toList())
                        }
                    },
                    onDrag = { change, _ ->
                        change.consume()

                        val dot = findConfirmDot(
                            change.position,
                            size.width.toFloat(),
                            size.height.toFloat()
                        )

                        if (dot != null && !currentDots.contains(dot)) {
                            currentDots.add(dot)
                            latestOnPatternChanged(currentDots.toList())
                        }
                    },
                    onDragEnd = {
                        if (!patternFinished) {
                            patternFinished = true
                            val finalPattern = currentDots.toList()

                            if (finalPattern.isNotEmpty()) {
                                latestOnPatternFinished(finalPattern)
                            }
                        }
                    },
                    onDragCancel = {
                        if (!patternFinished) {
                            patternFinished = true
                            val finalPattern = currentDots.toList()

                            if (finalPattern.isNotEmpty()) {
                                latestOnPatternFinished(finalPattern)
                            }
                        }
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val positions = getConfirmPositions(size.width, size.height)

            if (selectedDots.size >= 2) {
                for (i in 0 until selectedDots.size - 1) {
                    drawLine(
                        color = if (isError) errorColor else dotColor,
                        start = positions[selectedDots[i]],
                        end = positions[selectedDots[i + 1]],
                        strokeWidth = size.minDimension * 0.024f
                    )
                }
            }

            positions.forEachIndexed { index, position ->
                val selected = selectedDots.contains(index)

                drawCircle(
                    color = Color(0xFF0396FF),
                    radius = size.minDimension * 0.053f,
                    center = position
                )

                drawCircle(
                    color = backgroundColor,
                    radius = size.minDimension * 0.041f,
                    center = position
                )

                drawCircle(
                    color = when {
                        selected && isError -> errorColor
                        selected -> dotColor
                        else -> Color(0xFF0396FF)
                    },
                    radius = size.minDimension * 0.027f,
                    center = position
                )
            }
        }
    }
}

private fun getConfirmPositions(
    width: Float,
    height: Float
): List<Offset> {
    val x1 = width * 0.1667f
    val x2 = width * 0.5f
    val x3 = width * 0.8333f

    val y1 = height * 0.1667f
    val y2 = height * 0.5f
    val y3 = height * 0.8333f

    return listOf(
        Offset(x1, y1),
        Offset(x2, y1),
        Offset(x3, y1),
        Offset(x1, y2),
        Offset(x2, y2),
        Offset(x3, y2),
        Offset(x1, y3),
        Offset(x2, y3),
        Offset(x3, y3)
    )
}

private fun findConfirmDot(
    touch: Offset,
    width: Float,
    height: Float
): Int? {
    val positions = getConfirmPositions(width, height)
    val touchRadius = min(width, height) * 0.17f

    positions.forEachIndexed { index, dot ->
        val dx = touch.x - dot.x
        val dy = touch.y - dot.y
        val distance = sqrt(dx * dx + dy * dy)

        if (distance <= touchRadius) return index
    }

    return null
}
