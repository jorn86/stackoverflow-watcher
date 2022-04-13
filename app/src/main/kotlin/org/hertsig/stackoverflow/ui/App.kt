package org.hertsig.stackoverflow.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Checkbox
import androidx.compose.material.MaterialTheme
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import org.hertsig.compose.component.ScrollableColumn
import org.hertsig.compose.component.TextLine
import org.hertsig.core.logger
import org.hertsig.stackoverflow.APP_NAME
import org.hertsig.stackoverflow.QuestionController
import org.hertsig.stackoverflow.StackOverflowService

private val log = logger {}

@Composable
@Preview
fun App(service: StackOverflowService) {
    val questions by service.collectQuestionsAsState()
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
            val scrollState = rememberLazyListState()
            val visibleQuestions = controller.filter(questions);

            // DEBUG duplicate key crash
            visibleQuestions.groupBy { it.questionId }
                .filter { (_,v) -> v.size > 1 }
                .apply {
                    if (isNotEmpty()) {
                        log.warn("Duplicate questions: $this")
                    }
                }

            ScrollableColumn(state = scrollState, arrangement = Arrangement.spacedBy(8.dp)) {
                items(visibleQuestions, { it.questionId }) {
                    val alpha by animateFloatAsState(if (controller.fade(it)) .6f else 1f) { v ->
                        log.debug("Animation finished: $v")
                    }
                    Question(it, Modifier.alpha(alpha))
                }
            }
            LaunchedEffect(visibleQuestions) {
                if (scrollState.firstVisibleItemIndex <= 3) {
                    scrollState.animateScrollToItem(0)
                }
            }
        }
    }
}
