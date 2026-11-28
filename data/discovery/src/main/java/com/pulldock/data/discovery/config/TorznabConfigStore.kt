package com.pulldock.data.discovery.config

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.pulldock.domain.discovery.TorznabConfigRepository
import com.pulldock.domain.discovery.model.ProviderConfiguration
import com.pulldock.domain.discovery.model.SearchCategory
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Persists user-configured Torznab providers (FR-029..FR-034). API keys are NEVER persisted
 * (FR-042); only non-secret fields are stored. Endpoints are validated by the UI before save.
 */
@Singleton
class TorznabConfigStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : TorznabConfigRepository {

    @Serializable
    data class Dto(
        val providerId: String,
        val displayName: String,
        val endpoint: String,
        val enabled: Boolean,
        val lastCapabilityCheck: Long? = null,
        val categories: Map<String, String> = emptyMap(),
    )

    override fun observe(): Flow<List<ProviderConfiguration>> = dataStore.data.map { prefs ->
        decode(prefs[KEY_CONFIG]).map { it.toDomain() }.ifEmpty { DEFAULT_CONFIGS }
    }

    override suspend fun current(): List<ProviderConfiguration> = observe().first()

    override suspend fun replace(configs: List<ProviderConfiguration>) {
        val dtos = configs.map { it.toDto() }
        dataStore.edit { it[KEY_CONFIG] = Json.encodeToString(ListSerializer(Dto.serializer()), dtos) }
    }

    private fun decode(raw: String?): List<Dto> = raw?.let {
        runCatching { Json.decodeFromString(ListSerializer(Dto.serializer()), it) }.getOrDefault(emptyList())
    } ?: emptyList()

    private fun Dto.toDomain(): ProviderConfiguration = ProviderConfiguration(
        providerId = providerId,
        displayName = displayName,
        endpoint = endpoint,
        enabled = enabled,
        lastCapabilityCheck = lastCapabilityCheck?.let(java.time.Instant::ofEpochMilli),
        supportedCategories = categories.mapNotNull { (k, v) ->
            runCatching { SearchCategory.valueOf(k) to v }.getOrNull()
        }.toMap(),
        apiKey = null,
    )

    private fun ProviderConfiguration.toDto(): Dto = Dto(
        providerId = providerId,
        displayName = displayName,
        endpoint = endpoint,
        enabled = enabled,
        lastCapabilityCheck = lastCapabilityCheck?.toEpochMilli(),
        categories = supportedCategories.mapKeys { it.key.name },
    )

    private companion object {
        val KEY_CONFIG = stringPreferencesKey("discovery.torznab_config")

        /**
         * Predefined public Torznab endpoints so the feature works without manual setup.
         * Users can remove or replace them in Discovery settings. If an endpoint is offline
         * the provider reports a typed failure and other providers still return results.
         */
        val DEFAULT_CONFIGS = listOf(
            ProviderConfiguration(
                providerId = "torznab-default-bitsearch",
                displayName = "BitSearch (Torznab)",
                endpoint = "https://bitsearch.eu/api/v1",
                enabled = true,
            ),
        )
    }
}