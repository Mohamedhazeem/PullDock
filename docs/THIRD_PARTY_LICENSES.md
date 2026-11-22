# Third-Party Licenses (T178)

Pinned versions live in `gradle/libs.versions.toml` (single source of truth). The
complete generated report ships with the release; this document records the core set
and redistribution terms verified so far.

| Dependency | Version | License | Notes |
|---|---|---|---|
| Kotlin stdlib / coroutines | 2.4.20 / 1.11.0 | Apache 2.0 | Language + async runtime |
| Jetpack Compose (BOM 2026.09.00) + Material 3 | 1.4.0 | Apache 2.0 | UI toolkit |
| AndroidX (core, lifecycle, navigation 2.10.2, Room 2.7.0, DataStore 1.1.7, Media3 1.11.1, WorkManager 2.10.0) | per catalog | Apache 2.0 | Platform libraries |
| Hilt / Dagger | 2.60.1 | Apache 2.0 | DI |
| javax.inject | 1 | Apache 2.0 | JSR-330 annotations |
| libtorrent4j | 2.1.0-39 | MIT (wrapper); libtorrent core BSD-3-clause | Torrent engine; arm64-v8a/x86_64 native jars verified |
| yt-dlp (planned, T080) | to be pinned at PoC | Unlicense | Bundled inside signed app releases only |
| Chaquopy CPython runtime (planned, T080) | to be pinned at PoC | PSF/MIT per component | Verification pending |
| FFmpeg/FFprobe build (planned, D2) | to be pinned | LGPL-2.1+ (build configuration to be recorded) | Verification pending |

**Policy**: no dependency ships without its license being checked for redistribution
(AGENTS.md §17). This file is updated whenever the catalog changes.
