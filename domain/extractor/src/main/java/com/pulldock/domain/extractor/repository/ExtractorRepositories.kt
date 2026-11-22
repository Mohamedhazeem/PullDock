package com.pulldock.domain.extractor.repository

import com.pulldock.core.model.DownloadError
import com.pulldock.core.model.ProviderId
import com.pulldock.domain.extractor.model.ExtractorRuntimeInfo
import com.pulldock.domain.extractor.model.ProviderCompatibilityRecord
import com.pulldock.domain.extractor.model.ProviderHealth
import com.pulldock.domain.extractor.model.ProviderHealthState
import com.pulldock.domain.extractor.model.UpdateManifest
import kotlinx.coroutines.flow.Flow

/**
 * Extractor runtime inventory + validation state (FR-053/FR-054). Task T024 lists this
 * contract under domain:download; it is implemented here in domain:extractor because it
 * speaks extractor types, and domain:download depends on domain:extractor.
 */
interface ExtractorRuntimeRepository {
    fun observe(): Flow<ExtractorRuntimeInfo>
    /** Re-validates the bundled runtime; returns the refreshed inventory. */
    suspend fun validate(): ExtractorRuntimeInfo
}

/** Provider status and compatibility records (FR-061/FR-062/FR-063). */
interface ProviderCompatibilityRepository {
    fun observe(): Flow<Map<ProviderId, ProviderHealth>>
    suspend fun record(record: ProviderCompatibilityRecord): Unit

    /** Marks one provider's health without touching others (isolation, SC-007). */
    suspend fun mark(providerId: ProviderId, state: ProviderHealthState): Unit
}

/** Outcome of the non-blocking signed manifest check (FR-056). */
sealed interface UpdateManifestResult {
    /** Fresh, verified manifest from the network. */
    data class Success(val manifest: UpdateManifest) : UpdateManifestResult

    /** Network unavailable/failed; the cached last-known-good manifest, if any. */
    data class Cached(val manifest: UpdateManifest) : UpdateManifestResult

    /** No usable manifest: typed reason (never crashes, FR-057/FR-060). */
    data class Unavailable(val reason: DownloadError) : UpdateManifestResult
}

/** Signed metadata manifest fetch/verify/cache (FR-056–FR-060). */
interface UpdateManifestRepository {
    /** Non-blocking; returns the cached last-known-good on failure. */
    suspend fun fetch(): UpdateManifestResult
    fun cached(): UpdateManifest?
}
