package org.hertsig.stackoverflow.controller

import kotlinx.serialization.Serializable

@Serializable
data class Config(val controllers: List<ControllerConfig>)

@Serializable
data class ControllerConfig(
    val siteId: Int,
    val type: String,
    val tags: Set<String> = emptySet(),
    val ignoredTags: Set<String> = emptySet(),
)
