package org.hertsig.stackoverflow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Switch
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.hertsig.compose.component.SpacedRow
import org.hertsig.compose.component.TabBuilder
import org.hertsig.compose.component.TabView
import org.hertsig.compose.component.TextLine
import org.hertsig.stackoverflow.APP_NAME
import org.hertsig.stackoverflow.controller.BountyController
import org.hertsig.stackoverflow.controller.QuestionController
import org.hertsig.stackoverflow.controller.RecentQuestionController
import org.hertsig.stackoverflow.service.StackExchangeApiService
import org.hertsig.stackoverflow.service.StackExchangeWebsocketService

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
    StackOverflowTheme(dark) {
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
