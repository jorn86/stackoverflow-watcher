package org.hertsig.stackoverflow.controller

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.toMutableStateList
import kotlinx.coroutines.delay
import org.hertsig.core.debug
import org.hertsig.core.info
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
    private val newQuestionIds = mutableStateListOf<Long>()
    override val new get() = newQuestionIds.size
    private val questionIds: MutableSet<Long> = LinkedHashSet(limit)
    private val queryByTagInterval = queryByTagInterval.toJavaDuration()
    private var lastQueryByTags = Instant.EPOCH

    init {
        require(limit in 1..100)
        websocketService.addListener(::onNewQuestion)
    }

    private suspend fun onNewQuestion(question: NewQuestionMessage) {
        if (question.tags.any { it in watchedTags }) {
            val newQuestionId = question.id.toLong()
            if (!questionIds.add(newQuestionId)) return
            delay(1.seconds) // Give the API some time to realize the question exists
            newQuestionIds.add(newQuestionId)
            cleanupQuestionIds()
            log.debug { "Requesting poll to add new question $newQuestionId" }
            doPoll()
        }
    }

    private fun cleanupQuestionIds() {
        if (questionIds.size > limit) {
            log.debug { "Dropping ${questionIds.size - limit} watched question(s)" }
            val iterator = questionIds.iterator()
            repeat(questionIds.size - limit) { iterator.removeNext() }
        }
        newQuestionIds.retainAll(questionIds)
    }

    override suspend fun queryQuestions(): List<Question> {
        if (lastQueryByTags.plus(queryByTagInterval).isBefore(Instant.now())) {
            lastQueryByTags = Instant.now()
            val questions = queryByWatchedTags()
            val ids = questions.map { it.questionId }
            if (!ids.containsAll(questionIds)) {
                val missing = questionIds - ids.toSet()
                log.debug { "Query by tags missed ${missing.size} watched questions (probably too old)" }
            }
            if (!questionIds.containsAll(ids)) {
                val new = questions.filterNot { it.questionId in questionIds }
                if (questionIds.isNotEmpty()) {
                    newQuestionIds.addAll(new.map { it.questionId })
                    val detail = new.joinToString("\n", "\n") {
                        "(${it.questionId}) ${it.title} / ${resolveLocal(it.creationDate).formatDateOrTime()}"
                    }
                    log.info { "Query by tags got ${new.size} previously unwatched questions:$detail" }
                } else {
                    log.info { "Started with ${new.size} questions" }
                }

                // Add oldest first so that #cleanupQuestionIds will cleanup in the right order
                questionIds.addAll(ids.reversed())
                cleanupQuestionIds()
            }
        }
        if (questionIds.isEmpty()) return emptyList()
        return queryByWatchedIds()
    }

    private suspend fun queryByWatchedIds() = filterClosedAndMistagged(apiService.getQuestions(questionIds, limit, site))

    private suspend fun queryByWatchedTags(window: Duration = queryByTagWindow) = watchedTags
        .flatMap { apiService.getQuestionsByTag(it, window, limit, site) }.asSequence()
        .distinctBy { it.questionId }
        .filterNot { it.tags.anyIgnored(ignoredTags) }
        .filter { it.closedDate == null }
        .sortedByDescending { it.creationDate }
        .take(limit)
        .toList()

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

        cleanupQuestionIds()
        return qs
    }

    override fun fade(question: Question): Boolean {
        val closed = question.closedReason != null
        val ignored = question.tags.anyIgnored(ignoredTags)
        return closed || ignored
    }

    override fun isNew(questionId: Long) = questionId in newQuestionIds

    override fun resetNew() {
        newQuestionIds.clear()
    }

    override fun removeNew(questionId: Long) {
        // The list shouldn't have duplicates, but if it does this will remove all of them
        newQuestionIds.removeIf { it == questionId }
    }
}

private fun <T> MutableIterator<T>.removeNext() = next().apply { remove() }
