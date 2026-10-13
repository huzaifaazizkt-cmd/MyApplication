package com.example.myapplication.Design.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/**
 * System font size bohat bara ho tab bhi layout na toote.
 */
@Composable
fun LimitFontScale(
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