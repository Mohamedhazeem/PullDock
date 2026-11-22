package com.pulldock.engines.ffmpeg

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Process boundary tests (T092): argument-array invocation, bounded output, exit-code
 * verification, timeout behavior. Uses tiny host binaries as stand-ins; on-device
 * FFmpeg merge verification (T094) remains the device procedure.
 */
class FfmpegProcessTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun locator(name: String): File =
        File(if (name == "ffmpeg") "/bin/cat" else "/bin/echo")

    @Test
    fun `argument-array run captures bounded output and verifies exit code`() {
        val process = FfmpegProcess(::locator, tmp.newFolder())
        val result = process.run("ffprobe", listOf("hello-from-ffprobe-stand-in"))
        assertTrue(result.success)
        assertTrue(result.output.contains("hello-from-ffprobe-stand-in"))
    }

    @Test
    fun `non-zero exit is a typed failure, never silent success`() {
        val process = FfmpegProcess({ File("/bin/false") }, tmp.newFolder())
        val result = process.run("ffmpeg", emptyList())
        assertFalse(result.success)
        assertEquals(1, result.exitCode)
    }

    @Test
    fun `missing binary is rejected before any process starts`() {
        val process = FfmpegProcess({ File("/nonexistent/binary") }, tmp.newFolder())
        val threw = runCatching { process.run("ffmpeg", emptyList()) }
        assertTrue(threw.isFailure)
    }

    @Test
    fun `timeout forcibly reaps the process and reports timedOut`() {
        org.junit.Assume.assumeTrue(File("/bin/sleep").exists())
        val process = FfmpegProcess({ File("/bin/sleep") }, tmp.newFolder())
        val result = process.run("ffmpeg", listOf("5"), timeoutSeconds = 1)
        assertFalse(result.success)
        assertTrue(result.timedOut)
    }
}
