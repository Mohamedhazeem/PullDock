# Implementation Plan: PullDock — Production-Grade Android Download & Media Application

**Branch**: `001-production-android-app` | **Date**: 2026-11-22 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/001-production-android-app/spec.md`

## Summary

PullDock is a local-first native Android application that unifies BitTorrent downloads and
authorized media downloads into one queue, with a local media library and in-app playback.
This plan defines a Clean Architecture, multi-module Kotlin/Compose implementation with two
isolated engine adapters (torrent and extractor), a single durable queue coordinator, an
API-level-aware background execution strategy, and a threat-model-driven security boundary.

The design is slice-oriented: every module is buildable on its own, and the dependency graph
is strictly directional so that domain logic stays free of Android and native-engine types.

## Technical Context

- **Language/Version**: Kotlin 2.4.20, compiled by AGP 9 built-in Kotlin (do NOT apply
  `org.jetbrains.kotlin.android`). JDK 17.
- **Primary Dependencies**: AGP 9.4.1, Gradle 9.6.0, Jetpack Compose (BOM 2026.09.00),
  Material 3, Hilt 2.60.1, Room 2.7.0 (KSP 2.3.14), DataStore 1.1.7, Media3 1.11.1,
  Coroutines 1.11.0, Navigation Compose, libtorrent4j (2.1.0-x, version locked by the plan
  PoC), yt-dlp-android (Chaquopy CPython 3.13), Android-compatible FFmpeg/FFprobe.
- **Storage**: Room (durable state), DataStore (preferences/policies), app-specific files
  (temporary/intermediate), MediaStore (completed public media), SAF (user-chosen
  destinations).
- **Testing**: JUnit4, `kotlinx-coroutines-test`, Compose UI test, Room testing, Espresso,
  AndroidJUnit4 instrumentation, Macrobenchmark/Baseline Profile.
- **Target Platform**: Android 8.0+ (API 26+; provisional pending the T060 libtorrent4j
  verification), compileSdk/targetSdk 37, **64-bit only** (arm64-v8a, x86_64).
- **Project Type**: mobile-app, Gradle multi-module, version catalog.
- **Performance Goals**: 60 fps UI; no main-thread I/O; throttled progress emission; bounded
  queue/event buffering; predictable native cleanup; large queues (1000+ tasks) remain
  responsive.
- **Constraints**: no remote dynamic code execution; scoped storage only; respect Android 15
  `dataSync` budget and Android 16 job quotas; no `MANAGE_EXTERNAL_STORAGE`; minimum
  permissions; signed application releases only.
- **Scale/Scope**: 5 media providers, 2 engine types, 10 user stories, ~76 functional
  requirements, large download queues, long-running transfers.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Constitution principle | Gate | Status |
|---|---|---|
| I. Correctness/reliability first | State machine, persistence, recovery, verification before completion | Pass |
| II. Kotlin + Compose + Material 3, pinned versions | Version catalog, Compose BOM, Material 3 | Pass |
| III. Clean Architecture, dependency inversion, no god classes | Module graph below; domain has no Android/engine deps | Pass |
| IV. Immutable state, structured concurrency, no main-thread blocking | UiState via StateFlow; Dispatchers enforced | Pass |
| V. Compose design system, states, accessibility | Design system module; per-screen state matrix | Pass |
| VI. Engine isolation, typed results, no native leakage | `TorrentEngine`/`MediaExtractor` contracts | Pass |
| VII. Room is durable source of truth; migrations; reconcilable | Database design; migration strategy | Pass |
| VIII. Background restrictions respected; document limits | Execution matrix; no unlimited promises | Pass |
| IX. Untrusted input; no shell concatenation; no secret logging | Security architecture | Pass |
| X. Testing mandatory | Testing architecture | Pass |
| XI. Centralized typed errors | `DownloadError` hierarchy | Pass |
| XII. Dependency justification and ADRs | research.md + ADRs | Pass |
| XIII. Slice workflow, no disabling checks | Slice plan in this document | Pass |
| XIV. Definition of done | quickstart.md | Pass |
| XV. Constitution enforcement | No weakening; conflicts flagged | Pass |
| Additional: extractor updates | Pinned runtime, signed app releases, no remote code | Pass |

No violations. Complexity Tracking below records the intentionally added structure.

## Architecture

### Architectural style

Clean Architecture with MVVM and unidirectional data flow (UDF), organized into strict
layers:

- **Presentation** (`:app` navigation + `:feature:*`): Compose screens, screen ViewModels,
  immutable UiState, UiEvent. No business logic, no engine/database/storage access.
- **Domain** (`:domain:*`): use cases, entities, repository and engine contracts, state
  machine, validation invariants. Pure Kotlin; no Android framework, no Room, no engines.
- **Data** (`:data:*`): repository implementations, queue coordinator, persistence mappers,
  engine adapters, runtime/compatibility services.
- **Engines** (`:engines:*`): native/runtime integration (JNI lifecycle, Python/JS runtime,
  FFmpeg process). Owns resource ownership and cleanup.
- **Platform infrastructure** (`:core:*`): dispatchers, network monitoring, storage, Room,
  DataStore, design system.

### Dependency direction

```
:app ──────────────────────────────► :feature:* ───────────────► :domain:* ◄────────────── :data:*
                                      │                          │                          │
                                      ▼                          ▼                          ▼
                                  :core:designsystem      :core:model              :core:common
                                      │                    :core:common             :core:database
                                      ▼                                           :core:datastore
                                  :core:model                                     :core:storage
                                                                                  :core:network
                                                                                        │
:app ──────────────────────────────► :data:* ─────────────────────────────────────────────┘
                                       │
                                       ▼
                                  :engines:*  (torrent / extractor / ffmpeg)
                                       │
                                       ▼
                                  :core:common, :core:model
```

Rules (enforced by module `build.gradle.kts` and review):

- `:feature:*` may depend on `:domain:*`, `:core:model`, `:core:designsystem`, `:core:common`.
- `:feature:*` MUST NOT depend on `:data:*`, `:engines:*`, `:core:database`,
  `:core:datastore`, `:core:storage`, or Room/Compose-model implementation types.
- `:domain:*` MUST NOT depend on Android classes, Room, DataStore, engines, or
  `androidx.compose`.
- `:data:*` depends on `:domain:*` and `:core:*`; implements domain contracts.
- `:engines:*` depends on `:core:common` and `:core:model`; exposes only typed results and
  engine callbacks translated at the adapter boundary.
- `:app` is the composition root: it depends on everything and wires Hilt and navigation.
- No circular dependencies. Domain is the innermost ring.

### Layer responsibilities

| Layer | Module(s) | Responsibility |
|---|---|---|
| Presentation | `:app`, `:feature:*` | Screens, ViewModels, UiState/UiEvent, navigation, accessibility semantics |
| Domain | `:domain:download`, `:domain:torrent`, `:domain:extractor` | Use cases, entities, contracts, state machine, invariants |
| Data | `:data:download`, `:data:torrent`, `:data:extractor` | Repository/queue implementations, engine adapters, runtime and compatibility services |
| Engines | `:engines:torrent-libtorrent`, `:engines:extractor-ytdlp`, `:engines:media-ffmpeg` | Native/runtime lifecycle, thread marshalling, cleanup |
| Infrastructure | `:core:common`, `:core:model`, `:core:designsystem`, `:core:database`, `:core:datastore`, `:core:storage`, `:core:network` | Cross-cutting capabilities |

### Repository contracts

Defined in `:domain:download` / `:domain:torrent` / `:domain:extractor`:

- `DownloadRepository` — CRUD + observation of `DownloadTask`, queue ordering, transitions.
- `QueueCoordinator` — scheduling/fairness/concurrency/retry; exposes `QueueStateFlow`.
- `MediaLibraryRepository` — completed media records, reconciliation with files.
- `StorageRepository` — destination resolution, temporary files, finalization, deletion.
- `NetworkPolicyRepository` — Wi-Fi-only/metered/battery/limit policy read/write.
- `ExtractorRuntimeRepository` — runtime inventory + validation state.
- `ProviderCompatibilityRepository` — provider status and compatibility records.
- `UpdateManifestRepository` — fetch/verify/cache signed manifest.

### Engine interfaces

- `TorrentEngine` (in `:domain:torrent`): `addTorrent`, `fetchMetadata`, `selectFiles`,
  `start`, `pause`, `resume`, `recheck`, `remove`, `setFilePriority`, `setLimits`,
  `observeEvents`, `requestStreamingBridge`; typed `TorrentResult`/`TorrentEvent`; explicit
  `cancel()`/`close()`.
- `MediaExtractor` (in `:domain:extractor`): `resolve`, `availableFormats`, `download`,
  `cancel`, `observeEvents`; `Provider` implements URL recognition/resolution.
- Both return sealed result types and never expose JNI/libtorrent/yt-dlp/FFmpeg/process types.

### Dependency injection strategy

Hilt. Composition root in `:app` (`PullDockApplication` + `AppModule`). Convention modules:

- `DispatcherModule` (`:core:common`): `Io`, `Default`, `Main`.
- `DatabaseModule` (`:core:database`): `RoomDatabase`, DAOs.
- `DataStoreModule` (`:core:datastore`): preferences DataStore.
- `NetworkModule` (`:core:network`): `ConnectivityMonitor`, `UidtScheduler`.
- `RepositoryModule` (`:data:download`): binds domain contracts to implementations.
- `EngineModule` (`:data:torrent`, `:data:extractor`): binds `TorrentEngine`, `MediaExtractor`,
  `ExtractorRuntimeValidator`.
- `QueueModule` (`:data:download`): `QueueCoordinator` singleton.
- Feature ViewModels are `@HiltViewModel`; screens use `hiltViewModel()`.

Engine bindings are behind `@InstallIn(SingletonComponent)` adapters that own native
resources and are released on process death.

### Navigation architecture

Navigation Compose with a typed route sealed hierarchy in `:app`:

```
RootNavHost
├── Onboarding (if consent required)
├── MainScaffold (bottom navigation / adaptive rail)
│   ├── Home
│   ├── Downloads (Queue)
│   ├── Library
│   └── Settings
├── AddDownload (modal flow: URL / magnet / torrent file)
├── DownloadDetail (torrent | extractor)
├── Player
├── ProviderStatus
└── Legal / About
```

Each feature module exposes a `@Composable` NavGraph entry. `:app` owns the `NavHost` and the
top-level destinations; nested graphs are contributed via an injected `FeatureNavGraph`
registry to avoid `:app` hardcoding every screen. Deep links: `pulldock://add?url=`,
`pulldock://downloads/{id}`.

Adaptive navigation: bottom bar on phones, navigation rail on tablets/foldables, list-detail
on expanded widths.

### State management

- Screen state: immutable `data class XxxUiState` exposed as `StateFlow` from the ViewModel.
- Events: `SharedFlow<XxxUiEvent>` (one-shot: snackbars, navigation, dialogs).
- Intents: `XxxIntent` methods on the ViewModel; UDF (state down, events up).
- Queue state: `QueueCoordinator` is the single source of truth, persisted in Room, exposed
  as `StateFlow<QueueState>`; progress updates are throttled (see Performance).
- No global mutable state; no `GlobalScope`; no `static` singletons outside Hilt.

### Error propagation

Sealed `DownloadError` hierarchy in `:core:model`:

```
DownloadError
├── InputError (invalid url, malformed magnet/torrent)
├── NetworkError (offline, DNS, TLS, timeout, metered)
├── EngineError.Torrent (metadata timeout, no peers, corrupt piece, ...)
├── EngineError.Extractor (unsupported, provider change, rate limit, merge failed)
├── StorageError (insufficient space, volume missing, SAF revoked, finalize failed)
├── PermissionError (notification, network policy)
├── ProcessingError (ffmpeg exit, merge, probe)
├── PersistenceError (db, migration, recovery)
├── LifecycleError (service timeout, process death)
└── InternalError (unexpected; sanitized)
```

Mapping: data layer maps engine/native exceptions to `DownloadError`; presentation maps
`DownloadError` to user messages (no raw stack traces in UI). Internal diagnostics are
sanitized (no URLs/tokens/secret filenames).

### Lifecycle ownership

- `TorrentEngine` owned by a Hilt singleton adapter; session started/stopped explicitly.
- Extractor runtime owned by a Hilt singleton; initialized once, validated at startup.
- FFmpeg processes owned by the extractor adapter; cancelled and reaped on task cancel.
- Streaming bridge owned by `:feature:player` via a `StreamingBridgeOwner` bound to the
  player lifecycle; loopback-only; terminated on stop.
- Foreground services own notifications and call `stopSelf()` promptly on timeout/stop.
- All `Closeable`/native handles released in idempotent `onClose()`.

## Module Design

### Final Gradle module structure

```text
:app                                  # application, navigation, Hilt graph, MainActivity
:core:common                          # dispatchers, Result/Error, time, throttling
:core:model                          # shared immutable domain primitives/enums
:core:designsystem                    # Material 3 theme, tokens, shared components
:core:database                        # Room db, DAOs, migrations, entities
:core:datastore                       # DataStore preferences (policies/settings)
:core:storage                         # SAF, MediaStore, temp files, finalization, naming
:core:network                         # connectivity monitor, UIDT JobScheduler wrapper
:domain:download                      # download domain: entities, repos, use cases, state machine
:domain:torrent                       # TorrentEngine contract + torrent models
:domain:extractor                     # MediaExtractor/Provider contracts + models
:data:download                        # repository impls, QueueCoordinator, recovery
:data:torrent                         # libtorrent4j adapter
:data:extractor                       # yt-dlp adapter, provider registry, compatibility, FFmpeg
:engines:torrent-libtorrent           # libtorrent4j JNI packaging and session lifecycle
:engines:extractor-ytdlp              # Chaquopy Python/JS runtime bootstrap and bridge
:engines:media-ffmpeg                 # FFmpeg/FFprobe process wrapper
:feature:onboarding                   # consent + capability explanation
:feature:home                         # home screen, quick add, active summary
:feature:add                          # add-download flow (url/magnet/torrent file)
:feature:queue                        # unified queue, filters, item controls
:feature:download-detail              # torrent/extractor detail
:feature:library                      # media library, search/sort/filter
:feature:player                       # Media3 player + torrent streaming
:feature:settings                     # settings groups + provider status
:feature:legal                        # legal, privacy, licenses, about
```

Modules are created with their owning slice; do not create a module before it has a real
responsibility. `:engines:*` may be merged into the corresponding `:data:*` module if the
packaging boundary does not earn its own module, but the engine contract must remain in
`:domain:*`.

### Per-module contract

| Module | Responsibility | Public API | Depends on | Must NOT depend on | Key types | Testing |
|---|---|---|---|---|---|---|
| `:app` | Composition root, nav graph, adaptive scaffold | `MainActivity`, `PullDockApplication` | all | — | `AppModule`, `RootNavHost` | navigation smoke, Hilt graph test |
| `:core:common` | Dispatchers, throttling, Result/Error, time | `AppDispatchers`, `DownloadError` | — | Android UI | `Result`, `ErrorMapper` | unit |
| `:core:model` | Shared enums/primitives | `DownloadType`, `DownloadState`, `DownloadProgress`, `ProviderId` | — | Android | enums, value classes | unit |
| `:core:designsystem` | Theme, tokens, components | `PullDockTheme`, `PdButton`, `PdProgress`, `PdEmptyState` | Compose, Material3 | data/domain | tokens, components | Compose UI |
| `:core:database` | Room | `PullDockDatabase`, `DownloadTaskDao`, `Migrations` | Room, KSP | domain/UI | entities, DAOs | Room instrumentation |
| `:core:datastore` | Preferences | `SettingsDataStore`, `NetworkPolicyStore` | DataStore | domain/UI | serializers | unit |
| `:core:storage` | File/SAF/MediaStore | `FileFinalizer`, storage impl | SAF, MediaStore | domain | — | unit + instrumentation |
| `:core:network` | Connectivity/UIDT | `ConnectivityMonitor`, `UidtScheduler` | Android framework | domain | — | unit + instrumentation |
| `:domain:download` | Download domain | use cases, `DownloadRepository`, `QueueCoordinator` contract, state machine | core:model, core:common | Android, Room, engines | entities, contracts | unit (state machine) |
| `:domain:torrent` | Torrent contracts | `TorrentEngine`, torrent models | core:model, core:common | libtorrent, Android | interfaces | unit (fakes) |
| `:domain:extractor` | Extractor contracts | `MediaExtractor`, `Provider`, media models, `ExtractorRuntimeInfo` | core:model, core:common | yt-dlp/FFmpeg/Android | interfaces | unit (fakes) |
| `:data:download` | Repo + queue impl | `DownloadRepositoryImpl`, `QueueCoordinatorImpl` | domain, core | UI | mappers | unit + integration |
| `:data:torrent` | Torrent adapter | `TorrentEngineImpl` | domain:torrent, engines:torrent-libtorrent | UI | alert mappers | unit + native integration |
| `:data:extractor` | Extractor adapter | `MediaExtractorImpl`, `ProviderRegistry`, `RuntimeValidator` | domain:extractor, engines | UI | result mappers | unit + integration |
| `:engines:torrent-libtorrent` | JNI/session | `LibtorrentSession` | core:common, core:model | Android, domain | JNI wrapper | native PoC + integration |
| `:engines:extractor-ytdlp` | Python/JS runtime | `YtDlpRuntime` | core:common | Android UI, domain | typed results | runtime tests |
| `:engines:media-ffmpeg` | FFmpeg process | `FfmpegProcess`, `Ffprobe` | core:common | domain | exit codes/output | integration |
| `:feature:*` | Screens + ViewModels | `@Composable` screens, `XxxViewModel` | domain, core:model, core:designsystem | data, engines, database, datastore, storage | UiState/UiEvent | Compose UI + unit VM |

### Allowed dependency graph

```
app → feature:* , data:* , domain:* , core:* , engines:*
feature:* → domain:* , core:model , core:designsystem , core:common
data:* → domain:* , core:* , engines:*
engines:* → core:common , core:model
domain:* → core:model , core:common
core:* → (nothing above core)
```

## Domain Design

Full entity/field definitions are in [data-model.md](data-model.md). Summary:

- **DownloadTask** (common): id, type, source, displayName, outputFilename, state, progress,
  totalBytes, downloadedBytes, downloadSpeed, uploadSpeed, eta, createdAt, updatedAt,
  outputUri, errorCode, errorDetails, retryCount, destination, limits, engineTaskId.
- **TorrentTaskDetails**: infohash, name, totalSize, files (index, path, size, priority,
  selected), trackers, peers, resumeData, sequential, seeding policy/stats.
- **ExtractorTaskDetails**: providerId, originalUrl (sanitized), selectedFormat, container,
  codecs, mergeRequired, processingStatus, providerError.
- **DownloadState**: enum with legal transitions (see data-model.md).
- **DownloadProgress**: bytes, totalBytes, speed, eta (nullable), percent (derived).
- **DownloadError**: sealed hierarchy (see Error propagation).
- **MediaFormat**: id, resolution, container, videoCodec, audioCodec, estimatedBytes,
  mergeRequired, processingEstimate.
- **MediaSource**: url, providerId, title, thumbnail, duration, authorizedContext.
- **DownloadPolicy**: maxConcurrent, perEngineLimits, rateLimits, retry/backoff, priority.
- **StorageDestination**: type (app-specific / mediaStore / saf), uri, displayName, freeSpace.

Relationship: `DownloadTask` holds common fields; `TorrentTaskDetails` /
`ExtractorTaskDetails` are engine-specific records keyed by `DownloadTask.id`. The common
task never references engine types; adapters translate.

## Database Design

Full schema in [data-model.md](data-model.md). Summary:

- **Entities**: `DownloadTaskEntity`, `TorrentTaskEntity`, `ExtractorTaskEntity`,
  `LibraryItemEntity`, `ProviderStatusEntity`, `ExtractorRuntimeEntity`,
  `UpdateManifestCacheEntity`, `RecoveryMarkerEntity`.
- **Primary keys**: UUID strings for tasks; provider id for provider status; component name
  for runtime inventory.
- **Unique constraints**: `DownloadTask.sourceHash` (dedupe), `LibraryItem.fileUri`.
- **Indexes**: `state`, `type`, `createdAt`, `LibraryItem(type, completionDate)`,
  `ProviderStatus(providerId)`.
- **Foreign keys**: `TorrentTaskEntity.taskId → DownloadTaskEntity.id` (cascade delete);
  same for extractor.
- **Transactions**: task + engine detail + recovery marker written atomically; state
  transitions in a single transaction; library insert + file finalization coordinated.
- **Migrations**: explicit `Migration` per schema change plus `AutoMigrationSpec`; every
  migration tested; fallback marks tasks for recovery rather than destructive delete.
- **Recovery metadata**: `RecoveryMarkerEntity` records interrupted tasks and the resume
  data needed to restore or explicitly fail.
- **Engine resume data**: stored as opaque, versioned blobs on the engine detail row
  (never parsed by domain); cleared only after verified completion or explicit cancel.
- **File consistency reconciliation**: `reconcileLibrary()` compares library records to
  actual files and marks missing files; never deletes a record silently.
- **Not persisted**: raw URLs with credentials/tokens, cookies, raw engine alerts, raw
  subprocess output, transient progress bursts.

## Torrent Architecture

Adapter `:data:torrent` around `:engines:torrent-libtorrent`:

- **Initialization**: `TorrentEngineImpl` lazily creates a single `LibtorrentSession` on a
  dedicated single-threaded dispatcher. Session settings applied once.
- **Session ownership**: one session per process; torrents are handles within it; session
  survives task churn, not process death.
- **Lifecycle**: add → fetch metadata → await selection → start → running → pause/resume →
  verify → complete; remove and delete are separate.
- **Alert processing**: a dedicated alert loop on the session thread drains alerts and
  publishes typed `TorrentEvent`s to a serialized channel; UI events are throttled.
- **Metadata**: `fetchMetadata` with timeout; on timeout → `EngineError.Torrent(METADATA_TIMEOUT)`.
- **File selection**: priorities applied via engine API; selection changes after start update
  priorities and are validated against existing data.
- **Progress**: from alerts/state; throttled to ~2 Hz for UI, exact values persisted on
  transitions.
- **Pause/resume**: idempotent; repeated start is a no-op if already running.
- **Cancellation**: `cancel()` stops alerts and releases handles; `close()` shuts the session.
- **Resume data**: fetched after pause/periodically; stored as a versioned blob; restored on
  recovery; validated before use.
- **Shutdown**: `close()` idempotent; called from the process-death/Service termination path.
- **Error mapping**: alert types → `DownloadError`; sanitized.
- **Streaming**: `requestStreamingBridge(infohash, fileIndex)` returns a loopback URL after
  validating the file is media and sequential data is available; bridge owned by player.
- **Thread-safety**: all engine calls marshalled to the session thread; no engine calls from
  the main thread; callbacks posted to `Dispatchers.Io`.

## Extractor Architecture

Provider-based adapter `:data:extractor`:

- **`MediaExtractor` contract** (domain): resolve/formats/download/cancel/observe.
- **Provider registry**: `Provider` implementations keyed by `ProviderId`; URL recognition is
  provider-specific; unknown URL → `InputError(UNSUPPORTED_PROVIDER)`.
- **Format model**: `MediaFormat` as above; format list may be empty (typed "no formats").
- **Resolution workflow**: validate URL → provider.resolve → typed `MediaDescriptor` or
  error; no success claim without a descriptor.
- **Download descriptor**: `MediaDownloadDescriptor` with format, output template, merge
  requirement.
- **Download lifecycle**: create task → resolve → download to temp → (merge if needed) →
  verify → finalize atomically → complete.
- **FFmpeg processing**: `:engines:media-ffmpeg` runs FFmpeg/FFprobe with argument arrays
  (never shell strings); bounded output; exit-code verification; cancellation; temp cleanup;
  free-space check; preserve existing valid file on failure; atomic publish.
- **Error mapping**: provider/runtime/merge failures → typed `DownloadError`.
- **Provider compatibility**: `ProviderCompatibilityRegistry` + `ExtractorRuntimeValidator`
  (see Extractor Lifecycle below).
- **Runtime/ABI**: Chaquopy CPython 3.13 bundled; initialized once; arm64-v8a/x86_64 only;
  unsupported ABI → typed "provider unavailable".
- **Isolation**: each provider independent; a failure disables only that provider's health,
  never others.

## Queue Architecture

`QueueCoordinator` (`:data:download`) is the single scheduler for both types:

- **Scheduling**: ready tasks ordered by priority then creation time; per-engine queues with
  independent concurrency.
- **Fairness**: round-robin across engines to prevent one engine starving the other; a
  per-engine cap plus a global cap.
- **Global/per-engine concurrency**: `DownloadPolicy.maxConcurrent` (global) and
  `perEngine.maxConcurrent` (torrent/extractor), enforced by the coordinator.
- **Priority**: user-set priority; manual reorder persists order.
- **Retry**: only failed tasks with retryable errors; bounded attempts; exponential backoff
  with jitter; persisted `retryCount`/`nextRetryAt`.
- **Backoff**: stored; `RETRY_WAIT` state; no hot loops.
- **Cancellation**: cooperative; engine cancel + state persist.
- **Persistence**: every transition persisted; queue order persisted.
- **Recovery**: on startup, `RecoveryMarker`s drive restore; tasks with valid resume data
  resume, others are marked `NEEDS_USER_ACTION`.
- **Duplicate prevention**: `sourceHash` unique constraint + coordinator check.
- **Event reconciliation**: progress/state events applied idempotently by task id;
  out-of-order events resolved by persisted state + sequence numbers.
- **Engine-specific controls preserved**: limits, seeding, file priority, and format
  selection remain on the engine detail records and are applied by the adapter.

## Android Execution Architecture

API-level-aware matrix (ADR-0005):

| Mechanism | Appropriate workload | Restrictions | Fallback |
|---|---|---|---|
| User-Initiated Data Transfer job (API 34+) | Long user-started transfers (>10 min) | Permission, constraints, progress notification; quota-exempt | `dataSync` FGS on older versions |
| `dataSync` foreground service (API <34) | Continuous transfer while app backgrounded | Android 15: 6 h/24 h budget, `onTimeout`; cannot start from boot | UIDT on 34+; WorkManager for deferrable |
| WorkManager (deferrable) | Resume/recovery, library reconciliation, cleanup | Not for a continuous engine | — |
| Foreground service (mediaPlayback) | Active playback only | Type must match | — |

- **Process lifecycle**: engines live in a singleton scope; on process death, resume from
  Room + resume data; never assume a native process survives.
- **Boot handling**: no `BOOT_COMPLETED` start of a restricted FGS; restore persisted queue
  and surface recovery; resume only via user action or WorkManager.
- **Notification actions**: pause/resume/stop via PendingIntents; notification permission
  denied → in-app controls still work.
- **Guarantee**: no promise of unlimited background execution; document the 6 h/24 h budget
  and OS termination behavior.

## Storage and Media Architecture

- **SAF**: user-chosen destinations via `OpenDocumentTree`/`CreateDocument`; persistable
  permissions with revocation handling.
- **MediaStore**: insert completed media into Movies/Downloads; handle insert failure and
  interrupted writes.
- **Temporary files**: app-specific `cache/partial/<taskId>`; `FileFinalizer` writes to temp,
  verifies, then atomically renames/publishes; incomplete files never published.
- **File naming**: sanitized, collision strategy `name (n).ext`, documented; path traversal
  and reserved names rejected.
- **Duplicate handling**: `sourceHash` dedupe for tasks; filename collision strategy for
  outputs.
- **Storage reconciliation**: `reconcileLibrary()` compares records to files; marks missing.
- **Media library indexing**: from finalized files + MediaStore; thumbnails async.
- **Media3 playback**: `ExoPlayer` in `:feature:player`; position persisted (throttled).
- **Torrent streaming**: loopback server (`127.0.0.1` only), unpredictable token, byte-range,
  path traversal defense, lifecycle-bound shutdown.

## Compose UI Architecture

- **Navigation graph**: see Navigation architecture; typed routes.
- **Screen boundaries**: one module per screen group; screens receive UiState and emit intents.
- **ViewModels**: `@HiltViewModel`, `StateFlow<UiState>`, `SharedFlow<UiEvent>`.
- **UI states**: `Loading`, `Refreshing`, `Content`, `Empty`, `Error`, `Partial`, `Offline`,
  `PermissionDenied`, `RecoverableFailure`, `Restored`.
- **UI events**: snackbar, dialog, navigation, undo.
- **Shared components**: `:core:designsystem` (buttons, progress, empty/error, cards).
- **Design-system structure**: color roles, typography scale, spacing, radius, elevation,
  status colors; light/dark; optional dynamic color.
- **Theme architecture**: `PullDockTheme(darkTheme, dynamicColor)`; tokens via
  `LocalPdTokens`.
- **Adaptive layouts**: WindowSizeClass; bottom bar / rail / list-detail.
- **Accessibility**: semantics, 48dp targets, contrast, scalable type, TalkBack, RTL,
  reduced motion, non-color status indicators.
- **Flow → screens/use cases**: onboarding→`BeginUse`; home→`ObserveDashboard`; add→
  `AddTorrent`/`ResolveMediaUrl`; queue→`ObserveDownloadQueue`/`Pause|Resume|Cancel|Retry|Remove|Move`;
  detail→`ObserveDownloadDetails`; library→`ObserveLibrary`/`Search`/`Sort`; player→
  `PlayMedia`; settings→`UpdateNetworkPolicy`/`UpdateSeedingPolicy`/`ObserveProviderStatus`.

## Security Architecture

Threat-model-driven (ADR-0010). Boundaries: UI → domain → data → engines; engines are the
only place that touches native/process; UI never sees native types.

- **Input validation**: URL scheme allowlist, magnet/torrent validation, SSRF guard rejecting
  loopback/private/link-local/multicast/metadata IPs (with redirect and DNS-rebinding
  checks) while allowing verified media CDNs.
- **Torrent path traversal**: sanitize filenames, reject absolute paths, `..`, reserved
  names, case/unicode collisions; confine writes to the destination root.
- **Unsafe filenames**: allowlist extensions; length limits; normalization checks.
- **Native binaries**: verify ABI; document licenses; no execution from external storage.
- **Subprocess invocation**: argument arrays only; no shell string concatenation; bounded
  output; timeouts; cancellation.
- **Local streaming server**: loopback-only, token, byte-range, traversal defense, shutdown.
- **Sensitive logging**: no URLs with credentials, tokens, cookies; sanitized diagnostics.
- **Dependency integrity**: pinned versions; dependency verification; license report.
- **Permissions**: minimum set; justify each; notification permission handling.
- **No remote code**: executable updates only via signed app releases (ADR-0009).

## Performance Design

- **Dispatchers**: `Dispatchers.Io` for file/network/engine; `Dispatchers.Default` for
  mapping; `Dispatchers.Main` for UI; enforced by `AppDispatchers` (test-injectable).
- **Background I/O**: never on main thread; `withContext(IO)`.
- **Progress throttling**: engine events → conflated flow, ~2 Hz UI emission; exact values on
  transitions; no unbounded buffering.
- **Recomposition**: stable immutable UiState; `remember`/`derivedStateOf`; keys on lazy
  lists.
- **Thumbnails**: async loader with size cache; cancel on recycle.
- **Memory**: bounded queues; clear observers; release native handles; avoid leaking Activity
  context.
- **Native cleanup**: idempotent `close()`; alert loop cancellation; process reaping.
- **Queue scalability**: paged/lazy lists; indexes; conflated state.
- **Disk monitoring**: free-space checks before processing/finalization.
- **Measurement**: Macrobenchmark for navigation/startup; Baseline Profile; large-queue
  scroll benchmark; engine event-rate counters.

## Testing Architecture

| Level | Module | Focus |
|---|---|---|
| Unit | domain, data, core | state machine, use cases, queue scheduling, retry/backoff, validation, mappers, filename collision |
| Integration | data, core:database | Room + repository sync, migrations, recovery markers, engine adapter lifecycle |
| Native engine | engines, data:torrent/extractor | metadata, pause/resume, resume data, alert mapping, FFmpeg merge, runtime init |
| Compose UI | feature:* | states, events, flows, accessibility |
| Instrumentation | app, core:storage/network | SAF, MediaStore, notifications, UIDT, lifecycle |
| Lifecycle/recovery | app, data:download | process death, reboot, service timeout, update handoff |
| Performance | app, feature:queue/library | startup, large queue, thumbnails |

Deterministic automated tests use fakes/fixtures; live-provider and long-transfer validation
is a separate, documented manual/device procedure.

## Extractor Lifecycle and Update Architecture

Full detail in [data-model.md](data-model.md) and [contracts/](contracts). Summary:

- **`ExtractorRuntimeInfo`** (immutable): component, version, required range, runtime
  compatibility, channel, verification status, limitations.
- **Version compatibility validation**: on startup and before extraction, verify
  yt-dlp ↔ yt-dlp-ejs ↔ Python ↔ JS runtime ↔ FFmpeg/FFprobe compatibility; failure → typed
  error, app does not crash, existing extraction remains available if already initialized.
- **`ProviderCompatibilityRegistry`**: per-provider health and capability records.
- **Provider health state**: `CURRENT`, `UPDATE_RECOMMENDED`, `UPDATE_REQUIRED`,
  `PLATFORM_CHANGE_SUSPECTED`, `PROVIDER_TEMPORARILY_UNAVAILABLE`, `PROVIDER_UNSUPPORTED`,
  `AUTHORIZATION_REQUIRED`, `VERSION_CHECK_UNAVAILABLE`, `RUNTIME_INCOMPATIBLE`.
- **Signed metadata manifest**: schema version, timestamps, app versions, approved
  extractor/runtime versions, provider status, disablement notices, release notes;
  asymmetric signature with app-pinned key; expiration; rollback protection; HTTPS; cached
  last-known-good; parse limits. Metadata is never executable.
- **Update release information**: shown in Settings/Provider status; action is a normal app
  release, never an in-app executable swap.
- **Application update handoff**: before update, persist queue + resume data; mark
  unrecoverable tasks; after install, restore and surface recovery; never assume a native
  process survives replacement.
- **Stale-version detection**: compare installed vs required vs recommended; use upstream
  signal as one input; do not equate age with broken; do not equate network error with stale.
- **Provider-specific degradation**: one provider's failure disables only that provider.
- **Interrupted update recovery**: keep the old install intact until package install succeeds;
  on failure, preserve data and current extractor; mark tasks for recovery.
- **Ownership**: `:data:extractor` owns runtime validation and compatibility; `:app` owns
  manifest fetch/verify timing (non-blocking); no remote configuration introduces extraction
  logic.

## ADRs

See [adr/index.md](adr/index.md) for full records.

- ADR-0001 Torrent engine (libtorrent4j)
- ADR-0002 Extractor runtime (Chaquopy Python + pinned yt-dlp)
- ADR-0003 Native FFmpeg packaging
- ADR-0004 Queue coordinator
- ADR-0005 Background execution
- ADR-0006 File storage and finalization
- ADR-0007 Torrent streaming bridge
- ADR-0008 Module boundaries
- ADR-0009 Extractor update and compatibility architecture
- ADR-0010 Security boundaries and no remote code execution

## Project Structure

### Documentation (this feature)

```text
specs/001-production-android-app/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
├── adr/                 # Architecture Decision Records
├── checklists/
└── tasks.md             # Phase 2 output ($speckit-tasks)
```

### Source Code (repository root)

See "Final Gradle module structure" above. The currently scaffolded module is `:app`; all
other modules are created by their owning slice.

**Structure Decision**: multi-module Clean Architecture. `:app` is the only module present at
the start; `:core:*`, `:domain:*`, `:data:*`, `:engines:*`, and `:feature:*` are added in
dependency order as slices land, each with a real responsibility.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| Multi-module (25 modules) | Enforces dependency direction and engine isolation; keeps domain pure | Single-module app would let UI reach engines/Room directly and erode boundaries |
| Repository + use-case layering | Engines and persistence are swappable; testable without device | Direct DAO/engine calls from ViewModels would couple UI to native types |
| Separate `:engines:*` modules | Native runtime ownership, ABI packaging, and cleanup are isolated | Folding into `:data:*` risks leaking JNI/process types into repository code |
| Queue coordinator (not per-engine queues) | Global/per-engine limits, fairness, retry, recovery in one place | Two independent schedulers would race and could duplicate work |
| Signed manifest check in v1 | Provider rot is the main long-term risk; metadata-only is low cost | Local-only health cannot react to provider changes without an app update |

## Definition of Done for This Plan

- [x] Architecture, modules, dependency graph, domain, database, engines, queue, execution,
      storage, UI, security, performance, testing defined.
- [x] Extractor lifecycle and update architecture defined.
- [x] ADRs recorded.
- [x] Constitution re-check passed.
- [x] No circular dependencies; no domain→Android leakage; no UI→engine dependency.
- [ ] Task list generated by `/speckit-tasks` (not part of this phase).
