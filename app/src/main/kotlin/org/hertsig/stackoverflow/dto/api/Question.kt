package org.hertsig.stackoverflow.dto.api

import kotlinx.serialization.Serializable

@Serializable
data class Question(
    val questionId: Long,
    val title: String,
    val link: String,
    val acceptedAnswerId: Long? = null,
    val score: Int,
    val answerCount: Int,
    val bountyAmount: Int? = null,
    val bountyClosesDate: Long? = null,
    val closedReason: String? = null,
    val closedDate: Long? = null,
    val creationDate: Long,
    val viewCount: Int,
    val tags: List<String>,
    val answers: List<Answer> = emptyList(),
    val commentCount: Int = 0,
)
