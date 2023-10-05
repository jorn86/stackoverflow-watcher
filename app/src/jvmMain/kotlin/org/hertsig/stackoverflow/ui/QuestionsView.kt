package org.hertsig.stackoverflow.ui

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import org.hertsig.compose.component.LoadingIndicator
import org.hertsig.stackoverflow.controller.QuestionController

@Composable
fun QuestionsView(controller: QuestionController) {
    val questions by controller.collectAsState()
    val scrollState = rememberLazyListState()
    if (questions.isEmpty()) {
        LoadingIndicator()
    } else {
        QuestionList(controller, questions, scrollState)
    }
    LaunchedEffect(controller) { controller.startPolling() }
    LaunchedEffect(questions) {
        if (scrollState.firstVisibleItemIndex <= 3) {
            scrollState.animateScrollToItem(0)
        }
    }
}
