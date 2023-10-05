package org.hertsig.stackoverflow.dto

import kotlinx.serialization.Serializable

@Serializable
data class Site(
    val siteid: Int,
    val sitename: String,
    val description: String,
    val hostname: String,
) {
    fun isMeta() = sitename.endsWith(" Meta") || sitename.startsWith("Meta ")
}
