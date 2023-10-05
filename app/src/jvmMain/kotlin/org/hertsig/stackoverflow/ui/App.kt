package org.hertsig.stackoverflow.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.unit.dp
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.decodeFromStream
import org.hertsig.compose.component.*
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
        controllers.addAll(config.controllers.map { controllerConfig ->
            val site = sites.getValue(controllerConfig.siteId)
            when (controllerConfig.type) {
                "recent" -> RecentQuestionController(apiService, websocketService, controllerConfig.tags, controllerConfig.ignoredTags, site)
                "bounty" -> BountyController(apiService, controllerConfig.tags, controllerConfig.ignoredTags, site)
                else -> error("Unknown type ${controllerConfig.type}")
            }
        })
        controllers.filterIsInstance<RecentQuestionController>().forEach {
            it.watchedTags.forEach { tag -> websocketService.addWatchedTag(it.site, tag) }
        }
    }

    var dark by remember { mutableStateOf(true) }
    StackOverflowTheme(dark) {
        val views = controllers.map { TabBuilder({ TabTitle(it) }) { QuestionsView(it) } }
        Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar({
                TextLine(APP_NAME, Modifier.padding(start = 8.dp))
            }, actions = {
                if (websocketService.connectionError) {
                    TooltipText("Websocket has disconnected. Restart the app to restore instant addition of new questions") {
                        Icon(
                            Icons.Default.Warning, "disconnected",
                            Modifier.padding(end = 4.dp), Color.Yellow
                        )
                    }
                }
                Switch(dark, { dark = it })
            })
            if (views.isEmpty()) {
                LoadingIndicator()
            } else {
                TabView(views)
            }
        }
    }
}

@Composable
private fun TabTitle(controller: QuestionController) {
    SpacedRow(Modifier.padding(vertical = 4.dp)) {
        TooltipText(controller.site.name) {
            val image = remember { controller.iconUrl.openStream().use { loadImageBitmap(it) } }
            Image(image, "site icon", Modifier.size(24.dp))
        }
        RowTextLine(controller.name)
        if (controller.new > 0) {
            RowTextLine("(${controller.new})", Modifier.clickable { controller.resetNew() })
        }
    }
}
