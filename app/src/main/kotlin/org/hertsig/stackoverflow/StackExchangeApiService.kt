package org.hertsig.stackoverflow

import io.ktor.client.*
import io.ktor.client.plugins.compression.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import org.hertsig.core.logger
import org.hertsig.stackoverflow.dto.api.ApiError
import org.hertsig.stackoverflow.dto.api.ApiResponse
import org.hertsig.stackoverflow.dto.api.Filter
import org.hertsig.stackoverflow.dto.api.Question
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.toJavaDuration

private val log = logger {}
private const val FILTER_NAME = "5147LfK)H"

class StackExchangeApiService(
    // StackExchange API docs: "This is not considered a secret"
    private val apiKey: String = "igopOTPhc5E*ngiLuMc8HQ((",
    private val defaultSite: String = "stackoverflow",
    private val json: Json = defaultJson,
    private val client: HttpClient = defaultClient,
) {
    suspend fun getQuestionsByTag(
        tag: String,
        window: Duration = 2.hours,
        limit: Int = 50,
        site: String = defaultSite,
    ): List<Question> {
        require(limit in 1..100)
        val response = apiCall("questions", site) {
            parameter("filter", FILTER_NAME)
            parameter("sort", "creation")
            parameter("tagged", tag)
            parameter("min", Instant.now().minus(window.toJavaDuration()).epochSecond)
            parameter("pagesize", limit)
        }
        val (questions, _) = parseResponse<Question>(response)
        return questions
    }

    suspend fun getQuestions(ids: Collection<Long>, site: String = defaultSite): List<Question> {
        if (ids.isEmpty()) return emptyList()
        require(ids.size <= 100)
        val query = ids.distinct().joinToString(";")
        val response = apiCall("questions/$query", site) {
            parameter("filter", FILTER_NAME)
            parameter("sort", "creation")
        }
        val (questions, _) = parseResponse<Question>(response)
        return questions
    }

    suspend fun getQuestionsWithBounty(site: String = defaultSite, pageSize: Int = 100): List<Question> {
        val result = mutableListOf<Question>()
        var page = 1
        do {
            val (elements, more) = getQuestionsWithBounty(site, page++, pageSize)
            result.addAll(elements)
        } while (more)
        return result
    }

    private suspend fun getQuestionsWithBounty(site: String, page: Int = 0, pageSize: Int = 100): Pair<List<Question>, Boolean> {
        val response = apiCall("questions/featured", site) {
            parameter("filter", FILTER_NAME)
            parameter("sort", "creation")
            parameter("pagesize", pageSize)
            parameter("page", page)
        }
        return parseResponse<Question>(response)
    }

    suspend fun getFilter(): Filter {
        Instant.now()
        val response = apiCall("filters/$FILTER_NAME", null)
        val (filters, _) = parseResponse<Filter>(response)
        return filters.single()
    }

    suspend fun createFilter(unsafe: Boolean = false, vararg include: String) {
        val response = apiCall("filters/create", null) {
            parameter("unsafe", unsafe)
            parameter("include", include.joinToString(";"))
        }
        println(response.status)
        println(response.bodyAsText())
    }

    private suspend fun apiCall(
        path: String,
        site: String?,
        builder: HttpRequestBuilder.() -> Unit = {},
    ) = client.request("https://api.stackexchange.com/2.3/$path") {
            parameter("key", apiKey)
            parameter("site", site)
            builder()
        }

    private suspend inline fun <reified T> parseResponse(response: HttpResponse): Pair<List<T>, Boolean> {
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
            return emptyList<T>() to false
        }

        log.debug("Decoding API response: $text")
        val responseData = json.decodeFromString<ApiResponse<T>>(text)
        if (responseData.quotaRemaining < responseData.quotaMax / 100) {
            log.warn("Quota remaining low: ${responseData.quotaRemaining}/${responseData.quotaMax}")
        } else {
            log.debug("Quota remaining: ${responseData.quotaRemaining}/${responseData.quotaMax}")
        }
        handleBackoff(responseData.backoff)
        return responseData.items to responseData.hasMore
    }

    private fun handleBackoff(backoff: Int?) {
        if (backoff != null) {
            // This doesn't really count as "handling" it but I've never seen one of these so it's probably fine.
            log.warn("Got backoff request for $backoff seconds")
        }
    }
}

internal val defaultClient by lazy {
    HttpClient {
        install(WebSockets)
        install(ContentEncoding) { deflate() }
    }
}

@OptIn(ExperimentalSerializationApi::class)
internal val defaultJson by lazy {
    Json {
        ignoreUnknownKeys = true
        namingStrategy = JsonNamingStrategy.SnakeCase
    }
}
