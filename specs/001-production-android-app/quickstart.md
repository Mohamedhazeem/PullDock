# Quickstart: PullDock Validation Guide

**Feature**: `001-production-android-app` | **Date**: 2026-11-22
**Plan**: [plan.md](plan.md) · **Data model**: [data-model.md](data-model.md) · **Contracts**: [contracts/](contracts)

This is a run/validation guide, not an implementation. It lists runnable checks that prove a
slice works end-to-end. Exact task-level steps belong in `tasks.md`.

## Prerequisites

- JDK 17, Android SDK with API 36/37 and build-tools 36.0.0+.
- `ANDROID_HOME` set; `local.properties` or env pointing at the SDK.
- 64-bit device or emulator (arm64-v8a or x86_64); 32-bit is out of scope.
- For torrent/extractor slices: authorized test content only (owned, licensed, synthetic,
  or Creative Commons fixtures). No DRM, paywalled, private, or age-restricted content.

## Build and test commands

```bash
# Debug build (current scaffold)
./gradlew :app:assembleDebug

# Unit tests
./gradlew :app:testDebugUnitTest

# Lint
./gradlew :app:lintDebug

# Module-specific (once modules exist)
./gradlew :domain:download:testDebugUnitTest
./gradlew :data:download:testDebugUnitTest
./gradlew :data:torrent:testDebugUnitTest
./gradlew :data:extractor:testDebugUnitTest

# Instrumented (device/emulator required)
./gradlew :app:connectedDebugAndroidTest

# Release build (signing config excluded from source control)
./gradlew :app:assembleRelease
```

## Slice validation scenarios

### Slice 0 — Engineering foundation

- `./gradlew :app:assembleDebug` succeeds; app launches to a reachable first screen.
- Hilt graph resolves (`PullDockApplication` + `AppModule`).
- Theme renders in light and dark.
- `./gradlew :app:lintDebug` reports 0 errors.

### Slice 1 — Domain and persistence

- State machine: every legal transition in [data-model.md](data-model.md) §2.2 has a unit
  test; every illegal transition is rejected.
- Room: create/read/update/delete a task; migration from the previous version succeeds;
  a failed migration marks tasks `NEEDS_USER_ACTION`, never deletes.
- Invariants §3 hold under a concurrency test (parallel pause/resume/cancel).

### Slice 2 — Core application UX

- Onboarding blocks download actions until consent is acknowledged; re-acknowledges on
  version change.
- Add-download flow validates input and shows typed errors.
- Queue shows both types with filters and per-task controls.
- Every screen renders Loading, Empty, Content, Error, Offline, and Restored states.

### Slice 3 — Torrent engine

- Add a valid magnet and a valid `.torrent` fixture; metadata appears asynchronously.
- Select a file subset; only selected files download.
- Pause/resume preserves progress; repeated start creates no second session.
- Resume data is exported on pause and restored after process death.
- Malformed magnet/torrent and no-peer cases produce typed errors, not crashes.

### Slice 4 — Extractor engine

- Runtime validation succeeds for the pinned combination; a mismatched component is
  detected and reported.
- Resolve an authorized fixture URL; formats list is accurate.
- Separate audio/video streams merge, verify, and finalize atomically.
- A provider failure maps to a typed error and leaves other providers working.
- Unsupported ABI/runtime reports `RUNTIME_INCOMPATIBLE`, not a crash.

### Slice 5 — Execution and recovery

- Long transfer runs via UIDT on API 34+; `dataSync` FGS is used on older versions.
- Android 15 `onTimeout` stops the service promptly.
- Force-stop + relaunch restores the queue with no duplicate sessions.
- Reboot restores persisted state and surfaces recovery; no restricted service auto-starts.

### Slice 6 — Library and playback

- A completed download appears in the library with metadata.
- Playback supports play/pause/seek/speed/volume/fullscreen and resumes position.
- Streaming an eligible downloading torrent works; the bridge is loopback-only and stops.

### Slice 7 — Production hardening

- `./gradlew :app:assembleRelease` succeeds with R8 and resource shrinking.
- Lint/static analysis/format checks pass; license report generated.
- Migration, recovery, and lifecycle instrumentation tests pass on API 26, 34, 35, 36.
- No secrets in the repo; no remote executable code path exists.

## Manual / device validation (not automated)

- Live provider compatibility is verified with authorized fixtures and recorded in the
  provider compatibility matrix; results are dated and method-noted.
- Long-running transfer, Doze/Battery Saver, network switching, and OEM background
  restrictions are validated on at least one physical device and documented.
- Torrent streaming is validated for seeking, buffering, missing pieces, and shutdown.

## Expected outcomes

- Each slice builds and its tests pass before the next dependent slice begins.
- No critical or high-severity defect remains open; documentation is updated.
- The release build is reproducible and signed; keystores are not in source control.
