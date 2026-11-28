package com.pulldock.data.discovery.http

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json

/**
 * Builds the single Ktor client used by every provider adapter (ADR-0015). The OkHttp engine
 * provides connection pooling; content negotiation uses kotlinx-serialization with lenient,
 * unknown-key-tolerant parsing. Per-provider request timeouts are additionally enforced by the
 * repository via `withTimeout`.
 */
@Singleton
class HttpClientProvider @Inject constructor() {

    fun create(): HttpClient = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
                explicitNulls = false
            })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = REQUEST_TIMEOUT_MS
            connectTimeoutMillis = CONNECT_TIMEOUT_MS
        }
    }

    private companion object {
        const val REQUEST_TIMEOUT_MS = 8_000L
        const val CONNECT_TIMEOUT_MS = 5_000L
    }
}