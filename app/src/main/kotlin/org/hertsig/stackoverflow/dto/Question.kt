package org.hertsig.stackoverflow.dto

import kotlinx.serialization.Serializable
import org.hertsig.stackoverflow.questionUrl
import java.time.Instant

@Serializable
data class Question(
    val questionId: Long,
    val title: String,
    val acceptedAnswerId: Long? = null,
    val score: Int,
    val answerCount: Int,
    val bountyAmount: Int? = null,
    val bountyClosesDate: Long? = null,
    val closedReason: String? = null,
    val creationDate: Long,
    val viewCount: Int,
    val tags: List<String>,
) {
    val url get() = questionUrl(questionId.toString())
    val parsedDate get(): Instant = Instant.ofEpochSecond(creationDate)
    val parsedBountyClosesDate get() = bountyClosesDate?.let(Instant::ofEpochSecond)
}
