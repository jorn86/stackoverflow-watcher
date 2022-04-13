package org.hertsig.stackoverflow.dto

import kotlinx.serialization.Serializable

@Serializable
data class ApiError(
    val errorId: Int? = null,
    val errorMessage: String? = null,
    val errorName: String? = null,
    val backoff: Int? = null,
)
