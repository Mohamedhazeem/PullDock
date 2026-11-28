package com.pulldock.data.discovery

import com.pulldock.domain.discovery.ApiCredentialStore
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Session-only credential holder (FR-042). Keys never touch DataStore/Room and are never
 * logged; they are cleared when the process dies.
 */
@Singleton
class ApiCredentialStoreImpl @Inject constructor() : ApiCredentialStore {

    private val keys = ConcurrentHashMap<String, String>()

    override fun get(providerId: String): String? = keys[providerId]

    override fun set(providerId: String, key: String?) {
        if (key.isNullOrBlank()) keys.remove(providerId) else keys[providerId] = key
    }
}