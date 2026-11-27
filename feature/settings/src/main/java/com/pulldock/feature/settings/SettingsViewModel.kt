package com.pulldock.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pulldock.domain.download.repository.AppearanceTheme
import com.pulldock.core.model.DownloadType
import com.pulldock.core.model.ProviderId
import com.pulldock.domain.download.model.DownloadPolicy
import com.pulldock.domain.download.model.UsageDaily
import com.pulldock.domain.download.model.UsageRange
import com.pulldock.domain.download.repository.NetworkPolicyRepository
import com.pulldock.domain.download.repository.StoragePreferencesRepository
import com.pulldock.domain.download.repository.UsageRepository
import com.pulldock.domain.extractor.model.ExtractorRuntimeInfo
import com.pulldock.domain.extractor.model.ProviderHealth
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Settings UI state (T047/T091): only controls with real backends appear. */
data class SettingsUiState(
    val policy: DownloadPolicy? = null,
    val theme: AppearanceTheme = AppearanceTheme.SYSTEM,
    val savingPolicy: Boolean = false,
    /** Relative subfolder under app-specific storage; null = app downloads root. */
    val downloadFolder: String? = null,
    /** Preferred resolution height; null = ask every time. */
    val defaultQualityHeight: Int? = null,
    /** Per-provider extractor health (US8); keys follow ProviderId order. */
    val providers: Map<ProviderId, ProviderHealth> = emptyMap(),
    /** Extractor runtime info (version / verification) shown under each provider. */
    val runtime: ExtractorRuntimeInfo? = null,
    /** Selected usage range and its daily buckets (oldest first, zero-filled). */
    val usageRange: UsageRange = UsageRange.WEEK,
    val usage: List<UsageDaily> = emptyList(),
    val usageLoading: Boolean = true,
)

/** One-shot settings events. */
sealed interface SettingsEvent {
    data object PolicySaved : SettingsEvent
}

/**
 * Settings ViewModel (T047, US7): network/battery policy (persisted, enforced by the
 * execution slice) and appearance. Provider status and extractor updates arrive with
 * Slice 4 / the lifecycle group (T091/T127); legal content lives in its own screen.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val policyRepository: NetworkPolicyRepository,
    private val appearanceRepository: com.pulldock.domain.download.repository.AppearanceRepository,
    private val providerHealth: com.pulldock.domain.extractor.usecase.ObserveProviderHealth,
    private val storagePreferences: StoragePreferencesRepository,
    private val mediaPreferences: com.pulldock.domain.download.repository.MediaPreferencesRepository,
    private val usageRepository: UsageRepository,
) : ViewModel() {

    private val saving = MutableStateFlow(false)
    private val usageRange = MutableStateFlow(UsageRange.WEEK)

    // Provider health/runtime are effectively static for the process; snapshot them once
    // instead of recomputing (and re-touching the extractor) on every state emission.
    private val providerSnapshot by lazy { providerHealth.all() to providerHealth.runtimeInfo() }

    private val baseState = combine(
        policyRepository.observe(),
        appearanceRepository.observeTheme(),
        saving,
        storagePreferences.observeDownloadFolder(),
        mediaPreferences.observeDefaultQuality(),
    ) { policy, theme, savingPolicy, folder, quality ->
        SettingsUiState(
            policy = policy,
            theme = theme,
            savingPolicy = savingPolicy,
            downloadFolder = folder,
            defaultQualityHeight = quality,
            providers = providerSnapshot.first,
            runtime = providerSnapshot.second,
        )
    }

    val uiState: StateFlow<SettingsUiState> = baseState
        .combine(usageRange.flatMapLatest { range ->
            usageRepository.observe(range.days).map { days -> range to days }
        }) { base, (range, days) ->
            base.copy(usageRange = range, usage = days, usageLoading = false)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    private val _events = MutableSharedFlow<SettingsEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<SettingsEvent> = _events.asSharedFlow()

    /** Persists Wi-Fi-only preference (US7 scenario 1). */
    fun setWifiOnly(enabled: Boolean) = updatePolicy { it.copy(wifiOnly = enabled) }

    /** Persists metered-network allowance. */
    fun setMeteredAllowed(enabled: Boolean) = updatePolicy { it.copy(meteredAllowed = enabled) }

    /** Persists the battery threshold; 0 disables the limit. */
    fun setBatteryThreshold(percent: Int) {
        val clamped = percent.coerceIn(0, 100)
        updatePolicy { it.copy(batteryThresholdPercent = clamped) }
    }

    /** Persists concurrency limits (per engine and global). */
    fun setConcurrency(global: Int, torrent: Int, extractor: Int) = updatePolicy {
        it.copy(
            maxConcurrent = global.coerceIn(1, 10),
            torrentMaxConcurrent = torrent.coerceIn(1, 10),
            extractorMaxConcurrent = extractor.coerceIn(1, 10),
        )
    }

    /** Persists the appearance preference (wired to MainActivity theme, T015). */
    fun setTheme(preference: AppearanceTheme) {
        viewModelScope.launch { appearanceRepository.setTheme(preference) }
    }

    /** Persists a validated relative download subfolder (FR-038). */
    fun setDownloadFolder(relativePath: String?) {
        viewModelScope.launch { storagePreferences.setDownloadFolder(relativePath) }
    }

    /** Persists the preferred resolution used for automatic format fallback. */
    fun setDefaultQuality(height: Int?) {
        viewModelScope.launch { mediaPreferences.setDefaultQuality(height) }
    }

    /** Selects the usage chart range (Day / 3 days / 7 days / Month). */
    fun setUsageRange(range: UsageRange) {
        usageRange.value = range
    }

    private fun updatePolicy(transform: (DownloadPolicy) -> DownloadPolicy) {
        val current = uiState.value.policy ?: return
        if (saving.value) return
        saving.update { true }
        viewModelScope.launch {
            try {
                policyRepository.update(transform(current))
                _events.tryEmit(SettingsEvent.PolicySaved)
            } finally {
                saving.update { false }
            }
        }
    }

    /** Exposed for tests and future per-engine display formatting. */
    fun engineLabel(type: DownloadType): String = type.name.lowercase().replaceFirstChar { it.uppercase() }
}
