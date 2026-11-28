package com.pulldock.data.discovery.provider

import com.pulldock.domain.discovery.ApiCredentialStore
import com.pulldock.domain.discovery.ProviderPage
import com.pulldock.domain.discovery.error.DiscoveryError
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
import org.junit.Assert.assertThrows
import org.junit.Test

class BitSearchProviderTest {

    private val query = TorrentSearchQuery("test", SearchFilters())
    private val credentials = object : ApiCredentialStore {
        override fun get(providerId: String): String? = null
        override fun set(providerId: String, key: String?) = Unit
    }

    private fun client(engine: MockEngine) = HttpClient(engine)

    private fun MockRequestHandleScope.json(body: String) = respond(body, HttpStatusCode.OK, headersOf("Content-Type", "application/json"))

    @Test
    fun `valid search json maps results`() = runTest {
        val body = """{"results":[{"id":"1","infohash":"0123456789abcdef0123456789abcdef01234567","title":"Movie","size":1234,"category":"Movies","seeders":5,"leechers":2,"verified":true,"createdAt":"2026-01-01T00:00:00Z"}]}"""
        val engine = MockEngine { json(body) }
        val result = BitSearchProvider(client(engine), credentials).search(query, ProviderPage(1, null))
        assertEquals(1, result.results.size)
        assertEquals("Movie", result.results[0].title)
        assertEquals(5, result.results[0].seeders)
        assertEquals(SearchCategory.MOVIES, result.results[0].category)
    }

    @Test
    fun `empty results`() = runTest {
        val engine = MockEngine { json("""{"results":[]}""") }
        val result = BitSearchProvider(client(engine), credentials).search(query, ProviderPage(1, null))
        assertEquals(0, result.results.size)
    }

    @Test
    fun `malformed json throws Parse`() = runTest {
        val engine = MockEngine { json("not json") }
        assertThrows(DiscoveryError.Parse::class.java) {
            kotlinx.coroutines.runBlocking { BitSearchProvider(client(engine), credentials).search(query, ProviderPage(1, null)) }
        }
    }

    @Test
    fun `http 429 throws RateLimited`() = runTest {
        val engine = MockEngine { respond("", HttpStatusCode.TooManyRequests) }
        assertThrows(DiscoveryError.RateLimited::class.java) {
            kotlinx.coroutines.runBlocking { BitSearchProvider(client(engine), credentials).search(query, ProviderPage(1, null)) }
        }
    }

    @Test
    fun `http 500 throws Provider error`() = runTest {
        val engine = MockEngine { respond("", HttpStatusCode.InternalServerError) }
        assertThrows(DiscoveryError.Provider::class.java) {
            kotlinx.coroutines.runBlocking { BitSearchProvider(client(engine), credentials).search(query, ProviderPage(1, null)) }
        }
    }

    @Test
    fun `detail with valid magnet yields MAGNET descriptor`() = runTest {
        val magnet = "magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567&dn=x"
        val body = """{"torrent":{"id":"1","infohash":"0123456789abcdef0123456789abcdef01234567","title":"Movie","magnetUri":"$magnet"}}"""
        val engine = MockEngine { json(body) }
        val details = BitSearchProvider(client(engine), credentials).details("1")
        assertEquals("Movie", details?.title)
        assertEquals(com.pulldock.domain.discovery.model.DescriptorKind.MAGNET, details?.downloadDescriptor?.kind)
    }
}