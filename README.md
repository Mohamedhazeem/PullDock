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
- Gradle 9.6 + AGP 9.4 + version catalog

## Build

```bash
./gradlew :app:assembleDebug
```

Tests:

```bash
./gradlew :app:testDebugUnitTest
```

## Structure

- `app/` — application module (`com.pulldock`)
- `gradle/libs.versions.toml` — version catalog
- `.specify/` — Spec Kit configuration, templates, and constitution
- `specs/<feature>/` — per-feature spec, plan, tasks, research, contracts

See [AGENTS.md](AGENTS.md) for the full repository map and engineering rules.

## Spec Kit workflow

```bash
/speckit-specify  ->  /speckit-plan  ->  /speckit-tasks  ->  /speckit-implement
```

Feature documents live in `specs/<NNN-short-name>/`.
