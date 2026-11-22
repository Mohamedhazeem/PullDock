package com.pulldock.domain.extractor.model

import com.pulldock.core.model.ProviderId

import java.time.Instant

/** Verification status of an extractor runtime component (data-model.md §5.1). */
enum class RuntimeVerificationStatus { VERIFIED, PENDING, FAILED, UNAVAILABLE }

/**
 * One immutable extractor-runtime inventory entry (FR-053): component, pinned version,
 * required compatibility range, runtime compatibility, channel, verification status and
 * known limitations. The inventory is bundled and immutable for the app release; it is
 * never downloaded or swapped at runtime (FR-058/ADR-0009).
 */
data class ExtractorRuntimeInfo(
    val component: String,
    val installedVersion: String,
    val requiredRange: String,
    val runtimeCompatible: Boolean,
    val channel: String,
    val verificationStatus: RuntimeVerificationStatus,
    val knownLimitations: List<String>,
    val checkedAt: Instant,
    /** Stable id used to link tasks to the inventory snapshot that resolved them. */
    val inventoryId: String,
)

/** Verification result of one provider compatibility matrix row (data-model.md §5.3). */
enum class CompatibilityResult { PASS, FAIL, PARTIAL, UNVERIFIED }

/**
 * Provider compatibility matrix record (FR-063): what was verified, when, how, with
 * which versions, and under which authorized policy basis. Uses authorized fixtures —
 * never arbitrary live content.
 */
data class ProviderCompatibilityRecord(
    val providerId: ProviderId,
    val urlPatterns: List<String>,
    val capabilities: List<String>,
    val installedExtractorVersion: String,
    val requiredRuntimeVersions: String,
    val lastVerifiedAt: Instant,
    val verificationMethod: String,
    val result: CompatibilityResult,
    val knownLimitations: List<String>,
    /** Authorized basis for this provider integration (policy assessment). */
    val policyAssessment: String,
)

/**
 * Provider health states (FR-061). Network errors are never confused with staleness:
 * `VERSION_CHECK_UNAVAILABLE`/`PROVIDER_TEMPORARILY_UNAVAILABLE` cover "unknown right
 * now", while `UPDATE_*`/`PLATFORM_CHANGE_SUSPECTED` assert extractor condition.
 */
enum class ProviderHealthState {
    CURRENT,
    UPDATE_RECOMMENDED,
    UPDATE_REQUIRED,
    PLATFORM_CHANGE_SUSPECTED,
    PROVIDER_TEMPORARILY_UNAVAILABLE,
    PROVIDER_UNSUPPORTED,
    AUTHORIZATION_REQUIRED,
    VERSION_CHECK_UNAVAILABLE,
    RUNTIME_INCOMPATIBLE,
}

/** Per-provider health snapshot exposed to Settings and error screens (US8). */
data class ProviderHealth(
    val providerId: ProviderId,
    val state: ProviderHealthState,
    val installedExtractorVersion: String?,
    val lastVerifiedAt: Instant?,
    /** Message code mapped to a user string; never raw provider output (FR-049). */
    val messageCode: String?,
)

/**
 * The remote signed update/provider-compatibility manifest (FR-056). Metadata only —
 * never executable (data-model.md §6, ADR-0009/ADR-0010).
 */
data class UpdateManifest(
    val schemaVersion: Int,
    val publishedAt: Instant,
    val expiresAt: Instant,
    val recommendedAppVersion: String?,
    val minimumAppVersion: String?,
    /** Approved version ranges per component (extractor + runtimes). */
    val approvedExtractorVersions: Map<String, String>,
    val approvedRuntimeVersions: Map<String, String>,
    val providerStatus: Map<ProviderId, ProviderHealthState>,
    val disabledProviders: Set<ProviderId>,
    val releaseNotes: String?,
    val signature: String,
    val algorithm: String,
)
