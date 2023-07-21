package org.hertsig.stackoverflow.controller

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.hertsig.core.info
import org.hertsig.core.logger
import org.hertsig.core.trace
import org.hertsig.core.warn
import org.hertsig.stackoverflow.dto.api.Question
import org.hertsig.stackoverflow.util.backgroundTask
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

private val log = logger {}

abstract class QuestionController(
    val name: String,
    interval: Duration,
    private var lastPollTime: Instant = Instant.EPOCH
) {
    internal val questions = MutableStateFlow(emptyList<Question>())
    private val interval = interval.toJavaDuration()
    private val pollMutex = Mutex()

    open val new get() = 0
    open fun resetNew() {}

    @Composable
    fun collectAsState() = questions.collectAsState()

    open fun displayDate(question: Question): Long = question.creationDate
    open fun fade(question: Question): Boolean = false

    suspend fun startPolling() {
        resetNew()
        backgroundTask("Poller for $name", 10.seconds) {
            log.trace { "Checking poll time for $name" }
            if (pollMutex.isLocked) {
                log.warn{"Someone is holding lock for $name, skipping poll"}
            } else if (lastPollTime.plus(interval).isBefore(Instant.now())) {
                doPoll()
                Modifier.onGloballyPositioned {  }
            }
        }
    }

    internal suspend fun doPoll() {
        pollMutex.withLock {
            lastPollTime = Instant.now()
            val questions = queryQuestions()
            log.info { "$name polled ${questions.size} questions" }
            this.questions.emit(questions)
        }
    }

    internal abstract suspend fun queryQuestions(): List<Question>
}

internal fun questionUrl(id: String) = "https://stackoverflow.com/questions/$id"

fun Iterable<String>.anyIgnored(ignoredTags: Collection<String>) = any { tag ->
    ignoredTags.any { ignored -> tag.startsWith(ignored) }
}
