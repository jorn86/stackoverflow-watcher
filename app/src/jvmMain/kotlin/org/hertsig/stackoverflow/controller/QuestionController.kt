package org.hertsig.stackoverflow.controller

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.hertsig.logger.logger
import org.hertsig.stackoverflow.SiteMetadata
import org.hertsig.stackoverflow.dto.api.Question
import org.hertsig.stackoverflow.dto.api.TagWiki
import org.hertsig.stackoverflow.service.StackExchangeApiService
import org.hertsig.util.backgroundTask
import java.net.URL
import java.time.Instant
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.jvm.optionals.getOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

private val log = logger {}

abstract class QuestionController(
    protected val apiService: StackExchangeApiService,
    val name: String,
    val site: SiteMetadata,
    interval: Duration,
    private var lastPollTime: Instant = Instant.EPOCH
) {
    protected val questions = MutableStateFlow(emptyList<Question>())
    private val interval = interval.toJavaDuration()
    private val pollMutex = Mutex()
    protected val debugName get() = "$name ${site.name}"
    protected var active = false; private set

    open val new get() = 0
    open fun resetNew() {}
    open fun removeNew(questionId: Long) {}

    @Composable
    fun collectAsState() = questions.collectAsState()

    val iconUrl get() = URL(site.iconUrl)

    open fun displayDate(question: Question): Long = question.creationDate
    open fun fade(question: Question) = false
    open fun isNew(questionId: Long) = false

    suspend fun startPolling() {
        backgroundTask("Poller for $debugName", 10.seconds) {
            active = true
            log.trace { "Checking poll time for $debugName" }
            if (pollMutex.isLocked) {
                log.warn{"Someone is holding lock for $debugName, skipping poll"}
            } else if (lastPollTime.plus(interval).isBefore(Instant.now())) {
                doPoll()
                Modifier.onGloballyPositioned {  }
            }
        }
        active = false
    }

    internal suspend fun doPoll() {
        pollMutex.withLock {
            lastPollTime = Instant.now()
            val questions = queryQuestions()
            log.info { "$debugName polled ${questions.size} questions" }
            this.questions.emit(questions)
        }
    }

    internal abstract suspend fun queryQuestions(): List<Question>

    fun getTagWikiExcerpt(tag: String): String? {
        return tagCache.get(TagCacheKey(site.apiParameter, tag)) {
            runBlocking { Optional.ofNullable(apiService.getTagInfo(site.apiParameter, tag).singleOrNull()) }
        }.getOrNull()?.excerpt
    }

    companion object {
        private val tagCache: Cache<TagCacheKey, Optional<TagWiki>> = CacheBuilder.newBuilder()
            .expireAfterAccess(8, TimeUnit.HOURS)
            .build()
    }
}
private data class TagCacheKey(val site: String, val tag: String)

fun Iterable<String>.anyIgnored(ignoredTags: Collection<String>) = any { tag ->
    ignoredTags.any { ignored -> tag.startsWith(ignored) }
}
