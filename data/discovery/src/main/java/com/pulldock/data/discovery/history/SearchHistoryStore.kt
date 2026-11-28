package com.pulldock.data.discovery.history

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.pulldock.domain.discovery.SearchHistoryRepository
import com.pulldock.domain.discovery.model.SearchCategory
import com.pulldock.domain.discovery.model.SearchHistoryEntry
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Local, user-deletable search history backed by the shared preferences DataStore (FR-061).
 * History is never transmitted off-device. API keys are never stored here.
 */
@Singleton
class SearchHistoryStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SearchHistoryRepository {

    override val enabled: Flow<Boolean> = dataStore.data.map { it[KEY_ENABLED] ?: true }

    override fun observe(): Flow<List<SearchHistoryEntry>> = dataStore.data.map { prefs ->
        prefs[KEY_HISTORY].orEmpty()
            .mapNotNull(::decode)
            .sortedByDescending { it.usedAt }
    }

    override suspend fun add(query: String, category: SearchCategory?) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        dataStore.edit { prefs ->
            val current = prefs[KEY_HISTORY].orEmpty().toMutableSet()
            current.add(encode(trimmed, category, Instant.now()))
            prefs[KEY_HISTORY] = current.toList().takeLast(MAX_ENTRIES).toSet()
        }
    }

    override suspend fun delete(entry: SearchHistoryEntry) {
        dataStore.edit { prefs ->
            prefs[KEY_HISTORY] = prefs[KEY_HISTORY].orEmpty().filterNot { it == encode(entry) }.toSet()
        }
    }

    override suspend fun clear() {
        dataStore.edit { it.remove(KEY_HISTORY) }
    }

    override suspend fun setEnabled(value: Boolean) {
        dataStore.edit { it[KEY_ENABLED] = value }
    }

    private fun encode(query: String, category: SearchCategory?, usedAt: Instant): String =
        "$query|${category?.name ?: ""}|${usedAt.toEpochMilli()}"

    private fun encode(entry: SearchHistoryEntry): String =
        "${entry.query}|${entry.category?.name ?: ""}|${entry.usedAt.toEpochMilli()}"

    private fun decode(raw: String): SearchHistoryEntry? {
        val parts = raw.split("|", limit = 3)
        if (parts.size < 3) return null
        val category = parts[1].takeIf { it.isNotEmpty() }?.let { runCatching { SearchCategory.valueOf(it) }.getOrNull() }
        val at = parts[2].toLongOrNull() ?: return null
        return SearchHistoryEntry(parts[0], category, Instant.ofEpochMilli(at))
    }

    private companion object {
        val KEY_HISTORY = stringSetPreferencesKey("discovery.history")
        val KEY_ENABLED = booleanPreferencesKey("discovery.history_enabled")
        const val MAX_ENTRIES = 20
    }
}