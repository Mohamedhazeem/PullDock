package com.pulldock.data.torrent

import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Streaming bridge behavior (T159/SC-015): loopback-only, token-protected, byte-range
 * seeking, traversal defense, bounded responses, and clean shutdown.
 */
class StreamingBridgeTest {

    private fun fetch(url: String, range: String? = null): Pair<Int, ByteArray> {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 5_000
        connection.readTimeout = 5_000
        range?.let { connection.setRequestProperty("Range", it) }
        val code = connection.responseCode
        val body = if (code < 400) {
            connection.inputStream.readBytes()
        } else {
            connection.errorStream?.readBytes() ?: ByteArray(0)
        }
        return code to body
    }

    @Test
    fun `serves the file with token and supports byte-range seeking`() {
        val content = "0123456789ABCDEF".repeat(16) // 256 bytes
        val file = Files.createTempFile("bridge", ".mp4").toFile().apply { writeText(content) }
        val bridge = StreamingBridge(file, contentType = "video/mp4").start()
        try {
            val (fullCode, fullBody) = fetch(bridge.url)
            assertEquals(200, fullCode)
            assertEquals(content, String(fullBody))

            val (rangeCode, rangeBody) = fetch(bridge.url, range = "bytes=100-199")
            assertEquals(206, rangeCode)
            assertEquals(content.substring(100, 200), String(rangeBody))
        } finally {
            bridge.close()
        }
    }

    @Test
    fun `requests without the token are rejected`() {
        val file = Files.createTempFile("bridge2", ".mp4").toFile().apply { writeText("data") }
        val bridge = StreamingBridge(file, contentType = "video/mp4").start()
        try {
            val noTokenUrl = bridge.url.substringBeforeLast('/').substringBeforeLast('/') + "/media"
            val (code, _) = fetch(noTokenUrl)
            assertTrue(code == 403 || code == 404)
        } finally {
            bridge.close()
        }
    }

    @Test
    fun `traversal attempts are rejected`() {
        val file = Files.createTempFile("bridge3", ".mp4").toFile().apply { writeText("data") }
        val bridge = StreamingBridge(file, contentType = "video/mp4").start()
        try {
            val traversalUrl = bridge.url.replace("/media", "/..%2F..%2Fetc%2Fpasswd")
            val (code, _) = fetch(traversalUrl)
            assertTrue(code in 400..499)
        } finally {
            bridge.close()
        }
    }

    @Test
    fun `ranges beyond available data return 416, never fabricated bytes`() {
        val file = Files.createTempFile("bridge4", ".mp4").toFile().apply { writeText("small") }
        val bridge = StreamingBridge(file, contentType = "video/mp4").start()
        try {
            val (code, body) = fetch(bridge.url, range = "bytes=1000000-2000000")
            assertEquals(416, code)
            assertTrue(String(body).contains("416"))
        } finally {
            bridge.close()
        }
    }

    @Test
    fun `close stops serving and is idempotent`() {
        val file = Files.createTempFile("bridge5", ".mp4").toFile().apply { writeText("data") }
        val bridge = StreamingBridge(file, contentType = "video/mp4").start()
        bridge.close()
        bridge.close() // idempotent
        val threw = runCatching { fetch(bridge.url) }
        assertTrue(threw.isFailure)
        assertFalse(bridge.url.isEmpty())
    }
}
