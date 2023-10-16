package org.hertsig.stackoverflow.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import org.hertsig.compose.component.LoadingIndicator
import org.hertsig.compose.component.TextLine
import org.hertsig.stackoverflow.controller.QuestionController

@Composable
fun QuestionsView(controller: QuestionController) {
    val questions by controller.collectAsState()
    val scrollState = rememberLazyListState()
    if (controller.loading) {
        LoadingIndicator()
    } else if (questions.isEmpty()) {
        TextLine("No results", Modifier.fillMaxSize(), align = TextAlign.Center)
    } else {
        QuestionList(controller, questions, scrollState)
    }
    LaunchedEffect(controller) { controller.startPolling() }
    LaunchedEffect(questions) {
        if (scrollState.firstVisibleItemIndex <= 3) scrollState.animateScrollToItem(0)
    }
}
