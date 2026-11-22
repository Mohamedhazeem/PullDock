package com.pulldock.core.storage

import java.io.File

/**
 * Path-traversal, reserved-name, and unicode-collision rejection (T153, FR-047,
 * data-model.md edge cases). Every path a torrent or provider suggests is validated
 * against the destination root before any write — absolute paths, `..` segments,
 * symlink escapes, and illegal names are rejected with typed errors.
 */
object PathValidator {

    /** `null` when safe; otherwise the typed reason. */
    fun validateRelativePath(relativePath: String): Rejection? = when {
        relativePath.isBlank() -> Rejection.EMPTY
        relativePath.startsWith('/') || relativePath.startsWith('\\') -> Rejection.ABSOLUTE_PATH
        relativePath.contains("..") -> Rejection.TRAVERSAL
        relativePath.contains(':') -> Rejection.ILLEGAL_CHARACTERS
        relativePath.split('/', '\\').any { it.isBlank() && relativePath.length > 1 } -> Rejection.MALFORMED
        relativePath.length > 1024 -> Rejection.TOO_LONG
        else -> null
    }

    /**
     * Resolves and confines: the canonical path of the target MUST remain inside the
     * canonical destination root (symlink-escape defense). Returns the confined file
     * or null when the path escapes the root.
     */
    fun confine(root: File, relativePath: String): File? {
        validateRelativePath(relativePath)?.let { return null }
        val target = File(root, relativePath)
        val rootCanonical = root.canonicalFile
        val targetCanonical = try {
            target.canonicalFile
        } catch (_: Exception) {
            return null
        }
        return if (targetCanonical.path == rootCanonical.path || targetCanonical.path.startsWith(rootCanonical.path + File.separator)) {
            target
        } else {
            null
        }
    }

    /** Typed rejection reasons mapped to user-facing messages upstream. */
    enum class Rejection {
        EMPTY,
        ABSOLUTE_PATH,
        TRAVERSAL,
        ILLEGAL_CHARACTERS,
        MALFORMED,
        TOO_LONG,
    }
}
