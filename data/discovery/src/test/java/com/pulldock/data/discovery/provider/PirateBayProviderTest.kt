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

class PirateBayProviderTest {

    private val creds = object : ApiCredentialStore {
        override fun get(providerId: String): String? = null
        override fun set(providerId: String, key: String?) = Unit
    }
    private val query = TorrentSearchQuery("ubuntu", SearchFilters())

    private fun fixture(): String = javaClass.getResourceAsStream("/fixtures/apibay_q.json")!!.bufferedReader().readText()

    @Test
    fun validSearchMapsInfohash() = runTest {
        val engine = MockEngine { respond(fixture(), HttpStatusCode.OK) }
        val result = PirateBayProvider(HttpClient(engine), creds).search(query, ProviderPage(1, null))
        assertEquals(1, result.results.size)
        assertEquals("0123456789abcdef0123456789abcdef01234567", result.results[0].infoHash)
    }

    @Test
    fun emptyArrayIsEmpty() = runTest {
        val engine = MockEngine { respond("[]", HttpStatusCode.OK) }
        val result = PirateBayProvider(HttpClient(engine), creds).search(query, ProviderPage(1, null))
        assertEquals(0, result.results.size)
    }

    @Test
    fun malformedThrowsParse() = runTest {
        val engine = MockEngine { respond("<html>error</html>", HttpStatusCode.OK) }
        assertThrows(DiscoveryError.Parse::class.java) {
            runBlocking { PirateBayProvider(HttpClient(engine), creds).search(query, ProviderPage(1, null)) }
        }
    }

    private fun runTest(block: suspend kotlinx.coroutines.test.TestScope.() -> Unit) =
        kotlinx.coroutines.test.runTest { block() }
}