package org.hertsig.stackoverflow

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import io.ktor.client.*
import io.ktor.client.plugins.compression.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.websocket.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import org.hertsig.core.logger
import org.hertsig.stackoverflow.dto.*
import org.hertsig.stackoverflow.dto.api.ApiError
import org.hertsig.stackoverflow.dto.api.ApiResponse
import org.hertsig.stackoverflow.dto.api.Filter
import org.hertsig.stackoverflow.dto.api.Question
import org.hertsig.stackoverflow.dto.websocket.NewQuestionMessage
import org.hertsig.stackoverflow.dto.websocket.WebsocketMessage
import java.time.Instant
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

private val log = logger {}

private const val FILTER_NAME = "5147LfK)H"

class StackOverflowService(
    private val questionLimit: Int = 30,
    private val pollTime: Duration = 1.minutes,
    // "This is not considered a secret, and may be safely embed in client side code."
    private val apiKey: String = "igopOTPhc5E*ngiLuMc8HQ((",
) {
    init {
        require(questionLimit <= 100) {
            "StackExchange API only supports up to 100 questions in one query"
        }
        require(pollTime >= 1.minutes) {
            "StackExchange API limits similar queries to once per minute"
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        ignoreUnknownKeys = true
        namingStrategy = JsonNamingStrategy.SnakeCase
    }
    private val client = HttpClient {
        install(WebSockets)
        install(ContentEncoding) { deflate() }
    }

    private val watchedQuestions = LinkedHashSet<Long>()
    private val watchedTags = mutableSetOf<String>()
    private val ignoredTags = mutableSetOf<String>()

    private val currentQuestions = MutableStateFlow(emptyList<Question>())
    private val newQuestionFlow = MutableSharedFlow<Question>()

    private var activeWebsocket: WebSocketSession? = null

    suspend fun startWebsocket(vararg initialTags: String) {
        require(activeWebsocket == null) { "Websocket session already active: $activeWebsocket" }
        client.webSocket(urlString = "wss://qa.sockets.stackexchange.com/") {
            activeWebsocket = this

            addWatchedTags(initialTags)
            log.info("Started with ${watchedTags.size} watched tags")
            while (true) {
                val frame = incoming.receive()
                if (frame !is Frame.Text) {
                    log.warn("Skipping unexpected frame $frame")
                    continue
                }

                val text = frame.readText()
                try {
                    val container = json.decodeFromString<WebsocketMessage>(text)
                    if (container.action == "hb") {
                        log.trace("Responding to heartbeat: ${container.data}")
                        send(Frame.Text(container.data)) // pong
                        continue
                    }

                    val message = json.decodeFromString<NewQuestionMessage>(container.data)
                    if (message.tags.noneIgnored()) {
                        val questionId = message.id.toLong()
                        if (!watchedQuestions.add(questionId)) continue
                        val question = addWatchedQuestion(questionId)
                        if (question != null) {
                            log.info("${question.title} ${message.tags}: ${question.url}")
                        }
                        log.debug("Now watching ${watchedQuestions.size} questions")
                    } else {
                        log.info("New question has ignored tag: ${message.tags}")
                    }
                } catch (e: SerializationException) {
                    log.error("Error parsing frame: $text", e)
                } catch (e: Exception) {
                    log.error("Unexpected exception", e)
                }
            }
        }
    }

    suspend fun preloadWatchedQuestions(tags: Set<String> = watchedTags, window: Duration = 2.hours) {
        val questions = tags.flatMap {
            val response = apiCall("questions") {
                parameter("filter", FILTER_NAME)
                parameter("tagged", it)
                parameter("sort", "creation")
                parameter("min", Instant.now().minus(window.toJavaDuration()).epochSecond)
                parameter("pagesize", questionLimit)
            }
            parseResponse<Question>(response).filter { it.tags.noneIgnored() }
        }.distinctBy { it.questionId }
            .sortedByDescending { it.creationDate }
            .take(questionLimit)
        currentQuestions.emit(questions)
        watchedQuestions.addAll(questions.map { it.questionId })
        log.debug("Preloaded ${questions.size} questions")
    }

    suspend fun startQuestionPoller() {
        while (true) {
            delay(pollTime)
            poll()
        }
    }

    internal suspend fun poll() {
        log.debug("Polling ${watchedQuestions.size} questions")
        try {
            currentQuestions.emit(getQuestions(watchedQuestions))
        } catch (e: SerializationException) {
            log.error("Error parsing API response", e)
        } catch (e: Exception) {
            log.error("Unexpected exception", e)
        }
    }

    suspend fun watchTags(vararg tags: String) = with(activeWebsocket) {
        require(this != null) { "No active connection" }
        addWatchedTags(tags)
        log.debug("Now watching ${watchedTags.size} tags")
    }

    fun ignoreTags(vararg tags: String) {
        ignoredTags.addAll(tags)
    }

    fun hasActiveWebsocket() = activeWebsocket != null

    suspend fun watchNewQuestions(collector: FlowCollector<Question>) {
        newQuestionFlow.collect(collector)
    }

    suspend fun collectQuestions(collector: FlowCollector<List<Question>>) {
        currentQuestions.collect(collector)
    }

    @Composable
    fun collectQuestionsAsState(context: EmptyCoroutineContext = EmptyCoroutineContext) =
        currentQuestions.collectAsState(context)

    private suspend fun WebSocketSession.addWatchedTags(tags: Array<out String>) {
        tags.forEach {
            if (watchedTags.add(it)) {
                send(Frame.Text("1-questions-newest-tag-$it"))
            }
        }
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

        val question = getQuestions(setOf(id)).singleOrNull() ?: return null
        newQuestionFlow.emit(question)
        with(currentQuestions.value) {
            // Check *again* if the poller has already added the new question
            val existingQuestion = singleOrNull { it.questionId == id }
            if (existingQuestion != null) return existingQuestion
            currentQuestions.emit(listOf(question) + this)
        }
        return question
    }

    private suspend fun getQuestions(ids: Set<Long>): List<Question> {
        if (ids.isEmpty()) return emptyList()
        val query = ids.joinToString(";")
        val response = apiCall("questions/$query") {
            parameter("filter", FILTER_NAME)
            parameter("sort", "creation")
        }
        return parseResponse<Question>(response)
    }

    suspend fun getFilter() {
        val response = apiCall("filters/$FILTER_NAME", null)
        println( parseResponse<Filter>(response))
    }

    suspend fun createFilter() {
        val response = apiCall("filters/create", null) {
            parameter("unsafe", "true")
            parameter("include", "question.answers.answer_id;question.answers.is_accepted;question.comment_count")
        }
        println(response.status)
        println(response.bodyAsText())
    }

    private suspend fun apiCall(
        path: String,
        site: String? = "stackoverflow",
        builder: HttpRequestBuilder.() -> Unit = {},
    ) = client.request("https://api.stackexchange.com/2.3/$path") {
        parameter("key", apiKey)
        parameter("site", site)
        builder()
    }

    private suspend inline fun <reified T> StackOverflowService.parseResponse(response: HttpResponse): List<T> {
        val text = response.bodyAsText()
        if (response.status != HttpStatusCode.OK) {
            log.error("Request to ${response.request.url} failed with status code ${response.status}, body was $text")
            if (response.status.value in 400..499) {
                val error = try {
                    json.decodeFromString<ApiError>(text)
                } catch (e: Exception) {
                    log.error("Error parsing error response", e)
                    null
                }
                handleBackoff(error?.backoff)
            }
            return emptyList()
        }

        log.debug("Decoding API response: $text")
        val responseData = json.decodeFromString<ApiResponse<T>>(text)
        if (responseData.quotaRemaining < responseData.quotaMax / 100) {
            log.warn("Quota remaining low: ${responseData.quotaRemaining}/${responseData.quotaMax}")
        } else {
            log.debug("Quota remaining: ${responseData.quotaRemaining}/${responseData.quotaMax}")
        }
        handleBackoff(responseData.backoff)
        return responseData.items
    }

    private suspend fun handleBackoff(backoff: Int?) {
        if (backoff != null) {
            log.warn("Got backoff request, delaying for $backoff seconds")
            delay(backoff.seconds)
        }
    }

    private fun Iterable<String>.noneIgnored() = noneIgnored(ignoredTags)
}

internal fun questionUrl(id: String) = "https://stackoverflow.com/questions/$id"
internal fun Iterable<String>.noneIgnored(ignoredTags: Collection<String>) = none { tag ->
    ignoredTags.any { ignored -> tag.startsWith(ignored) }
}
private fun MutableIterator<*>.removeNext() {
    next()
    remove()
}

suspend fun main() {
    StackOverflowService().createFilter()
}