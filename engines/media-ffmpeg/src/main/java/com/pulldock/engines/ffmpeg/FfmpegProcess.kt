package com.pulldock.engines.ffmpeg

import java.io.File
import java.util.concurrent.TimeUnit

/**
 * FFmpeg/FFprobe invocation boundary (T082, ADR-0003, FR-048/FR-049).
 *
 * Rules enforced here, non-negotiable:
 * - Argument arrays only — never a shell string built from user input (FR-048).
 * - Bounded output capture; oversized diagnostics are truncated (FR-049).
 * - Explicit timeout and cooperative cancellation; the process is always reaped.
 * - Exit codes verified; a non-zero exit is a typed failure, never silent success.
 *
 * Binaries are bundled, pinned, and validated per ABI (D2); [binaryLocator] resolves
 * the executable path so tests can inject a stub without touching native code.
 */
class FfmpegProcess(
    private val binaryLocator: (String) -> File,
    private val workDirectory: File,
) {

    /** Result of a completed process run. */
    data class RunResult(
        val exitCode: Int,
        /** Combined stdout+stderr, bounded to [MAX_OUTPUT_BYTES] (sanitized upstream). */
        val output: String,
        val timedOut: Boolean,
    ) {
        val success: Boolean get() = exitCode == 0 && !timedOut
    }

    /** Runs the named binary (ffmpeg | ffprobe) with argument arrays. */
    fun run(
        binary: String,
        arguments: List<String>,
        timeoutSeconds: Long = DEFAULT_TIMEOUT_SECONDS,
    ): RunResult {
        val executable = binaryLocator(binary)
        require(executable.exists()) { "missing binary: $binary" }
        val command = listOf(executable.absolutePath) + arguments // argument array (FR-048)

        val process = ProcessBuilder(command)
            .directory(workDirectory)
            .redirectErrorStream(true)
            .start()

        val output = StringBuilder()
        // Consume output concurrently so a silent, long-running process can never
        // block the wait below on a full pipe (bounded capture, FR-049).
        val reader = Thread {
            try {
                process.inputStream.use { stream ->
                    val buffer = ByteArray(8 * 1024)
                    var read: Int
                    var total = 0
                    while (stream.read(buffer).also { read = it } != -1) {
                        total += read
                        if (total <= MAX_OUTPUT_BYTES) {
                            output.append(String(buffer, 0, read, Charsets.UTF_8))
                        }
                    }
                }
            } catch (_: Exception) {
                // Stream torn down by destroyForcibly; nothing to record.
            }
        }
        reader.isDaemon = true
        try {
            reader.start()
            val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                reader.join(1_000)
                return RunResult(exitCode = -1, output = bounded(output), timedOut = true)
            }
            reader.join(1_000)
            return RunResult(exitCode = process.exitValue(), output = bounded(output), timedOut = false)
        } finally {
            if (process.isAlive) {
                process.destroyForcibly()
            }
        }
    }

    /** Convenience: probe a media file and return parsed duration seconds (or null). */
    fun probeDurationSeconds(path: String): Long? {
        val result = run(
            binary = "ffprobe",
            arguments = listOf(
                "-v", "error",
                "-show_entries", "format=duration",
                "-of", "default=noprint_wrappers=1:nokey=1",
                path,
            ),
        )
        if (!result.success) return null
        return result.output.trim().toDoubleOrNull()?.let { it.toLong() }
    }

    /** Merges separate video/audio streams into one container (FR-016/FR-022). */
    fun merge(videoPath: String, audioPath: String, outputPath: String): RunResult = run(
        binary = "ffmpeg",
        arguments = listOf(
            "-y",
            "-i", videoPath,
            "-i", audioPath,
            "-c", "copy",
            "-map", "0:v:0",
            "-map", "1:a:0",
            outputPath,
        ),
    )

    private fun bounded(output: StringBuilder): String =
        output.toString().takeLast(MAX_OUTPUT_BYTES.toInt())

    companion object {
        const val DEFAULT_TIMEOUT_SECONDS = 600L
        const val MAX_OUTPUT_BYTES = 64 * 1024
    }
}
