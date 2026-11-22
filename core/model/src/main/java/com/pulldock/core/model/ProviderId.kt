package com.pulldock.core.model

/**
 * The v1 media provider set (spec FR-014). Providers outside this set are surfaced as
 * unsupported rather than bypassed. Each provider has its own compatibility record,
 * health state, and policy assessment; failures are isolated per provider.
 */
enum class ProviderId {
    YOUTUBE,
    X,
    INSTAGRAM,
    FACEBOOK,
    TIKTOK,
}
