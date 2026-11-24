package com.pulldock.feature.player

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Throttled playback-position persistence (T158, US5 acceptance 2). */
@Singleton
class PlaybackPositionStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    fun position(itemId: String): Flow<Long> =
        dataStore.data.map { it[key(itemId)] ?: 0L }

    suspend fun save(itemId: String, positionMs: Long) {
        dataStore.edit { it[key(itemId)] = positionMs.coerceAtLeast(0L) }
    }

    suspend fun clear(itemId: String) {
        dataStore.edit { it.remove(key(itemId)) }
    }

    private fun key(itemId: String) = longPreferencesKey("player.position.$itemId")
}
