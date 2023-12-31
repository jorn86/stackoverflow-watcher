package org.hertsig.stackoverflow.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import org.hertsig.compose.component.ScrollableColumn
import org.hertsig.stackoverflow.controller.QuestionController
import org.hertsig.stackoverflow.dto.api.Question

@Composable
fun QuestionList(
    controller: QuestionController,
    questions: List<Question>,
    scrollState: LazyListState = rememberLazyListState(),
) {
    ScrollableColumn(state = scrollState, arrangement = Arrangement.spacedBy(8.dp)) {
        items(questions, { it.questionId }) {
            Question(controller, it)
        }
    }
}
