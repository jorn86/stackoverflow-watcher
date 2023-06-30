package org.hertsig.stackoverflow.dto.api

import kotlinx.serialization.Serializable

@Serializable
data class Answer(
    val answerId: Int,
    val isAccepted: Boolean,
)