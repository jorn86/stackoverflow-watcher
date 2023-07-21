package org.hertsig.stackoverflow.controller

import org.hertsig.stackoverflow.dto.api.Question
import org.hertsig.stackoverflow.service.StackExchangeApiService
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class BountyController(
    private val apiService: StackExchangeApiService,
    private val watchedTags: Set<String> = emptySet(),
    private val ignoredTags: Set<String> = emptySet(),
    private val site: String = "stackoverflow",
    interval: Duration = 15.minutes,
): QuestionController("Bounty", interval) {
    override fun displayDate(question: Question) = question.bountyClosesDate ?: 0
    override fun fade(question: Question) = question.tags.anyIgnored(ignoredTags)

    override suspend fun queryQuestions() = apiService
        .getQuestionsWithBounty(site)
        .filter { it.tags.any(watchedTags::contains) }
        .sortedByDescending { it.bountyClosesDate }
}
