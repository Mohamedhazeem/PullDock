package com.pulldock.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Persisted transfer policy snapshot (FR-028, US7). */
data class PolicySnapshot(
    val maxConcurrent: Int,
    val torrentMaxConcurrent: Int,
    val extractorMaxConcurrent: Int,
    val maxRetries: Int,
    val wifiOnly: Boolean,
    val meteredAllowed: Boolean,
    val batteryThresholdPercent: Int,
)

/**
 * DataStore-backed transfer policy storage (US7). Defaults mirror
 * `DownloadPolicy` (data-model.md §1.4). Invalid persisted values fall back to the
 * defaults instead of crashing at read time; writes are atomic edits.
 */
@Singleton
class NetworkPolicyStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {

    val policy: Flow<PolicySnapshot> = dataStore.data
        .map(::toSnapshot)
        .distinctUntilChanged()

    /** Persists the mutable policy fields atomically. */
    suspend fun update(
        maxConcurrent: Int,
        torrentMaxConcurrent: Int,
        extractorMaxConcurrent: Int,
        maxRetries: Int,
        wifiOnly: Boolean,
        meteredAllowed: Boolean,
        batteryThresholdPercent: Int,
    ): Unit {
        dataStore.edit { preferences ->
            preferences[KEY_MAX_CONCURRENT] = maxConcurrent
            preferences[KEY_TORRENT_MAX_CONCURRENT] = torrentMaxConcurrent
            preferences[KEY_EXTRACTOR_MAX_CONCURRENT] = extractorMaxConcurrent
            preferences[KEY_MAX_RETRIES] = maxRetries
            preferences[KEY_WIFI_ONLY] = wifiOnly
            preferences[KEY_METERED_ALLOWED] = meteredAllowed
            preferences[KEY_BATTERY_THRESHOLD] = batteryThresholdPercent
        }
    }

    private fun toSnapshot(preferences: Preferences): PolicySnapshot = PolicySnapshot(
        maxConcurrent = preferences[KEY_MAX_CONCURRENT] ?: DEFAULT.maxConcurrent,
        torrentMaxConcurrent = preferences[KEY_TORRENT_MAX_CONCURRENT] ?: DEFAULT.torrentMaxConcurrent,
        extractorMaxConcurrent = preferences[KEY_EXTRACTOR_MAX_CONCURRENT] ?: DEFAULT.extractorMaxConcurrent,
        maxRetries = preferences[KEY_MAX_RETRIES] ?: DEFAULT.maxRetries,
        wifiOnly = preferences[KEY_WIFI_ONLY] ?: DEFAULT.wifiOnly,
        meteredAllowed = preferences[KEY_METERED_ALLOWED] ?: DEFAULT.meteredAllowed,
        batteryThresholdPercent = preferences[KEY_BATTERY_THRESHOLD] ?: DEFAULT.batteryThresholdPercent,
    )

    private companion object {
        val DEFAULT = PolicySnapshot(
            maxConcurrent = 3,
            torrentMaxConcurrent = 2,
            extractorMaxConcurrent = 1,
            maxRetries = 3,
            wifiOnly = false,
            meteredAllowed = true,
            batteryThresholdPercent = 15,
        )

        val KEY_MAX_CONCURRENT = intPreferencesKey("policy.max_concurrent")
        val KEY_TORRENT_MAX_CONCURRENT = intPreferencesKey("policy.torrent_max_concurrent")
        val KEY_EXTRACTOR_MAX_CONCURRENT = intPreferencesKey("policy.extractor_max_concurrent")
        val KEY_MAX_RETRIES = intPreferencesKey("policy.max_retries")
        val KEY_WIFI_ONLY = booleanPreferencesKey("policy.wifi_only")
        val KEY_METERED_ALLOWED = booleanPreferencesKey("policy.metered_allowed")
        val KEY_BATTERY_THRESHOLD = intPreferencesKey("policy.battery_threshold")
    }
}
