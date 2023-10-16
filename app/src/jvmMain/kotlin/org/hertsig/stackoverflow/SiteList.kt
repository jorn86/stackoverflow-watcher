package org.hertsig.stackoverflow

import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.serialization.decodeFromString
import org.hertsig.logger.logger
import org.hertsig.stackoverflow.dto.Site
import org.hertsig.stackoverflow.service.StackExchangeApiService
import org.hertsig.stackoverflow.service.defaultClient
import org.hertsig.stackoverflow.service.defaultJson

private val log = logger {}

data class SiteMetadata(
    val siteId: Int,
    val name: String,
    val type: SiteType,
    val description: String,
    val apiParameter: String,
    val iconUrl: String,
)

enum class SiteType {
    MAIN, META, OTHER
}

suspend fun getSiteList(includeMeta: Boolean = false): List<Site> {
    val json = defaultClient.get("https://meta.stackexchange.com/topbar/site-switcher/all-pinnable-sites")
        .bodyAsText()
    try {
        return parseSiteList(json, includeMeta)
    } catch (e: Exception) {
        log.warn(e) { "Failed parsing site list, falling back to saved file (was: $json)" }
    }
    val localJson = SiteList::class.java.getResourceAsStream("/all-pinnable-sites.json")!!.bufferedReader()
        .use { it.readText() }
    return parseSiteList(localJson, includeMeta)
}

private fun parseSiteList(json: String, includeMeta: Boolean): List<Site> {
    return defaultJson.decodeFromString<List<Site>>(json)
        .filter { includeMeta || !it.isMeta() }
        .sortedBy { it.sitename }
}

object SiteList {
    private lateinit var metadata: List<SiteMetadata>

    suspend fun get(apiService: StackExchangeApiService): List<SiteMetadata> {
        if (::metadata.isInitialized) return metadata
        val apiSites = apiService.getSites()
        val sites = getSiteList(true).associateBy { it.hostname }
        metadata = apiSites.map {
            val hostName = it.siteUrl
            val site = sites[hostName.substring(8)] ?: error("No site entry for $hostName in ${sites.keys}")
            val type = when(it.siteType) {
                "main_site" -> SiteType.MAIN
                "meta_site" -> SiteType.META
                else -> SiteType.OTHER
            }
            SiteMetadata(site.siteid, it.name, type, site.description, it.apiSiteParameter, it.iconUrl)
        }
        return metadata
    }
}
