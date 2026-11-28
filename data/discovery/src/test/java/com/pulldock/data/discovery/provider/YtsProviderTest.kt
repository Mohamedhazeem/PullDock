package com.pulldock.data.discovery.provider

import com.pulldock.domain.discovery.ApiCredentialStore
import com.pulldock.domain.discovery.ProviderPage
import com.pulldock.domain.discovery.error.DiscoveryError
import com.pulldock.domain.discovery.model.SearchCategory
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

class YtsProviderTest {

    private val creds = object : ApiCredentialStore {
        override fun get(providerId: String): String? = null
        override fun set(providerId: String, key: String?) = Unit
    }
    private val query = TorrentSearchQuery("movie", SearchFilters())

    private fun fixture(): String = javaClass.getResourceAsStream("/fixtures/yts_movies.json")!!.bufferedReader().readText()

    @Test
    fun validSearchMapsMovies() = runTest {
        val engine = MockEngine { respond(fixture(), HttpStatusCode.OK) }
        val result = YtsProvider(HttpClient(engine), creds).search(query, ProviderPage(1, null))
        assertEquals(1, result.results.size)
        assertEquals(SearchCategory.MOVIES, result.results[0].category)
        assertEquals(10, result.results[0].seeders)
    }

    @Test
    fun serverErrorThrowsProvider() = runTest {
        val engine = MockEngine { respond("", HttpStatusCode.InternalServerError) }
        assertThrows(DiscoveryError.Provider::class.java) {
            runBlocking { YtsProvider(HttpClient(engine), creds).search(query, ProviderPage(1, null)) }
        }
    }

    private fun runTest(block: suspend kotlinx.coroutines.test.TestScope.() -> Unit) =
        kotlinx.coroutines.test.runTest { block() }
}