package org.hertsig.stackoverflow.ui

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.hertsig.compose.component.TextLine
import org.hertsig.stackoverflow.APP_NAME
import org.hertsig.stackoverflow.QuestionController
import org.hertsig.stackoverflow.StackOverflowService

@Composable
@Preview
fun App(service: StackOverflowService) {
    val controller = remember { QuestionController(service) }
//    LaunchedEffect(Unit) { controller.startNewQuestionWatcher() }

    MaterialTheme {
        var showIgnored by controller.showIgnoredState
        var showClosed by controller.showClosedState
        Column {
            TopAppBar {
                TextLine(APP_NAME, Modifier.padding(start = 8.dp))
                Spacer(Modifier.weight(1f))
                Checkbox(showIgnored, { showIgnored = !showIgnored })
                Checkbox(showClosed, { showClosed = !showClosed })
            }

            var tabIndex by remember { mutableStateOf(0) }
            TabRow(tabIndex, Modifier.height(32.dp)) {
                Tab(tabIndex == 0, { tabIndex = 0 }) { TextLine("Recent") }
                Tab(tabIndex == 1, { tabIndex = 1 }) { TextLine("Bounty") }
            }

            when (tabIndex) {
                0 -> RecentQuestionsView(controller)
                1 -> BountyQuestionsView(controller)
            }
        }
    }
}
