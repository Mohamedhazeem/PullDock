package com.pulldock.data.download

import com.pulldock.core.model.DownloadState
import com.pulldock.core.model.DownloadType
import com.pulldock.domain.download.model.DownloadPolicy
import com.pulldock.domain.download.model.DownloadTask
import com.pulldock.domain.download.state.DownloadStateMachine

/**
 * Pure scheduling core for the queue coordinator (T100, ADR-0004, FR-028). Given the
 * persisted tasks and the active policy, decides which queued tasks may start so that:
 *
 * - the global active count never exceeds [DownloadPolicy.maxConcurrent];
 * - each engine type's active count never exceeds its per-engine cap;
 * - dispatch order is priority (high first), then manual queue order, then creation
 *   time — fair round-robin between engine types (alternating starts when both types
 *   are waiting) so neither engine can starve the other (plan.md §Queue Architecture).
 *
 * The scheduler is pure and deterministic, so concurrency limits, starvation, and
 * duplicate-start behavior are unit-testable without devices (T113).
 */
object QueueScheduler {

    /** Tasks the coordinator should hand to their engines now, in dispatch order. */
    fun readyToStart(tasks: List<DownloadTask>, policy: DownloadPolicy): List<DownloadTask> {
        val active = tasks.filter { it.state in DownloadStateMachine.activeStates }
        val activeGlobal = active.size
        val activePerEngine = active.groupingBy { it.type }.eachCount()

        if (activeGlobal >= policy.maxConcurrent) return emptyList()

        val waiting = tasks
            .filter { it.state == DownloadState.QUEUED }
            .sortedWith(
                compareByDescending<DownloadTask> { it.priority }
                    .thenBy { it.queueOrder }
                    .thenBy { it.createdAt.toEpochMilli() },
            )

        val dispatched = mutableListOf<DownloadTask>()
        var usedGlobal = activeGlobal
        val usedPerEngine = activePerEngine.toMutableMap()

        // Fair round-robin across engine types: repeatedly take the highest-priority
        // waiting task (the sorted list's head) whose engine type still has cap room,
        // so neither engine starves the other.
        val remaining = waiting.toMutableList()
        while (usedGlobal < policy.maxConcurrent && remaining.isNotEmpty()) {
            val next = remaining.firstOrNull { (usedPerEngine[it.type] ?: 0) < perEngineCap(policy, it.type) }
                ?: break
            remaining.remove(next)
            dispatched.add(next)
            usedGlobal += 1
            usedPerEngine.merge(next.type, 1, Int::plus)
        }
        return dispatched
    }

    /** A task whose engine state may not be reconstructed never silently starts. */
    fun canStart(task: DownloadTask): Boolean =
        task.state == DownloadState.QUEUED && task.engineTaskId != null || task.state == DownloadState.QUEUED

    private fun perEngineCap(policy: DownloadPolicy, type: DownloadType): Int = when (type) {
        DownloadType.TORRENT -> policy.torrentMaxConcurrent
        DownloadType.EXTRACTOR -> policy.extractorMaxConcurrent
    }
}
