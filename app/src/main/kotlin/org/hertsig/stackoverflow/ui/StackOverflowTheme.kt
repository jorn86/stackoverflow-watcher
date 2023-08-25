package org.hertsig.stackoverflow.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import org.hertsig.compose.component.Theme

private val offWhite = Color(0xFFE0E0E0)

private val darkColors = darkColors(
    background = Color.Black,
    onBackground = offWhite,

    surface = Color(0xFF202020),
    onSurface = offWhite,

    primary = Color(0xFF03A9F4),
    primaryVariant = Color(0xFF0288D1),
    onPrimary = offWhite,

    secondary = Color(0xFFF48225),
    onSecondary = Color.Black,

    error = Color.Red,
    onError = offWhite,
)

private val lightColors = lightColors(
    background = offWhite,
    onBackground = Color.Black,

    surface = offWhite,
    onSurface = Color.Black,

    primary = Color(0xFF03A9F4),
    primaryVariant = Color(0xFF0288D1),
    onPrimary = offWhite,

    secondary = Color(0xFFF48225),
    onSecondary = Color.Black,

    error = Color.Red,
    onError = Color.Black,
)

@Composable
fun StackOverflowTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    Theme(if (dark) darkColors else lightColors, content = content)
}
