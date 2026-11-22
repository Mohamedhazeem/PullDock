package com.pulldock.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Error codes are persisted with the task (`download_task.errorCode`) and mapped to user
 * messages, so every code must be unique and stable-shaped. This test pins that contract
 * across the whole hierarchy.
 */
class DownloadErrorTest {

    private fun allErrors(): List<DownloadError> = buildList {
        addAll(DownloadError.InputReason.entries.map { DownloadError.Input(it, "detail") })
        addAll(DownloadError.NetworkReason.entries.map { DownloadError.Network(it, "detail") })
        addAll(DownloadError.TorrentReason.entries.map { DownloadError.TorrentEngine(it, "detail") })
        addAll(DownloadError.ExtractorReason.entries.map { DownloadError.ExtractorEngine(it, "detail") })
        addAll(DownloadError.StorageReason.entries.map { DownloadError.Storage(it, "detail") })
        addAll(DownloadError.PermissionReason.entries.map { DownloadError.Permission(it, "detail") })
        addAll(DownloadError.ProcessingReason.entries.map { DownloadError.Processing(it, "detail") })
        addAll(DownloadError.PersistenceReason.entries.map { DownloadError.Persistence(it, "detail") })
        addAll(DownloadError.LifecycleReason.entries.map { DownloadError.Lifecycle(it, "detail") })
        add(DownloadError.Internal("detail"))
    }

    @Test
    fun `every error code is unique across the hierarchy`() {
        val errors = allErrors()
        val codes = errors.map { it.code }
        assertEquals(errors.size, codes.toSet().size)
    }

    @Test
    fun `every error code is a stable uppercase underscore token`() {
        allErrors().forEach { error ->
            assertTrue(
                "Code '${error.code}' must match [A-Z0-9_]+",
                error.code.matches(Regex("[A-Z0-9_]+")),
            )
        }
    }

    @Test
    fun `codes are prefixed with their category for persisted diagnostics`() {
        assertEquals("INPUT_MALFORMED_MAGNET", DownloadError.Input(DownloadError.InputReason.MALFORMED_MAGNET).code)
        assertEquals("TORRENT_METADATA_TIMEOUT", DownloadError.TorrentEngine(DownloadError.TorrentReason.METADATA_TIMEOUT).code)
        assertEquals("INTERNAL", DownloadError.Internal().code)
    }

    @Test
    fun `terminal states are exactly completed cancelled and removing`() {
        val terminal = DownloadState.entries.filter { it.isTerminal }
        assertEquals(setOf(DownloadState.COMPLETED, DownloadState.CANCELLED, DownloadState.REMOVING), terminal.toSet())
        // FAILED must stay retryable (FAILED -> QUEUED is a legal transition).
        assertFalse(DownloadState.FAILED.isTerminal)
    }
}
