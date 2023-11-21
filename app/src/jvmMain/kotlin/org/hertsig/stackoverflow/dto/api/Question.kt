package org.hertsig.stackoverflow.dto.api

import kotlinx.serialization.Serializable

@Serializable
data class Question(
    val questionId: Long,
    val title: String = "",
    val link: String,
    val acceptedAnswerId: Long? = null,
    val score: Int = 0,
    val answerCount: Int = 0,
    val bountyAmount: Int? = null,
    val bountyClosesDate: Long? = null,
    val closedReason: String? = null,
    val closedDate: Long? = null,
    val creationDate: Long,
    val viewCount: Int = 0,
    val tags: List<String> = emptyList(),
    val answers: List<Answer> = emptyList(),
    val commentCount: Int = 0,
)
