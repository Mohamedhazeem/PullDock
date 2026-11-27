# Quickstart: Validate Torrent Discovery

**Feature**: `002-torrent-discovery` | **Date**: 2026-11-28

A runnable guide to prove the feature works end-to-end. Commands are real repository
commands; scenarios reference [contracts](./contracts/README.md) and
[data-model.md](./data-model.md). No implementation code here — see `tasks.md`.

## Prerequisites

- Android 8.0+ (API 26+) device/emulator; 64-bit ABI (arm64-v8a or x86_64).
- JDK 17+ and the Android SDK (`local.properties` with `sdk.dir`).
- Network access for manual provider scenarios (unit tests do not need it).

## Build & test commands

```bash
# Build the app (includes the new :feature:discovery via :app)
./gradlew :app:assembleDebug

# New module unit tests (offline; mocked providers)
./gradlew :domain:discovery:test
./gradlew :data:discovery:testDebugUnitTest
./gradlew :feature:discovery:testDebugUnitTest

# Compose UI tests (device/emulator)
./gradlew :feature:discovery:connectedDebugAndroidTest

# Existing regression (must stay green)
./gradlew :domain:torrent:test :domain:download:test
./gradlew :data:torrent:testDebugUnitTest :data:download:testDebugUnitTest
./gradlew :feature:add:testDebugUnitTest :app:testDebugUnitTest

# Lint
./gradlew :app:lintDebug
```

## Manual validation scenarios

### S1 — Search once, combined results (US1)
1. Open Discover, enter a title, submit.
2. Expected: results from BitSearch and Internet Archive appear in one list; each card shows
   its provider; only supplied metadata is shown; missing fields read "unknown".
   Ref: FR-001..FR-003, FR-037, FR-049..FR-053.

### S2 — Partial failure isolation (US5)
1. Force one provider to fail (e.g., block its host / mock 429).
2. Expected: the other provider's results still show; a per-provider message names the
   failure; no generic fatal error. Ref: FR-039, FR-068, SC-002.

### S3 — Add a magnet result (US2)
1. Open a BitSearch/Torznab result → Add to PullDock.
2. Expected: existing Add flow opens with the magnet; metadata fetch, file selection,
   destination, explicit start; transfer appears in the Home queue. Ref: FR-054, FR-057,
   contracts/discovery-handoff.md.

### S4 — Add an Internet Archive result (US2)
1. Open an Archive result with a `.torrent` → Add to PullDock.
2. Expected: `.torrent` fetched to app-private temp, validated, existing file-based Add flow
   opens; temp file cleaned up after success/back. Ref: FR-055, FR-056.
3. Archive item with multiple `.torrent` files → a choice is presented before Add.
   Ref: FR-024. Item with no `.torrent` → no Add action. Ref: US2 scenario 5.

### S5 — Duplicate guard (US2)
1. Add a source already active/completed.
2. Expected: existing duplicate handling reports it; no second transfer. Ref: FR-058, SC-004.

### S6 — Filters, sort, load more (US3)
1. Apply category + min-seeders, change sort, load more.
2. Expected: per-provider pagination advances independently; no repeated results;
   unsupported filters labeled client-side. Ref: FR-046..FR-048.

### S7 — No Torznab configured (US4)
1. With no Torznab indexer, search.
2. Expected: succeeds on built-in providers; no error for empty config. Ref: FR-033, SC-012.

### S8 — Adult default-off (US4/US5)
1. Fresh state → search.
2. Expected: no Adult category offered; adult results excluded. After explicit opt-in →
   Adult category appears; disable again → hidden. Ref: FR-064..FR-066, SC-010.

### S9 — Offline / timeout / cancel (US5)
1. Offline → submit: offline state, no request. Slow provider → bounded timeout, others show.
   Submit a new query mid-search → old results never overwrite new. Ref: FR-041, FR-042,
   FR-067.

### S10 — Accessibility (US5)
1. TalkBack + large fonts + RTL, light/dark.
2. Expected: meaningful semantics, no truncation/overlap. Ref: NFR-005, NFR-006, SC-009.

## Done criteria

All S1–S10 pass on a device/emulator; new module tests and the regression suite are green;
`./gradlew :app:lintDebug` reports no new errors. Provider API specifics (field names,
rate-limit headers, Archive download URL, Torznab variance) are confirmed via fixtures
(research D15), not live-only assumptions.