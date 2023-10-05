package org.hertsig.stackoverflow.dto.api

import kotlinx.serialization.Serializable

@Serializable
data class ApiSite(
    val name: String,
    val apiSiteParameter: String,
    val iconUrl: String,
    val siteUrl: String,
    val siteType: String,
) {
    val plainUrl = siteUrl.substringAfter("https://")
    fun isMainSite() = siteType == "main_site"
    fun isMeta() = siteType == "meta_site"
}
