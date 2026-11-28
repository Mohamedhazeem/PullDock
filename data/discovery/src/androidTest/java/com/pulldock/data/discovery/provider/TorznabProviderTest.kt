package com.pulldock.data.discovery.provider

import com.pulldock.domain.discovery.ProviderPage
import com.pulldock.domain.discovery.model.ProviderConfiguration
import com.pulldock.domain.discovery.model.SearchCategory
import com.pulldock.domain.discovery.model.SearchFilters
import com.pulldock.domain.discovery.model.TorrentSearchQuery
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TorznabProviderTest {

    private val config = ProviderConfiguration(
        providerId = "t1",
        displayName = "TestIndexer",
        endpoint = "https://example.test",
        enabled = true,
    )
    private val query = TorrentSearchQuery("movie", SearchFilters())
    private fun client(engine: MockEngine) = HttpClient(engine)

    @Test
    fun capabilitiesParseSearchTypeAndCategories() = runTest {
        val caps = """<?xml version="1.0"?><caps><searching><search available="yes" name="search"/></searching><category id="2000" name="Movies"/></caps>"""
        val engine = MockEngine { respond(caps, HttpStatusCode.OK, headersOf("Content-Type", "application/xml")) }
        val capsResult = TorznabProvider(config, client(engine)).capabilities()
        assertTrue(capsResult.categories.containsKey(SearchCategory.MOVIES))
    }

    @Test
    fun rssSearchParsesTitleSizeSeedersAndMagnet() = runTest {
        val rss = """<?xml version="1.0"?><rss xmlns:torznab="https://torznab.github.io/spec-1.3-draft/torznab/Specification-v1.3.html"><channel>
            <item><title>Movie 2024</title><size>1500</size><torznab:seeders>7</torznab:seeders><torznab:peers>3</torznab:peers>
            <enclosure url="magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567"/></item></channel></rss>"""
        val engine = MockEngine { respond(rss, HttpStatusCode.OK, headersOf("Content-Type", "application/xml")) }
        val result = TorznabProvider(config, client(engine)).search(query, ProviderPage(1, null))
        assertEquals(1, result.results.size)
        assertEquals(1500L, result.results[0].sizeBytes)
        assertEquals(7, result.results[0].seeders)
        assertEquals("0123456789abcdef0123456789abcdef01234567", result.results[0].infoHash)
    }

    @Test
    fun malformedXmlYieldsEmptyResultsWithoutCrashing() = runTest {
        val engine = MockEngine { respond("<rss><channel><item>", HttpStatusCode.OK, headersOf("Content-Type", "application/xml")) }
        val result = TorznabProvider(config, client(engine)).search(query, ProviderPage(1, null))
        assertEquals(0, result.results.size)
    }
}