package com.pulldock.domain.discovery

/**
 * Session-only credential holder (FR-017/FR-034/FR-042). Keys live in memory for the process
 * lifetime and are never persisted or logged. Implemented in `:data:discovery`.
 */
interface ApiCredentialStore {
    fun get(providerId: String): String?
    fun set(providerId: String, key: String?)
}