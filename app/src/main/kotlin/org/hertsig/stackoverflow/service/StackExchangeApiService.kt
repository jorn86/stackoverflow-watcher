package org.hertsig.stackoverflow.service

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
import org.hertsig.core.error
import org.hertsig.core.logger
import org.hertsig.core.trace
import org.hertsig.core.warn
import org.hertsig.stackoverflow.dto.api.*
import java.time.Instant

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
        after: Instant,
        limit: Int = 50,
        site: String = defaultSite,
    ): List<Question> {
        require(limit in 1..100)
        val response = apiCall("questions", site) {
            parameter("filter", FILTER_NAME)
            parameter("sort", "creation")
            parameter("tagged", tag)
            parameter("min", after.epochSecond)
            parameter("pagesize", limit)
        }
        val (questions) = parseResponse<Question>(response)
        return questions
    }

    suspend fun getQuestions(ids: Set<Long>, limit: Int = 50, site: String = defaultSite): List<Question> {
        if (ids.isEmpty()) return emptyList()
        require(ids.size <= 100)
        val query = ids.joinToString(";")
        val response = apiCall("questions/$query", site) {
            parameter("filter", FILTER_NAME)
            parameter("sort", "creation")
            parameter("pagesize", limit)
        }
        val (questions) = parseResponse<Question>(response)
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

    suspend fun getSites(): List<ApiSite> {
        val response = apiCall("sites", null) {
            parameter("pagesize", 9999)
        }
        val (sites) = parseResponse<ApiSite>(response)
        return sites
    }

    suspend fun getFilter(): Filter {
        val response = apiCall("filters/$FILTER_NAME", null)
        val (filters) = parseResponse<Filter>(response)
        return filters.single()
    }

    suspend fun createFilter(unsafe: Boolean = false, vararg include: String): Filter {
        val response = apiCall("filters/create", null) {
            parameter("unsafe", unsafe)
            parameter("include", include.joinToString(";"))
        }
        val (filter) = parseResponse<Filter>(response)
        return filter.single()
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
            log.error { "Request to ${response.request.url} failed with status code ${response.status}, body was $text" }
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

        log.trace { "Decoding API response: $text" }
        val responseData = json.decodeFromString<ApiResponse<T>>(text)
        if (responseData.quotaRemaining < responseData.quotaMax / 2) {
            log.warn { "Quota remaining low: ${responseData.quotaRemaining}/${responseData.quotaMax}" }
        } else {
            log.trace { "Quota remaining: ${responseData.quotaRemaining}/${responseData.quotaMax}" }
        }
        handleBackoff(responseData.backoff)
        return responseData.items to responseData.hasMore
    }

    private fun handleBackoff(backoff: Int?) {
        if (backoff != null) {
            // This doesn't really count as "handling" it but I've never seen one of these so it's probably fine.
            log.warn { "Got backoff request for $backoff seconds" }
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
