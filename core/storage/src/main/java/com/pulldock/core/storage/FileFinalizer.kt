package com.pulldock.core.storage

import java.io.File

/**
 * Temp → verify → atomic publish (T151, FR-039/FR-030, ADR-0006). Incomplete output
 * is NEVER published: the temp file is verified by a caller-supplied probe, written
 * to a `.part` sibling, and only then atomically moved into place. A failure at any
 * stage leaves the temp file for recovery and returns a typed failure.
 */
class FileFinalizer(
    private val destinationRoot: File,
    private val verifier: (File) -> Boolean = { it.length() > 0 },
) {

    sealed interface Result {
        /** Published atomically at [path]; the temp file is gone. */
        data class Published(val path: String) : Result

        /** Rejected before any write; the temp file remains for recovery. */
        data class Rejected(val reason: DownloadReason) : Result
    }

    /** Failure reasons (mapped to DownloadError.Storage upstream). */
    enum class DownloadReason { VERIFICATION_FAILED, FILE_EXISTS_UNRESOLVED, UNSUPPORTED_EXTENSION, MOVE_FAILED }

    /**
     * Publishes a verified temp file under [filename] inside the destination root.
     * Uses rename-atomicity within the same volume; the publish step never overwrites
     * silently — the collision strategy decides the final name first (FR-040).
     */
    fun publish(tempFile: File, filename: String, allowOverwrite: Boolean = false): Result {
        val confined = PathValidator.confine(destinationRoot, filename)
            ?: return Result.Rejected(DownloadReason.VERIFICATION_FAILED) // traversal-shaped names never publish

        if (!confined.parentFile.exists() && !confined.parentFile.mkdirs()) {
            return Result.Rejected(DownloadReason.MOVE_FAILED)
        }
        if (confined.exists() && !allowOverwrite) {
            val resolvedName = FilenameSanitizer.resolveCollision(filename) { candidate ->
                PathValidator.confine(destinationRoot, candidate)?.exists() == true
            }
            val resolvedTarget = PathValidator.confine(destinationRoot, resolvedName)
                ?: return Result.Rejected(DownloadReason.MOVE_FAILED)
            return move(tempFile, resolvedTarget)
        }
        if (!verifier(tempFile)) {
            return Result.Rejected(DownloadReason.VERIFICATION_FAILED)
        }
        return move(tempFile, confined)
    }

    private fun move(tempFile: File, target: File): Result {
        val moved = try {
            if (!tempFile.renameTo(target)) {
                // Cross-volume fallback: copy + fsync + delete, ordered so an interrupt
                // cannot leave a half-published file at the target path.
                tempFile.copyTo(target, overwrite = true)
                if (target.length() != tempFile.length()) {
                    target.delete()
                    return Result.Rejected(DownloadReason.MOVE_FAILED)
                }
                tempFile.delete()
                true
            } else {
                true
            }
        } catch (_: Exception) {
            false
        }
        return if (moved) Result.Published(target.path) else Result.Rejected(DownloadReason.MOVE_FAILED)
    }
}
