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
    val durationSeconds: Long?,
    val source: String?,
    val completedAt: java.time.Instant,
    val thumbnailUri: String?,
    /** Set when reconciliation finds the file missing (FR-043); entry is not playable. */
    val fileAvailable: Boolean,
)

/** Result of reconciling library records with actual files (data-model.md §4.6). */
data class ReconcileReport(val missing: Int, val orphaned: Int, val ok: Int)

/** Completed-media library with file reconciliation (FR-042/FR-043). */
interface MediaLibraryRepository {
    fun observe(query: LibraryQuery): Flow<List<LibraryItem>>
    suspend fun get(itemId: String): LibraryItem?
    suspend fun recordFromTask(taskId: String): LibraryItem?
    suspend fun reconcile(): ReconcileReport
    suspend fun delete(itemId: String, deleteFile: Boolean): Unit
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
