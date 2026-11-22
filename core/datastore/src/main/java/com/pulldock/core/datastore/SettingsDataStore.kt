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

/** Immutable snapshot of user-level application settings. */
data class AppSettings(
    val theme: ThemePreference,
)

/**
 * Preferences DataStore access for application settings (constitution II: DataStore for
 * preferences). Reads emit the current snapshot and replay on change; writes are
 * atomic. Network/battery policy storage arrives with T105 (`NetworkPolicyStore`) in
 * this module; consent acknowledgement arrives with T041 (`ConsentStore`).
 *
 * The [DataStore] instance is provided by [DataStoreModule]; this class never creates
 * its own scope, so all reads/writes run in the caller's context.
 */
@Singleton
class SettingsDataStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {

    /** Current settings, replayed and de-duplicated on every change. */
    val settings: Flow<AppSettings> = dataStore.data
        .map(::toSettings)
        .distinctUntilChanged()

    /** Persists the appearance preference; safe to call concurrently (atomic edit). */
    suspend fun setTheme(preference: ThemePreference): Unit {
        dataStore.edit { preferences ->
            preferences[KEY_THEME] = preference.name
        }
    }

    private fun toSettings(preferences: Preferences): AppSettings = AppSettings(
        theme = preferences[KEY_THEME]?.let { stored ->
            ThemePreference.entries.firstOrNull { it.name == stored }
        } ?: ThemePreference.SYSTEM,
    )

    private companion object {
        val KEY_THEME = stringPreferencesKey("appearance.theme")
    }
}
