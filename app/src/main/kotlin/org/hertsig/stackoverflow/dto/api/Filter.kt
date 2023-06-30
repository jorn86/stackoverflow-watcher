package org.hertsig.stackoverflow.dto.api

import kotlinx.serialization.Serializable

@Serializable
data class Filter(
    val filter: String,
    val filterType: String,
    val includedFields: List<String>,
)