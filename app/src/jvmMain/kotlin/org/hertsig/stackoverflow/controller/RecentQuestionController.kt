package org.hertsig.stackoverflow.controller

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.toMutableStateList
import kotlinx.coroutines.delay
import org.hertsig.logger.logger
import org.hertsig.stackoverflow.SiteMetadata
import org.hertsig.stackoverflow.dto.api.Question
import org.hertsig.stackoverflow.dto.websocket.NewQuestionMessage
import org.hertsig.stackoverflow.service.StackExchangeApiService
import org.hertsig.stackoverflow.service.StackExchangeWebsocketService
import org.hertsig.stackoverflow.ui.formatDateOrTime
import org.hertsig.stackoverflow.ui.resolveLocal
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

private val log = logger {}

class RecentQuestionController(
    apiService: StackExchangeApiService,
    websocketService: StackExchangeWebsocketService,
    initialWatchedTags: Collection<String> = emptyList(),
    initialIgnoredTags: Collection<String> = emptyList(),
    site: SiteMetadata,
    private val limit: Int = 80,
    private val queryByTagWindow: Duration = 14.days,
    queryByTagInterval: Duration = 15.minutes,
): QuestionController(apiService, "Recent", site, 1.minutes) {
    val watchedTags = initialWatchedTags.toMutableStateList()
    val ignoredTags = initialIgnoredTags.toMutableStateList()
    private val newQuestionIds = mutableStateListOf<Long>()
    override val new get() = newQuestionIds.size
    private val questionIds = mutableSetOf<Long>()
    private val queryByTagInterval = queryByTagInterval.toJavaDuration()
    private var lastQueryByTags = Instant.EPOCH

    init {
        require(limit in 1..100)
        websocketService.addListener(::onNewQuestion)
    }

    private suspend fun onNewQuestion(action: String, question: NewQuestionMessage) {
        val match = tagRegex.find(action) ?: return log.warn("Couldn't parse action $action")
        val siteId = match.groups[1]!!.value.toInt()
        val tag = match.groups[2]!!.value
        if (siteId == site.siteId && tag in watchedTags) {
            val newQuestionId = question.id.toLong()
            if (!questionIds.add(newQuestionId)) return
            delay(1.seconds) // Give the API some time to realize the question exists
            newQuestionIds.add(newQuestionId)
            cleanupQuestionIds()
            if (active) {
                log.debug { "Requesting poll to add new question $newQuestionId on active controller $debugName" }
                doPoll()
            } else {
                log.debug{"Not requesting poll to add new question $newQuestionId on inactive controller $debugName"}
            }
        } else {
            log.debug{ "Action $action not relevant for $debugName" }
        }
    }

    private fun cleanupQuestionIds() {
        if (questionIds.size > limit) {
            val toRemove = questionIds.size - limit
            val oldest = questions.value.let { it.subList(it.size - toRemove, it.size) }
                .map { it.questionId }
            log.debug { "Dropping $toRemove watched question(s)" }
            questionIds.removeIf { it in oldest }
        }
        newQuestionIds.retainAll(questionIds)
    }

    override suspend fun queryQuestions(): List<Question> {
        if (lastQueryByTags.plus(queryByTagInterval).isBefore(Instant.now())) {
            lastQueryByTags = Instant.now()
            // Prevent old questions from showing up again at the end of the list.
            // This can happen because newer questions appear, push old questions out, then get closed
            var after = Instant.now().minus(queryByTagWindow.toJavaDuration())
            if (questions.value.isNotEmpty()) {
                val oldest = questions.value.minOf { it.creationDate }
                after = after.coerceAtLeast(Instant.ofEpochSecond(oldest))
            }
            log.info { "$debugName querying questions created after ${resolveLocal(after.epochSecond)}" }
            val questions = queryByWatchedTags(after)
            val ids = questions.map { it.questionId }
            if (!questionIds.containsAll(ids)) {
                val new = questions.filterNot { it.questionId in questionIds }
                if (this.questions.value.isEmpty()) {
                    log.debug { "$debugName started with ${new.size} questions" }
                } else {
                    newQuestionIds.addAll(new.map { it.questionId })
                    val detail = new.joinToString("\n", "\n") {
                        "(${it.questionId}) ${it.title} / ${resolveLocal(it.creationDate).formatDateOrTime()}"
                    }
                    log.info { "$debugName query by tags got ${new.size} previously unwatched questions:$detail" }
                }

                // Add oldest first so that #cleanupQuestionIds will cleanup in the right order
                questionIds.addAll(ids.reversed())
                cleanupQuestionIds()
            }
        }
        if (questionIds.isEmpty()) return emptyList()
        return queryByWatchedIds()
    }

    private suspend fun queryByWatchedIds() = filterClosedAndMistagged(apiService.getQuestions(questionIds, limit, site.apiParameter))

    private suspend fun queryByWatchedTags(after: Instant, limit: Int = this.limit) = watchedTags
        .flatMap { apiService.getQuestionsByTag(it, after, limit, site.apiParameter) }.asSequence()
        .distinctBy { it.questionId }
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
            log.info{"Removed ${closedIds.size} closed question(s) from list: $closedIds"}
        }

        val mistaggedIds = qs.filter { it.tags.none { tag -> tag in watchedTags } }.map { it.questionId }.toSet()
        if (mistaggedIds.isNotEmpty()) {
            qs = qs.filterNot { it.questionId in mistaggedIds }
            questionIds.removeAll(mistaggedIds)
            log.info{"Removed ${mistaggedIds.size} question(s) that no longer have watched tags from list: $mistaggedIds"}
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
private val tagRegex = Regex("(\\d+)-questions-newest-tag-(.+)")
