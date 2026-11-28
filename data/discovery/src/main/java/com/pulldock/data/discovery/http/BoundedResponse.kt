package com.pulldock.data.discovery.http

import com.pulldock.domain.discovery.error.DiscoveryError
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode

/** Result of a bounded HTTP GET. */
sealed interface HttpResult {
    data class Ok(val body: String, val contentType: String?) : HttpResult
    data class Failure(val error: DiscoveryError) : HttpResult
}

/**
 * Bounded response handling shared by providers (FR-072/FR-073): a maximum response size, a
 * content-type capture, and mapping of HTTP status codes to typed [DiscoveryError]s. No
 * redirects to unexpected hosts are followed here; callers validate final URLs.
 */
object BoundedResponse {

    /** Hard cap on a single provider response body (bytes/chars). */
    const val MAX_BYTES: Int = 2_000_000

    suspend fun fetch(
        client: HttpClient,
        url: String,
        maxBytes: Int = MAX_BYTES,
        headers: Map<String, String> = emptyMap(),
        build: HttpRequestBuilder.() -> Unit = {},
    ): HttpResult {
        val response = try {
            client.get(url) {
                build()
                headers.forEach { (k, v) -> header(k, v) }
                // Some providers (e.g. Internet Archive, YTS, Knaben) reject the default client UA.
                header("User-Agent", "Mozilla/5.0 (Linux; Android 13) PullDock/0.2")
                header("Accept", "application/json, application/xml, */*")
            }
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
            return HttpResult.Failure(DiscoveryError.Network(DiscoveryError.NetworkReason.TIMEOUT, "request failed"))
        }

        if (response.status != HttpStatusCode.OK) {
            System.err.println("Discovery http ${response.status.value} for ${runCatching { io.ktor.http.Url(url).host }.getOrNull()}")
        }
        return when (response.status) {
            HttpStatusCode.OK -> {
                val length = response.headers["Content-Length"]?.toLongOrNull()
                if (length != null && length > maxBytes) {
                    HttpResult.Failure(DiscoveryError.Provider(DiscoveryError.ProviderReason.SERVER_ERROR, "response too large"))
                } else {
                    val body = response.bodyAsText()
                    if (body.length > maxBytes) {
                        HttpResult.Failure(DiscoveryError.Provider(DiscoveryError.ProviderReason.SERVER_ERROR, "response too large"))
                    } else {
                        HttpResult.Ok(body, response.headers["Content-Type"])
                    }
                }
            }
            HttpStatusCode.TooManyRequests -> HttpResult.Failure(DiscoveryError.RateLimited())
            HttpStatusCode.Unauthorized -> HttpResult.Failure(DiscoveryError.Provider(DiscoveryError.ProviderReason.UNAUTHORIZED))
            HttpStatusCode.Forbidden -> HttpResult.Failure(DiscoveryError.Provider(DiscoveryError.ProviderReason.FORBIDDEN))
            HttpStatusCode.NotFound -> HttpResult.Failure(DiscoveryError.Provider(DiscoveryError.ProviderReason.NOT_FOUND))
            else -> if (response.status.value in 500..599) {
                HttpResult.Failure(DiscoveryError.Provider(DiscoveryError.ProviderReason.SERVER_ERROR))
            } else {
                HttpResult.Failure(DiscoveryError.Provider(DiscoveryError.ProviderReason.SERVER_ERROR, "http ${response.status.value}"))
            }
        }
    }
}