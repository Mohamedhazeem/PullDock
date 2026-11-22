# PullDock — Agent Instructions

This file is the repository-level instruction set for every AI coding agent and human
contributor working on PullDock. It is a working standard, not a product specification.
Read it before touching code, and keep it synchronized with the actual repository.

PullDock is a native Android application (Kotlin + Jetpack Compose) for managing
authorized media downloads and BitTorrent transfers.

---

## 1. Source of Truth and Conflict Handling

When guidance conflicts, the following order prevails:

1. Security, legal, and Android platform constraints.
2. The engineering constitution: `.specify/memory/constitution.md`
   (PullDock Constitution, current version 1.0.1, ratified 2026-11-22).
3. The approved PullDock specification for the active feature
   (`specs/<feature>/spec.md`).
4. The approved implementation plan and architecture decisions
   (`specs/<feature>/plan.md`, ADRs).
5. The approved task list (`specs/<feature>/tasks.md`).
6. This `AGENTS.md`.

Rules for handling conflicts:

- Identify the conflict explicitly. Never silently change approved requirements.
- Explain the consequences of each option.
- Recommend a correction and update dependent documents only after the appropriate
  decision is made.
- Never allow this file, a plan, or a task to weaken the engineering constitution.
- If a genuine trade-off is unavoidable, stop and request an explicit decision.

The constitution is the permanent engineering standard. This file may add repository
conventions and workflow detail, but it MUST NOT contradict the constitution.

---

## 2. Repository Overview

Inspected 2026-11-22. The repository contains a Spec Kit scaffold and a 25-module Android
application scaffold (Slice 0). See `specs/001-production-android-app/plan.md` §Module
Design and ADR-0008 for the module-boundary rules; the summary below is the operative map.

### 2.1 Structure

```
.
├── app/                           # composition root (com.pulldock): MainActivity,
│   │                              # navigation shell (RootNavHost, MainScaffold, routes),
│   │                              # manifest, resources, Hilt graph, 64-bit ABI packaging
│   └── src/
│       ├── main/java/com/pulldock/
│       │   ├── MainActivity.kt
│       │   ├── PullDockApplication.kt
│       │   └── navigation/        # PullDockDestination, RootNavHost, MainScaffold
│       └── res/
├── core/
│   ├── common/                    # pure Kotlin: AppDispatchers (io/default/main)
│   ├── model/                     # pure Kotlin: DownloadType, DownloadState, DownloadError,
│   │                              # ProviderId, StorageKind
│   ├── designsystem/              # Material 3 theme: PullDockTheme, color/typography,
│   │                              # PdTokens (spacing/radius/elevation/status), LocalPdTokens
│   ├── database/                  # Room: PullDockDatabase skeleton (v1), KSP schemas/
│   ├── datastore/                 # DataStore: SettingsDataStore, DataStoreModule
│   ├── storage/                   # StorageRepository contract (platform-facing)
│   └── network/                   # ConnectivityMonitor contract (NetworkStatus)
├── domain/{download,torrent,extractor}/   # pure Kotlin; content arrives in Slice 1
├── data/{download,torrent,extractor}/     # repo impls/adapters; content in Slices 1,3,4,5
├── engines/{torrent-libtorrent,extractor-ytdlp,media-ffmpeg}/  # native runtime modules;
│                               # engine dependencies are pinned only after T060/T080 PoCs
├── feature/{onboarding,home,add,queue,download-detail,library,player,settings,legal}/
│                               # screen modules; content arrives in Slice 2+
├── gradle/libs.versions.toml  # version catalog (single source of truth)
├── gradle/wrapper/            # Gradle 9.6.0 wrapper
├── scripts/ci/                # CI helpers: check-apk-abis.sh, secret-scan.sh
├── .specify/                  # Spec Kit configuration, templates, constitution
├── .zcode/skills/             # mirrored Spec Kit skills
├── specs/                     # per-feature documents
├── .github/workflows/         # CI: build, unit tests, lint, ABI packaging, secret scan
├── AGENTS.md
└── README.md
```

### 2.2 Module responsibilities

- `:app` — the Android application and Hilt composition root. Owns the manifest,
  resources, the navigation shell, and depends on every other module. Feature code
  belongs in `:feature:<name>` under `com.pulldock.feature.<name>`.
- `:core:common` — pure-Kotlin cross-cutting utilities. `AppDispatchers` is the only
  sanctioned way to obtain dispatchers; never reference `Dispatchers` directly in
  features/domain.
- `:core:model` — pure-Kotlin shared primitives. `DownloadState` names are persisted in
  the database and are a durable contract; `DownloadError` codes follow
  `<CATEGORY>_<REASON>` and are likewise persisted.
- `:core:designsystem` — the design system (theme, tokens, shared components). Screens
  must not hardcode colors, spacing, or radii.
- `:core:database` — Room. Schema exports land under `core/database/schemas/`; every
  schema change needs a tested migration.
- `:core:datastore` — preferences DataStore. `DataStoreModule` provides the single
  process-wide instance.
- `:core:storage` / `:core:network` — platform-facing contracts (`StorageRepository`,
  `ConnectivityMonitor`). These contracts are deliberately framework-light and live
  outside `:domain:*` so `core:*` never depends on `:domain:*`; the data layer maps
  between them.
- `:domain:*` — pure Kotlin (no Android, no Room, no engines, no Compose). Contracts and
  use cases arrive in Slice 1.
- `:data:*` — implements domain contracts; the only layer allowed to touch `:core:*`
  infrastructure and `:engines:*`.
- `:engines:*` — native/runtime integration. Engine dependencies are intentionally
  absent until the feasibility PoCs (T060 torrent, T080 extractor) verify version, ABI,
  and license (AGENTS.md §17).
- `gradle/libs.versions.toml` — single source of truth for plugin and dependency versions.
  Pin versions here; never hardcode versions in module build files.

### 2.3 Not yet present (status after Slices 0–5 + lifecycle/storage cores)

- Domain/data/feature module **sources are now present** for Slices 1–5 and the
  lifecycle/storage cores; the remaining UI surfaces are the Library and Player
  feature modules (T156–T158, T160, T164–T165).
- libtorrent4j 2.1.0-39 is pinned (arm64-v8a/x86_64 jars, MIT wrapper). The
  Chaquopy/yt-dlp and FFmpeg dependencies remain unpinned until the T080/D2 device
  verifications.
- Device-verified steps still open: torrent runtime PoC (T075/T060), extractor PoC
  (T080/T094), background-execution behavior (T114/T163), connected tests.
- Static analysis (Detekt/ktlint), coverage tooling, dependency verification metadata —
  CI documents where they activate (T176).

Update this section whenever modules, packages, or build capabilities change.

---

## 3. Spec Kit Workflow

PullDock uses Spec Kit for spec-driven development.

- Features live in `specs/<NNN-short-name>/` (sequential 3-digit prefix) or
  `specs/<YYYYMMDD-HHMMSS>-<short-name>/` when timestamped.
- Per-feature documents: `spec.md`, `plan.md`, `tasks.md`, `research.md`,
  `data-model.md`, `quickstart.md`, and `contracts/`.
- Available commands: `/speckit-specify`, `/speckit-clarify`, `/speckit-plan`,
  `/speckit-tasks`, `/speckit-implement`, `/speckit-analyze`, `/speckit-checklist`,
  `/speckit-converge`, `/speckit-constitution`.
- The bundled `speckit` workflow runs the full cycle with review gates after the spec and
  after the plan.
- Use `.specify/scripts/bash/check-prerequisites.sh --json` to resolve the active feature
  directory and available documents.
- Template resolution: `.specify/scripts/bash/resolve-template.sh <template> --json`.
  Never hand-edit files under `.specify/templates/`; project overrides go through the
  resolver stack.

Always read the active feature's `spec.md`, `plan.md`, and `tasks.md` before implementing.

---

## 4. Kotlin Standards

- Write idiomatic Kotlin. Prefer expressive, readable code over clever code.
- Respect null safety. Never use `!!` to force a value; handle nullability explicitly.
- Use immutable data structures (`val`, immutable collections, `data class` with `val`
  properties) wherever practical.
- Use structured concurrency. Respect `CoroutineScope` and `Job` lifetimes.
- Propagate cancellation. Never launch unowned work.
- Never block the main thread. Use appropriate dispatchers for I/O and CPU work.
- Never use `GlobalScope`.
- Handle errors explicitly. Never silently swallow exceptions. Never catch broadly without
  a recovery strategy.
- Keep classes and functions small, focused, and single-purpose.
- Use clear, intention-revealing names.
- Document public APIs and architectural boundaries.

---

## 5. Android Platform Standards

- Respect the Android lifecycle at every layer (Activity, Fragment, ViewModel, Service,
  Worker, Compose).
- Respect foreground-service type restrictions and declare the correct type and
  permissions. Never misuse a foreground-service type.
- Use scoped storage. Do not rely on legacy external storage or broad storage permissions.
- Request the minimum permissions required. Justify each permission.
- Take clear ownership of resources (streams, handles, callbacks, receivers) and release
  them deterministically.
- Avoid memory leaks: no implicit references to Activity/Context from long-lived objects,
  no unregistered listeners, no leaked coroutines.
- Respect native engine lifecycle (initialization, threading, shutdown). See §9.
- Handle process death and configuration changes. State that must survive MUST be
  persisted or restored explicitly.
- Respect Doze, Battery Saver, service timeouts, notification permissions, and process
  termination. Do not promise unlimited background execution.

---

## 6. Jetpack Compose and UI Standards

- Use Material 3 and the PullDock design system.
- Build reusable design-system components; do not reimplement one-off variants.
- Use screen-level ViewModels. Keep business logic out of composables.
- Expose UI state as immutable state, using `StateFlow` where appropriate.
- Follow unidirectional data flow: state down, events up.
- Never access databases, native engines, repositories, or storage implementations
  directly from a composable.
- Handle loading, empty, error, and success states for every screen where they apply.
- Add accessibility semantics (`contentDescription`, `semantics`, roles, state
  descriptions). Support TalkBack, large font sizes, RTL, and adaptive layouts.
- Avoid expensive computation in composables and avoid unnecessary recomposition.
- Do not ship placeholder screens when the real requirement is known. Do not leave dead
  buttons, fake interactions, or misleading progress indicators.

---

## 7. Architecture and Module Rules

The constitution mandates Clean Architecture with dependency inversion and
unidirectional data flow. Enforce it in code.

- Maintain directional boundaries: Presentation → Domain ← Data, plus Platform
  infrastructure and Native download engines.
- The domain layer MUST NOT depend on Android framework classes or concrete third-party
  engines.
- The presentation layer MUST NOT access Room, native engines, or storage directly.
- Define interfaces at real architectural boundaries; do not create speculative
  abstractions.
- Prevent circular dependencies between modules and packages.
- Keep features independently testable.
- Avoid god classes, god ViewModels, and duplicated business rules.
- New modules MUST be justified by a real boundary. Do not prematurely micro-modularize.
- Any significant architectural choice MUST be recorded as an ADR and reflected in
  `plan.md` / the feature docs.

When the Gradle module graph exists, document the enforced dependency rules here (for
example via a dependency-validation plugin or module-level `build.gradle.kts` checks).

---

## 8. Database and Persistence

- Room is the durable source of application state.
- Room migrations are mandatory for every schema change. Never ship a schema change
  without a tested migration and a fallback strategy.
- Use transactions where consistency requires them.
- Keep DAO and database access inside the data layer. No DAO access from UI or domain.
- Protect consistency between the database and the filesystem. Database state and actual
  file state must be reconcilable.
- Preserve engine-specific resume/recovery data. Storing progress percentages alone is
  not recoverable.
- Never report a download as complete before successful verification and finalization.
- Test recovery paths, process death, and migrations.

---

## 9. Native Engines

Torrent and media-extraction engines are isolated behind explicit interfaces with typed
result models.

- Never call native engines directly from Compose or from the domain layer.
- Respect each engine's thread-safety requirements; marshal callbacks and progress events
  safely across thread boundaries.
- Implement explicit resource ownership and cleanup.
- Implement explicit cancellation and shutdown.
- Handle engine crashes and failures as typed errors; never let them corrupt UI state.
- Verify Android ABI compatibility before adopting any native library.
- Sanitize native diagnostics before logging (see §10).
- Do not leak JNI, libtorrent, yt-dlp, FFmpeg, process, or native data types into domain
  or UI layers.
- Never assume a desktop executable or native library is Android-compatible without
  verification.

---

## 10. Security

- Treat all external input as untrusted: URLs, torrent metadata, filenames, network
  responses, and external process output.
- Validate URLs and filenames. Prevent path traversal and unsafe destination access.
- Never build shell commands through string concatenation. Use argument arrays.
- Never commit credentials, signing keys, or other secrets.
- Keep logs free of URLs, tokens, and private information.
- Review third-party dependencies and licenses before adoption (see §17).
- Minimize permissions. Avoid unnecessary analytics or tracking.
- Do not silently read clipboard contents.
- Protect local services and temporary files.
- Respect applicable rights, platform restrictions, and legal requirements for all media
  integrations.

---

## 11. File and Package Conventions

No project conventions exist yet. The following are the established defaults; revisit them
when the first Android module is created and record the final choice here.

### 11.1 Package naming

- Base package: `com.pulldock` (proposed; freeze the real `applicationId`/base package
  when `:app` is created).
- Layer/feature packages follow the architecture: `com.pulldock.<feature>.<layer>`, for
  example `com.pulldock.downloads.data`, `com.pulldock.player.ui`.
- One feature per package group. Keep layers separated within a feature.

### 11.2 Type and file naming

- Classes: `PascalCase`, descriptive nouns (`DownloadRepository`, `TorrentEngine`).
- Interfaces: `PascalCase`, often role-named (`DownloadEngine`, `MediaExtractor`).
- Use cases / interactors: verb-first `PascalCase` (`StartDownload`, `PauseTransfer`).
- ViewModels: `<ScreenOrFeature>ViewModel` (`DownloadsViewModel`).
- Repositories: `<Domain>Repository` (`DownloadRepository`), implemented in the data layer.
- UI state: immutable `<Screen>UiState`; UI events: `<Screen>Event` or `<Screen>UiEvent`.
- Composables: `PascalCase` descriptive (`DownloadListScreen`, `DownloadListItem`).
- DI modules: `PascalCase` + `Module` (`AppModule`, `DatabaseModule`, `EngineModule`).
- Test classes: `<ClassUnderTest>Test` (unit), `<ClassUnderTest>InstrumentedTest`
  (instrumented); name test methods descriptively.
- Resources: `snake_case` for drawable/layout/string names; prefix feature where helpful.

### 11.3 File organization

- One primary type per file when the file name carries meaning; do not force
  one-class-per-file when grouping tiny related declarations adds clarity.
- Keep files small enough to read in one pass. Split when a file mixes responsibilities.
- Place tests in the matching `src/test` and `src/androidTest` source sets.

---

## 12. UI/UX Implementation Standards

- Follow the PullDock design system. Before creating a new shared component, search for
  an existing equivalent and reuse it when appropriate.
- Maintain light and dark theme compatibility in every component.
- Support accessibility and adaptive layouts in every screen.
- Do not introduce visual inconsistencies or one-off styling.
- Do not implement generic placeholder screens when the actual screen requirement is known.
- Do not leave nonfunctional buttons or fake interactions.

---

## 13. Testing and Verification Commands

Verified in this repository (2026-11-22):

| Purpose | Command | Status |
| --- | --- | --- |
| Debug build | `./gradlew :app:assembleDebug` | verified |
| Unit tests | `./gradlew :app:testDebugUnitTest` | verified |
| Android lint | `./gradlew :app:lintDebug` | verified (0 errors) |
| Discover real tasks | `./gradlew tasks --all` | available |
| Release build | `./gradlew :app:assembleRelease` | not yet run |
| Instrumentation tests | `./gradlew :app:connectedDebugAndroidTest` | requires a device/emulator |
| Module-specific tests | `./gradlew :<module>:testDebugUnitTest` | applies to future modules |

Not configured yet — MUST be established rather than assumed:

- Static analysis (Detekt and/or custom Android Lint rules) is not configured.
- Formatting checks (ktlint / spotless) are not configured.
- Coverage tooling is not configured.

CI runs `assembleDebug`, `testDebugUnitTest`, and `lintDebug` on push and pull requests
(`.github/workflows/ci.yml`).

Spec Kit verification:

```bash
.specify/scripts/bash/check-prerequisites.sh --json
.specify/scripts/bash/resolve-template.sh constitution-template --json
```

General rules:

- A successful compilation alone does not constitute completion.
- Never disable tests, lint rules, or compiler checks to obtain a green build.
- Run only the commands that apply to the slice you changed, and report their real output.

---

## 14. Implementation Workflow

Every agent MUST follow this sequence:

1. Read this file.
2. Read `.specify/memory/constitution.md`.
3. Read the relevant `specs/<feature>/spec.md`.
4. Read the approved `plan.md` and any ADRs.
5. Read the relevant `tasks.md`.
6. Inspect the existing implementation before changing it.
7. Implement only the requested slice or task.
8. Add or update tests.
9. Run the applicable quality checks.
10. Report actual results (see §16).

Additional constraints:

- Do not implement multiple future slices in one operation unless explicitly instructed.
- Do not modify unrelated files.
- Do not silently change architecture.
- Do not delete working functionality merely to simplify an implementation.
- Never mark incomplete work as finished.

---

## 15. Documentation Maintenance

Keep documentation accurate and current.

- Architecture change → update the relevant ADRs and `plan.md`.
- Database schema change → document the migration and its tests.
- Native dependency change → update the dependency assessment and license list (§17).
- Background-execution behavior change → update background-execution documentation.
- Storage behavior change → update storage documentation.
- New module or responsibility → update §2 of this file.
- New convention → update §11 of this file.

---

## 16. Completion Reporting

At the end of every implementation task, report:

- Summary of changes.
- Files created.
- Files modified.
- Architecture decisions made.
- Tests added or updated.
- Commands actually executed (exact command strings).
- Pass/fail results for each command.
- Known limitations.
- Outstanding issues.

Never claim a test passed when it was not executed. Report the real exit status.

---

## 17. Extractor Dependency Maintenance

Before changing yt-dlp, yt-dlp-ejs, Python, JavaScript runtime, FFmpeg, FFprobe, or any
native binary:

1. Check compatibility with the existing dependency set.
2. Verify Android ABI compatibility.
3. Check licenses and redistribution conditions.
4. Run extractor runtime tests.
5. Run provider compatibility tests.
6. Update the dependency assessment.
7. Record version changes.

Hard rules:

- Never automatically download and execute extractor code from a remote source.
- Use the approved application-release update mechanism.
- Keep provider-specific failures isolated and report actual compatibility status.
- Any exception to the pinned/validated distribution policy requires an approved ADR,
  security review, integrity verification, compatibility testing, and a
  distribution-policy assessment.
