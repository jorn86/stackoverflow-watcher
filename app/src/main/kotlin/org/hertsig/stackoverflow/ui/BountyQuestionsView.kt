package org.hertsig.stackoverflow.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.hertsig.stackoverflow.QuestionController

@Composable
fun BountyQuestionsView(controller: QuestionController) {
    val questions by controller.collectBountiesAsState()
    LaunchedEffect(Unit) { controller.pollBounties() }
    QuestionList(controller, questions)
}
