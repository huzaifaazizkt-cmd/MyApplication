
package com.example.myapplication.Design.screens

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
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

import com.example.myapplication.Design.components.NumberPad
import com.example.myapplication.data.DataStoreManager
import com.example.myapplication.R

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import kotlin.math.sqrt


@Composable
fun PinConfirmScreen(
    navController: NavController,
    context: Context,
    type: String,
    value: String,
    isReset: Boolean = false
) {

    val scope = rememberCoroutineScope()

    val appContext = remember {
        context.applicationContext
    }

    val dataStore = remember {
        DataStoreManager(appContext)
    }

    val lifecycleOwner = LocalLifecycleOwner.current

    /*
     * =========================================================
     * APP LIST CACHE
     * =========================================================
     */

    LaunchedEffect(Unit) {
        AppListCache.preload(appContext)
    }


    /*
     * =========================================================
     * PIN / PATTERN STATES
     * =========================================================
     */

    var confirmPin by remember {
        mutableStateOf("")
    }

    var confirmPattern by remember {
        mutableStateOf<List<Int>>(emptyList())
    }

    var error by remember {
        mutableStateOf("")
    }

    var patternError by remember {
        mutableStateOf(false)
    }

    var patternConfirmed by remember {
        mutableStateOf(false)
    }

    var clearErrorJob by remember {
        mutableStateOf<Job?>(null)
    }


    /*
     * =========================================================
     * DIALOG STATES
     * =========================================================
     */

    var showPermissionDialog by remember {
        mutableStateOf(false)
    }

    var showSecurityDialog by remember {
        mutableStateOf(false)
    }

    var isNavigatingToAppList by remember {
        mutableStateOf(false)
    }


    /*
     * =========================================================
     * PERMISSION STATES
     * =========================================================
     */

    var overlayAllowed by remember {
        mutableStateOf(
            Settings.canDrawOverlays(
                context
            )
        )
    }

    var accessibilityAllowed by remember {
        mutableStateOf(
            isAccessibilityServiceEnabled(
                context
            )
        )
    }


    /*
     * =========================================================
     * AUTO START STATES
     * =========================================================
     */

    var autoStartAvailable by remember {
        mutableStateOf(false)
    }

    /*
     * IMPORTANT:
     *
     * This state becomes TRUE when the user opens
     * Auto Start settings and comes back to the app.
     *
     * This makes the button:
     *
     * Allow -> Allowed
     *
     * green after returning.
     */

    var autoStartAllowed by remember {
        mutableStateOf(false)
    }

    var autoStartOpened by remember {
        mutableStateOf(false)
    }


    /*
     * =========================================================
     * SECURITY QUESTION STATES
     * =========================================================
     */

    var selectedQuestion by remember {
        mutableStateOf("")
    }

    var securityAnswer by remember {
        mutableStateOf("")
    }

    var securityDropdownExpanded by remember {
        mutableStateOf(false)
    }

    var securityAnswerError by remember {
        mutableStateOf(false)
    }

    var securityQuestionLocked by remember {
        mutableStateOf(false)
    }


    /*
     * =========================================================
     * PATTERN / COLORS
     * =========================================================
     */

    val isPattern =
        type.equals(
            "pattern",
            ignoreCase = true
        )

    val backgroundColor =
        Color(0xFF29A0F0)

    val numberButtonColor =
        Color(0xFF69B9F3)

    val patternDotColor =
        Color(0xFF83CCFF)

    val errorColor =
        Color.Red

    val pinLength =
        value.length


    /*
     * =========================================================
     * STRINGS
     * =========================================================
     */

    val passwordNotMatchText =
        stringResource(
            R.string.password_not_match
        )

    val enterCorrectPasswordText =
        stringResource(
            R.string.enter_correct_password
        )


    /*
     * =========================================================
     * CHECK AUTO START AVAILABILITY
     * =========================================================
     */

    LaunchedEffect(Unit) {

        autoStartAvailable =
            getAutoStartIntent(
                appContext
            ) != null
    }


    /*
     * =========================================================
     * SHOW RESET TOAST
     * =========================================================
     */

    fun showResetToast() {

        val toastView =
            TextView(context).apply {

                text =
                    "Password reset successfully"

                setTextColor(
                    android.graphics.Color.rgb(
                        0x7B,
                        0x7B,
                        0x7B
                    )
                )

                textSize = 14f

                gravity =
                    Gravity.CENTER

                setPadding(
                    28,
                    14,
                    28,
                    14
                )

                background =
                    GradientDrawable().apply {

                        setColor(
                            android.graphics.Color.WHITE
                        )

                        cornerRadius =
                            30f
                    }

                elevation = 6f
            }

        Toast(context).apply {

            duration =
                Toast.LENGTH_LONG

            view =
                toastView

            setGravity(
                Gravity.BOTTOM or
                        Gravity.CENTER_HORIZONTAL,
                0,
                80
            )

            show()
        }
    }


    /*
     * =========================================================
     * CHECK PERMISSIONS
     * =========================================================
     */

    fun checkPermissions() {

        overlayAllowed =
            Settings.canDrawOverlays(
                context
            )

        accessibilityAllowed =
            isAccessibilityServiceEnabled(
                context
            )
    }


    /*
     * =========================================================
     * ALL REQUIRED PERMISSIONS
     * =========================================================
     *
     * Auto Start is NOT included here.
     *
     * Security Question should open when:
     *
     * Overlay + Accessibility
     *
     * are allowed.
     */

    fun allPermissionsAllowed(): Boolean {

        return overlayAllowed &&
                accessibilityAllowed
    }


    /*
     * =========================================================
     * OPEN SECURITY QUESTION
     * =========================================================
     */

    fun openSecurityQuestion() {

        showPermissionDialog =
            false

        securityAnswer =
            ""

        securityAnswerError =
            false

        securityDropdownExpanded =
            false

        selectedQuestion =
            ""

        securityQuestionLocked =
            false

        showSecurityDialog =
            true
    }


    /*
     * =========================================================
     * LIFECYCLE
     * =========================================================
     *
     * This is important for:
     *
     * Overlay
     * Accessibility
     * Auto Start
     *
     * When user comes back from Android Settings,
     * ON_RESUME runs.
     */

    val currentShowPermissionDialog =
        rememberUpdatedState(
            showPermissionDialog
        )

    val currentAutoStartOpened =
        rememberUpdatedState(
            autoStartOpened
        )


    DisposableEffect(
        lifecycleOwner
    ) {

        val observer =
            LifecycleEventObserver { _, event ->

                if (
                    event ==
                    Lifecycle.Event.ON_RESUME
                ) {

                    /*
                     * -------------------------------------------------
                     * Refresh Overlay + Accessibility
                     * -------------------------------------------------
                     */

                    val newOverlayAllowed =
                        Settings.canDrawOverlays(
                            context
                        )

                    val newAccessibilityAllowed =
                        isAccessibilityServiceEnabled(
                            context
                        )

                    overlayAllowed =
                        newOverlayAllowed

                    accessibilityAllowed =
                        newAccessibilityAllowed


                    /*
                     * -------------------------------------------------
                     * AUTO START
                     * -------------------------------------------------
                     *
                     * Android does not provide one universal API
                     * to read the Auto Start switch on every OEM.
                     *
                     * Therefore:
                     *
                     * Auto Start opened
                     * +
                     * user returned
                     *
                     * = Allowed
                     *
                     * This gives the same UI behavior as requested:
                     *
                     * Allow -> Allowed
                     */

                    if (
                        currentAutoStartOpened.value
                    ) {

                        autoStartAllowed =
                            true

                        autoStartOpened =
                            false
                    }


                    /*
                     * -------------------------------------------------
                     * OPEN SECURITY QUESTION
                     * -------------------------------------------------
                     *
                     * Only Overlay + Accessibility are required.
                     */

                    if (
                        currentShowPermissionDialog.value &&
                        newOverlayAllowed &&
                        newAccessibilityAllowed
                    ) {

                        showPermissionDialog =
                            false

                        openSecurityQuestion()
                    }
                }
            }

        lifecycleOwner.lifecycle.addObserver(
            observer
        )

        onDispose {

            lifecycleOwner.lifecycle.removeObserver(
                observer
            )
        }
    }


    /*
     * =========================================================
     * PERMISSION DIALOG EFFECT
     * =========================================================
     */

    LaunchedEffect(
        showPermissionDialog
    ) {

        if (showPermissionDialog) {

            checkPermissions()

            if (
                allPermissionsAllowed()
            ) {

                showPermissionDialog =
                    false

                openSecurityQuestion()
            }
        }
    }


    /*
     * =========================================================
     * PERMISSION STATE EFFECT
     * =========================================================
     */

    LaunchedEffect(
        overlayAllowed,
        accessibilityAllowed
    ) {

        if (
            showPermissionDialog &&
            overlayAllowed &&
            accessibilityAllowed
        ) {

            showPermissionDialog =
                false

            openSecurityQuestion()
        }
    }


    /*
     * =========================================================
     * CLEAR PATTERN ERROR
     * =========================================================
     */

    fun clearPatternError() {

        clearErrorJob?.cancel()

        clearErrorJob =
            null

        patternError =
            false

        error =
            ""

        confirmPattern =
            emptyList()
    }


    /*
     * =========================================================
     * SHOW PATTERN ERROR
     * =========================================================
     */

    fun showPatternError() {

        clearErrorJob?.cancel()

        patternError =
            true

        error =
            passwordNotMatchText

        clearErrorJob =
            scope.launch {

                delay(1500)

                confirmPattern =
                    emptyList()

                patternError =
                    false

                error =
                    ""

                clearErrorJob =
                    null
            }
    }


    /*
     * =========================================================
     * FINISH RESET
     * =========================================================
     */

    fun finishReset() {

        if (
            isNavigatingToAppList
        ) {
            return
        }

        isNavigatingToAppList =
            true

        showSecurityDialog =
            false

        showPermissionDialog =
            false

        scope.launch(
            Dispatchers.IO
        ) {

            try {

                if (isPattern) {

                    dataStore.savePattern(
                        value
                    )

                    dataStore.saveAuthType(
                        "pattern"
                    )

                } else {

                    dataStore.savePin(
                        value
                    )

                    dataStore.saveAuthType(
                        "pin"
                    )

                }

                withContext(
                    Dispatchers.Main
                ) {

                    showResetToast()

                    if (
                        !navController.popBackStack(
                            "appList",
                            false
                        )
                    ) {

                        navController.navigate(
                            "appList"
                        ) {

                            launchSingleTop =
                                true
                        }
                    }
                }

            } catch (
                e: Exception
            ) {

                e.printStackTrace()

                withContext(
                    Dispatchers.Main
                ) {

                    isNavigatingToAppList =
                        false

                    Toast.makeText(
                        context,
                        "Password reset failed",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }


    /*
     * =========================================================
     * CONTINUE WITH PIN
     * =========================================================
     */

    fun continueWithPin() {

        if (isReset) {

            finishReset()

            return
        }

        checkPermissions()

        /*
         * Always show permission dialog first.
         *
         * If Overlay + Accessibility are already allowed,
         * LaunchedEffect will immediately open Security Question.
         */

        showPermissionDialog =
            true

        scope.launch(
            Dispatchers.IO
        ) {

            try {

                dataStore.savePin(
                    value
                )

                dataStore.saveAuthType(
                    "pin"
                )

            } catch (
                e: Exception
            ) {

                e.printStackTrace()
            }
        }
    }


    /*
     * =========================================================
     * CONTINUE WITH PATTERN
     * =========================================================
     */

    fun continueWithPattern() {

        if (isReset) {

            finishReset()

            return
        }

        checkPermissions()

        /*
         * Always show permission dialog first.
         */

        showPermissionDialog =
            true

        scope.launch(
            Dispatchers.IO
        ) {

            try {

                dataStore.savePattern(
                    value
                )

                dataStore.saveAuthType(
                    "pattern"
                )

            } catch (
                e: Exception
            ) {

                e.printStackTrace()
            }
        }
    }


    /*
     * =========================================================
     * OPEN OVERLAY PERMISSION
     * =========================================================
     */

    fun openOverlayPermission() {

        try {

            val intent =
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse(
                        "package:${context.packageName}"
                    )
                )

            context.startActivity(
                intent
            )

        } catch (
            e: Exception
        ) {

            try {

                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION
                    )
                )

            } catch (
                e2: Exception
            ) {

                try {

                    context.startActivity(
                        Intent(
                            Settings.ACTION_SETTINGS
                        )
                    )

                } catch (
                    e3: Exception
                ) {

                    e3.printStackTrace()
                }
            }
        }
    }


    /*
     * =========================================================
     * OPEN ACCESSIBILITY SETTINGS
     * =========================================================
     */

    fun openAccessibilitySettings() {

        try {

            context.startActivity(
                Intent(
                    Settings.ACTION_ACCESSIBILITY_SETTINGS
                )
            )

        } catch (
            e: Exception
        ) {

            e.printStackTrace()
        }
    }


    /*
     * =========================================================
     * OPEN AUTO START SETTINGS
     * =========================================================
     *
     * IMPORTANT:
     *
     * autoStartOpened = true
     * before opening settings.
     *
     * When user returns:
     *
     * ON_RESUME
     *
     * detects autoStartOpened == true
     *
     * and changes:
     *
     * autoStartAllowed = true
     *
     * So button becomes:
     *
     * GREEN
     * Allowed
     */

    fun openAutoStartSettings() {

        val autoStartIntent =
            getAutoStartIntent(
                context
            )

        if (
            autoStartIntent == null
        ) {

            autoStartAvailable =
                false

            return
        }

        /*
         * Mark that Auto Start settings
         * has been opened.
         */

        autoStartOpened =
            true

        try {

            context.startActivity(
                autoStartIntent
            )

        } catch (
            e: Exception
        ) {

            /*
             * If OEM Auto Start page failed,
             * don't mark it as opened.
             */

            autoStartOpened =
                false

            try {

                context.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse(
                            "package:${context.packageName}"
                        )
                    )
                )

            } catch (
                e2: Exception
            ) {

                e2.printStackTrace()
            }
        }
    }


    /*
     * =========================================================
     * GO TO APP LIST
     * =========================================================
     */

    fun goToAppList() {

        if (
            isNavigatingToAppList
        ) {
            return
        }

        isNavigatingToAppList =
            true

        showSecurityDialog =
            false

        showPermissionDialog =
            false

        scope.launch {

            try {

                dataStore.saveAppInitialized(
                    true
                )

            } catch (
                e: Exception
            ) {

                e.printStackTrace()
            }

            navController.navigate(
                "appList"
            ) {

                popUpTo(
                    "create"
                ) {
                    inclusive = true
                }

                launchSingleTop =
                    true
            }
        }
    }


    /*
     * =========================================================
     * MAIN SCREEN
     * =========================================================
     */

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    backgroundColor
                )
    ) {

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(
                        horizontal = 30.dp
                    ),

            horizontalAlignment =
                Alignment.CenterHorizontally,

            verticalArrangement =
                Arrangement.Center
        ) {

            Text(
                text =
                    if (isPattern) {

                        stringResource(
                            R.string.confirm_pattern
                        )

                    } else {

                        stringResource(
                            R.string.confirm_passcode
                        )
                    },

                color =
                    Color.White,

                fontSize =
                    25.sp
            )

            Spacer(
                modifier =
                    Modifier.height(28.dp)
            )

            Row(
                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Box(
                    modifier =
                        Modifier
                            .size(25.dp)
                            .border(
                                2.dp,
                                Color.White,
                                CircleShape
                            ),

                    contentAlignment =
                        Alignment.Center
                ) {

                    Text(
                        text = "1",
                        color =
                            Color.White,
                        fontSize =
                            12.sp
                    )
                }

                Box(
                    modifier =
                        Modifier
                            .width(95.dp)
                            .height(2.dp)
                            .background(
                                Color.White
                            )
                )

                Box(
                    modifier =
                        Modifier
                            .size(31.dp)
                            .background(
                                Color.White.copy(
                                    alpha = 0.08f
                                ),
                                CircleShape
                            )
                            .border(
                                2.dp,
                                Color.White.copy(
                                    alpha = 0.35f
                                ),
                                CircleShape
                            ),

                    contentAlignment =
                        Alignment.Center
                ) {

                    Box(
                        modifier =
                            Modifier
                                .size(21.dp)
                                .background(
                                    Color.White,
                                    CircleShape
                                ),

                        contentAlignment =
                            Alignment.Center
                    ) {

                        Text(
                            text = "2",
                            color =
                                backgroundColor,
                            fontSize =
                                15.sp
                        )
                    }
                }
            }

            Spacer(
                modifier =
                    Modifier.height(65.dp)
            )

            /*
             * =================================================
             * PATTERN
             * =================================================
             */

            if (isPattern) {

                Text(
                    text =
                        stringResource(
                            R.string.draw_pattern_again
                        ),

                    color =
                        Color.White,

                    fontSize =
                        18.sp
                )

                Spacer(
                    modifier =
                        Modifier.height(18.dp)
                )

                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(24.dp),

                    contentAlignment =
                        Alignment.Center
                ) {

                    if (
                        error.isNotEmpty()
                    ) {

                        Text(
                            text =
                                error,

                            color =
                                Color.Red,

                            fontSize =
                                15.sp
                        )
                    }
                }

                Spacer(
                    modifier =
                        Modifier.height(38.dp)
                )

                ConfirmPatternGrid(
                    selectedDots =
                        confirmPattern,

                    isError =
                        patternError,

                    onPatternChanged =
                        { dots ->

                            confirmPattern =
                                dots

                            if (
                                patternError
                            ) {

                                clearErrorJob?.cancel()

                                clearErrorJob =
                                    null

                                patternError =
                                    false

                                error =
                                    ""
                            }
                        },

                    onPatternFinished =
                        { pattern ->

                            val originalPattern =
                                value
                                    .split("-")
                                    .mapNotNull {
                                        it.toIntOrNull()
                                    }

                            if (
                                pattern.size < 4
                            ) {

                                showPatternError()

                                return@ConfirmPatternGrid
                            }

                            if (
                                pattern !=
                                originalPattern
                            ) {

                                showPatternError()

                                return@ConfirmPatternGrid
                            }

                            clearErrorJob?.cancel()

                            clearErrorJob =
                                null

                            patternError =
                                false

                            error =
                                ""

                            confirmPattern =
                                pattern

                            patternConfirmed =
                                true
                        },

                    dotColor =
                        patternDotColor,

                    errorColor =
                        errorColor,

                    backgroundColor =
                        backgroundColor
                )

                Spacer(
                    modifier =
                        Modifier.height(55.dp)
                )

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                end = 30.dp
                            ),

                    horizontalArrangement =
                        Arrangement.End
                ) {

                    Text(
                        text =
                            stringResource(
                                R.string.continue_text
                            ),

                        color =
                            if (
                                patternConfirmed
                            ) {
                                Color.White
                            } else {
                                Color.White.copy(
                                    alpha = 0.35f
                                )
                            },

                        fontSize =
                            20.sp,

                        modifier =
                            Modifier
                                .clickable(
                                    enabled =
                                        patternConfirmed,

                                    indication =
                                        null,

                                    interactionSource =
                                        remember {
                                            MutableInteractionSource()
                                        }
                                ) {

                                    continueWithPattern()
                                }
                                .padding(
                                    10.dp
                                )
                    )
                }

            } else {

                /*
                 * =================================================
                 * PIN
                 * =================================================
                 */

                Text(
                    text =
                        stringResource(
                            R.string.confirm_passcode
                        ),

                    color =
                        Color.White,

                    fontSize =
                        18.sp
                )

                Spacer(
                    modifier =
                        Modifier.height(18.dp)
                )

                Row(
                    horizontalArrangement =
                        Arrangement.spacedBy(
                            14.dp
                        )
                ) {

                    repeat(
                        pinLength
                    ) { index ->

                        Box(
                            modifier =
                                Modifier
                                    .size(20.dp)
                                    .border(
                                        1.5.dp,
                                        Color.White,
                                        CircleShape
                                    )
                                    .background(
                                        if (
                                            index <
                                            confirmPin.length
                                        ) {
                                            Color.White
                                        } else {
                                            Color.Transparent
                                        },
                                        CircleShape
                                    )
                        )
                    }
                }

                Spacer(
                    modifier =
                        Modifier.height(15.dp)
                )

                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(22.dp)
                            .padding(
                                top = 5.dp
                            ),

                    contentAlignment =
                        Alignment.Center
                ) {

                    if (
                        error.isNotEmpty()
                    ) {

                        Text(
                            text =
                                error,

                            color =
                                Color.Red,

                            fontSize =
                                16.sp,

                            textAlign =
                                TextAlign.Center
                        )
                    }
                }

                Spacer(
                    modifier =
                        Modifier.height(50.dp)
                )

                NumberPad(
                    onNumberClick =
                        { number ->

                            if (
                                confirmPin.length <
                                pinLength
                            ) {

                                confirmPin +=
                                    number

                                error =
                                    ""
                            }
                        },

                    onDelete = {

                        if (
                            confirmPin.isNotEmpty()
                        ) {

                            confirmPin =
                                confirmPin.dropLast(
                                    1
                                )

                            error =
                                ""
                        }
                    },

                    buttonColor =
                        numberButtonColor
                )

                Spacer(
                    modifier =
                        Modifier.height(55.dp)
                )

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                end = 30.dp
                            ),

                    horizontalArrangement =
                        Arrangement.spacedBy(
                            30.dp,
                            Alignment.End
                        )
                ) {

                    Text(
                        text =
                            stringResource(
                                R.string.reset
                            ),

                        color =
                            if (
                                confirmPin.isEmpty()
                            ) {
                                Color.White.copy(
                                    alpha = 0.35f
                                )
                            } else {
                                Color.White
                            },

                        fontSize =
                            20.sp,

                        modifier =
                            Modifier
                                .clickable(
                                    enabled =
                                        confirmPin.isNotEmpty(),

                                    indication =
                                        null,

                                    interactionSource =
                                        remember {
                                            MutableInteractionSource()
                                        }
                                ) {

                                    confirmPin =
                                        ""

                                    error =
                                        ""
                                }
                                .padding(
                                    10.dp
                                )
                    )

                    Text(
                        text =
                            stringResource(
                                R.string.continue_text
                            ),

                        color =
                            if (
                                confirmPin.length ==
                                pinLength
                            ) {
                                Color.White
                            } else {
                                Color.White.copy(
                                    alpha = 0.35f
                                )
                            },

                        fontSize =
                            20.sp,

                        modifier =
                            Modifier
                                .clickable(
                                    enabled =
                                        confirmPin.length ==
                                                pinLength,

                                    indication =
                                        null,

                                    interactionSource =
                                        remember {
                                            MutableInteractionSource()
                                        }
                                ) {

                                    if (
                                        confirmPin !=
                                        value
                                    ) {

                                        error =
                                            enterCorrectPasswordText

                                        confirmPin =
                                            ""

                                        return@clickable
                                    }

                                    continueWithPin()
                                }
                                .padding(
                                    10.dp
                                )
                    )
                }
            }
        }


        /*
         * =========================================================
         * PERMISSION DIALOG
         * =========================================================
         */

        if (
            showPermissionDialog
        ) {

            PermissionRequiredDialog(

                autoStartAvailable =
                    autoStartAvailable,

                autoStartAllowed =
                    autoStartAllowed,

                overlayAllowed =
                    overlayAllowed,

                accessibilityAllowed =
                    accessibilityAllowed,

                onAutoStartAllow =
                    {
                        openAutoStartSettings()
                    },

                onOverlayAllow =
                    {
                        openOverlayPermission()
                    },

                onAccessibilityAllow =
                    {
                        openAccessibilitySettings()
                    },

                onDone = {}
            )
        }


        /*
         * =========================================================
         * SECURITY QUESTION
         * =========================================================
         */

        if (
            showSecurityDialog
        ) {

            SecurityQuestionDialog(

                selectedQuestion =
                    selectedQuestion,

                answer =
                    securityAnswer,

                dropdownExpanded =
                    securityDropdownExpanded,

                isReset =
                    isReset,

                questionLocked =
                    securityQuestionLocked,

                answerError =
                    securityAnswerError,

                onDropdownClick =
                    {

                        if (
                            !securityQuestionLocked
                        ) {

                            securityDropdownExpanded =
                                !securityDropdownExpanded
                        }
                    },

                onQuestionSelected =
                    { question ->

                        if (
                            !securityQuestionLocked
                        ) {

                            selectedQuestion =
                                question

                            securityDropdownExpanded =
                                false

                            securityAnswerError =
                                false
                        }
                    },

                onAnswerChanged =
                    { newAnswer ->

                        securityAnswer =
                            newAnswer

                        securityAnswerError =
                            false
                    },

                onSkip =
                    {

                        if (!isReset) {
                            goToAppList()
                        }
                    },

                onSave =
                    {

                        scope.launch {

                            try {

                                val question =
                                    selectedQuestion
                                        .trim()

                                val enteredAnswer =
                                    securityAnswer
                                        .trim()

                                if (
                                    question.isEmpty() ||
                                    enteredAnswer.isEmpty()
                                ) {

                                    securityAnswerError =
                                        true

                                    return@launch
                                }

                                dataStore
                                    .saveSecurityQuestion(
                                        question
                                    )

                                dataStore
                                    .saveSecurityAnswer(
                                        enteredAnswer
                                    )

                                securityAnswerError =
                                    false

                                goToAppList()

                            } catch (
                                e: Exception
                            ) {

                                e.printStackTrace()

                                securityAnswerError =
                                    true
                            }
                        }
                    }
            )
        }
    }
}


/*
 * =============================================================
 * PERMISSION REQUIRED DIALOG
 * =============================================================
 */

@Composable
private fun PermissionRequiredDialog(
    autoStartAvailable: Boolean,
    autoStartAllowed: Boolean,
    overlayAllowed: Boolean,
    accessibilityAllowed: Boolean,
    onAutoStartAllow: () -> Unit,
    onOverlayAllow: () -> Unit,
    onAccessibilityAllow: () -> Unit,
    onDone: () -> Unit
) {

    Dialog(
        onDismissRequest = {},

        properties =
            DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false
            )
    ) {

        val dialogWindow =
            (
                    LocalView.current.parent
                            as? DialogWindowProvider
                    )?.window

        DisposableEffect(
            dialogWindow
        ) {

            dialogWindow?.setSoftInputMode(
                WindowManager.LayoutParams
                    .SOFT_INPUT_ADJUST_NOTHING
            )

            onDispose {

                dialogWindow?.setSoftInputMode(
                    WindowManager.LayoutParams
                        .SOFT_INPUT_ADJUST_RESIZE
                )
            }
        }

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Color.Transparent
                    ),

            contentAlignment =
                Alignment.Center
        ) {

            Column(
                modifier =
                    Modifier
                        .fillMaxWidth(0.90f)
                        .wrapContentHeight()
                        .background(
                            Color.White,
                            RoundedCornerShape(
                                20.dp
                            )
                        )
                        .padding(
                            start = 25.dp,
                            end = 25.dp,
                            top = 24.dp,
                            bottom = 24.dp
                        )
            ) {

                Text(
                    text =
                        stringResource(
                            R.string.permissions_required
                        ),

                    color =
                        Color(0xFF333333),

                    fontSize =
                        20.sp,

                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                bottom = 24.dp
                            ),

                    textAlign =
                        TextAlign.Center
                )


                /*
                 * =================================================
                 * AUTO START
                 * =================================================
                 */

                if (
                    autoStartAvailable
                ) {

                    PermissionRow(

                        icon = {

                            Image(
                                painter =
                                    painterResource(
                                        R.drawable.group3
                                    ),

                                contentDescription =
                                    stringResource(
                                        R.string.auto_start
                                    ),

                                modifier =
                                    Modifier.size(
                                        20.dp
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

                        allowed =
                            autoStartAllowed,

                        onAllow =
                            onAutoStartAllow
                    )

                    PermissionDivider()
                }


                /*
                 * =================================================
                 * OVERLAY
                 * =================================================
                 */

                PermissionRow(

                    icon = {

                        Image(
                            painter =
                                painterResource(
                                    R.drawable.group1
                                ),

                            contentDescription =
                                stringResource(
                                    R.string.show_over_other_apps
                                ),

                            modifier =
                                Modifier.size(
                                    20.dp
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

                    allowed =
                        overlayAllowed,

                    onAllow =
                        onOverlayAllow
                )

                PermissionDivider()


                /*
                 * =================================================
                 * ACCESSIBILITY
                 * =================================================
                 */

                PermissionRow(

                    icon = {

                        Image(
                            painter =
                                painterResource(
                                    R.drawable.group2
                                ),

                            contentDescription =
                                stringResource(
                                    R.string.detect_launched_app
                                ),

                            modifier =
                                Modifier.size(
                                    20.dp
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

                    allowed =
                        accessibilityAllowed,

                    onAllow =
                        onAccessibilityAllow
                )


                /*
                 * =================================================
                 * BOTTOM TEXT
                 * =================================================
                 */

                Text(
                    text =
                        stringResource(
                            R.string.permissions_work_properly
                        ),

                    color =
                        Color(0xFFBDBDBD),

                    fontSize =
                        12.sp,

                    lineHeight =
                        13.sp,

                    modifier =
                        Modifier.padding(
                            start = 12.dp,
                            top = 20.dp
                        )
                )
            }
        }
    }
}


/*
 * =============================================================
 * PERMISSION ROW
 * =============================================================
 */

@Composable
private fun PermissionRow(
    icon: @Composable () -> Unit,
    title: String,
    description: String,
    allowed: Boolean,
    onAllow: () -> Unit
) {

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    vertical = 8.dp
                ),

        verticalAlignment =
            Alignment.Top
    ) {

        Box(
            modifier =
                Modifier
                    .width(32.dp)
                    .padding(
                        top = 4.dp
                    ),

            contentAlignment =
                Alignment.Center
        ) {

            icon()
        }


        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .padding(
                        start = 8.dp
                    )
        ) {

            Text(
                text =
                    title,

                color =
                    Color(0xFF333333),

                fontSize =
                    15.sp
            )

            Spacer(
                modifier =
                    Modifier.height(7.dp)
            )

            Text(
                text =
                    description,

                color =
                    Color(0xFFBDBDBD),

                fontSize =
                    13.sp,

                lineHeight =
                    18.sp
            )
        }


        /*
         * =========================================================
         * ALLOW / ALLOWED BUTTON
         * =========================================================
         */

        Box(
            modifier =
                Modifier
                    .padding(
                        top = 8.dp
                    )
                    .width(72.dp)
                    .height(40.dp)
                    .background(
                        if (allowed) {

                            /*
                             * GREEN
                             */

                            Color(0xFF4CAF50)

                        } else {

                            /*
                             * BLUE
                             */

                            Color(0xFF2196F3)
                        },

                        RoundedCornerShape(
                            4.dp
                        )
                    )
                    .clickable(
                        enabled = !allowed
                    ) {

                        if (!allowed) {
                            onAllow()
                        }
                    },

            contentAlignment =
                Alignment.Center
        ) {

            Text(
                text =
                    if (allowed) {

                        stringResource(
                            R.string.allowed
                        )

                    } else {

                        stringResource(
                            R.string.allow
                        )
                    },

                color =
                    Color.White,

                fontSize =
                    13.sp,

                textAlign =
                    TextAlign.Center
            )
        }
    }
}


/*
 * =============================================================
 * PERMISSION DIVIDER
 * =============================================================
 */

@Composable
private fun PermissionDivider() {

    Spacer(
        modifier =
            Modifier.height(9.dp)
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
            Modifier.height(9.dp)
    )
}


/*
 * =============================================================
 * SECURITY QUESTION DIALOG
 * =============================================================
 */

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

    val questions =
        listOf(

            stringResource(
                R.string.security_question_name
            ),

            stringResource(
                R.string.security_question_father
            ),

            stringResource(
                R.string.security_question_pet
            ),

            stringResource(
                R.string.security_question_job
            )
        )


    Dialog(
        onDismissRequest = {},

        properties =
            DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false
            )
    ) {

        val dialogWindow =
            (
                    LocalView.current.parent
                            as? DialogWindowProvider
                    )?.window

        DisposableEffect(
            dialogWindow
        ) {

            dialogWindow?.setSoftInputMode(
                WindowManager.LayoutParams
                    .SOFT_INPUT_ADJUST_NOTHING
            )

            onDispose {

                dialogWindow?.setSoftInputMode(
                    WindowManager.LayoutParams
                        .SOFT_INPUT_ADJUST_RESIZE
                )
            }
        }


        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Color.Transparent
                    ),

            contentAlignment =
                Alignment.Center
        ) {

            Column(
                modifier =
                    Modifier
                        .fillMaxWidth(0.90f)
                        .wrapContentHeight()
                        .background(
                            Color.White,
                            RoundedCornerShape(
                                26.dp
                            )
                        )
                        .padding(
                            start = 25.dp,
                            end = 25.dp,
                            top = 30.dp,
                            bottom = 30.dp
                        )
            ) {

                Text(
                    text =
                        stringResource(
                            R.string.security_questions
                        ),

                    color =
                        Color(0xFF333333),

                    fontSize =
                        19.sp,

                    modifier =
                        Modifier.fillMaxWidth(),

                    textAlign =
                        TextAlign.Center
                )

                Spacer(
                    modifier =
                        Modifier.height(8.dp)
                )

                Text(
                    text =
                        stringResource(
                            R.string.security_question_description
                        ),

                    color =
                        Color(0xFFBDBDBD),

                    fontSize =
                        11.sp,

                    lineHeight =
                        18.sp,

                    modifier =
                        Modifier.fillMaxWidth(),

                    textAlign =
                        TextAlign.Center
                )

                Spacer(
                    modifier =
                        Modifier.height(28.dp)
                )

                Text(
                    text =
                        stringResource(
                            R.string.select_security_questions
                        ),

                    color =
                        Color(0xFF333333),

                    fontSize =
                        14.sp
                )

                Spacer(
                    modifier =
                        Modifier.height(8.dp)
                )


                /*
                 * =================================================
                 * QUESTION DROPDOWN
                 * =================================================
                 */

                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                ) {

                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .background(
                                    Color(0xFFF8F8F8),
                                    RoundedCornerShape(
                                        15.dp
                                    )
                                )
                                .clickable(
                                    enabled =
                                        !questionLocked,

                                    indication =
                                        null,

                                    interactionSource =
                                        remember {
                                            MutableInteractionSource()
                                        }
                                ) {

                                    if (
                                        !questionLocked
                                    ) {

                                        onDropdownClick()
                                    }
                                }
                                .padding(
                                    start = 18.dp,
                                    end = 14.dp
                                ),

                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {

                        Text(
                            text =
                                if (
                                    selectedQuestion.isEmpty()
                                ) {

                                    stringResource(
                                        R.string.select_security_question
                                    )

                                } else {

                                    selectedQuestion
                                },

                            color =
                                if (
                                    selectedQuestion.isEmpty()
                                ) {

                                    Color(0xFFBDBDBD)

                                } else {

                                    Color(0xFF333333)
                                },

                            fontSize =
                                14.sp,

                            maxLines =
                                1,

                            modifier =
                                Modifier.weight(
                                    1f
                                )
                        )


                        if (
                            !questionLocked
                        ) {

                            Icon(
                                imageVector =
                                    Icons.Outlined.KeyboardArrowDown,

                                contentDescription =
                                    stringResource(
                                        R.string.select_security_question
                                    ),

                                tint =
                                    Color(0xFF8F8F8F),

                                modifier =
                                    Modifier.size(
                                        22.dp
                                    )
                            )
                        }
                    }


                    /*
                     * =================================================
                     * POPUP QUESTIONS
                     * =================================================
                     */

                    if (
                        dropdownExpanded &&
                        !questionLocked
                    ) {

                        Popup(
                            alignment =
                                Alignment.TopEnd,

                            onDismissRequest = {
                                onDropdownClick()
                            },

                            properties =
                                PopupProperties(
                                    focusable = true
                                )
                        ) {

                            Column(
                                modifier =
                                    Modifier
                                        .width(210.dp)
                                        .background(
                                            Color.White,
                                            RoundedCornerShape(
                                                15.dp
                                            )
                                        )
                                        .border(
                                            1.dp,
                                            Color(0xFFE5E5E5),
                                            RoundedCornerShape(
                                                15.dp
                                            )
                                        )
                            ) {

                                questions.forEachIndexed {
                                        index,
                                        question ->

                                    Text(
                                        text =
                                            question,

                                        color =
                                            Color(0xFF333333),

                                        fontSize =
                                            13.sp,

                                        maxLines =
                                            1,

                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .clickable(
                                                    indication =
                                                        null,

                                                    interactionSource =
                                                        remember {
                                                            MutableInteractionSource()
                                                        }
                                                ) {

                                                    onQuestionSelected(
                                                        question
                                                    )
                                                }
                                                .padding(
                                                    horizontal =
                                                        18.dp,

                                                    vertical =
                                                        14.dp
                                                )
                                    )

                                    if (
                                        index <
                                        questions.lastIndex
                                    ) {

                                        Box(
                                            modifier =
                                                Modifier
                                                    .fillMaxWidth()
                                                    .height(1.dp)
                                                    .background(
                                                        Color(
                                                            0xFFF0F0F0
                                                        )
                                                    )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }


                Spacer(
                    modifier =
                        Modifier.height(23.dp)
                )


                /*
                 * =================================================
                 * ANSWER
                 * =================================================
                 */

                Text(
                    text =
                        stringResource(
                            R.string.enter_security_answer
                        ),

                    color =
                        Color(0xFF333333),

                    fontSize =
                        14.sp
                )

                Spacer(
                    modifier =
                        Modifier.height(8.dp)
                )

                BasicTextField(
                    value =
                        answer,

                    onValueChange =
                        onAnswerChanged,

                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .background(
                                Color(0xFFF8F8F8),
                                RoundedCornerShape(
                                    15.dp
                                )
                            )
                            .padding(
                                horizontal = 15.dp
                            ),

                    singleLine =
                        true,

                    textStyle =
                        TextStyle(
                            color =
                                Color(0xFF333333),

                            fontSize =
                                12.sp,

                            lineHeight =
                                16.sp
                        ),

                    decorationBox =
                        { innerTextField ->

                            Box(
                                modifier =
                                    Modifier.fillMaxSize(),

                                contentAlignment =
                                    Alignment.CenterStart
                            ) {

                                if (
                                    answer.isEmpty()
                                ) {

                                    Text(
                                        text =
                                            stringResource(
                                                R.string.enter_your_answer
                                            ),

                                        color =
                                            Color(0xFFBDBDBD),

                                        fontSize =
                                            12.sp
                                    )
                                }

                                innerTextField()
                            }
                        }
                )


                if (
                    answerError
                ) {

                    Spacer(
                        modifier =
                            Modifier.height(5.dp)
                    )

                    Text(
                        text =
                            "Enter your correct answer",

                        color =
                            Color.Red,

                        fontSize =
                            12.sp,

                        modifier =
                            Modifier.fillMaxWidth()
                    )
                }


                Spacer(
                    modifier =
                        Modifier.height(30.dp)
                )


                /*
                 * =================================================
                 * BUTTONS
                 * =================================================
                 */

                Row(
                    modifier =
                        Modifier.fillMaxWidth(),

                    horizontalArrangement =
                        Arrangement.End,

                    verticalAlignment =
                        Alignment.CenterVertically
                ) {

                    if (!isReset) {

                        Text(
                            text =
                                stringResource(
                                    R.string.skip
                                ),

                            color =
                                Color(0xFF2196F3),

                            fontSize =
                                16.sp,

                            modifier =
                                Modifier
                                    .clickable(
                                        indication =
                                            null,

                                        interactionSource =
                                            remember {
                                                MutableInteractionSource()
                                            }
                                    ) {

                                        onSkip()
                                    }
                                    .padding(
                                        horizontal =
                                            20.dp,

                                        vertical =
                                            10.dp
                                    )
                        )

                        Spacer(
                            modifier =
                                Modifier.width(
                                    20.dp
                                )
                        )
                    }


                    val saveEnabled =
                        selectedQuestion.isNotEmpty() &&
                                answer.trim()
                                    .isNotEmpty()


                    Text(
                        text =
                            stringResource(
                                R.string.save
                            ),

                        color =
                            if (saveEnabled) {

                                Color(0xFF2196F3)

                            } else {

                                Color(0xFF90CAF9)
                            },

                        fontSize =
                            16.sp,

                        modifier =
                            Modifier
                                .clickable(
                                    enabled =
                                        saveEnabled,

                                    indication =
                                        null,

                                    interactionSource =
                                        remember {
                                            MutableInteractionSource()
                                        }
                                ) {

                                    onSave()
                                }
                                .padding(
                                    horizontal =
                                        20.dp,

                                    vertical =
                                        10.dp
                                )
                    )
                }
            }
        }
    }
}


/*
 * =============================================================
 * ACCESSIBILITY SERVICE CHECK
 * =============================================================
 */

private fun isAccessibilityServiceEnabled(
    context: Context
): Boolean {

    return try {

        val enabledServices =
            Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )

        if (
            enabledServices.isNullOrEmpty()
        ) {

            false

        } else {

            val packageName =
                context.packageName

            enabledServices
                .split(":")
                .any { serviceName ->

                    val component =
                        ComponentName.unflattenFromString(
                            serviceName
                        )

                    component
                        ?.packageName
                        ?.equals(
                            packageName,
                            ignoreCase = true
                        ) == true
                }
        }

    } catch (
        e: Exception
    ) {

        e.printStackTrace()

        false
    }
}


/*
 * =============================================================
 * AUTO START INTENT
 * =============================================================
 */

private fun getAutoStartIntent(
    context: Context
): Intent? {

    val manufacturer =
        Build.MANUFACTURER.lowercase()


    val intents =
        when {

            /*
             * -----------------------------------------------------
             * XIAOMI
             * -----------------------------------------------------
             */

            manufacturer.contains(
                "xiaomi"
            ) -> {

                listOf(

                    Intent(
                        "miui.intent.action.OP_AUTO_START"
                    ),

                    Intent().setComponent(
                        ComponentName(
                            "com.miui.securitycenter",
                            "com.miui.permcenter.autostart.AutoStartManagementActivity"
                        )
                    )
                )
            }


            /*
             * -----------------------------------------------------
             * OPPO
             * -----------------------------------------------------
             */

            manufacturer.contains(
                "oppo"
            ) -> {

                listOf(

                    Intent(
                        "oppo.intent.action.OPPO_AUTO_START"
                    ),

                    Intent().setComponent(
                        ComponentName(
                            "com.coloros.safecenter",
                            "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                        )
                    )
                )
            }


            /*
             * -----------------------------------------------------
             * VIVO
             * -----------------------------------------------------
             */

            manufacturer.contains(
                "vivo"
            ) -> {

                listOf(

                    Intent(
                        "vivo.intent.action.OP_AUTO_START"
                    ),

                    Intent().setComponent(
                        ComponentName(
                            "com.vivo.permissionmanager",
                            "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                        )
                    )
                )
            }


            /*
             * -----------------------------------------------------
             * HUAWEI
             * -----------------------------------------------------
             */

            manufacturer.contains(
                "huawei"
            ) -> {

                listOf(

                    Intent().setComponent(
                        ComponentName(
                            "com.huawei.systemmanager",
                            "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                        )
                    )
                )
            }


            /*
             * -----------------------------------------------------
             * HONOR
             * -----------------------------------------------------
             */

            manufacturer.contains(
                "honor"
            ) -> {

                listOf(

                    Intent().setComponent(
                        ComponentName(
                            "com.hihonor.systemmanager",
                            "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                        )
                    )
                )
            }


            /*
             * -----------------------------------------------------
             * OTHER DEVICES
             * -----------------------------------------------------
             */

            else -> {
                emptyList()
            }
        }


    /*
     * =========================================================
     * FIND WORKING INTENT
     * =========================================================
     */

    for (
    intent in intents
    ) {

        try {

            if (
                intent.resolveActivity(
                    context.packageManager
                ) != null
            ) {

                return intent
            }

        } catch (
            e: Exception
        ) {

            e.printStackTrace()
        }
    }


    return null
}


/*
 * =============================================================
 * CONFIRM PATTERN GRID
 * =============================================================
 */

@Composable
private fun ConfirmPatternGrid(
    selectedDots: List<Int>,
    isError: Boolean,
    onPatternChanged: (List<Int>) -> Unit,
    onPatternFinished: (List<Int>) -> Unit,
    dotColor: Color =
        Color(0xFF83CCFF),
    errorColor: Color =
        Color.Red,
    backgroundColor: Color =
        Color(0xFF29A0F0)
) {

    val latestOnPatternChanged by
    rememberUpdatedState(
        onPatternChanged
    )

    val latestOnPatternFinished by
    rememberUpdatedState(
        onPatternFinished
    )


    Box(
        modifier =
            Modifier
                .size(330.dp)
                .pointerInput(Unit) {

                    var currentDots =
                        mutableListOf<Int>()

                    var patternFinished =
                        false


                    detectDragGestures(

                        onDragStart =
                            { offset ->

                                currentDots =
                                    mutableListOf()

                                patternFinished =
                                    false

                                val dot =
                                    findConfirmDot(
                                        offset,
                                        size.width.toFloat(),
                                        size.height.toFloat()
                                    )

                                if (
                                    dot != null
                                ) {

                                    currentDots.add(
                                        dot
                                    )

                                    latestOnPatternChanged(
                                        currentDots.toList()
                                    )
                                }
                            },


                        onDrag =
                            { change, _ ->

                                change.consume()

                                val dot =
                                    findConfirmDot(
                                        change.position,
                                        size.width.toFloat(),
                                        size.height.toFloat()
                                    )

                                if (
                                    dot != null &&
                                    !currentDots.contains(
                                        dot
                                    )
                                ) {

                                    currentDots.add(
                                        dot
                                    )

                                    latestOnPatternChanged(
                                        currentDots.toList()
                                    )
                                }
                            },


                        onDragEnd = {

                            if (
                                !patternFinished
                            ) {

                                patternFinished =
                                    true

                                val finalPattern =
                                    currentDots.toList()

                                if (
                                    finalPattern.isNotEmpty()
                                ) {

                                    latestOnPatternFinished(
                                        finalPattern
                                    )
                                }
                            }
                        },


                        onDragCancel = {

                            if (
                                !patternFinished
                            ) {

                                patternFinished =
                                    true

                                val finalPattern =
                                    currentDots.toList()

                                if (
                                    finalPattern.isNotEmpty()
                                ) {

                                    latestOnPatternFinished(
                                        finalPattern
                                    )
                                }
                            }
                        }
                    )
                }

    ) {

        Canvas(
            modifier =
                Modifier.fillMaxSize()
        ) {

            val positions =
                getConfirmPositions(
                    size.width,
                    size.height
                )


            if (
                selectedDots.size >= 2
            ) {

                for (
                i in 0 until
                        selectedDots.size - 1
                ) {

                    drawLine(
                        color =
                            if (isError) {

                                errorColor

                            } else {

                                Color.White
                            },

                        start =
                            positions[
                                selectedDots[i]
                            ],

                        end =
                            positions[
                                selectedDots[
                                    i + 1
                                ]
                            ],

                        strokeWidth =
                            8f
                    )
                }
            }


            positions.forEachIndexed {
                    index,
                    position ->

                val selected =
                    selectedDots.contains(
                        index
                    )


                drawCircle(
                    color =
                        dotColor,

                    radius =
                        17.5.dp.toPx(),

                    center =
                        position
                )


                drawCircle(
                    color =
                        backgroundColor,

                    radius =
                        13.5.dp.toPx(),

                    center =
                        position
                )


                drawCircle(
                    color =
                        when {

                            selected &&
                                    isError ->
                                errorColor

                            selected ->
                                Color.White

                            else ->
                                dotColor
                        },

                    radius =
                        9.dp.toPx(),

                    center =
                        position
                )
            }
        }
    }
}


/*
 * =============================================================
 * PATTERN POSITIONS
 * =============================================================
 */

private fun getConfirmPositions(
    width: Float,
    height: Float
): List<Offset> {

    val x1 =
        width * 0.1667f

    val x2 =
        width * 0.5f

    val x3 =
        width * 0.8333f

    val y1 =
        height * 0.1667f

    val y2 =
        height * 0.5f

    val y3 =
        height * 0.8333f


    return listOf(

        Offset(
            x1,
            y1
        ),

        Offset(
            x2,
            y1
        ),

        Offset(
            x3,
            y1
        ),

        Offset(
            x1,
            y2
        ),

        Offset(
            x2,
            y2
        ),

        Offset(
            x3,
            y2
        ),

        Offset(
            x1,
            y3
        ),

        Offset(
            x2,
            y3
        ),

        Offset(
            x3,
            y3
        )
    )
}


/*
 * =============================================================
 * FIND PATTERN DOT
 * =============================================================
 */

private fun findConfirmDot(
    touch: Offset,
    width: Float,
    height: Float
): Int? {

    val positions =
        getConfirmPositions(
            width,
            height
        )


    positions.forEachIndexed {
            index,
            dot ->

        val dx =
            touch.x - dot.x

        val dy =
            touch.y - dot.y

        val distance =
            sqrt(
                dx * dx +
                        dy * dy
            )


        if (
            distance <= 55f
        ) {

            return index
        }
    }


    return null
}


