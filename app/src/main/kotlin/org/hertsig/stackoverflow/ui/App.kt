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
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.decodeFromStream
import org.hertsig.compose.component.SpacedRow
import org.hertsig.compose.component.TabBuilder
import org.hertsig.compose.component.TabView
import org.hertsig.compose.component.TextLine
import org.hertsig.stackoverflow.APP_NAME
import org.hertsig.stackoverflow.SiteList
import org.hertsig.stackoverflow.controller.BountyController
import org.hertsig.stackoverflow.controller.Config
import org.hertsig.stackoverflow.controller.QuestionController
import org.hertsig.stackoverflow.controller.RecentQuestionController
import org.hertsig.stackoverflow.service.StackExchangeApiService
import org.hertsig.stackoverflow.service.StackExchangeWebsocketService
import org.hertsig.stackoverflow.service.defaultJson
import java.io.FileInputStream

@Composable
fun App(apiService: StackExchangeApiService, websocketService: StackExchangeWebsocketService) {
    val controllers = remember { mutableStateListOf<QuestionController>() }
    LaunchedEffect(Unit) {
        val sites = SiteList.get(apiService).associateBy { it.siteId }
        @OptIn(ExperimentalSerializationApi::class)
        val config = FileInputStream("stackexchange.json").use {
            defaultJson.decodeFromStream<Config>(it)
        }
        config.controllers.forEach { controllerConfig ->
            val site = sites.getValue(controllerConfig.siteId)
            val controller = when (controllerConfig.type) {
                "recent" -> RecentQuestionController(apiService, websocketService, controllerConfig.tags, controllerConfig.ignoredTags, site.apiParameter)
                    .also { controllerConfig.tags.forEach { websocketService.addWatchedTag(it, site.siteId) } }
                "bounty" -> BountyController(apiService, controllerConfig.tags, controllerConfig.ignoredTags, site.apiParameter).also { it.doPoll() }
                else -> error("Unknown type ${controllerConfig.type}")
            }
            controllers.add(controller)
        }
    }

    val views = controllers.map { TabBuilder({ TabTitle(it) }) { QuestionsView(it) } }
    var dark by remember { mutableStateOf(true) }
    StackOverflowTheme(dark) {
        Column(Modifier.background(MaterialTheme.colors.surface)) {
            TopAppBar {
                TextLine(APP_NAME, Modifier.padding(start = 8.dp), Color.White)
                Spacer(Modifier.weight(1f))
                Switch(dark, { dark = it })
            }
            if (views.isNotEmpty()) TabView(views)
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
