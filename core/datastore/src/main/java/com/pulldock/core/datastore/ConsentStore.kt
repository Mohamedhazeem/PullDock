package com.pulldock.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Versioned consent acknowledgement storage (US1, FR-013). The acknowledged version is
 * compared against the bundled legal text version: a version bump forces re-consent
 * (US1 scenario 3). Local-only; nothing here ever leaves the device (FR-050).
 */
@Singleton
class ConsentStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {

    /** Acknowledged legal version, or null on a fresh install. */
    val acknowledgedVersion: Flow<String?> = dataStore.data
        .map { it[KEY_ACKNOWLEDGED_VERSION] }
        .distinctUntilChanged()

    /** Persists the acknowledgement; the version must match the current legal text. */
    suspend fun acknowledge(version: String): Unit {
        dataStore.edit { preferences ->
            preferences[KEY_ACKNOWLEDGED_VERSION] = version
        }
    }

    private companion object {
        val KEY_ACKNOWLEDGED_VERSION = stringPreferencesKey("consent.acknowledged_version")
    }
}
