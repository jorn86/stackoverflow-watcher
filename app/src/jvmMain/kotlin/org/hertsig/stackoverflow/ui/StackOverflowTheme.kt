package org.hertsig.stackoverflow.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import org.hertsig.compose.component.Theme

@Composable
fun StackOverflowTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    Theme(if (dark) DarkColors else LightColors, content = content)
}
