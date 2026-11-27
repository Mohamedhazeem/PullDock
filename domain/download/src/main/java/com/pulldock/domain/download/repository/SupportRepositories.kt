package com.pulldock.domain.download.repository

import com.pulldock.core.model.DownloadType
import com.pulldock.core.model.ProviderId
import com.pulldock.domain.extractor.repository.UpdateManifestResult
import kotlinx.coroutines.flow.Flow

/** Query for the completed-media library (FR-042). */
data class LibraryQuery(
    val section: LibrarySection = LibrarySection.ALL,
    val searchText: String? = null,
    val sortBy: LibrarySort = LibrarySort.NEWEST_FIRST,
)

/** Library sections. */
enum class LibrarySection { ALL, VIDEO, AUDIO, OTHER }

/** Library sort orders. */
enum class LibrarySort { NEWEST_FIRST, OLDEST_FIRST, TITLE_ASC, TITLE_DESC, SIZE_DESC }

/** A completed media record (data-model.md Key Entities). */
data class LibraryItem(
    val id: String,
    val type: DownloadType,
    val title: String,
    val fileUri: String,
    val sizeBytes: Long?,
    /** Media resolution (e.g. "720p"); null when unknown. */
    val resolution: String? = null,
    val durationSeconds: Long?,
    val source: String?,
    val completedAt: java.time.Instant,
    val thumbnailUri: String?,
    /** Set when reconciliation finds the file missing (FR-043); entry is not playable. */
    val fileAvailable: Boolean,
    /** Deterministic source key; used to prevent downloading the same content twice. */
    val sourceHash: String? = null,
    /** Owning group id (playlist / multi-file torrent); null for standalone items. */
    val groupId: String? = null,
)

/** Result of reconciling library records with actual files (data-model.md §4.6). */
data class ReconcileReport(val missing: Int, val orphaned: Int, val ok: Int)

/** Completed-media library with file reconciliation (FR-042/FR-043). */
interface MediaLibraryRepository {
    fun observe(query: LibraryQuery): Flow<List<LibraryItem>>
    suspend fun get(itemId: String): LibraryItem?
    /** An available library entry for a previously downloaded source (dedupe). */
    suspend fun findBySourceHash(hash: String): LibraryItem?
    suspend fun reconcile(): ReconcileReport
    suspend fun delete(itemId: String, deleteFile: Boolean): Unit

    /** Records a derived audio-only item (e.g. extracted from a completed video). */
    suspend fun addAudioItem(
        fileUri: String,
        title: String,
        sizeBytes: Long?,
        groupId: String?,
        sourceHash: String?,
        source: String?,
    ): Unit
}

/** User-selected destination (FR-038): a validated relative subfolder of app-specific storage. */
interface StoragePreferencesRepository {
    fun observeDownloadFolder(): Flow<String?>
    suspend fun currentDownloadFolder(): String?
    suspend fun setDownloadFolder(relativePath: String?)
}

/** Resolves the directory downloads/extractions are written to (shared /PullDock or app storage). */
interface MediaStorageDirectory {
    suspend fun downloadsDir(): String?
}

/** Preferred media quality (FR-016/US3): a height used to auto-select the closest format. */
interface MediaPreferencesRepository {
    fun observeDefaultQuality(): Flow<Int?>
    suspend fun currentDefaultQuality(): Int?
    suspend fun setDefaultQuality(height: Int?)
}

/**
 * Global transfer policy read/write (FR-028, US7). Persisted in DataStore via
 * NetworkPolicyStore; the queue coordinator observes it for scheduling decisions.
 */
interface NetworkPolicyRepository {
    fun observe(): Flow<com.pulldock.domain.download.model.DownloadPolicy>
    suspend fun current(): com.pulldock.domain.download.model.DownloadPolicy
    suspend fun update(policy: com.pulldock.domain.download.model.DownloadPolicy): Unit
}

/**
 * Consent acknowledgement gate (US1). Added beyond the base contract list in
 * contracts/repositories.md because the spec requires persisted, versioned consent;
 * stored via ConsentStore (T041) and enforced by the navigation gate (T049).
 */
interface ConsentRepository {
    fun observe(): Flow<ConsentState>
    suspend fun current(): ConsentState
    suspend fun acknowledge(version: String): Unit
}

/** Consent state: acknowledged version and whether re-prompt is required. */
data class ConsentState(
    val acknowledgedVersion: String?,
    val requiredVersion: String,
) {
    /** `true` when the current legal version has been acknowledged (FR + US1). */
    val isAcknowledged: Boolean get() = acknowledgedVersion == requiredVersion
}
