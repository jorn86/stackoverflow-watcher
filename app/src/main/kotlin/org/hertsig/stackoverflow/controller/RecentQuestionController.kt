package org.hertsig.stackoverflow.controller

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import kotlinx.coroutines.delay
import org.hertsig.core.debug
import org.hertsig.core.logger
import org.hertsig.stackoverflow.dto.api.Question
import org.hertsig.stackoverflow.dto.websocket.NewQuestionMessage
import org.hertsig.stackoverflow.service.StackExchangeApiService
import org.hertsig.stackoverflow.service.StackExchangeWebsocketService
import org.hertsig.stackoverflow.ui.formatDateOrTime
import org.hertsig.stackoverflow.ui.resolveLocal
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

private val log = logger {}

class RecentQuestionController(
    private val apiService: StackExchangeApiService,
    websocketService: StackExchangeWebsocketService,
    initialWatchedTags: Collection<String> = emptyList(),
    initialIgnoredTags: Collection<String> = emptyList(),
    private val site: String = "stackoverflow",
    private val limit: Int = 99,
    private val queryByTagWindow: Duration = 3.hours,
    queryByTagInterval: Duration = 15.minutes,
): QuestionController("Recent", 1.minutes) {
    val watchedTags = initialWatchedTags.toMutableStateList()
    val ignoredTags = initialIgnoredTags.toMutableStateList()
    override var new by mutableStateOf(0); private set
    private val questionIds: MutableSet<Long> = LinkedHashSet(limit)
    private val queryByTagInterval = queryByTagInterval.toJavaDuration()
    private var lastQueryByTags = Instant.EPOCH

    init {
        (1..100).count()
        require(limit in 1..100)
        websocketService.addListener(::onNewQuestion)
    }

    private suspend fun onNewQuestion(question: NewQuestionMessage) {
        if (question.tags.any { it in watchedTags }) {
            questionIds.add(question.id.toLong())
            cleanupQuestionIds()
            delay(1.seconds) // Give the API some time to realize the question exists
            log.debug{"Requesting poll to add new question ${question.id}"}
            new++
            doPoll()
        }
    }

    private fun cleanupQuestionIds() {
        if (questionIds.size > limit) {
            log.debug { "Dropping ${questionIds.size - limit} watched question(s)" }
            val iterator = questionIds.iterator()
            repeat(questionIds.size - limit) { iterator.removeNext() }
        }
    }

    override suspend fun queryQuestions(): List<Question> {
        if (lastQueryByTags.plus(queryByTagInterval).isBefore(Instant.now())) {
            lastQueryByTags = Instant.now()
            val questions = queryByWatchedTags()
            val ids = questions.map { it.questionId }.toSet()
            if (!ids.containsAll(questionIds)) {
                val missing = questionIds - ids
                log.info("Query by tags missed ${missing.size} watched questions (probably too old)")
            }
            if (!questionIds.containsAll(ids)) {
                val new = questions.filterNot { it.questionId in questionIds }
                this.new += new.size
                if (questionIds.isNotEmpty()) {
                    val detail = new.joinToString("\n", ":\n") { "${it.title} / ${resolveLocal(it.creationDate).formatDateOrTime()}"}
                    log.info("Query by tags got ${new.size} previously unwatched questions:$detail")
                } else {
                    log.info("Started with ${new.size} questions")
                }
                questionIds.addAll(ids)
                cleanupQuestionIds()
            }
        }
        if (questionIds.isEmpty()) return emptyList()
        return queryByWatchedIds()
    }

    private suspend fun queryByWatchedIds() = filterClosedAndMistagged(apiService.getQuestions(questionIds, site))

    private suspend fun queryByWatchedTags(window: Duration = queryByTagWindow) = watchedTags
        .flatMap { apiService.getQuestionsByTag(it, window) }
        .distinctBy { it.questionId }
        .filterNot { it.tags.anyIgnored(ignoredTags) }
        .sortedByDescending { it.creationDate }
        .let(::filterClosedAndMistagged)
        .take(limit)

    private fun filterClosedAndMistagged(questions: List<Question>): List<Question> {
        var qs = questions
        val closedIds = qs.filter { it.closedDate != null }.map { it.questionId }.toSet()
        if (closedIds.isNotEmpty()) {
            qs = qs.filterNot { it.questionId in closedIds }
            questionIds.removeAll(closedIds)
            log.debug{"Removed ${closedIds.size} closed question(s) from list"}
        }

        val mistaggedIds = qs.filter { it.tags.none { tag -> tag in watchedTags } }.map { it.questionId }.toSet()
        if (mistaggedIds.isNotEmpty()) {
            qs = qs.filterNot { it.questionId in mistaggedIds }
            questionIds.removeAll(mistaggedIds)
            log.debug{"Removed ${mistaggedIds.size} question(s) that no longer have watched tags from list"}
        }

        return qs
    }

    override fun fade(question: Question): Boolean {
        val closed = question.closedReason != null
        val ignored = question.tags.anyIgnored(ignoredTags)
        return closed || ignored
    }

    override fun resetNew() {
        new = 0
        String::class.java.annotations
    }
}

private fun MutableIterator<*>.removeNext() {
    next()
    remove()
}
