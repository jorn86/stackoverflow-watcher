package org.hertsig.stackoverflow

import androidx.compose.runtime.*
import org.hertsig.core.logger
import org.hertsig.stackoverflow.dto.api.Question
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

private val log = logger {}

class QuestionController(
    private val service: StackOverflowService,
    vararg initialIgnoredTags: String =
        arrayOf("hibernate", "javafx", "javascript", "jpa", "minecraft", "pdf", "python", "react", "selenium", "swing"),
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

    suspend fun pollBounties() = service.pollBounties()

    @Composable
    fun collectQuestionsAsState(context: CoroutineContext = EmptyCoroutineContext): State<List<Question>> =
        service.questionFlow.collectAsState(context)

    @Composable
    fun collectBountiesAsState(context: CoroutineContext = EmptyCoroutineContext): State<List<Question>> =
        service.bountyFlow.collectAsState(context)
}

private fun Iterable<String>.noneIgnored(ignoredTags: Collection<String>) = none { tag ->
    ignoredTags.any { ignored -> tag.startsWith(ignored) }
}
