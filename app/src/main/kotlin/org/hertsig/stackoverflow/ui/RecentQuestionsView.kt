package org.hertsig.stackoverflow.ui

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import org.hertsig.core.logger
import org.hertsig.stackoverflow.QuestionController

private val log = logger {}

@Composable
fun RecentQuestionsView(controller: QuestionController) {
    val questions by controller.collectQuestionsAsState()
    val scrollState = rememberLazyListState()
    val visibleQuestions = controller.filter(questions)

    // DEBUG duplicate key crash
    visibleQuestions.groupBy { it.questionId }
        .filter { (_,v) -> v.size > 1 }
        .apply {
            if (isNotEmpty()) {
                log.warn("Duplicate questions: $this")
            }
        }

    QuestionList(controller, visibleQuestions, scrollState)

    LaunchedEffect(questions) {
        if (scrollState.firstVisibleItemIndex <= 3) {
            scrollState.animateScrollToItem(0)
        }
    }
}
