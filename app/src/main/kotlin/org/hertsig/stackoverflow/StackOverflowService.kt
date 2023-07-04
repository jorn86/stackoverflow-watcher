package org.hertsig.stackoverflow

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.SerializationException
import org.hertsig.core.logger
import org.hertsig.stackoverflow.dto.api.Question
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private val log = logger {}

class StackOverflowService(
    private val apiService: StackExchangeApiService,
    private val websocketService: StackExchangeWebsocketService,
    private val questionLimit: Int = 50,
    private val pollTime: Duration = 1.minutes,
) {
    init {
        require(questionLimit <= 100) {
            "StackExchange API only supports up to 100 questions in one query"
        }
        require(pollTime >= 1.minutes) {
            "StackExchange API limits similar queries to once per minute"
        }
    }

    private var websocketJob: Job? = null

    private val watchedQuestions = LinkedHashSet<Long>()
    private val watchedTags = mutableSetOf<String>()
    private val ignoredTags = mutableSetOf<String>()

    private val bounties = MutableStateFlow(emptyList<Question>())
    val bountyFlow: StateFlow<List<Question>> get() = bounties

    private val currentQuestions = MutableStateFlow(emptyList<Question>())
    val questionFlow: StateFlow<List<Question>> get() = currentQuestions

    private val newQuestionFlow = MutableSharedFlow<Question>()

    suspend fun startWebsocket(vararg initialTags: String) {
        websocketJob = websocketService.connect()
        initialTags.forEach { websocketService.addWatchedTag(it) }
        log.info("Started with ${watchedTags.size} watched tags")
    }

    suspend fun preloadWatchedQuestions(tags: Set<String> = watchedTags, window: Duration = 3.hours) {
        watchedTags.addAll(tags)
        val questions = tags
            .flatMap { apiService.getQuestionsByTag(it, window) }
            .distinctBy { it.questionId }
            .sortedByDescending { it.creationDate }
            .take(questionLimit)
        currentQuestions.emit(questions)
        watchedQuestions.addAll(questions.map { it.questionId })
        log.debug("Preloaded ${questions.size} questions")
    }

    suspend fun pollBounties(interval: Duration = 15.minutes) {
        while (true) {
            val bounties = apiService.getQuestionsWithBounty()
                .filter { it.tags.any { it in watchedTags } }
                .sortedBy { it.bountyClosesDate }
            log.debug("Polled ${bounties.size} bounties with tags $watchedTags")
            this.bounties.emit(bounties)
            delay(interval)
        }
    }

    suspend fun startQuestionPoller() {
        while (true) {
            delay(pollTime)
            poll()
        }
    }

    private suspend fun poll() {
        log.debug("Polling ${watchedQuestions.size} questions")
        try {
            currentQuestions.emit(apiService.getQuestions(watchedQuestions))
        } catch (e: SerializationException) {
            log.error("Error parsing API response", e)
        } catch (e: Exception) {
            log.error("Unexpected exception", e)
        }
    }

    suspend fun watchNewQuestions(collector: FlowCollector<Question>) {
        newQuestionFlow.collect(collector)
    }

    private suspend fun addWatchedQuestion(id: Long): Question? {
        if (watchedQuestions.size > questionLimit) {
            log.debug("Dropping ${watchedQuestions.size - questionLimit} watched question(s)")
            val iterator = watchedQuestions.iterator()
            repeat(watchedQuestions.size - questionLimit) { iterator.removeNext() }
        }
        delay(1.seconds) // Give the API some time to realize the question exists

        // Check if the poller has already added the new question
        val existingQuestion = currentQuestions.value.singleOrNull { it.questionId == id }
        if (existingQuestion != null) {
            newQuestionFlow.emit(existingQuestion)
            return existingQuestion
        }

        val question = apiService.getQuestions(setOf(id)).singleOrNull() ?: return null
        newQuestionFlow.emit(question)
        with(currentQuestions.value) {
            // Check *again* if the poller has already added the new question
            val existingQuestion = singleOrNull { it.questionId == id }
            if (existingQuestion != null) return existingQuestion
            currentQuestions.emit(listOf(question) + this)
        }
        return question
    }
}

internal fun questionUrl(id: String) = "https://stackoverflow.com/questions/$id"
private fun MutableIterator<*>.removeNext() {
    next()
    remove()
}
