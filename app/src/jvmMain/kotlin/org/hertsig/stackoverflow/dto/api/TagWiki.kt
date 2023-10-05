package org.hertsig.stackoverflow.dto.api

import kotlinx.serialization.Serializable

@Serializable
data class TagWiki(
    val tagName: String,
    val excerpt: String? = null,
)
