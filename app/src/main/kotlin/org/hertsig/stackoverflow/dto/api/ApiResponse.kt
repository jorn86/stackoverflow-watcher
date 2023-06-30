package org.hertsig.stackoverflow.dto.api

import kotlinx.serialization.Serializable

@Serializable
data class ApiResponse<T>(
    val items: List<T> = emptyList(),
    val quotaMax: Int,
    val quotaRemaining: Int,
    val backoff: Int? = null,
)