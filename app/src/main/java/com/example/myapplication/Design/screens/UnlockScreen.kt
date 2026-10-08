package com.example.myapplication.Design.screens

import android.content.Context
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.Design.components.NumberPad
import com.example.myapplication.R
import com.example.myapplication.data.DataStoreManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.sqrt

fun showCustomToast(context: Context, message: String) {
    val textView = TextView(context).apply {
        text = message
        setTextColor(AndroidColor.parseColor("#7B7B7B"))
        textSize = 14f
        gravity = Gravity.CENTER
        setPadding(32, 18, 32, 18)
        setBackgroundColor(AndroidColor.WHITE)
    }
    val toast = Toast(context)
    toast.duration = Toast.LENGTH_LONG
    toast.view = textView
    toast.setGravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, 0, 120)
    toast.show()
    Handler(Looper.getMainLooper()).postDelayed({
        try {
            toast.cancel()
        } catch (_: Exception) {
        }
    }, 5000L)
}

private fun performUnlockVibration(context: Context, duration: Long = 70L) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            val vibrator = vibratorManager.defaultVibrator
            if (vibrator.hasVibrator()) {
                vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        } else {
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(duration)
                }
            }
        }
    } catch (e: Exception) {
        Log.e("UNLOCK_VIBRATION", "VIBRATION ERROR", e)
    }
}

@Composable
fun UnlockScreen(
    onUnlockSuccess: () -> Unit,
    onIntruderCapture: (Long) -> Unit = {},
    onFingerprintRequest: () -> Unit = {},
    onForgotPasswordSuccess: () -> Unit = {}
) {
    val context = LocalContext.current
    val dataStore = remember { DataStoreManager(context) }
    val scope = rememberCoroutineScope()
    val patternNotMatchText = stringResource(R.string.pattern_not_match)
    val correctPasswordText = stringResource(R.string.enter_correct_password)
    val securityQuestionNotSetText = stringResource(R.string.security_question_not_set)
    val pleaseEnterYourAnswerText = stringResource(R.string.please_enter_your_answer)
    val incorrectAnswerText = stringResource(R.string.incorrect_answer)

    var authType by remember { mutableStateOf("pin") }
    var savedPin by remember { mutableStateOf("") }
    var savedPattern by remember { mutableStateOf("") }
    var enteredPin by remember { mutableStateOf("") }
    var enteredPattern by remember { mutableStateOf<List<Int>>(emptyList()) }
    var error by remember { mutableStateOf("") }
    var patternError by remember { mutableStateOf(false) }
    var clearErrorJob by remember { mutableStateOf<Job?>(null) }
    var fingerprintEnabled by remember { mutableStateOf(false) }
    var vibrationEnabled by remember { mutableStateOf(false) }
    var hideTrackEnabled by remember { mutableStateOf(false) }
    var intruderObservationAttempts by remember { mutableStateOf(3) }
    var showForgotPasswordDialog by remember { mutableStateOf(false) }
    var securityQuestion by remember { mutableStateOf("") }
    var securityAnswer by remember { mutableStateOf("") }
    var securityQuestionError by remember { mutableStateOf("") }
    var checkingSecurityQuestion by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        authType = dataStore.getAuthType().first().orEmpty().lowercase()
        savedPin = dataStore.getPin().first().orEmpty()
        savedPattern = dataStore.getPattern().first().orEmpty()
        fingerprintEnabled = dataStore.getFingerprintEnabled().first()
        vibrationEnabled = dataStore.getVibrationEnabled().first()
        hideTrackEnabled = dataStore.getHideTrackEnabled().first()
        intruderObservationAttempts = dataStore.getIntruderObservationAttempts().first()
        if (fingerprintEnabled) {
            delay(350)
            onFingerprintRequest()
        }
    }

    val backgroundColor = Color(0xFF29A0F0)
    val numberButtonColor = Color(0xFF69B9F3)
    val patternDotColor = Color(0xFF83CCFF)
    val errorColor = Color.Red

    fun registerWrongAttempt() {
        if (vibrationEnabled) {
            performUnlockVibration(context = context, duration = 100L)
        }

        (context as? FragmentActivity)?.lifecycleScope?.launch {
            try {
                val enabled = dataStore.getIntruderEnabled().first()
                if (!enabled) return@launch

                val selectedAttempts = dataStore.getIntruderObservationAttempts().first()
                intruderObservationAttempts = selectedAttempts
                Log.d("INTRUDER_DEBUG", "SELECTED ATTEMPTS = $selectedAttempts")

                if (selectedAttempts <= 0) {
                    dataStore.resetIntruderWrongAttempts()
                    Log.d("INTRUDER_DEBUG", "INTRUDER CAPTURE DISABLED - NEVER")
                    return@launch
                }

                val currentAttempts = dataStore.getIntruderWrongAttempts().first()
                val newAttempts = currentAttempts + 1
                Log.d("INTRUDER_DEBUG", "WRONG ATTEMPT = $newAttempts / $selectedAttempts")
                dataStore.saveIntruderWrongAttempts(newAttempts)

                if (newAttempts < selectedAttempts) return@launch

                dataStore.resetIntruderWrongAttempts()

                val observationTime = dataStore.getIntruderObservationTime().first()
                val delayMillis = observationTime.toLong().coerceAtLeast(0L) * 1_000L
                if (delayMillis > 0L) delay(delayMillis)

                val stillEnabled = dataStore.getIntruderEnabled().first()
                if (!stillEnabled) return@launch

                Log.d("INTRUDER_DEBUG", "CALLING INTRUDER CAPTURE")
                onIntruderCapture(System.currentTimeMillis())
            } catch (e: Exception) {
                Log.e("INTRUDER_DEBUG", "WRONG ATTEMPT ERROR", e)
            }
        }
    }

    fun openForgotPassword() {
        scope.launch {
            try {
                val question = dataStore.getSecurityQuestion().first()?.trim().orEmpty()
                if (question.isEmpty()) {
                    showCustomToast(context, securityQuestionNotSetText)
                    return@launch
                }
                securityQuestion = question
                securityAnswer = ""
                securityQuestionError = ""
                showForgotPasswordDialog = true
            } catch (e: Exception) {
                Log.e("FORGOT_PASSWORD", "SECURITY QUESTION ERROR", e)
            }
        }
    }

    fun verifySecurityAnswer() {
        if (securityAnswer.trim().isEmpty()) {
            securityQuestionError = pleaseEnterYourAnswerText
            return
        }

        checkingSecurityQuestion = true
        scope.launch {
            try {
                val savedAnswer = dataStore.getSecurityAnswer().first()?.trim().orEmpty()
                if (savedAnswer.isEmpty()) {
                    checkingSecurityQuestion = false
                    showForgotPasswordDialog = false
                    showCustomToast(context, securityQuestionNotSetText)
                    return@launch
                }

                if (securityAnswer.trim().equals(savedAnswer, ignoreCase = true)) {
                    checkingSecurityQuestion = false
                    showForgotPasswordDialog = false
                    securityAnswer = ""
                    securityQuestionError = ""
                    onForgotPasswordSuccess()
                } else {
                    checkingSecurityQuestion = false
                    securityQuestionError = incorrectAnswerText
                }
            } catch (e: Exception) {
                checkingSecurityQuestion = false
                Log.e("FORGOT_PASSWORD", "SECURITY ANSWER ERROR", e)
            }
        }
    }

    fun showPatternError() {
        if (vibrationEnabled) {
            performUnlockVibration(context = context, duration = 100L)
        }
        registerWrongAttempt()
        clearErrorJob?.cancel()
        patternError = true
        error = patternNotMatchText
        clearErrorJob = scope.launch {
            delay(1500)
            enteredPattern = emptyList()
            patternError = false
            error = ""
            clearErrorJob = null
        }
    }

    fun checkPin(pin: String) {
        if (pin == savedPin) {
            error = ""
            enteredPin = ""
            scope.launch {
                try {
                    dataStore.resetIntruderWrongAttempts()
                } catch (e: Exception) {
                    Log.e("INTRUDER_DEBUG", "RESET ERROR", e)
                }
            }
            onUnlockSuccess()
        } else {
            registerWrongAttempt()
            error = correctPasswordText
            enteredPin = ""
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(backgroundColor)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = if (authType == "pattern") stringResource(R.string.draw_pattern) else stringResource(R.string.enter_passcode),
                color = Color.White,
                fontSize = 25.sp,
                fontWeight = FontWeight.Normal
            )

            Spacer(modifier = Modifier.height(15.dp))

            if (authType == "pin") {
                val pinLength = if (savedPin.isNotEmpty()) savedPin.length else 6

                Text(
                    text = stringResource(R.string.enter_digit_pin, pinLength),
                    color = Color.White,
                    fontSize = 16.sp
                )

                Spacer(modifier = Modifier.height(18.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    repeat(pinLength) { index ->
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .background(
                                    if (index < enteredPin.length) Color.White else Color.White.copy(alpha = 0.45f),
                                    CircleShape
                                )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(25.dp))

                if (error.isNotEmpty()) {
                    Text(text = error, color = Color.Red, fontSize = 14.sp)
                }

                Spacer(modifier = Modifier.height(35.dp))

                NumberPad(
                    onNumberClick = { number ->
                        if (enteredPin.length < pinLength) {
                            val newPin = enteredPin + number
                            enteredPin = newPin
                            error = ""
                            if (newPin.length == pinLength) {
                                checkPin(newPin)
                            }
                        }
                    },
                    onDelete = {
                        if (enteredPin.isNotEmpty()) {
                            enteredPin = enteredPin.dropLast(1)
                            error = ""
                        }
                    },
                    buttonColor = numberButtonColor
                )

                Spacer(modifier = Modifier.height(30.dp))

                Box(
                    modifier = Modifier.fillMaxWidth().padding(end = 30.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Text(
                        text = stringResource(R.string.forgot_password),
                        color = Color.White,
                        fontSize = 16.sp,
                        modifier = Modifier
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) {
                                openForgotPassword()
                            }
                            .padding(8.dp)
                    )
                }
            } else {
                Box(
                    modifier = Modifier.height(24.dp).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    if (patternError && error.isNotEmpty()) {
                        Text(text = error, color = Color.Red, fontSize = 20.sp)
                    }
                }

                UnlockPatternGrid(
                    selectedDots = enteredPattern,
                    isError = patternError,
                    hideTrack = hideTrackEnabled,
                    vibrationEnabled = vibrationEnabled,
                    onPatternChanged = { dots ->
                        enteredPattern = dots
                        if (patternError) {
                            clearErrorJob?.cancel()
                            clearErrorJob = null
                            patternError = false
                            error = ""
                        }
                    },
                    onPatternFinished = { pattern ->
                        val originalPattern = savedPattern.split("-").mapNotNull { it.toIntOrNull() }

                        if (pattern != originalPattern) {
                            showPatternError()
                            return@UnlockPatternGrid
                        }

                        clearErrorJob?.cancel()
                        clearErrorJob = null
                        patternError = false
                        error = ""
                        enteredPattern = emptyList()

                        scope.launch {
                            try {
                                dataStore.resetIntruderWrongAttempts()
                            } catch (e: Exception) {
                                Log.e("INTRUDER_DEBUG", "RESET ERROR", e)
                            }
                        }

                        onUnlockSuccess()
                    },
                    dotColor = patternDotColor,
                    errorColor = errorColor,
                    backgroundColor = backgroundColor
                )

                Spacer(modifier = Modifier.height(30.dp))

                Box(
                    modifier = Modifier.fillMaxWidth().padding(end = 30.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Text(
                        text = stringResource(R.string.forgot_password),
                        color = Color.White,
                        fontSize = 16.sp,
                        modifier = Modifier
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) {
                                openForgotPassword()
                            }
                            .padding(8.dp)
                    )
                }
            }
        }

        if (showForgotPasswordDialog) {
            Dialog(
                onDismissRequest = {
                    if (!checkingSecurityQuestion) {
                        showForgotPasswordDialog = false
                        securityAnswer = ""
                        securityQuestionError = ""
                    }
                },
                properties = DialogProperties(
                    dismissOnBackPress = !checkingSecurityQuestion,
                    dismissOnClickOutside = !checkingSecurityQuestion,
                    usePlatformDefaultWidth = true
                )
            ) {
                val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window

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

                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text(
                            text = stringResource(R.string.security_question),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = securityQuestion,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        TextField(
                            value = securityAnswer,
                            onValueChange = {
                                securityAnswer = it
                                securityQuestionError = ""
                            },
                            singleLine = true,
                            enabled = !checkingSecurityQuestion,
                            placeholder = {
                                Text(
                                    text = stringResource(R.string.answer),
                                    color = Color(0xFF7B7B7B)
                                )
                            },
                            isError = securityQuestionError.isNotEmpty(),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                errorContainerColor = Color.Transparent
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (securityQuestionError.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = securityQuestionError,
                                color = Color.Red,
                                fontSize = 13.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Button(
                                enabled = !checkingSecurityQuestion,
                                onClick = {
                                    showForgotPasswordDialog = false
                                    securityAnswer = ""
                                    securityQuestionError = ""
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.Transparent,
                                    disabledContainerColor = Color.Transparent,
                                    contentColor = Color(0xFF7B7B7B),
                                    disabledContentColor = Color(0xFF7B7B7B)
                                )
                            ) {
                                Text(
                                    text = stringResource(R.string.cancel),
                                    color = Color(0xFF7B7B7B)
                                )
                            }

                            Button(
                                enabled = !checkingSecurityQuestion,
                                onClick = {
                                    verifySecurityAnswer()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.Transparent,
                                    disabledContainerColor = Color.Transparent,
                                    contentColor = backgroundColor,
                                    disabledContentColor = backgroundColor
                                )
                            ) {
                                Text(
                                    text = stringResource(R.string.continue_text),
                                    color = backgroundColor
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UnlockPatternGrid(
    selectedDots: List<Int>,
    isError: Boolean,
    hideTrack: Boolean,
    vibrationEnabled: Boolean,
    onPatternChanged: (List<Int>) -> Unit,
    onPatternFinished: (List<Int>) -> Unit,
    dotColor: Color,
    errorColor: Color,
    backgroundColor: Color
) {
    val context = LocalContext.current
    val latestOnPatternChanged by rememberUpdatedState(onPatternChanged)
    val latestOnPatternFinished by rememberUpdatedState(onPatternFinished)

    Box(
        modifier = Modifier
            .size(270.dp)
            .pointerInput(vibrationEnabled) {
                var currentDots = mutableListOf<Int>()
                var patternFinished = false

                fun vibrate() {
                    if (!vibrationEnabled) return

                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                            val vibrator = vibratorManager.defaultVibrator
                            if (vibrator.hasVibrator()) {
                                vibrator.vibrate(
                                    VibrationEffect.createOneShot(
                                        35L,
                                        VibrationEffect.DEFAULT_AMPLITUDE
                                    )
                                )
                            }
                        } else {
                            @Suppress("DEPRECATION")
                            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                            if (vibrator.hasVibrator()) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    vibrator.vibrate(
                                        VibrationEffect.createOneShot(
                                            35L,
                                            VibrationEffect.DEFAULT_AMPLITUDE
                                        )
                                    )
                                } else {
                                    @Suppress("DEPRECATION")
                                    vibrator.vibrate(35L)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("PATTERN_VIBRATION", "VIBRATION ERROR", e)
                    }
                }

                detectDragGestures(
                    onDragStart = { offset ->
                        currentDots = mutableListOf()
                        patternFinished = false

                        val dot = findUnlockDot(
                            touch = offset,
                            width = size.width.toFloat(),
                            height = size.height.toFloat()
                        )

                        if (dot != null) {
                            currentDots.add(dot)
                            vibrate()
                            latestOnPatternChanged(currentDots.toList())
                        }
                    },
                    onDrag = { change, _ ->
                        change.consume()

                        val dot = findUnlockDot(
                            touch = change.position,
                            width = size.width.toFloat(),
                            height = size.height.toFloat()
                        )

                        if (dot != null && !currentDots.contains(dot)) {
                            currentDots.add(dot)
                            vibrate()
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
            val positions = getUnlockPositions(
                width = size.width,
                height = size.height
            )

            if (!hideTrack && selectedDots.size >= 2) {
                for (i in 0 until selectedDots.size - 1) {
                    drawLine(
                        color = if (isError) errorColor else Color.White,
                        start = positions[selectedDots[i]],
                        end = positions[selectedDots[i + 1]],
                        strokeWidth = 8f
                    )
                }
            }

            positions.forEachIndexed { index, position ->
                val selected = selectedDots.contains(index)

                drawCircle(
                    color = dotColor,
                    radius = 17.5.dp.toPx(),
                    center = position
                )

                drawCircle(
                    color = backgroundColor,
                    radius = 13.5.dp.toPx(),
                    center = position
                )

                drawCircle(
                    color = when {
                        selected && isError -> errorColor
                        selected -> Color.White
                        else -> dotColor
                    },
                    radius = 9.dp.toPx(),
                    center = position
                )
            }
        }
    }
}

private fun getUnlockPositions(width: Float, height: Float): List<Offset> {
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

private fun findUnlockDot(touch: Offset, width: Float, height: Float): Int? {
    val positions = getUnlockPositions(width = width, height = height)

    positions.forEachIndexed { index, dot ->
        val dx = touch.x - dot.x
        val dy = touch.y - dot.y
        val distance = sqrt(dx * dx + dy * dy)

        if (distance <= 55f) {
            return index
        }
    }

    return null
}