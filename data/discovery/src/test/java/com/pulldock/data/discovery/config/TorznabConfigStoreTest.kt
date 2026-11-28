package com.pulldock.data.discovery.config

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.pulldock.domain.discovery.model.ProviderConfiguration
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TorznabConfigStoreTest {

    private fun store(): TorznabConfigStore {
        val file = File.createTempFile("torznab", ".preferences_pb")
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + Job()),
        ) { file }
        return TorznabConfigStore(dataStore)
    }

    @Test
    fun `config round-trips but api key is never persisted`() = runTest {
        val store = store()
        store.replace(
            listOf(
                ProviderConfiguration(
                    providerId = "t1",
                    displayName = "Indexer",
                    endpoint = "https://example.test",
                    enabled = true,
                    apiKey = "SECRET",
                ),
            ),
        )
        val loaded = store.current()
        assertEquals(1, loaded.size)
        assertEquals("https://example.test", loaded[0].endpoint)
        assertNull(loaded[0].apiKey)
    }
}