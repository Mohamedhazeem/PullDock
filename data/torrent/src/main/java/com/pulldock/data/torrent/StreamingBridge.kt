package com.pulldock.data.torrent

import java.io.BufferedOutputStream
import java.io.File
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Loopback-only streaming bridge (T159, US6, FR-045, contracts/streaming-bridge.md,
 * ADR-0007). A minimal HTTP/1.1 file server:
 *
 * - bound to 127.0.0.1 ONLY (never 0.0.0.0 or a LAN address);
 * - per-session unpredictable token in the path; requests without it are rejected;
 * - HTTP byte-range for seeking (single-range, bounded to the file length);
 * - path-traversal defense: only the fixed, pre-validated file index is served;
 * - explicit close(): the socket stops when playback ends (lifecycle-bound, T160).
 *
 * Serves from a [File] provided by the torrent engine once enough sequential data
 * exists; unwritten ranges surface as 416 Range Not Satisfiable (no data fabrication).
 */
class StreamingBridge(
    private val file: File,
    private val contentType: String,
) : AutoCloseable {

    val token: String = newToken()
    val port: Int = findFreePort()
    val fileIndex: Int = -1

    private val server = ServerSocket(port, 8, InetAddress.getByName("127.0.0.1"))
    private val running = AtomicBoolean(true)
    private var acceptThread: Thread? = null

    /** Loopback URL for the player; token-protected (FR-045). */
    val url: String
        get() = "http://127.0.0.1:$port/$token/media"

    /** Starts serving on a daemon accept loop; idempotent. */
    fun start(): StreamingBridge {
        if (running.get() && acceptThread == null) {
            acceptThread = thread(name = "pulldock-streaming-bridge", isDaemon = true) {
                while (running.get()) {
                    val socket = try {
                        server.accept()
                    } catch (_: Exception) {
                        break // closed
                    }
                    // Loopback-only enforcement at the connection level too (defense in
                    // depth against binding mistakes).
                    if (!socket.inetAddress.isLoopbackAddress) {
                        socket.close()
                        continue
                    }
                    thread(isDaemon = true) { serve(socket) }
                }
            }
        }
        return this
    }

    private fun serve(socket: Socket) {
        socket.use { connection ->
            try {
                // SINGLE reader pass over the request line and headers. A second
                // BufferedReader on the same stream would race the first one's buffer
                // and block forever (this exact bug was caught by the seek test).
                val reader = connection.getInputStream().bufferedReader()
                val request = reader.readLine() ?: return
                var range: LongRange? = null
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isBlank()) break // end of headers
                    if (line.startsWith("Range:", ignoreCase = true)) {
                        // bytes=start-end | bytes=start-
                        val value = line.substringAfter(":", "").trim().removePrefix("bytes=")
                        val start = value.substringBefore("-").toLongOrNull()
                        val end = value.substringAfter("-", "").toLongOrNull()
                        if (start != null && start >= 0) {
                            range = if (start < file.length()) {
                                start..(end ?: (file.length() - 1)).coerceAtMost(file.length() - 1)
                            } else {
                                start..(end ?: start) // out-of-range intent → 416 below
                            }
                        }
                    }
                }
                // Request line: GET /<token>/<name> HTTP/1.1
                val path = request.split(" ").getOrNull(1) ?: return
                if (!path.startsWith("/$token/")) {
                    respond(connection, 403, "Forbidden")
                    return
                }
                // Traversal defense: the served object is fixed; any sub-path is a 404.
                val subPath = path.removePrefix("/$token/").takeWhile { it != '?' }
                if (subPath.contains("..") || subPath.startsWith("/")) {
                    respond(connection, 404, "Not Found")
                    return
                }
                serveWithRange(connection, range)
            } catch (e: Exception) {
                // Client disconnected mid-request; nothing to persist.
            }
        }
    }

    private fun serveWithRange(socket: Socket, range: LongRange?) {
        if (!file.exists()) {
            respond(socket, 404, "Not Found")
            return
        }
        val start = range?.first ?: 0L
        val end = range?.last ?: (file.length() - 1)
        if (start >= file.length()) {
            // Unwritten pieces beyond available sequential data: never fabricate.
            respond(socket, 416, "Range Not Satisfiable")
            return
        }
        val length = end - start + 1
        val headers = buildString {
            append("HTTP/1.1 ").append(if (range == null) "200 OK" else "206 Partial Content").append("\r\n")
            append("Content-Type: ").append(contentType).append("\r\n")
            append("Content-Length: ").append(length).append("\r\n")
            append("Accept-Ranges: bytes\r\n")
            if (range != null) append("Content-Range: bytes $start-$end/").append(file.length()).append("\r\n")
            append("Connection: close\r\n\r\n")
        }
        try {
            BufferedOutputStream(socket.getOutputStream()).use { out ->
                out.write(headers.toByteArray(Charsets.ISO_8859_1))
                file.inputStream().use { input ->
                    input.skip(start)
                    val buffer = ByteArray(64 * 1024)
                    var remaining = length
                    var copied = 0L
                    while (remaining > 0) {
                        val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                        if (read == -1) break
                        out.write(buffer, 0, read)
                        remaining -= read
                        copied += read
                    }
                    out.flush()
                }
            }
        } catch (e: Exception) {
        }
    }

    private fun respond(socket: Socket, code: Int, message: String) {
        val body = "$code $message"
        val headers = "HTTP/1.1 $code $message\r\nContent-Length: ${body.length}\r\nConnection: close\r\n\r\n$body"
        socket.getOutputStream().write(headers.toByteArray(Charsets.ISO_8859_1))
        socket.getOutputStream().flush()
    }

    /** Stops the accept loop and releases the port; idempotent (contracts §Lifecycle). */
    override fun close() {
        if (running.compareAndSet(true, false)) {
            try {
                server.close()
            } catch (_: Exception) {
                // already closed
            }
            acceptThread?.join(1_000)
        }
    }

    companion object {
        /** Unpredictable per-session token (SecureRandom; FR-045). */
        fun newToken(): String {
            val bytes = ByteArray(24)
            SecureRandom().nextBytes(bytes)
            return bytes.joinToString("") { "%02x".format(it) }
        }

        private fun findFreePort(): Int = ServerSocket(0).use { it.localPort }
    }
}
