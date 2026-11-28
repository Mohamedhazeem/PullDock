package com.pulldock.data.discovery.history

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.pulldock.domain.discovery.model.SearchCategory
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchHistoryStoreTest {

    private fun store(): SearchHistoryStore {
        val file = File.createTempFile("history", ".preferences_pb")
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + Job()),
        ) { file }
        return SearchHistoryStore(dataStore)
    }

    @Test
    fun `add then observe returns entry`() = runTest {
        val store = store()
        store.add("cats", SearchCategory.OTHER)
        val entries = store.observe().first()
        assertEquals(1, entries.size)
        assertEquals("cats", entries[0].query)
    }

    @Test
    fun `clear removes history`() = runTest {
        val store = store()
        store.add("cats", null)
        store.clear()
        assertTrue(store.observe().first().isEmpty())
    }
}