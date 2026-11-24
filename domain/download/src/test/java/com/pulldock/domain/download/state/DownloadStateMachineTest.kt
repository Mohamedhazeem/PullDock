package com.pulldock.domain.download.state

import com.pulldock.core.model.DownloadState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exhaustive verification of the legal-transition table (data-model.md §2.2/§2.3,
 * quickstart Slice 1): every legal transition passes, every other pair is rejected.
 */
class DownloadStateMachineTest {

    @Test
    fun `every documented legal transition is accepted`() {
        val legal = listOf(
            DownloadState.CREATED to DownloadState.VALIDATING,
            DownloadState.CREATED to DownloadState.QUEUED,
            DownloadState.RUNNING to DownloadState.QUEUED, // recovery from resume data
            DownloadState.VALIDATING to DownloadState.RESOLVING,
            DownloadState.VALIDATING to DownloadState.AWAITING_FILE_SELECTION,
            DownloadState.VALIDATING to DownloadState.FAILED,
            DownloadState.RESOLVING to DownloadState.AWAITING_FORMAT_SELECTION,
            DownloadState.RESOLVING to DownloadState.FAILED,
            DownloadState.FETCHING_METADATA to DownloadState.AWAITING_FILE_SELECTION,
            DownloadState.FETCHING_METADATA to DownloadState.FAILED,
            DownloadState.AWAITING_FILE_SELECTION to DownloadState.QUEUED,
            DownloadState.AWAITING_FILE_SELECTION to DownloadState.CANCELLED,
            DownloadState.AWAITING_FORMAT_SELECTION to DownloadState.QUEUED,
            DownloadState.AWAITING_FORMAT_SELECTION to DownloadState.CANCELLED,
            DownloadState.QUEUED to DownloadState.STARTING,
            DownloadState.QUEUED to DownloadState.CANCELLED,
            DownloadState.STARTING to DownloadState.RUNNING,
            DownloadState.STARTING to DownloadState.FAILED,
            DownloadState.RUNNING to DownloadState.PAUSING,
            DownloadState.RUNNING to DownloadState.VERIFYING,
            DownloadState.RUNNING to DownloadState.FAILED,
            DownloadState.RUNNING to DownloadState.CANCELLED,
            DownloadState.PAUSING to DownloadState.PAUSED,
            DownloadState.PAUSED to DownloadState.QUEUED,
            DownloadState.PAUSED to DownloadState.REMOVING,
            DownloadState.PAUSED to DownloadState.CANCELLED,
            DownloadState.RETRY_WAIT to DownloadState.QUEUED,
            DownloadState.RETRY_WAIT to DownloadState.CANCELLED,
            DownloadState.VERIFYING to DownloadState.POST_PROCESSING,
            DownloadState.VERIFYING to DownloadState.COMPLETED,
            DownloadState.VERIFYING to DownloadState.FAILED,
            DownloadState.POST_PROCESSING to DownloadState.COMPLETED,
            DownloadState.POST_PROCESSING to DownloadState.FAILED,
            DownloadState.FAILED to DownloadState.QUEUED,
            DownloadState.FAILED to DownloadState.REMOVING,
            DownloadState.NEEDS_PERMISSION to DownloadState.QUEUED,
            DownloadState.NEEDS_USER_ACTION to DownloadState.QUEUED,
            DownloadState.BLOCKED to DownloadState.QUEUED,
            DownloadState.COMPLETED to DownloadState.REMOVING,
        )
        legal.forEach { (from, to) ->
            assertTrue("Expected legal: $from -> $to", DownloadStateMachine.canTransition(from, to))
            assertNotNull(DownloadStateMachine.transition(from, to))
        }
    }

    @Test
    fun `documented illegal transitions are rejected`() {
        val illegal = listOf(
            // §2.3: completed cannot resume/retry; cancelled cannot restart.
            DownloadState.COMPLETED to DownloadState.RUNNING,
            DownloadState.COMPLETED to DownloadState.QUEUED,
            DownloadState.COMPLETED to DownloadState.FAILED,
            DownloadState.CANCELLED to DownloadState.QUEUED,
            DownloadState.CANCELLED to DownloadState.RUNNING,
            DownloadState.CANCELLED to DownloadState.FAILED,
            // Awaiting selection cannot run (§2.3).
            DownloadState.AWAITING_FILE_SELECTION to DownloadState.RUNNING,
            DownloadState.AWAITING_FORMAT_SELECTION to DownloadState.RUNNING,
            DownloadState.AWAITING_FILE_SELECTION to DownloadState.COMPLETED,
            // CREATED goes through VALIDATING or is accepted by the queue.
            DownloadState.CREATED to DownloadState.RUNNING,
            DownloadState.CREATED to DownloadState.COMPLETED,
            // VERIFYING must never report COMPLETED backwards or skip processing.
            DownloadState.VERIFYING to DownloadState.RUNNING,
            DownloadState.POST_PROCESSING to DownloadState.RUNNING,
            // REMOVING is terminal: no outgoing transitions.
            DownloadState.REMOVING to DownloadState.QUEUED,
            DownloadState.REMOVING to DownloadState.RUNNING,
            DownloadState.REMOVING to DownloadState.REMOVING,
            // Terminal targets from running states without the documented trigger.
            DownloadState.PAUSED to DownloadState.RUNNING,
            DownloadState.FAILED to DownloadState.RUNNING,
        )
        illegal.forEach { (from, to) ->
            assertFalse("Expected illegal: $from -> $to", DownloadStateMachine.canTransition(from, to))
            assertNull(DownloadStateMachine.transition(from, to))
        }
    }

    @Test
    fun `user cancel is legal from every live state and illegal from terminal states`() {
        DownloadState.entries
            .filter { it != DownloadState.REMOVING && it != DownloadState.CANCELLED }
            .forEach { from ->
                assertTrue("Cancel should be legal from $from", DownloadStateMachine.canTransition(from, DownloadState.CANCELLED))
            }
        assertFalse(DownloadStateMachine.canTransition(DownloadState.REMOVING, DownloadState.CANCELLED))
        assertFalse(DownloadStateMachine.canTransition(DownloadState.CANCELLED, DownloadState.CANCELLED))
    }

    @Test
    fun `removal is legal from every state except removing itself`() {
        DownloadState.entries
            .filter { it != DownloadState.REMOVING }
            .forEach { from ->
                assertTrue("Removal should be legal from $from", DownloadStateMachine.canTransition(from, DownloadState.REMOVING))
            }
        assertFalse(DownloadStateMachine.canTransition(DownloadState.REMOVING, DownloadState.REMOVING))
    }

    @Test
    fun `exhaustive pair sweep accepts exactly the expected set`() {
        // Expected = documented table + user-cancel catch-all + removal catch-all.
        val table = setOf(
            DownloadState.CREATED to DownloadState.VALIDATING,
            DownloadState.CREATED to DownloadState.QUEUED,
            DownloadState.RUNNING to DownloadState.QUEUED,
            DownloadState.VALIDATING to DownloadState.RESOLVING,
            DownloadState.VALIDATING to DownloadState.AWAITING_FILE_SELECTION,
            DownloadState.VALIDATING to DownloadState.FAILED,
            DownloadState.RESOLVING to DownloadState.AWAITING_FORMAT_SELECTION,
            DownloadState.RESOLVING to DownloadState.FAILED,
            DownloadState.FETCHING_METADATA to DownloadState.AWAITING_FILE_SELECTION,
            DownloadState.FETCHING_METADATA to DownloadState.FAILED,
            DownloadState.AWAITING_FILE_SELECTION to DownloadState.QUEUED,
            DownloadState.AWAITING_FORMAT_SELECTION to DownloadState.QUEUED,
            DownloadState.QUEUED to DownloadState.STARTING,
            DownloadState.STARTING to DownloadState.RUNNING,
            DownloadState.STARTING to DownloadState.FAILED,
            DownloadState.RUNNING to DownloadState.PAUSING,
            DownloadState.RUNNING to DownloadState.VERIFYING,
            DownloadState.RUNNING to DownloadState.FAILED,
            DownloadState.PAUSING to DownloadState.PAUSED,
            DownloadState.PAUSED to DownloadState.QUEUED,
            DownloadState.RETRY_WAIT to DownloadState.QUEUED,
            DownloadState.VERIFYING to DownloadState.POST_PROCESSING,
            DownloadState.VERIFYING to DownloadState.COMPLETED,
            DownloadState.VERIFYING to DownloadState.FAILED,
            DownloadState.POST_PROCESSING to DownloadState.COMPLETED,
            DownloadState.POST_PROCESSING to DownloadState.FAILED,
            DownloadState.FAILED to DownloadState.QUEUED,
            DownloadState.NEEDS_PERMISSION to DownloadState.QUEUED,
            DownloadState.NEEDS_USER_ACTION to DownloadState.QUEUED,
            DownloadState.BLOCKED to DownloadState.QUEUED,
            DownloadState.COMPLETED to DownloadState.REMOVING,
        )
        val expected = buildSet {
            addAll(table)
            DownloadState.entries.filter { it != DownloadState.REMOVING && it != DownloadState.CANCELLED }
                .forEach { add(it to DownloadState.CANCELLED) }
            DownloadState.entries.filter { it != DownloadState.REMOVING }
                .forEach { add(it to DownloadState.REMOVING) }
        }

        val accepted = DownloadState.entries.flatMap { from ->
            DownloadState.entries.map { to -> from to to }
        }.filter { (from, to) -> DownloadStateMachine.canTransition(from, to) }.toSet()

        assertEquals(expected, accepted)
    }

    @Test
    fun `active states cover only engine-occupied states`() {
        assertEquals(
            setOf(
                DownloadState.STARTING,
                DownloadState.RUNNING,
                DownloadState.PAUSING,
                DownloadState.VERIFYING,
                DownloadState.POST_PROCESSING,
            ),
            DownloadStateMachine.activeStates,
        )
    }

    @Test
    fun `pause is meaningful only while running or already paused`() {
        assertTrue(DownloadStateMachine.canPause(DownloadState.RUNNING))
        assertTrue(DownloadStateMachine.canPause(DownloadState.PAUSED))
        DownloadState.entries
            .filter { it != DownloadState.RUNNING && it != DownloadState.PAUSED }
            .forEach { assertFalse(DownloadStateMachine.canPause(it)) }
    }
}
