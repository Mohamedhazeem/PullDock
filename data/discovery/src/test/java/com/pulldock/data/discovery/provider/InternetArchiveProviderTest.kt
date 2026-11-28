package com.pulldock.data.discovery.provider

import com.pulldock.domain.discovery.ProviderPage
import com.pulldock.domain.discovery.model.RightsStatus
import com.pulldock.domain.discovery.model.SearchCategory
import com.pulldock.domain.discovery.model.SearchFilters
import com.pulldock.domain.discovery.model.TorrentSearchQuery
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class InternetArchiveProviderTest {

    private val query = TorrentSearchQuery("book", SearchFilters())
    private fun client(engine: MockEngine) = HttpClient(engine)
    private fun MockRequestHandleScope.json(body: String) = respond(body, HttpStatusCode.OK, headersOf("Content-Type", "application/json"))

    @Test
    fun `advanced search maps identifier and rights`() = runTest {
        val body = """{"response":{"numFound":1,"docs":[{"identifier":"id1","title":"Book","mediatype":"texts","licenseurl":"https://example.org/lic"}]}}"""
        val result = InternetArchiveProvider(client(MockEngine { json(body) })).search(query, ProviderPage(1, null))
        assertEquals(1, result.results.size)
        assertEquals("id1", result.results[0].resultId)
        assertEquals(RightsStatus.CONFIRMED, result.results[0].rightsStatus)
        assertEquals(SearchCategory.BOOKS, result.results[0].category)
    }

    @Test
    fun `missing licence means unknown rights`() = runTest {
        val body = """{"response":{"numFound":1,"docs":[{"identifier":"id2","title":"Doc"}]}}"""
        val result = InternetArchiveProvider(client(MockEngine { json(body) })).search(query, ProviderPage(1, null))
        assertEquals(RightsStatus.UNKNOWN, result.results[0].rightsStatus)
    }

    @Test
    fun `details expose licence and torrent availability`() = runTest {
        val body = """{"files":[{"name":"a.torrent","format":"Archive BitTorrent"}],"metadata":{"title":"Book","licenseurl":"https://example.org/lic"}}"""
        val details = InternetArchiveProvider(client(MockEngine { json(body) })).details("id1")
        assertEquals("https://example.org/lic", details?.licence)
        assertEquals(RightsStatus.CONFIRMED, details?.rightsStatus)
    }

    @Test
    fun `metadata without torrent yields no add action`() = runTest {
        val body = """{"files":[{"name":"page.txt","format":"Text"}],"metadata":{"title":"Book"}}"""
        val details = InternetArchiveProvider(client(MockEngine { json(body) })).details("id1")
        assertEquals(emptySet<com.pulldock.domain.discovery.model.ResultAction>(), details?.availableActions)
    }
}