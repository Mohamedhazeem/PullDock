package com.pulldock.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Immutable snapshot of user-level application settings. */
data class AppSettings(
    val theme: ThemePreference,
    /** Relative subfolder under app-specific external storage; null = app downloads root. */
    val downloadFolder: String?,
    /** Preferred resolution height (360/480/720/1080); null = let the user choose each time. */
    val defaultQualityHeight: Int?,
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

    /** Persists a validated relative download subfolder (null resets to the app root). */
    suspend fun setDownloadFolder(relativePath: String?) {
        dataStore.edit { preferences ->
            if (relativePath.isNullOrBlank()) preferences.remove(KEY_DOWNLOAD_FOLDER)
            else preferences[KEY_DOWNLOAD_FOLDER] = relativePath
        }
    }

    /** Persists the preferred resolution height (null = ask every time). */
    suspend fun setDefaultQuality(height: Int?) {
        dataStore.edit { preferences ->
            if (height == null) preferences.remove(KEY_DEFAULT_QUALITY)
            else preferences[KEY_DEFAULT_QUALITY] = height.toLong()
        }
    }

    private fun toSettings(preferences: Preferences): AppSettings = AppSettings(
        theme = preferences[KEY_THEME]?.let { stored ->
            ThemePreference.entries.firstOrNull { it.name == stored }
        } ?: ThemePreference.SYSTEM,
        downloadFolder = preferences[KEY_DOWNLOAD_FOLDER],
        defaultQualityHeight = preferences[KEY_DEFAULT_QUALITY]?.toInt(),
    )

    private companion object {
        val KEY_THEME = stringPreferencesKey("appearance.theme")
        val KEY_DOWNLOAD_FOLDER = stringPreferencesKey("storage.download_folder")
        val KEY_DEFAULT_QUALITY = longPreferencesKey("media.default_quality_height")
    }
}
