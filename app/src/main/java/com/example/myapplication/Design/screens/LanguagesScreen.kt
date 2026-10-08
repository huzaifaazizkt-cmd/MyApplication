package com.example.myapplication.Design.screens

import android.content.Context
import android.graphics.Color as AndroidColor
import android.graphics.drawable.GradientDrawable
import android.util.Log
import android.view.Gravity
import android.widget.TextView
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.AppLanguageManager
import com.example.myapplication.R
import com.example.myapplication.data.DataStoreManager
import kotlinx.coroutines.launch

private const val TAG = "LanguagesScreen"

data class LanguageItem(
    val name: String,
    val flagRes: Int,
    val code: String
)

@Composable
fun LanguagesScreen(
    onBackClick: (() -> Unit)? = null,
    onLanguageSelected: (() -> Unit)? = null,
    isSetup: Boolean = false
) {
    val context = LocalContext.current
    val dataStoreManager = remember { DataStoreManager(context) }
    val scope = rememberCoroutineScope()
    val setupMode = isSetup || onBackClick == null

    Log.d(TAG, "LanguagesScreen opened")
    Log.d(TAG, "isSetup = $isSetup")
    Log.d(TAG, "onBackClick exists = ${onBackClick != null}")
    Log.d(TAG, "setupMode = $setupMode")

    val languages = listOf(
        LanguageItem(name = "English", flagRes = R.drawable.english, code = "en"),
        LanguageItem(name = "Portagual", flagRes = R.drawable.portagual, code = "pt"),
        LanguageItem(name = "France", flagRes = R.drawable.france1, code = "fr"),
        LanguageItem(name = "Spain", flagRes = R.drawable.spain1, code = "es"),
        LanguageItem(name = "Turkey", flagRes = R.drawable.turkey, code = "tr"),
        LanguageItem(name = "Japan", flagRes = R.drawable.japan, code = "ja"),
        LanguageItem(name = "Korean", flagRes = R.drawable.korean, code = "ko"),
        LanguageItem(name = "Indonesia", flagRes = R.drawable.indonesia, code = "id"),
        LanguageItem(name = "India", flagRes = R.drawable.india, code = "hi"),
        LanguageItem(name = "Norway", flagRes = R.drawable.norway, code = "no"),
        LanguageItem(name = "Arabic", flagRes = R.drawable.sudia, code = "ar")
    )

    val savedLanguageCode by dataStoreManager
        .getLanguage()
        .collectAsState(initial = null)

    var selectedLanguageCode by remember {
        mutableStateOf<String?>(null)
    }

    LaunchedEffect(savedLanguageCode, setupMode) {
        Log.d(TAG, "Saved language changed = $savedLanguageCode")

        if (savedLanguageCode != null && !setupMode) {
            selectedLanguageCode = savedLanguageCode
            Log.d(
                TAG,
                "Existing language selected = $savedLanguageCode"
            )
        } else if (setupMode) {
            Log.d(
                TAG,
                "Setup mode active - no language preselected"
            )
        }
    }

    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F7F7))
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(90.dp)
            ) {
                if (onBackClick != null) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = Color(0xFF444444),
                        modifier = Modifier
                            .padding(
                                start = 18.dp,
                                top = 34.5.dp
                            )
                            .size(25.dp)
                            .clickable(
                                interactionSource = remember {
                                    MutableInteractionSource()
                                },
                                indication = ripple(bounded = true)
                            ) {
                                Log.d(
                                    TAG,
                                    "Back button clicked"
                                )
                                onBackClick()
                            }
                    )
                }

                Text(
                    text = stringResource(R.string.languages),
                    modifier = Modifier.align(Alignment.Center),
                    color = Color(0xFF333333),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(
                        start = 14.dp,
                        end = 14.dp,
                        bottom = 20.dp
                    ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                languages.forEach { language ->

                    LanguageCard(
                        language = language,
                        selected = selectedLanguageCode == language.code,
                        onClick = {
                            selectedLanguageCode = language.code

                            Log.d(
                                TAG,
                                "Language selected: ${language.name} (${language.code})"
                            )
                        }
                    )
                }
            }

            Button(
                onClick = {
                    Log.d(
                        TAG,
                        "SELECT button clicked"
                    )

                    Log.d(
                        TAG,
                        "Current selectedLanguageCode = $selectedLanguageCode"
                    )

                    val selectedItem =
                        languages.firstOrNull {
                            it.code == selectedLanguageCode
                        }

                    if (selectedItem == null) {
                        Log.d(
                            TAG,
                            "No language selected - showing toast"
                        )

                        showLanguageSelectionToast(context)

                        return@Button
                    }

                    Log.d(
                        TAG,
                        "Selected item = ${selectedItem.name}"
                    )

                    Log.d(
                        TAG,
                        "Selected language code = ${selectedItem.code}"
                    )

                    scope.launch {
                        try {

                            Log.d(
                                TAG,
                                "Saving language to DataStore..."
                            )

                            dataStoreManager.saveLanguage(
                                selectedItem.code
                            )

                            Log.d(
                                TAG,
                                "Language saved successfully: ${selectedItem.code}"
                            )

                            /*
                             * IMPORTANT:
                             *
                             * Language ko Settings mode mein bhi
                             * pehle apply karna zaroori hai.
                             *
                             * Pehle code mein Settings mode ke andar
                             * yahan se return ho raha tha aur
                             * AppLanguageManager.setLanguage()
                             * call nahi hota tha.
                             */
                            Log.d(
                                TAG,
                                "Calling AppLanguageManager.setLanguage()..."
                            )

                            AppLanguageManager.setLanguage(
                                selectedItem.code
                            )

                            Log.d(
                                TAG,
                                "AppLanguageManager.setLanguage() completed"
                            )

                            /*
                             * Settings se language change hui hai.
                             * Language apply hone ke baad direct
                             * Settings screen par wapas jayenge.
                             */
                            if (!setupMode) {

                                Log.d(
                                    TAG,
                                    "SETTINGS MODE"
                                )

                                Log.d(
                                    TAG,
                                    "Language changed, returning to Settings screen"
                                )

                                onBackClick?.invoke()

                                return@launch
                            }

                            /*
                             * Setup mode ka existing flow
                             * bilkul same rakha gaya hai.
                             */
                            Log.d(
                                TAG,
                                "SETUP MODE"
                            )

                            Log.d(
                                TAG,
                                "Calling onLanguageSelected()..."
                            )

                            onLanguageSelected?.invoke()

                            Log.d(
                                TAG,
                                "Setup flow continued"
                            )

                        } catch (e: Exception) {

                            Log.e(
                                TAG,
                                "LANGUAGE SAVE/CHANGE ERROR",
                                e
                            )
                        }
                    }
                },
                enabled = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(
                        start = 14.dp,
                        end = 14.dp,
                        bottom = 10.dp
                    )
                    .height(51.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2196F3),
                    disabledContainerColor = Color(0xFFD6D6D6),
                    disabledContentColor = Color.White
                )
            ) {
                Text(
                    text = stringResource(R.string.select),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun LanguageCard(
    language: LanguageItem,
    selected: Boolean,
    onClick: () -> Unit
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
            .clip(cardShape)
            .then(
                if (selected) {
                    Modifier.border(
                        width = 1.5.dp,
                        color = Color(0xFF0396FF),
                        shape = cardShape
                    )
                } else {
                    Modifier
                }
            )
            .clickable(
                interactionSource = remember {
                    MutableInteractionSource()
                },
                indication = ripple(bounded = true)
            ) {
                Log.d(
                    TAG,
                    "Language card clicked: ${language.name} (${language.code})"
                )

                onClick()
            },
        shape = cardShape,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                Color(0xFFF8FCFF)
            } else {
                Color.White
            }
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp,
            pressedElevation = 1.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .padding(
                    start = 12.dp,
                    end = 10.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Image(
                painter = painterResource(
                    id = language.flagRes
                ),
                contentDescription = language.name,
                modifier = Modifier.size(
                    width = 32.dp,
                    height = 32.dp
                ),
                contentScale = ContentScale.Fit
            )

            Spacer(
                modifier = Modifier.width(18.dp)
            )

            Text(
                text = language.name,
                modifier = Modifier.weight(1f),
                color = if (selected) {
                    Color(0xFF0396FF)
                } else {
                    Color(0xFF555555)
                },
                fontSize = 15.sp,
                fontWeight = if (selected) {
                    FontWeight.Medium
                } else {
                    FontWeight.Normal
                }
            )

            RadioButton(
                selected = selected,
                onClick = {
                    Log.d(
                        TAG,
                        "RadioButton clicked: ${language.name} (${language.code})"
                    )

                    onClick()
                },
                modifier = Modifier.size(30.dp),
                colors = RadioButtonDefaults.colors(
                    selectedColor = Color(0xFF0396FF),
                    unselectedColor = Color(0xFFBDBDBD)
                )
            )
        }
    }
}

private fun showLanguageSelectionToast(
    context: Context
) {
    Log.d(
        TAG,
        "Showing language selection warning toast"
    )

    val textView = TextView(context)

    textView.text =
        "Don't select your language\nPlease select your language"

    textView.setTextColor(
        AndroidColor.rgb(92, 92, 92)
    )

    textView.textSize = 14f
    textView.gravity = Gravity.CENTER
    textView.setPadding(
        28,
        16,
        28,
        16
    )

    val background = GradientDrawable()

    background.setColor(
        AndroidColor.WHITE
    )

    background.cornerRadius = 18f

    textView.background = background
    textView.elevation = 6f

    val toast = Toast(context)

    toast.view = textView
    toast.duration = Toast.LENGTH_SHORT

    toast.setGravity(
        Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
        0,
        90
    )

    toast.show()

    Log.d(
        TAG,
        "Language warning toast shown"
    )
}