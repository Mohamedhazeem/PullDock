package com.pulldock.data.discovery.provider

import com.pulldock.data.discovery.provider.dto.BitSearchSearchResponse
import com.pulldock.data.discovery.provider.dto.ArchiveSearchResponse
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fixtures encode the documented provider response shapes (research D15). Confirm field names
 * against live documentation and refresh these fixtures when providers change.
 */
class ProviderFixtureTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun fixture(name: String): String =
        javaClass.getResourceAsStream("/fixtures/$name")?.bufferedReader()?.readText()
            ?: error("missing fixture $name")

    @Test
    fun `bitsearch fixture parses documented fields`() {
        val parsed = json.decodeFromString(BitSearchSearchResponse.serializer(), fixture("bitsearch_search.json"))
        assertEquals(2, parsed.results.size)
        assertEquals("Fixture Movie 2024", parsed.results[0].title)
        assertEquals(42, parsed.results[0].seeders)
        assertTrue(parsed.results[0].verified == true)
    }

    @Test
    fun `archive fixture parses documented fields`() {
        val parsed = json.decodeFromString(ArchiveSearchResponse.serializer(), fixture("archive_search.json"))
        val doc = parsed.response?.docs?.first()
        assertEquals("fixture-archive-1", doc?.identifier)
        assertEquals("https://example.org/license", doc?.licenseurl)
    }
}