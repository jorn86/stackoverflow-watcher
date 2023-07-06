package org.hertsig.stackoverflow.ui

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import org.hertsig.compose.component.TextLine
import org.hertsig.compose.component.Theme
import org.hertsig.stackoverflow.APP_NAME
import org.hertsig.stackoverflow.QuestionController
import org.hertsig.stackoverflow.StackOverflowService

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
@Preview
fun App(service: StackOverflowService) {
    val controller = remember { QuestionController(service) }
//    LaunchedEffect(Unit) { controller.startNewQuestionWatcher() }
    var dark by remember { mutableStateOf(true) }
    Theme(if (dark) darkColors else lightColors) {
        var showIgnored by controller.showIgnoredState
        var showClosed by controller.showClosedState
        Column(Modifier.background(MaterialTheme.colors.surface)) {
            TopAppBar {
                TextLine(APP_NAME, Modifier.padding(start = 8.dp), Color.White)
                Spacer(Modifier.weight(1f))
                Checkbox(showIgnored, { showIgnored = !showIgnored })
                Checkbox(showClosed, { showClosed = !showClosed })
                Switch(dark, { dark = it })
            }

            var tabIndex by remember { mutableStateOf(0) }
            CompositionLocalProvider(LocalTextStyle provides LocalTextStyle.current.copy(color = MaterialTheme.colors.contentColorFor(MaterialTheme.colors.primarySurface))) {
                TabRow(tabIndex, Modifier.height(32.dp)) {
                    Tab(tabIndex == 0, { tabIndex = 0 }) { TextLine("Recent") }
                    Tab(tabIndex == 1, { tabIndex = 1 }) { TextLine("Bounty") }
                }
            }

            when (tabIndex) {
                0 -> RecentQuestionsView(controller)
                1 -> BountyQuestionsView(controller)
            }
        }
    }
}

fun main() {
    fun rgb(r: Long, g: Long, b: Long) = 0xFF000000 or (r shl 16) or (g shl 8) or b
    println("0x" + rgb(244, 130, 37).toString(16).uppercase())
}
