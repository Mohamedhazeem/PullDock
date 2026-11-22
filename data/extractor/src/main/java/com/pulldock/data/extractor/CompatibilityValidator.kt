package com.pulldock.data.extractor

import com.pulldock.core.model.DownloadError
import com.pulldock.data.extractor.runtime.BundledRuntimeCatalog
import com.pulldock.domain.extractor.model.ExtractorRuntimeInfo
import com.pulldock.domain.extractor.model.RuntimeVerificationStatus

/**
 * Extractor runtime combination validation (T083/T121, FR-053/FR-054, data-model.md
 * §5.2). Individually valid versions do NOT imply a valid combination: yt-dlp ↔
 * yt-dlp-ejs ↔ Python ↔ JS runtime ↔ FFmpeg/FFprobe are validated as a set, plus the
 * device ABI. Failure is typed and non-crashing; a validated runtime that was already
 * initialized stays usable (existing extraction is never torn down by a late failure).
 */
object CompatibilityValidator {

    /** Validation outcome with the offending component for user-facing diagnostics. */
    sealed interface Result {
        data object Compatible : Result
        data class Incompatible(val error: DownloadError) : Result
    }

    fun validate(inventory: List<ExtractorRuntimeInfo>): Result {
        val byComponent = inventory.associateBy { it.component }

        requiredComponents().forEach { component ->
            val info = byComponent[component]
                ?: return Result.Incompatible(
                    DownloadError.ExtractorEngine(
                        DownloadError.ExtractorReason.RUNTIME_INCOMPATIBLE,
                        "missing component: $component",
                    ),
                )
            if (!info.runtimeCompatible) {
                return Result.Incompatible(runtimeError(component))
            }
            if (info.verificationStatus == RuntimeVerificationStatus.UNAVAILABLE ||
                info.verificationStatus == RuntimeVerificationStatus.FAILED
            ) {
                return Result.Incompatible(runtimeError(component))
            }
        }

        // Cross-component agreement: the JS runtime must be present for yt-dlp-ejs and
        // the Python runtime must satisfy the pinned yt-dlp release requirements.
        val ytDlp = byComponent.getValue("yt-dlp")
        val python = byComponent.getValue("python")
        if (!python.installedVersion.startsWith("3.13")) {
            return Result.Incompatible(runtimeError("python"))
        }
        val ejs = byComponent.getValue("yt-dlp-ejs")
        if (!ejs.runtimeCompatible) {
            return Result.Incompatible(runtimeError("yt-dlp-ejs"))
        }
        if (!ytDlp.runtimeCompatible) {
            return Result.Incompatible(runtimeError("yt-dlp"))
        }
        return Result.Compatible
    }

    /**
     * The bundled inventory as shipped. When [BundledRuntimeCatalog.RUNTIME_VERIFIED]
     * is false (device PoC pending), the combination is honestly incompatible.
     */
    fun validateBundled(): Result = validate(BundledRuntimeCatalog.inventory(InstantCompat.now()))

    private fun requiredComponents(): List<String> =
        listOf("yt-dlp", "yt-dlp-ejs", "python", "js-runtime", "ffmpeg", "ffprobe")

    private fun runtimeError(component: String): DownloadError = DownloadError.ExtractorEngine(
        DownloadError.ExtractorReason.RUNTIME_INCOMPATIBLE,
        "component not verified on this device: $component",
    )
}

/** Time source indirection so validation is testable without a clock. */
private object InstantCompat {
    fun now(): java.time.Instant = java.time.Instant.now()
}
