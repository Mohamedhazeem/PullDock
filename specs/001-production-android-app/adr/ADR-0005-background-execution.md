# ADR-0005: Background execution

**Status**: Accepted | **Date**: 2026-11-22

## Context

Android restricts background work and changes behavior by API level: Android 15 limits
`dataSync` foreground services to 6 h/24 h; Android 16 makes background jobs started from a
foreground service respect quotas; Android 14+ offers User-Initiated Data Transfer (UIDT)
jobs; Android 15 blocks starting `dataSync` from `BOOT_COMPLETED`.

## Decision

Use an API-level-aware matrix:

- API 34+: UIDT jobs for long user-initiated transfers (quota-exempt, constraint support,
  progress notification).
- API <34: `dataSync` foreground service with a meaningful notification and prompt
  `stopSelf()` on timeout.
- WorkManager for deferrable work (recovery, reconciliation, cleanup).
- No restricted foreground service auto-starts from boot.

## Alternatives

- **Always-on `dataSync` service**: rejected — 6 h/24 h budget and boot restrictions.
- **WorkManager for continuous transfers**: rejected — not a continuous engine.
- **Assume unlimited background execution**: rejected — false promise.

## Trade-offs

- More code paths and per-API tests.
- Long transfers may still be interrupted; recovery must be explicit.

## Consequences

- No promise of unlimited background execution; limits are documented in-app.
- Boot recovery restores state and surfaces tasks; it does not auto-start a restricted
  service.
- Notification actions provide pause/resume/stop; denied notification permission degrades
  gracefully.
