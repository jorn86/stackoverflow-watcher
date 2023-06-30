package org.hertsig.stackoverflow

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.hertsig.core.logger
import org.hertsig.stackoverflow.dto.api.Question

private val log = logger {}

class QuestionController(
    private val service: StackOverflowService,
    vararg initialIgnoredTags: String =
        arrayOf("javafx", "python", "javascript", "minecraft", "pdf", "react", "selenium", "swing"),
) {
    val ignoredTags = mutableStateListOf(*initialIgnoredTags)
    val showIgnoredState = mutableStateOf(true)
    val showClosedState = mutableStateOf(false)
    private var showIgnored by showIgnoredState
    private var showClosed by showClosedState

    fun filter(questions: List<Question>) = questions.filter { filterClosed(it) && filterIgnored(it) }
    private fun filterClosed(it: Question) = showClosed || it.closedReason == null
    private fun filterIgnored(question: Question) = showIgnored || question.tags.noneIgnored(ignoredTags)

    fun fade(question: Question): Boolean {
        val closed = question.closedReason != null
        val ignored = !question.tags.noneIgnored(ignoredTags)
        return closed || ignored
    }

    suspend fun startNewQuestionWatcher() {
        service.watchNewQuestions {
            val tags = it.tags.joinToString(", ", "[", "]")
            if (filterIgnored(it)) {
                sendNotification("New question: ${it.title}", tags)
            } else {
                log.debug("Not showing notification for ignored question ${it.title} with tags $tags")
            }
        }
    }
}
