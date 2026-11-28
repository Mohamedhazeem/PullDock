package com.pulldock.data.discovery.provider

import android.content.Context
import com.pulldock.domain.discovery.error.DiscoveryError
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Outcome of fetching an Archive `.torrent` into app-private temp storage. */
sealed interface TempTorrentResult {
    data class Ok(val file: File, val sizeBytes: Long) : TempTorrentResult
    data class Failure(val error: DiscoveryError) : TempTorrentResult
}

/**
 * Downloads a validated Archive `.torrent` into app-private cache storage (FR-026, FR-055,
 * FR-056). Enforces a bounded size, rejects HTML/error pages, and returns a `file://` uri the
 * existing Add flow consumes. Callers MUST invoke [cleanup] after consumption or failure.
 */
@Singleton
class TemporaryTorrentFile @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: HttpClient,
) {

    suspend fun download(url: String): TempTorrentResult {
        val response = try {
            client.get(url)
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
            return TempTorrentResult.Failure(DiscoveryError.Network(DiscoveryError.NetworkReason.TIMEOUT, "download failed"))
        }
        if (response.status != HttpStatusCode.OK) {
            return TempTorrentResult.Failure(DiscoveryError.Provider(DiscoveryError.ProviderReason.SERVER_ERROR, "http ${response.status.value}"))
        }
        val bytes = try {
            response.body<ByteArray>()
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
            return TempTorrentResult.Failure(DiscoveryError.Network(DiscoveryError.NetworkReason.TIMEOUT, "read failed"))
        }
        if (bytes.size > MAX_BYTES) {
            return TempTorrentResult.Failure(DiscoveryError.Provider(DiscoveryError.ProviderReason.TORRENT_FILE_TOO_LARGE))
        }
        if (!looksLikeTorrent(bytes)) {
            return TempTorrentResult.Failure(DiscoveryError.Provider(DiscoveryError.ProviderReason.TORRENT_FILE_INVALID, "not a torrent"))
        }
        val dir = File(context.cacheDir, "discovery").apply { mkdirs() }
        val file = File(dir, "torrent-${UUID.randomUUID()}.torrent")
        file.writeBytes(bytes)
        return TempTorrentResult.Ok(file, bytes.size.toLong())
    }

    /** Idempotent cleanup; safe on success/back/cancel (FR-056). */
    suspend fun cleanup(file: File) {
        runCatching { if (file.exists()) file.delete() }
    }

    companion object {
        /** A real torrent is a bencoded dict (`d…`) and is not an HTML error page. */
        internal fun looksLikeTorrent(bytes: ByteArray): Boolean {
            if (bytes.size < 2) return false
            val first = bytes[0].toInt().toChar()
            if (first != 'd') return false
            val head = String(bytes, 0, minOf(bytes.size, 64), Charsets.ISO_8859_1).lowercase()
            if (head.contains("<html") || head.contains("<!doctype")) return false
            return true
        }

        private const val MAX_BYTES = 5_000_000
    }
}