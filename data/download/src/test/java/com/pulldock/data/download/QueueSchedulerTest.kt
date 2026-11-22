package com.pulldock.data.download

import com.pulldock.core.model.DownloadState
import com.pulldock.core.model.DownloadType
import com.pulldock.core.model.StorageKind
import com.pulldock.core.model.TransferLimits
import com.pulldock.domain.download.model.DownloadPolicy
import com.pulldock.domain.download.model.DownloadProgress
import com.pulldock.domain.download.model.DownloadTask
import com.pulldock.domain.download.model.StorageDestination
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Scheduling core tests (T113, US4 scenarios 2/5): global + per-engine concurrency
 * caps, priority/order dispatch, engine fairness (no starvation), and duplicate-start
 * safety (a RUNNING task is never dispatched twice).
 */
class QueueSchedulerTest {

    private fun task(id: String, state: DownloadState, type: DownloadType, priority: Int = 0, order: Int = 0, createdAt: Long = 0): DownloadTask = DownloadTask(
        id = id,
        type = type,
        source = "src-$id",
        sourceHash = "hash-$id",
        displayName = "Task $id",
        outputFilename = "",
        state = state,
        progress = DownloadProgress.IDLE,
        totalBytes = null,
        downloadedBytes = 0,
        downloadSpeed = 0,
        uploadSpeed = null,
        etaSeconds = null,
        createdAt = Instant.ofEpochMilli(createdAt),
        updatedAt = Instant.ofEpochMilli(createdAt),
        completedAt = null,
        outputUri = null,
        errorCode = null,
        errorDetails = null,
        retryCount = 0,
        nextRetryAt = null,
        destination = StorageDestination(StorageKind.APP_SPECIFIC, null, "App", null),
        limits = TransferLimits.NONE,
        priority = priority,
        queueOrder = order,
        engineTaskId = "engine-$id",
    )

    @Test
    fun `global cap limits concurrent dispatch`() {
        val tasks = listOf(
            task("r1", DownloadState.RUNNING, DownloadType.TORRENT),
            task("r2", DownloadState.RUNNING, DownloadType.EXTRACTOR),
            task("q1", DownloadState.QUEUED, DownloadType.TORRENT),
            task("q2", DownloadState.QUEUED, DownloadType.EXTRACTOR),
        )
        val policy = DownloadPolicy(maxConcurrent = 2, torrentMaxConcurrent = 2, extractorMaxConcurrent = 2)
        assertTrue(QueueScheduler.readyToStart(tasks, policy).isEmpty())
    }

    @Test
    fun `per-engine cap blocks a type even when the global cap has room`() {
        val tasks = listOf(
            task("t1", DownloadState.RUNNING, DownloadType.TORRENT),
            task("t2", DownloadState.QUEUED, DownloadType.TORRENT),
            task("e1", DownloadState.QUEUED, DownloadType.EXTRACTOR),
        )
        val policy = DownloadPolicy(maxConcurrent = 3, torrentMaxConcurrent = 1, extractorMaxConcurrent = 1)
        val dispatched = QueueScheduler.readyToStart(tasks, policy)
        assertEquals(listOf("e1"), dispatched.map { it.id }) // torrent cap is full
    }

    @Test
    fun `priority then queue order determines dispatch order`() {
        val tasks = listOf(
            task("low", DownloadState.QUEUED, DownloadType.TORRENT, priority = 0, order = 0),
            task("high", DownloadState.QUEUED, DownloadType.EXTRACTOR, priority = 10, order = 9),
            task("mid", DownloadState.QUEUED, DownloadType.TORRENT, priority = 5, order = 1),
        )
        val policy = DownloadPolicy(maxConcurrent = 3, torrentMaxConcurrent = 3, extractorMaxConcurrent = 3)
        val dispatched = QueueScheduler.readyToStart(tasks, policy)
        assertEquals(listOf("high", "mid", "low"), dispatched.map { it.id })
    }

    @Test
    fun `round-robin fairness prevents one engine from starving the other`() {
        val tasks = (1..6).map { i ->
            task(
                "q$i",
                DownloadState.QUEUED,
                if (i % 2 == 0) DownloadType.EXTRACTOR else DownloadType.TORRENT,
                order = i,
            )
        }
        val policy = DownloadPolicy(maxConcurrent = 2, torrentMaxConcurrent = 3, extractorMaxConcurrent = 3)
        val dispatched = QueueScheduler.readyToStart(tasks, policy)
        assertEquals(2, dispatched.size)
        // Alternating types: a torrent and an extractor share the two slots.
        assertEquals(1, dispatched.count { it.type == DownloadType.TORRENT })
        assertEquals(1, dispatched.count { it.type == DownloadType.EXTRACTOR })
    }

    @Test
    fun `running tasks are never dispatched twice (duplicate-start safety, FR-026)`() {
        val tasks = listOf(
            task("r1", DownloadState.RUNNING, DownloadType.TORRENT),
            task("s1", DownloadState.STARTING, DownloadType.TORRENT),
            task("q1", DownloadState.QUEUED, DownloadType.TORRENT),
        )
        val policy = DownloadPolicy(maxConcurrent = 10, torrentMaxConcurrent = 10, extractorMaxConcurrent = 10)
        val dispatched = QueueScheduler.readyToStart(tasks, policy)
        assertEquals(listOf("q1"), dispatched.map { it.id })
    }

    @Test
    fun `retry-wait tasks do not dispatch before their backoff elapses`() {
        val tasks = listOf(
            task("w", DownloadState.RETRY_WAIT, DownloadType.EXTRACTOR),
            task("q", DownloadState.QUEUED, DownloadType.TORRENT),
        )
        val policy = DownloadPolicy(maxConcurrent = 5, torrentMaxConcurrent = 5, extractorMaxConcurrent = 5)
        val dispatched = QueueScheduler.readyToStart(tasks, policy)
        assertEquals(listOf("q"), dispatched.map { it.id })
    }
}
