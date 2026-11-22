# PullDock

PullDock is a native Android application (Kotlin + Jetpack Compose) for managing
authorized media downloads and BitTorrent transfers.

This repository uses Spec Kit for spec-driven development. Read
[AGENTS.md](AGENTS.md) before making changes, and read
[.specify/memory/constitution.md](.specify/memory/constitution.md) for the engineering
constitution.

## Tech stack

- Kotlin, Jetpack Compose, Material 3
- Clean Architecture (presentation / domain / data / platform / native engines)
- Hilt for dependency injection
- Room for durable structured data, DataStore for preferences
- AndroidX Media3 for media playback
- Kotlin Coroutines + Flow
- libtorrent4j (pinned 2.1.0-39) for torrent transfers
- Gradle 9.6 + AGP 9.4 + version catalog

## Build

```bash
./gradlew assembleDebug        # all 25 modules
./gradlew :app:assembleRelease # R8 + resource shrinking (unsigned)
```

Tests:

```bash
./gradlew :app:testDebugUnitTest :core:common:test :core:model:test \
  :domain:download:test :domain:torrent:test :domain:extractor:test \
  :data:download:testDebugUnitTest :data:torrent:testDebugUnitTest \
  :data:extractor:testDebugUnitTest \
  :feature:queue:testDebugUnitTest :feature:add:testDebugUnitTest \
  :feature:onboarding:testDebugUnitTest :feature:settings:testDebugUnitTest
```

CI (`.github/workflows/ci.yml`) runs the build, unit tests, lint, a 64-bit-only ABI
packaging check, and a secret scan on every push/PR.

## Structure

- `app/` — composition root: navigation shell, execution layer (UIDT jobs, dataSync
  service, boot receiver, notifications), Hilt graph
- `core/` — platform infrastructure: common (dispatchers), model (shared primitives),
  designsystem (theme/tokens), database (Room), datastore, storage, network
- `domain/` — pure Kotlin: download/torrent/extractor contracts, models, state machine
- `data/` — repository implementations, queue coordinator/scheduler, engine adapters
- `engines/` — native/runtime integration: torrent-libtorrent, extractor-ytdlp,
  media-ffmpeg
- `feature/` — screen modules: onboarding, home, add, queue, download-detail, library,
  player, settings, legal
- `docs/` — security threat model, extractor lifecycle/update strategy, licenses,
  known limitations
- `specs/` — per-feature spec, plan, tasks, research, contracts, ADRs

See [AGENTS.md](AGENTS.md) for the full module map, dependency rules, and engineering
rules; [docs/KNOWN_LIMITATIONS.md](docs/KNOWN_LIMITATIONS.md) for honest current
constraints (device-verification steps pending).

## Spec Kit workflow

```bash
/speckit-specify  ->  /speckit-plan  ->  /speckit-tasks  ->  /speckit-implement
```

Feature documents live in `specs/<NNN-short-name>/`.
