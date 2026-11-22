# Tasks: PullDock — Production-Grade Android Download & Media Application

**Input**: Design documents from `specs/001-production-android-app/`
**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [data-model.md](data-model.md), [contracts/](contracts/), [research.md](research.md), [quickstart.md](quickstart.md)

**Tests**: Included. The specification (§10, FR-072) makes testing mandatory, so each slice
has explicit test tasks and acceptance criteria.

**Organization**: Tasks are grouped by the specification's implementation slices (§22), each
of which maps to user stories. Story labels (`[US1]`…`[US10]`) provide traceability to
`spec.md`.

## Format: `[ID] [P?] [Story?] Description`

- **[P]**: safe to run in parallel (different files, no dependency on incomplete tasks).
- **[USn]**: the user story this task serves (see `spec.md`).
- Every task names exact file paths.

## Path Conventions

Gradle multi-module root. Modules: `:app`, `:core:{common,model,designsystem,database,datastore,storage,network}`,
`:domain:{download,torrent,extractor}`, `:data:{download,torrent,extractor}`,
`:engines:{torrent-libtorrent,extractor-ytdlp,media-ffmpeg}`, `:feature:{onboarding,home,add,queue,download-detail,library,player,settings,legal}`.

---

## Phase 1: Slice 0 — Project Foundation

**Purpose**: buildable multi-module scaffold, design system, navigation shell, persistence
skeletons, CI foundation.

**⚠️ BLOCKING**: Slices 1–7 depend on this phase.

- [X] T001 Create Gradle module skeleton and include all modules in `settings.gradle.kts` (one module dir + `build.gradle.kts` per module; no empty modules left unused)
- [X] T002 [P] Add pure-Kotlin module config and `AppDispatchers` (`Dispatchers.Io/Default/Main`) in `core/common/src/main/java/com/pulldock/core/common/dispatchers/AppDispatchers.kt`
- [X] T003 [P] Add shared primitives `DownloadType`, `DownloadState`, `DownloadError`, `ProviderId` in `core/model/src/main/java/com/pulldock/core/model/`
- [X] T004 [P] Add `PullDockTheme`, token object, and color/typography/spacing in `core/designsystem/src/main/java/com/pulldock/core/designsystem/`
- [X] T005 [P] Add Room + KSP config and `PullDockDatabase` skeleton in `core/database/build.gradle.kts` and `core/database/src/main/java/com/pulldock/core/database/PullDockDatabase.kt`
- [X] T006 [P] Add DataStore config and `SettingsDataStore` skeleton in `core/datastore/src/main/java/com/pulldock/core/datastore/SettingsDataStore.kt`
- [X] T007 [P] Add SAF/MediaStore dependencies and `StorageRepository` interface in `core/storage/src/main/java/com/pulldock/core/storage/`
- [X] T008 [P] Add connectivity + UIDT dependencies and `ConnectivityMonitor` interface in `core/network/src/main/java/com/pulldock/core/network/`
- [X] T009 [P] Add pure-Kotlin `build.gradle.kts` for `domain/download`, `domain/torrent`, `domain/extractor` with no Android dependencies
- [X] T010 [P] Add `build.gradle.kts` for `data/download`, `data/torrent`, `data/extractor` depending only on `:domain:*` and `:core:*`
- [X] T011 [P] Add `build.gradle.kts` for `engines/torrent-libtorrent`, `engines/extractor-ytdlp`, `engines/media-ffmpeg`
- [X] T012 [P] Add `build.gradle.kts` for `feature/onboarding`, `feature/home`, `feature:add`, `feature:queue`, `feature:download-detail`, `feature:library`, `feature:player`, `feature:settings`, `feature/legal`
- [X] T013 Wire Hilt in `app/build.gradle.kts` and verify the dependency graph with `./gradlew :app:assembleDebug`
- [X] T014 [P] Add navigation shell: `RootNavHost`, typed routes, `MainScaffold` in `app/src/main/java/com/pulldock/navigation/`
- [X] T015 [P] Add light/dark theme wiring in `core/designsystem/src/main/java/com/pulldock/core/designsystem/theme/PullDockTheme.kt`
- [X] T016 [P] Add `proguard-rules.pro` entries for Compose, Hilt, Room, and native libs in `app/proguard-rules.pro`
- [X] T017 Add CI jobs (build, unit test, lint, static analysis, format, dependency verification, license report, ABI packaging check, secret scan) in `.github/workflows/ci.yml`
      *(Implemented: build, unit tests, lint, ABI packaging check, secret scan. Static analysis, formatting, dependency verification, and license report jobs are intentionally absent — their tooling is not configured in the repo (AGENTS.md §13); T176 activates those gates in Slice 7. CI documents the planned activation rather than shipping fake-green jobs.)*
- [X] T018 Verify Slice 0: `./gradlew :app:assembleDebug :app:lintDebug :app:testDebugUnitTest` all succeed
      *(Executed 2026-11-22: all three commands BUILD SUCCESSFUL; `assembleDebug` re-run after T019 covers the whole 25-module graph; ABI check script verified universal + arm64-v8a + x86_64 APKs, no 32-bit ABIs; unit tests 5/5 passing.)*
- [X] T019 [P] Configure 64-bit ABI packaging (`ndk.abiFilters` arm64-v8a and x86_64, ABI splits) in `app/build.gradle.kts` and verify no 32-bit APK is produced (re-run T018 after this task)

**Checkpoint**: foundation builds, Hilt graph resolves, app launches to the navigation shell.

---

## Phase 2: Slice 1 — Domain and Persistence

**Purpose**: pure domain model, state machine, repository contracts, Room schema, migrations,
recovery model. **BLOCKING** for all user stories.

- [X] T020 [P] Define `DownloadTask`, `DownloadProgress`, `TransferLimits`, `StorageDestination`, `DownloadPolicy` in `domain/download/src/main/java/com/pulldock/domain/download/model/`
- [X] T021 [P] Define `DownloadState` enum and `DownloadStateMachine` with the legal transitions from `data-model.md` §2.2 in `domain/download/src/main/java/com/pulldock/domain/download/state/DownloadStateMachine.kt`
- [X] T022 [P] Define `TorrentTaskDetails`, `TorrentFile`, `FileSelection`, `ResumeBlob` in `domain/torrent/src/main/java/com/pulldock/domain/torrent/model/`
- [X] T023 [P] Define `ExtractorTaskDetails`, `MediaFormat`, `MediaDescriptor`, `MediaSource` in `domain/extractor/src/main/java/com/pulldock/domain/extractor/model/`
- [X] T024 [P] Define repository contracts `DownloadRepository`, `MediaLibraryRepository`, `StorageRepository`, `NetworkPolicyRepository`, `ExtractorRuntimeRepository`, `ProviderCompatibilityRepository`, `UpdateManifestRepository` in `domain/download/src/main/java/com/pulldock/domain/download/repository/`
- [X] T025 [P] Define `QueueCoordinator` contract and `QueueState` in `domain/download/src/main/java/com/pulldock/domain/download/queue/QueueCoordinator.kt`
- [X] T026 [P] Define `TorrentEngine` contract and `TorrentResult`/`TorrentEvent` in `contracts/torrent-engine.md` → `domain/torrent/src/main/java/com/pulldock/domain/torrent/TorrentEngine.kt`
- [X] T027 [P] Define `MediaExtractor`, `Provider`, `ExtractorResult`/`ExtractorEvent` in `domain/extractor/src/main/java/com/pulldock/domain/extractor/MediaExtractor.kt`
- [X] T028 [P] Define use cases `AddTorrent`, `FetchTorrentMetadata`, `SelectTorrentFiles`, `StartDownload`, `PauseDownload`, `ResumeDownload`, `CancelDownload`, `RetryDownload`, `RemoveDownload`, `ObserveDownloadQueue`, `ObserveDownloadDetails`, `ResolveMediaUrl`, `FetchAvailableFormats`, `EnqueueDownload` in `domain/download/src/main/java/com/pulldock/domain/download/usecase/`
- [X] T029 Add Room entities `DownloadTaskEntity`, `TorrentTaskEntity`, `ExtractorTaskEntity`, `RecoveryMarkerEntity` in `core/database/src/main/java/com/pulldock/core/database/entity/` with constraints/indexes from `data-model.md` §4.1
- [X] T030 Add DAOs `DownloadTaskDao`, `TorrentTaskDao`, `ExtractorTaskDao`, `RecoveryMarkerDao` in `core/database/src/main/java/com/pulldock/core/database/dao/`
- [X] T031 Add `PullDockDatabase` version 1 + `RoomModule` binding in `core/database/src/main/java/com/pulldock/core/database/RoomModule.kt`
- [X] T032 Add migration framework: `Migrations` object and a test harness that opens a v(n-1) database fixture in `core/database/src/androidTest/`
- [X] T033 Add `DownloadRepositoryImpl` and mappers in `data/download/src/main/java/com/pulldock/data/download/DownloadRepositoryImpl.kt`
- [X] T034 Add `QueueCoordinatorImpl` skeleton (state flow, no engine calls yet) in `data/download/src/main/java/com/pulldock/data/download/QueueCoordinatorImpl.kt`
- [X] T035 Add `DownloadStateMachineTest` covering every legal transition and rejecting illegal ones in `domain/download/src/test/java/com/pulldock/domain/download/state/DownloadStateMachineTest.kt`
- [X] T036 Add domain unit tests for use cases with fake repositories in `domain/download/src/test/java/com/pulldock/domain/download/usecase/`
- [X] T037 Add Room instrumentation tests for CRUD, indexes, and migration in `core/database/src/androidTest/java/com/pulldock/core/database/`
- [X] T038 Verify Slice 1: `./gradlew :domain:download:testDebugUnitTest :core:database:testDebugUnitTest` pass

**Checkpoint**: domain and persistence compile and test independently; repository contracts
exist for every later slice.

---

## Phase 3: Slice 2 — Core UI and Navigation

**Purpose**: onboarding/consent, home, add-download, queue, details, settings, empty/loading/
error states, accessibility. Serves **US1**, **US4**, **US10**.

- [X] T039 [US4] Add destructive-action safeguards (confirmation dialogs for remove/delete, undo for reversible actions) and `PdConfirmDialog` in `core/designsystem/src/main/java/com/pulldock/core/designsystem/components/PdConfirmDialog.kt`
- [X] T040 [P] [US1] Implement consent/onboarding screen and `OnboardingViewModel` in `feature/onboarding/src/main/java/com/pulldock/feature/onboarding/`
- [X] T041 [US1] Persist versioned acknowledgement in `core/datastore/src/main/java/com/pulldock/core/datastore/ConsentStore.kt` and re-prompt on version change
- [X] T042 [P] [US10] Add design-system components `PdButton`, `PdProgress`, `PdEmptyState`, `PdErrorState`, `PdLoadingState` in `core/designsystem/src/main/java/com/pulldock/core/designsystem/components/`
- [X] T043 [P] Implement `HomeScreen` + `HomeViewModel` (quick add, active summary, recent) in `feature/home/src/main/java/com/pulldock/feature/home/`
- [X] T044 [P] Implement `AddDownloadScreen` + `AddDownloadViewModel` (URL/magnet/torrent entry, validation) in `feature/add/src/main/java/com/pulldock/feature/add/`
- [X] T045 [P] [US4] Implement `QueueScreen` + `QueueViewModel` (list, filters, item controls) in `feature/queue/src/main/java/com/pulldock/feature/queue/`
- [X] T046 [P] Implement `DownloadDetailScreen` + `DownloadDetailViewModel` in `feature/download-detail/src/main/java/com/pulldock/feature/downloaddetail/`
- [X] T047 [P] Implement `SettingsScreen` + `SettingsViewModel` (groups: downloads, network, battery, torrent, storage, notifications, playback, appearance, privacy, legal, about) in `feature/settings/src/main/java/com/pulldock/feature/settings/`
- [X] T048 [P] Implement `LegalScreen` and third-party license list in `feature/legal/src/main/java/com/pulldock/feature/legal/`
- [X] T049 [US1] Wire consent gate into navigation in `app/src/main/java/com/pulldock/navigation/RootNavHost.kt` (download actions blocked until acknowledged)
- [X] T050 [US4] Add queue filter state and per-task action intents in `feature/queue/src/main/java/com/pulldock/feature/queue/QueueUiState.kt`
- [X] T051 [US10] Add accessibility semantics and 48dp targets across all Slice 2 screens in `feature/*/src/main/`
- [X] T052 Add adaptive navigation (bottom bar / rail / list-detail) using WindowSizeClass in `app/src/main/java/com/pulldock/navigation/MainScaffold.kt`
- [X] T053 Add Compose UI tests for loading/empty/error/offline states per screen in `feature/*/src/androidTest/`
- [X] T054 Add ViewModel unit tests for each Slice 2 feature in `feature/*/src/test/`
- [X] T055 Verify Slice 2: `./gradlew :app:connectedDebugAndroidTest` for the new screens

**Checkpoint**: all core screens render states and are navigable with consent enforced.

---

## Phase 4: Slice 3 — Torrent Engine

**Purpose**: verified libtorrent4j integration. Serves **US2**, **US9**. **HIGH RISK** — start
with the feasibility PoC.

- [ ] T056 Implement `SeedingPolicy` and persist it on `TorrentTaskEntity` in `domain/torrent/src/main/java/com/pulldock/domain/torrent/model/SeedingPolicy.kt` and `core/database/src/main/java/com/pulldock/core/database/entity/TorrentTaskEntity.kt`
- [ ] T057 [US2] Implement seeding controls (seed-after-completion, max upload rate, stop ratio, time limit, per-torrent override, stop-all) in `feature/download-detail/src/main/java/com/pulldock/feature/downloaddetail/SeedingControls.kt` and apply them via `TorrentEngineImpl.setLimits`
- [ ] T058 Add seeding unit tests (defaults, stop-ratio and time-limit transitions, no indefinite seeding) in `domain/torrent/src/test/` and `data/torrent/src/test/`
- [ ] T060 Run the torrent dependency feasibility PoC: pin `libtorrent4j` for arm64-v8a/x86_64 with minSdk 26, verify init/close and ABI packaging; record result in `research.md` (resolves D1)
- [ ] T061 [P] Add `TorrentEngineImpl` skeleton and session factory in `data/torrent/src/main/java/com/pulldock/data/torrent/TorrentEngineImpl.kt`
- [ ] T062 Implement `LibtorrentSession` ownership and idempotent `close()` in `engines/torrent-libtorrent/src/main/java/com/pulldock/engines/torrent/LibtorrentSession.kt`
- [ ] T063 Implement alert loop draining to typed `TorrentEvent`s on a single thread in `engines/torrent-libtorrent/src/main/java/com/pulldock/engines/torrent/AlertLoop.kt`
- [ ] T064 Implement magnet ingestion `addTorrent(MAGNET)` and `TorrentEngineImpl.addTorrent` in `engines/torrent-libtorrent/src/main/java/com/pulldock/engines/torrent/`
- [ ] T065 Implement `.torrent` ingestion via SAF in `feature/add/src/main/java/com/pulldock/feature/add/TorrentFilePicker.kt` and `data/torrent/`
- [ ] T066 Implement `fetchMetadata` with timeout and `METADATA_TIMEOUT` mapping in `data/torrent/src/main/java/com/pulldock/data/torrent/TorrentEngineImpl.kt`
- [ ] T067 [US2] Implement file selection UI and `selectFiles` priority updates in `feature/download-detail/src/main/java/com/pulldock/feature/downloaddetail/` and `data/torrent/`
- [ ] T068 Implement start/pause/resume with idempotent pause and duplicate-start prevention in `data/torrent/src/main/java/com/pulldock/data/torrent/TorrentEngineImpl.kt`
- [ ] T069 Implement recheck, remove (no file delete), file priority, and limits in `data/torrent/`
- [ ] T070 Implement resume-data export/import as versioned opaque blobs in `data/torrent/src/main/java/com/pulldock/data/torrent/ResumeData.kt`
- [ ] T071 [US9] Implement recovery from resume data and mark unrecoverable tasks `NEEDS_USER_ACTION` in `data/download/src/main/java/com/pulldock/data/download/Recovery.kt`
- [ ] T072 Map torrent alerts to `DownloadError` (no peers, corrupt piece, tracker failure) in `data/torrent/src/main/java/com/pulldock/data/torrent/ErrorMapper.kt`
- [ ] T073 Add native engine tests: init/close, metadata, pause/resume, resume data, invalid torrent in `engines/torrent-libtorrent/src/test/` and `data/torrent/src/test/`
- [ ] T074 Add share-intent handling for magnet/torrent in `app/src/main/AndroidManifest.xml` and `MainActivity.kt`
- [ ] T075 Verify Slice 3 on a 64-bit device: add magnet, select files, pause/resume, recover after force-stop (record actual results)

**Checkpoint**: a real torrent can be added, selected, transferred, paused/resumed, and
recovered.

---

## Phase 5: Slice 4 — Media Extractor

**Purpose**: verified extractor runtime and provider registry. Serves **US3**, **US8**.
**HIGH RISK** — start with the runtime feasibility PoC.

- [ ] T080 Run the extractor runtime feasibility PoC: Chaquopy CPython 3.13 + pinned yt-dlp on arm64-v8a/x86_64, verify resolve/download; record in `research.md` (resolves D2/D3)
- [ ] T081 [P] Add `YtDlpRuntime` bootstrap and teardown in `engines/extractor-ytdlp/src/main/java/com/pulldock/engines/extractor/YtDlpRuntime.kt`
- [ ] T082 [P] Add `FfmpegProcess`/`Ffprobe` with argument-array invocation, bounded output, exit codes in `engines/media-ffmpeg/src/main/java/com/pulldock/engines/ffmpeg/`
- [ ] T083 Add `ExtractorRuntimeInfo` model and `ExtractorRuntimeValidator` in `data/extractor/src/main/java/com/pulldock/data/extractor/RuntimeValidator.kt`
- [ ] T084 Implement provider registry and `Provider` implementations for YouTube, X, Instagram, Facebook, TikTok in `data/extractor/src/main/java/com/pulldock/data/extractor/provider/`
- [ ] T085 Implement URL recognition and `UNSUPPORTED_PROVIDER` mapping in `data/extractor/src/main/java/com/pulldock/data/extractor/UrlValidator.kt`
- [ ] T086 Implement `resolve` + `availableFormats` returning typed `MediaDescriptor`/`MediaFormat` in `data/extractor/src/main/java/com/pulldock/data/extractor/MediaExtractorImpl.kt`
- [ ] T087 [US3] Implement format selection UI in `feature/add/src/main/java/com/pulldock/feature/add/FormatSelection.kt`
- [ ] T088 Implement download to temp → verify → merge (FFmpeg) → finalize in `data/extractor/src/main/java/com/pulldock/data/extractor/DownloadPipeline.kt`
- [ ] T089 Implement provider error mapping (unsupported, provider change, rate limit, merge failed) in `data/extractor/src/main/java/com/pulldock/data/extractor/ErrorMapper.kt`
- [ ] T090 Implement cancellation, timeout, bounded retry, and exponential backoff with jitter in `data/extractor/src/main/java/com/pulldock/data/extractor/RetryPolicy.kt`
- [ ] T091 [US8] Implement `ProviderHealth` states and Settings provider-status UI in `feature/settings/src/main/java/com/pulldock/feature/settings/ProviderStatus.kt`
- [ ] T092 Add extractor runtime tests: compatible/mismatched combination, missing JS runtime, FFmpeg missing, provider failure isolation in `data/extractor/src/test/`
- [ ] T093 Add provider regression tests with authorized fixtures (no live-only dependency) in `data/extractor/src/test/java/com/pulldock/data/extractor/provider/`
- [ ] T094 Verify Slice 4: resolve an authorized fixture URL, select a format, download, merge, play (record actual results)

**Checkpoint**: a supported authorized URL resolves, downloads, merges, and plays; provider
failures are isolated.

---

## Phase 6: Slice 5 — Queue and Android Execution

**Purpose**: one coordinator, concurrency, retry, background execution, notifications,
recovery, network/battery policy. Serves **US4**, **US7**, **US9**.

- [ ] T100 Implement scheduling, fairness, global/per-engine concurrency in `data/download/src/main/java/com/pulldock/data/download/QueueCoordinatorImpl.kt`
- [ ] T101 Implement priority, manual reorder, and persisted queue order in `data/download/src/main/java/com/pulldock/data/download/QueueOrder.kt`
- [ ] T102 Implement retry with persisted backoff/jitter and `RETRY_WAIT` in `data/download/src/main/java/com/pulldock/data/download/RetryCoordinator.kt`
- [ ] T103 Implement duplicate prevention via `sourceHash` and coordinator check in `data/download/src/main/java/com/pulldock/data/download/DuplicateGuard.kt`
- [ ] T104 Implement event reconciliation (idempotent by task id + sequence) in `data/download/src/main/java/com/pulldock/data/download/EventReconciler.kt`
- [ ] T105 [US7] Implement `NetworkPolicyRepository` and Wi-Fi-only/metered/battery policies in `core/datastore/src/main/java/com/pulldock/core/datastore/NetworkPolicyStore.kt`
- [ ] T106 [US7] Implement battery-threshold pause and rate-limit enforcement in `data/download/src/main/java/com/pulldock/data/download/PolicyEnforcer.kt`
- [ ] T107 Implement UIDT job scheduling (API 34+) in `core/network/src/main/java/com/pulldock/core/network/UidtScheduler.kt`
- [ ] T108 Implement `dataSync` foreground service for API <34 with notification and prompt `stopSelf()` on timeout in `app/src/main/java/com/pulldock/execution/TransferService.kt`
- [ ] T109 Implement WorkManager deferrable jobs (recovery, reconciliation, cleanup) in `app/src/main/java/com/pulldock/execution/`
- [ ] T110 [US9] Implement boot recovery that restores state and does not auto-start a restricted service in `app/src/main/java/com/pulldock/execution/BootReceiver.kt`
- [ ] T111 Implement notification pause/resume/stop actions and notification-permission fallback in `app/src/main/java/com/pulldock/execution/TransferNotifications.kt`
- [ ] T112 Add background execution tests (UIDT eligibility, dataSync timeout, boot path) in `app/src/androidTest/` and `core/network/src/test/`
- [ ] T113 Add queue tests: concurrency limits, duplicate start, starvation, out-of-order events in `data/download/src/test/`
- [ ] T114 Verify Slice 5 on device: background transfer, Doze, force-stop recovery, reboot recovery (record actual results)

**Checkpoint**: both engine types run under one queue with limits, retries, notifications,
and recovery.

---

## Phase 7: Extractor Lifecycle and Update Management

**Purpose**: dedicated group for extractor version management, compatibility, signed updates,
and degradation. Serves **US8**. Depends on Slices 4 and 5.

- [ ] T120 Implement `ExtractorRuntimeInfo` inventory persistence in `core/database/src/main/java/com/pulldock/core/database/entity/ExtractorRuntimeEntity.kt`
- [ ] T121 Implement dependency compatibility validation (yt-dlp ↔ yt-dlp-ejs ↔ Python ↔ JS runtime ↔ FFmpeg/FFprobe) in `data/extractor/src/main/java/com/pulldock/data/extractor/CompatibilityValidator.kt`
- [ ] T122 Implement `ProviderCompatibilityRegistry` and matrix records in `data/extractor/src/main/java/com/pulldock/data/extractor/ProviderCompatibilityRegistry.kt`
- [ ] T123 Implement extractor initialization health check (non-crashing, typed error) in `data/extractor/src/main/java/com/pulldock/data/extractor/InitializerHealthCheck.kt`
- [ ] T124 Implement signed metadata manifest parser and schema validation in `data/extractor/src/main/java/com/pulldock/data/extractor/update/ManifestParser.kt`
- [ ] T125 Implement signature verification with app-pinned public key in `data/extractor/src/main/java/com/pulldock/data/extractor/update/SignatureVerifier.kt`
- [ ] T126 Implement expiration and rollback protection plus cached last-known-good in `data/extractor/src/main/java/com/pulldock/data/extractor/update/ManifestGuard.kt`
- [ ] T127 [US8] Implement update availability UI and provider status in `feature/settings/src/main/java/com/pulldock/feature/settings/UpdateStatus.kt`
- [ ] T128 Implement stale-provider detection distinguishing network error from staleness in `data/extractor/src/main/java/com/pulldock/data/extractor/StalenessDetector.kt`
- [ ] T129 Implement provider degradation and safe disablement in `data/extractor/src/main/java/com/pulldock/data/extractor/ProviderDegradation.kt`
- [ ] T130 Implement active-download recovery during application replacement in `data/download/src/main/java/com/pulldock/data/download/UpdateHandoff.kt`
- [ ] T131 Add invalid-manifest tests (bad schema, bad signature) in `data/extractor/src/test/java/.../update/`
- [ ] T132 Add expired-manifest and rollback-protection tests in `data/extractor/src/test/java/.../update/`
- [ ] T133 Add mismatched yt-dlp/EJS tests in `data/extractor/src/test/`
- [ ] T134 Add missing-JS-runtime tests in `data/extractor/src/test/`
- [ ] T135 Add FFmpeg compatibility tests in `data/extractor/src/test/`
- [ ] T136 Add update-cancellation and offline update-check tests in `data/extractor/src/test/`
- [ ] T137 Add provider regression tests (one provider failure does not break others) in `data/extractor/src/test/`
- [ ] T138 Write update security and distribution documentation in `docs/EXTRACTOR_LIFECYCLE.md` and `docs/EXTRACTOR_UPDATE_STRATEGY.md`
- [ ] T139 Verify the lifecycle group: run the full update/compatibility test set and record actual results
- [ ] T140 Add a remote-code-loading guard test asserting no DCL/`DexClassLoader`/remote-module path exists in `data/extractor/src/test/java/com/pulldock/data/extractor/NoRemoteCodeTest.kt`
- [ ] T141 Add a no-watermark restriction test asserting no watermark-removal path exists in `data/extractor/src/test/java/com/pulldock/data/extractor/NoWatermarkTest.kt`

**Checkpoint**: incompatible runtimes are detected, manifests verified, providers degrade
independently, updates never execute remote code.

---

## Phase 8: Slice 6 — Storage, Library and Playback

**Purpose**: storage abstraction, MediaStore/SAF, finalization, library, Media3, streaming.
Serves **US5**, **US6**, **US7**.

- [ ] T150 [P] Implement `StorageRepository` (SAF/MediaStore/app-specific) in `core/storage/src/main/java/com/pulldock/core/storage/StorageRepositoryImpl.kt`
- [ ] T151 Implement `FileFinalizer` with temp → verify → atomic publish in `core/storage/src/main/java/com/pulldock/core/storage/FileFinalizer.kt`
- [ ] T152 Implement filename sanitization and `name (n).ext` collision strategy in `core/storage/src/main/java/com/pulldock/core/storage/FilenameSanitizer.kt`
- [ ] T153 Implement path-traversal/reserved-name/unicode-collision rejection in `core/storage/src/main/java/com/pulldock/core/storage/PathValidator.kt`
- [ ] T154 Implement MediaStore insertion and interrupted-write handling in `core/storage/src/main/java/com/pulldock/core/storage/MediaStoreWriter.kt`
- [ ] T155 Implement `MediaLibraryRepository` and reconciliation in `data/download/src/main/java/com/pulldock/data/download/MediaLibraryRepositoryImpl.kt`
- [ ] T156 [US5] Implement `LibraryScreen` + `LibraryViewModel` (sections, search, sort, filter) in `feature/library/src/main/java/com/pulldock/feature/library/`
- [ ] T157 [US5] Implement `PlayerScreen` + `PlayerViewModel` with Media3 in `feature/player/src/main/java/com/pulldock/feature/player/`
- [ ] T158 Implement playback position persistence (throttled) in `feature/player/src/main/java/com/pulldock/feature/player/PlaybackPositionStore.kt`
- [ ] T159 [US6] Implement loopback streaming bridge (token, byte-range, traversal defense) in `data/torrent/src/main/java/com/pulldock/data/torrent/StreamingBridge.kt`
- [ ] T160 Implement streaming lifecycle ownership in `feature/player/src/main/java/com/pulldock/feature/player/StreamingBridgeOwner.kt`
- [ ] T161 Add storage tests: failed finalize never publishes incomplete output, collision strategy, traversal rejection in `core/storage/src/test/`
- [ ] T162 Add library reconciliation and playback tests in `data/download/src/test/` and `feature/player/src/test/`
- [ ] T163 Verify Slice 6 on device: complete a download, library entry, playback resume, torrent streaming (record actual results)
- [ ] T164 Implement async thumbnail loading with a size cache and cancel-on-recycle in `feature/library/src/main/java/com/pulldock/feature/library/ThumbnailLoader.kt`
- [ ] T165 Implement audio/subtitle track selection when the media exposes more than one track in `feature/player/src/main/java/com/pulldock/feature/player/TrackSelection.kt` (hide the control otherwise)

**Checkpoint**: files finalize safely, library stays consistent, playback and streaming work.

---

## Phase 9: Slice 7 — Production Hardening

**Purpose**: security, accessibility, performance, migrations, CI gates, release, licenses.

- [ ] T170 [P] Run the security review: SSRF guard, filename handling, subprocess args, streaming exposure, log redaction, and DRM/paywall/authentication bypass checkpoints; record in `docs/SECURITY_THREAT_MODEL.md`
- [ ] T171 [P] Complete accessibility audit (TalkBack, contrast, large fonts, RTL, reduced motion) across all screens in `feature/*/src/main/`
- [ ] T172 [P] Run performance profiling (startup, large queue, thumbnails, native cleanup) and record in `docs/PERFORMANCE.md`
- [ ] T173 Add database migration tests for every schema version in `core/database/src/androidTest/`
- [ ] T174 Add native resource lifecycle checks (init/close, no leaks) in `engines/*/src/test/`
- [ ] T175 Add error-state review and user-message mapping audit in `core/model/src/main/java/com/pulldock/core/model/ErrorMessages.kt`
- [ ] T176 Add CI quality gates (static analysis, formatting, license report, ABI check, secret scan) in `.github/workflows/ci.yml`
- [ ] T177 Produce a signed release build and record the release checklist in `docs/RELEASE_CHECKLIST.md`
- [ ] T178 Generate the third-party license report in `docs/THIRD_PARTY_LICENSES.md`
- [ ] T179 [P] Write `docs/KNOWN_LIMITATIONS.md` (background limits, provider restrictions, 64-bit only)
- [ ] T180 Run the full `quickstart.md` validation and record actual results
- [ ] T181 [P] Audit every declared permission in `app/src/main/AndroidManifest.xml`, map each to a justification, and remove any unnecessary permission; record in `docs/SECURITY_THREAT_MODEL.md`
- [ ] T182 [P] Add a privacy verification check asserting no analytics SDK, no account, no clipboard read, and no remote upload path in `app/src/main/` and `core/`

**Checkpoint**: release build succeeds; all quality gates pass; documentation is current.

---

## Phase 10: Polish & Cross-Cutting Concerns

- [ ] T190 [P] Update `README.md` and `AGENTS.md` module map to match the final structure
- [ ] T191 [P] Final dependency/license audit in `gradle/libs.versions.toml`
- [ ] T192 Final regression pass across all slices and record outcomes in `research.md`
- [ ] T193 Confirm no `TODO`/mock/hardcoded-success path remains in release code paths

---

## Dependencies & Execution Order

### Phase dependencies

- **Slice 0**: none — start immediately.
- **Slice 1**: requires Slice 0. Blocks all user stories.
- **Slice 2**: requires Slice 1 (contracts/entities).
- **Slice 3**: requires Slice 1; benefits from Slice 2 for the detail UI (T067).
- **Slice 4**: requires Slice 1; format UI depends on Slice 2 (T087).
- **Slice 5**: requires Slices 1, 3, 4 (engines must exist to coordinate them).
- **Extractor Lifecycle**: requires Slices 4 and 5.
- **Slice 6**: requires Slices 1, 3, 5.
- **Slice 7**: requires all feature slices.
- **Polish**: requires Slice 7.

### Story completion order

US1 (consent) → Slice 2. US2 (torrent) → Slice 3. US3 (media) → Slice 4. US4 (queue) →
Slice 5. US5 (library/player) → Slice 6. US6 (streaming) → Slice 6. US7 (network/battery/
storage) → Slices 5–6. US8 (extractor health) → Slices 4 + Lifecycle. US9 (recovery) →
Slices 3, 5. US10 (accessibility) → Slices 2, 7.

### External verification / blocking risks

| Risk | Resolution task | Type |
|---|---|---|
| libtorrent4j min API vs minSdk 26 / ABI | T060 | Native verification |
| Extractor runtime (Chaquopy/yt-dlp) | T080 | Native verification |
| FFmpeg build + license | T081, T178 | Native + legal |
| JS runtime for yt-dlp-ejs | T080, T121 | Native verification |
| Background execution per API level | T107–T114 | Device testing |
| Provider authorization/legal | T093, T138, T170 | Human/legal review |
| Store distribution eligibility | T179 | Human/legal review |

### Within-slice order

Tests first where specified → models/contracts → services/adapters → UI → verification.

---

## Parallel Execution Examples

```bash
# Slice 0 module configs (T002–T012) can run in parallel — different files.
# Slice 1 models/contracts (T020–T028) can run in parallel.
# Slice 2 screens (T043–T048) can run in parallel once Slice 1 contracts exist.
# Slice 4 providers (T084) can be developed per-provider in parallel.
# Slice 7 audit tasks (T170–T172, T179) can run in parallel.
```

---

## Implementation Strategy

### MVP first

1. Slice 0 (foundation) → Slice 1 (domain/persistence) → Slice 2 (core UX incl. US1).
2. STOP and validate: onboarding + navigation + empty/error states work.
3. Then Slice 3 (torrent) → validate a real magnet/torrent end-to-end.
4. Then Slice 4 (extractor) → validate an authorized URL end-to-end.

### Incremental delivery

Each slice is a buildable, testable increment. No slice depends on a future slice. Higher
slices add value without breaking earlier ones.

### Risk-first

T060 (torrent PoC) and T080 (extractor PoC) run at the start of their slices, before any
adapter code, because they can invalidate the architecture.

---

## Coverage Matrix (spec → tasks)

| Spec area | Tasks |
|---|---|
| US1 Onboarding/consent | T040, T041, T049 |
| US2 Torrent | T056–T058, T060–T075 |
| US3 Media extraction | T080–T094 |
| US4 Unified queue | T039, T045, T050, T100–T104, T113 |
| US5 Library/playback | T155–T158, T162, T164, T165 |
| US6 Streaming | T159, T160 |
| US7 Network/battery/storage | T105, T106, T150–T154 |
| US8 Extractor health | T091, T120–T141 |
| US9 Recovery | T070, T071, T110, T130 |
| US10 Accessibility | T042, T051, T171 |
| Background execution | T107–T114 |
| Extractor lifecycle/updates | T120–T141 |
| Release/hardening | T170–T182 |
| ABI/packaging | T019 |

## Notes

- [P] tasks have no dependency on incomplete tasks and touch different files.
- Each slice ends with a verification task that records **actual** results; never claim a
  test passed that was not run.
- Tasks requiring physical-device, native-verification, or legal review are marked above.
- Do not disable lint/tests to make a task pass (constitution XIII).
