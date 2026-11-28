package com.pulldock.data.discovery.provider

import com.pulldock.domain.discovery.ApiCredentialStore
import com.pulldock.domain.discovery.ProviderPage
import com.pulldock.domain.discovery.error.DiscoveryError
import com.pulldock.domain.discovery.model.SearchFilters
import com.pulldock.domain.discovery.model.TorrentSearchQuery
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class EztvProviderTest {

    private val creds = object : ApiCredentialStore {
        override fun get(providerId: String): String? = null
        override fun set(providerId: String, key: String?) = Unit
    }

    private fun fixture(): String = javaClass.getResourceAsStream("/fixtures/eztv_torrents.json")!!.bufferedReader().readText()

    @Test
    fun validSearchFiltersByTitle() = runTest {
        val engine = MockEngine { respond(fixture(), HttpStatusCode.OK) }
        val result = EztvProvider(HttpClient(engine), creds).search(TorrentSearchQuery("show", SearchFilters()), ProviderPage(1, null))
        assertEquals(1, result.results.size)
        assertEquals(10, result.results[0].seeders)
    }

    @Test
    fun missingTorrentsIsEmpty() = runTest {
        val engine = MockEngine { respond("{}", HttpStatusCode.OK) }
        val result = EztvProvider(HttpClient(engine), creds).search(TorrentSearchQuery("x", SearchFilters()), ProviderPage(1, null))
        assertEquals(0, result.results.size)
    }

    @Test
    fun malformedJsonThrowsParse() = runTest {
        val engine = MockEngine { respond("not json", HttpStatusCode.OK) }
        assertThrows(DiscoveryError.Parse::class.java) {
            runBlocking { EztvProvider(HttpClient(engine), creds).search(TorrentSearchQuery("x", SearchFilters()), ProviderPage(1, null)) }
        }
    }

    private fun runTest(block: suspend kotlinx.coroutines.test.TestScope.() -> Unit) =
        kotlinx.coroutines.test.runTest { block() }
}