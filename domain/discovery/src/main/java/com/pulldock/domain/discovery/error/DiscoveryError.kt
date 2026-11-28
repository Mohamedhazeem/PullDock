package com.pulldock.domain.discovery.error

/**
 * Typed discovery errors (FR-070). Distinct from the persisted `DownloadError` used by the
 * transfer pipeline (NFR-001). [detail] is always sanitized diagnostics: never URLs with
 * credentials, tokens, raw XML/HTML, or secrets. Extends [Exception] so adapters can throw a
 * typed error that the repository catches to build per-provider status.
 */
sealed class DiscoveryError(message: String? = null) : Exception(message) {

    /** Untrusted input failed validation before any request. */
    data class Input(val reason: InputReason, val detail: String? = null) : DiscoveryError(detail)

    /** Network-level failure or offline posture. */
    data class Network(val reason: NetworkReason, val detail: String? = null) : DiscoveryError(detail)

    /** A provider returned an error response or unusable payload. */
    data class Provider(val reason: ProviderReason, val detail: String? = null) : DiscoveryError(detail)

    /** The provider signalled rate limiting (HTTP 429 / documented quota). */
    data class RateLimited(val detail: String? = null) : DiscoveryError(detail)

    /** A response could not be parsed (malformed JSON/XML). */
    data class Parse(val detail: String? = null) : DiscoveryError(detail)

    /** A provider request exceeded its bounded timeout. */
    data class Timeout(val providerId: String?, val detail: String? = null) : DiscoveryError(detail)

    /** The provider is disabled or not configured. */
    data class Disabled(val providerId: String) : DiscoveryError(providerId)

    /** No enabled provider supports the requested category/search. */
    data object NoSupportedProviders : DiscoveryError()

    enum class InputReason { EMPTY_QUERY, UNSUPPORTED_SCHEME, MALFORMED_MAGNET, MALFORMED_URL }

    enum class NetworkReason { OFFLINE, DNS_FAILURE, TLS_FAILURE, TIMEOUT }

    enum class ProviderReason {
        UNAUTHORIZED, FORBIDDEN, NOT_FOUND, SERVER_ERROR, EMPTY, MALFORMED,
        NO_TORRENT_FILE, TORRENT_FILE_TOO_LARGE, TORRENT_FILE_INVALID,
    }
}