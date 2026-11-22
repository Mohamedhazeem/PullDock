# ADR-0004: Queue coordinator

**Status**: Accepted | **Date**: 2026-11-22

## Context

Torrent and media tasks must share one queue with global and per-engine limits, priority,
retry, recovery, and duplicate prevention, without losing engine-specific controls.

## Decision

Implement a single `QueueCoordinator` in `:data:download`, backed by Room as the source of
truth, exposing a conflated `QueueState` stream. Per-engine adapters apply their own
controls.

## Alternatives

- **Two independent per-engine schedulers**: rejected — races, duplicate work, no global cap.
- **Database-only scheduling**: rejected — no serialization/fairness guarantees under
  concurrency.
- **WorkManager as the scheduler**: rejected — not a continuous engine scheduler.

## Trade-offs

- A single coordinator is more code but prevents races and starvation.
- Engine-specific controls must be modeled on detail records, not flattened.

## Consequences

- Fairness is round-robin across engines with global and per-engine caps.
- Retry uses persisted backoff with jitter.
- Recovery uses `RecoveryMarker`s; unrecoverable tasks become `NEEDS_USER_ACTION`.
- Events are applied idempotently by task id and sequence.
