package org.hertsig.stackoverflow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.hertsig.compose.component.*
import org.hertsig.stackoverflow.APP_NAME
import org.hertsig.stackoverflow.controller.BountyController
import org.hertsig.stackoverflow.controller.QuestionController
import org.hertsig.stackoverflow.controller.RecentQuestionController
import org.hertsig.stackoverflow.service.StackExchangeApiService
import org.hertsig.stackoverflow.service.StackExchangeWebsocketService

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

val initialWatchedTags = setOf("java", "kotlin", "jooq", "guava", "guice", "jersey", "compose-desktop")
private val initialIgnoredTags = setOf("hibernate", "javafx", "javascript", "jpa", "minecraft", "pdf", "python", "react", "selenium", "swing")

@Composable
fun App(apiService: StackExchangeApiService, websocketService: StackExchangeWebsocketService) {
    val controller = remember {
        RecentQuestionController(apiService, websocketService, initialWatchedTags, initialIgnoredTags)
    }

    val bountyController = remember { BountyController(apiService, initialWatchedTags, initialIgnoredTags) }
    LaunchedEffect(Unit) { bountyController.doPoll() }

    val views = listOf(controller, bountyController).map {
        TabBuilder({ TabTitle(it) }) { QuestionsView(it) }
    }

    var dark by remember { mutableStateOf(true) }
    Theme(if (dark) darkColors else lightColors) {
        Column(Modifier.background(MaterialTheme.colors.surface)) {
            TopAppBar {
                TextLine(APP_NAME, Modifier.padding(start = 8.dp), Color.White)
                Spacer(Modifier.weight(1f))
                Switch(dark, { dark = it })
            }
            TabView(views)
        }
    }
}

@Composable
private fun TabTitle(controller: QuestionController) {
    SpacedRow {
        TextLine(controller.name)
        if (controller.new > 0) {
            TextLine("(${controller.new})", Modifier.clickable { controller.resetNew() })
        }
    }
}
