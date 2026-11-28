package com.pulldock.data.discovery.provider

import com.pulldock.data.discovery.provider.dto.ApibayTorrent
import com.pulldock.data.discovery.provider.dto.BitSearchSearchResponse
import com.pulldock.data.discovery.provider.dto.ArchiveSearchResponse
import com.pulldock.data.discovery.provider.dto.EztvResponse
import com.pulldock.data.discovery.provider.dto.KnabenResponse
import com.pulldock.data.discovery.provider.dto.YbtResponse
import com.pulldock.data.discovery.provider.dto.YtsResponse
import kotlinx.serialization.builtins.ListSerializer
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

    @Test
    fun `yourbittorrent fixture parses documented fields`() {
        val parsed = json.decodeFromString(YbtResponse.serializer(), fixture("yourbittorrent_search.json"))
        assertEquals(1, parsed.results.size)
        assertEquals("Ubuntu-22.04-desktop-amd64", parsed.results[0].name)
        assertEquals(47, parsed.results[0].seeds)
        assertEquals(5, parsed.results[0].category_id)
    }

    @Test
    fun `eztv fixture parses documented fields`() {
        val parsed = json.decodeFromString(EztvResponse.serializer(), fixture("eztv_torrents.json"))
        assertEquals(1, parsed.torrents.size)
        assertEquals("Show S01E01", parsed.torrents[0].title)
        assertEquals("1", parsed.torrents[0].season)
    }

    @Test
    fun `apibay fixture parses documented fields`() {
        val parsed = json.decodeFromString(ListSerializer(ApibayTorrent.serializer()), fixture("apibay_q.json"))
        assertEquals(1, parsed.size)
        assertEquals("0123456789abcdef0123456789abcdef01234567", parsed[0].info_hash)
        assertEquals(5, parsed[0].seeders)
    }

    @Test
    fun `yts fixture parses documented fields`() {
        val parsed = json.decodeFromString(YtsResponse.serializer(), fixture("yts_movies.json"))
        val movie = parsed.data?.movies?.first()
        assertEquals("Fixture Movie", movie?.title)
        assertEquals(10, movie?.seeds)
    }

    @Test
    fun `knaben fixture parses documented fields`() {
        val parsed = json.decodeFromString(KnabenResponse.serializer(), fixture("knaben_search.json"))
        assertEquals(1, parsed.hits?.size)
        assertEquals("0123456789abcdef0123456789abcdef01234567", parsed.hits?.first()?.hash)
    }
}