package com.pulldock.data.discovery.config

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.pulldock.domain.discovery.ProviderToggleRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Persists non-secret provider toggles (TPB default-off). No API keys stored. */
@Singleton
class ProviderToggleStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : ProviderToggleRepository {

    override val tpbEnabled: Flow<Boolean> = dataStore.data.map { it[KEY_TPB] ?: false }

    override suspend fun setTpbEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_TPB] = enabled }
    }

    private companion object {
        val KEY_TPB = booleanPreferencesKey("discovery.tpb_enabled")
    }
}