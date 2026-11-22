package com.pulldock.core.storage

/**
 * Filename sanitization and collision strategy (T152, FR-040, ADR-0006).
 *
 * - Untrusted names (torrent metadata, provider titles) are stripped to a safe,
 *   cross-platform basename: path separators, traversal fragments, control chars,
 *   reserved Windows names, and reserved characters are removed/replaced.
 * - Unicode is normalized (NFC) and NUL/RTL-bidi controls are dropped so a collision
 *   or spoofing via visually identical names cannot publish unexpected files.
 * - Collision strategy is documented and deterministic: `name (2).ext`, `name (3).ext`, …
 */
object FilenameSanitizer {

    private val controlChars = Regex("[\\p{Cntrl}\\u202A-\\u202E\\u2066-\\u2069\\uFEFF]")
    private val unsafeChars = Regex("[/\\\\:*?\"<>|]")
    private val trailingDots = Regex("\\.{1,10}$")
    private val leadingDots = Regex("^\\.{2,}")

    /** Windows reserved device names (case-insensitive, with or without extension). */
    private val reservedNames = setOf(
        "CON", "PRN", "AUX", "NUL",
        "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
        "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9",
    )

    const val MAX_LENGTH = 200

    /** Sanitizes an untrusted filename to a safe basename (no directories). */
    fun sanitize(raw: String): String {
        val normalized = java.text.Normalizer.normalize(raw, java.text.Normalizer.Form.NFC)
        var name = normalized
            .replace(controlChars, "")
            // Traversal fragments first: "../" must disappear before separators
            // become underscores, so the result can never hide a dot-sequence.
            .replace("..", "")
            .replace(unsafeChars, "_")
            .replace(leadingDots, "_")
            .replace(trailingDots, "")
            .trim()
            .take(MAX_LENGTH)

        if (name.isBlank()) name = "download"

        // Reserved device names: rename rather than reject (predictable, documented).
        val stem = name.substringBefore('.', missingDelimiterValue = name)
        if (stem.uppercase() in reservedNames) {
            name = "_$name"
        }
        return name
    }

    /** Derives the output filename, preserving a safe extension from the source name. */
    fun outputFilename(sourceName: String, taskType: String): String {
        val sanitized = sanitize(sourceName)
        val ext = sanitized.substringAfterLast('.', missingDelimiterValue = "")
        val stem = if (ext.isNotEmpty() && ext.length <= 10) sanitized.removeSuffix(".$ext") else sanitized
        val safeExt = ext.lowercase().ifBlank { defaultExtension(taskType) }
        return "${stem}.${safeExt}"
    }

    /**
     * Applies the collision strategy: `name.ext` → `name (2).ext` → `name (3).ext`.
     * `exists` is the caller's existence check (filesystem or MediaStore query).
     */
    fun resolveCollision(baseName: String, exists: (String) -> Boolean): String {
        if (!exists(baseName)) return baseName
        val dot = baseName.lastIndexOf('.')
        val stem = if (dot > 0) baseName.substring(0, dot) else baseName
        val ext = if (dot > 0) baseName.substring(dot) else ""
        var counter = 2
        while (true) {
            val candidate = "$stem ($counter)$ext"
            if (!exists(candidate)) return candidate
            counter += 1
        }
    }

    private fun defaultExtension(taskType: String): String = when (taskType) {
        "TORRENT" -> "bin"
        else -> "mp4"
    }
}
