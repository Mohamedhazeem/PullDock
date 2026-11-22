package com.pulldock.core.storage

import com.pulldock.core.model.DownloadError
import com.pulldock.core.model.StorageKind
import java.io.File

/**
 * A request to resolve the destination a download output is published to.
 *
 * @param kind the storage location kind (spec FR-037).
 * @param uri SAF tree or document URI; required when [kind] is [StorageKind.SAF],
 *   otherwise `null` (the platform picks the app-specific/MediaStore location).
 * @param displayName advisory destination label, e.g. a SAF tree display name.
 */
data class DestinationRequest(
    val kind: StorageKind,
    val uri: String?,
    val displayName: String?,
)

/**
 * A concrete, writable destination produced by [StorageRepository.resolveDestination].
 * All URIs here are content or file URIs the app already holds permission for; scoped
 * storage rules apply and broad external storage is never used (FR-036).
 */
data class ResolvedDestination(
    val kind: StorageKind,
    val uri: String,
    val displayName: String,
    /** Free bytes if the volume exposes them; `null` when unknown. */
    val freeSpaceBytes: Long?,
)

/**
 * Outcome of publishing a verified temp file to its destination. Incomplete output is
 * never published (FR-039): failures keep the temp file in place for recovery.
 */
sealed interface FinalizeResult {

    /** The file was verified and atomically published at [uri]. */
    data class Published(
        val uri: String,
        /** Filesystem path when the destination exposes one; `null` for SAF/MediaStore. */
        val path: String?,
    ) : FinalizeResult

    /** Publication failed; the temp file is preserved and the error typed. */
    data class Failed(val error: DownloadError.Storage) : FinalizeResult
}

/**
 * Platform storage contract: destination resolution, temporary files, verification-time
 * finalization, deletion, and space checks.
 *
 * This is the platform-facing contract in `core:storage`; the data layer maps it to the
 * domain model's `StorageDestination` (defined in `:domain:download`, T020) so `core:*`
 * never depends on `:domain:*` (ADR-0008). The SAF/MediaStore implementation arrives
 * with T150; `FileFinalizer` (T151), filename sanitization (T152), path validation
 * (T153), and MediaStore write handling (T154) complete it.
 *
 * All operations are cancellable and run off the main thread in implementations.
 */
interface StorageRepository {

    /** Resolves [request] into a concrete writable destination. */
    suspend fun resolveDestination(request: DestinationRequest): ResolvedDestination

    /**
     * Creates an app-specific temporary file for the task's partial output
     * (`cache/partial/<taskId>`). The caller owns the returned [File] and must not
     * publish it directly; only [finalize] publishes output.
     */
    suspend fun createTempFile(taskId: String): File

    /**
     * Publishes a verified [tempFile] to [destination] under [filename]. Implementations
     * MUST fail with [DownloadError.Storage] rather than overwriting silently or
     * publishing partial output; the collision strategy (`name (n).ext`) is decided by
     * T152.
     */
    suspend fun finalize(
        tempFile: File,
        destination: ResolvedDestination,
        filename: String,
    ): FinalizeResult

    /** Deletes the file at [uri]; `true` when deletion succeeded or the file was gone. */
    suspend fun deleteFile(uri: String): Boolean

    /** Free bytes on the destination's volume; `null` when the volume cannot answer. */
    suspend fun freeSpace(destination: ResolvedDestination): Long?
}
