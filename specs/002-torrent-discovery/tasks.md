# Tasks: Torrent Discovery and Multi-Provider Search

**Input**: Design documents from `/specs/002-torrent-discovery/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

**Tests**: Included because the specification has a mandatory Testing Requirements section.
Tests MUST be written and FAIL before the corresponding implementation.

**Organization**: Grouped by user story (US1–US5) for independent implementation/testing.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Parallelizable (different files, no incomplete dependency)
- **[Story]**: US1–US5 for story-phase tasks
- Exact file paths are given; constraints quoted from data-model.md are binding.

## Path Conventions (Android multi-module)

- `domain/discovery/src/main/java/com/pulldock/domain/discovery/…`
- `data/discovery/src/main/java/com/pulldock/data/discovery/…`
- `feature/discovery/src/main/java/com/pulldock/feature/discovery/…`
- Tests mirror under `src/test` (unit) and `src/androidTest` (Compose/instrumented).

---

## Phase 1: Setup (Shared Infrastructure)

- [ ] T001 Create `:domain:discovery`, `:data:discovery`, `:feature:discovery` modules and register them in `settings.gradle.kts`; add each module `build.gradle.kts` per plan.md (domain = `kotlin.jvm`; data = `android.library` + `ksp` + `hilt`; feature = `android.library` + `kotlin.compose` + `ksp` + `hilt`; all JVM 17, compileSdk 37, minSdk 26).
- [ ] T002 [P] Add Ktor Client + OkHttp engine + kotlinx-serialization JSON + ContentNegotiation (and the Kotlin serialization plugin) to `gradle/libs.versions.toml`, pinned to latest stable compatible with Kotlin 2.4.20 / AGP 9.4.1 / minSdk 26.
- [ ] T003 [P] Record ADR-0014 (discovery modules & provider-adapter architecture) and ADR-0015 (HTTP client: Ktor + OkHttp + kotlinx-serialization) under `specs/001-production-android-app/adr/`, and update `specs/001-production-android-app/adr/index.md`.
- [ ] T004 [P] Update `AGENTS.md` §2 module map (add the three modules) and `docs/THIRD_PARTY_LICENSES.md` (Ktor/kotlinx, Apache-2.0).

---

## Phase 2: Foundational (Blocking Prerequisites)

**⚠️ No user story work begins until this phase is complete.**

- [ ] T005 [P] Create domain models in `domain/discovery/src/main/java/com/pulldock/domain/discovery/model/` per data-model.md: `TorrentSearchResult`, `ContributingSource`, `TorrentResultDetails`, `DownloadDescriptor`, `SearchCategory`, `SearchFilters`, `SortOrder`, `ProviderCapabilities`, `ProviderStatus`, `SearchPageState`, `RightsStatus`, `ResultSourceType`, `SearchHistoryEntry`, `ProviderConfiguration`, `TorrentSearchQuery`. All optional fields nullable; missing metadata stays null (never 0/now).
- [ ] T006 [P] Create `DiscoveryError` sealed hierarchy in `domain/discovery/src/main/java/com/pulldock/domain/discovery/error/DiscoveryError.kt` (Input, Network/Offline, Provider, RateLimited, Parse, Timeout, Disabled, NoSupportedProviders) with sanitized `detail`; distinct from `DownloadError`.
- [ ] T007 Create domain contracts in `domain/discovery/src/main/java/com/pulldock/domain/discovery/`: `TorrentSearchRepository.kt`, `TorrentSearchProvider.kt`, `ProviderRegistry.kt` per contracts/.
- [ ] T008 Create Ktor client provider + bounded response handling in `data/discovery/src/main/java/com/pulldock/data/discovery/http/` (`HttpClientProvider.kt`, `BoundedResponse.kt`): per-request timeout, max response size, content-type check.
- [ ] T009 [P] Create in-memory TTL cache `data/discovery/src/main/java/com/pulldock/data/discovery/cache/SearchResultCache.kt` (key = normalized query + category; provider results separate; no caching of auth/rate-limit failures).
- [ ] T010 [P] Create DataStore-backed `data/discovery/src/main/java/com/pulldock/data/discovery/history/SearchHistoryStore.kt` and `.../config/TorznabConfigStore.kt` (Torznab config persisted; API keys NEVER persisted — session-only).
- [ ] T011 Create `data/discovery/src/main/java/com/pulldock/data/discovery/DiscoveryModule.kt` (Hilt) and `DiscoveryRepositoryImpl.kt` skeleton with `search/loadMore/refresh/getDetails` orchestration (structured concurrency, `AppDispatchers.io`, per-provider ~8s timeout, overall deadline, cancellation) per contracts/torrent-search-repository.md.

**Checkpoint**: Foundation ready — user stories can proceed.

---

## Phase 3: User Story 1 — Search once, combined results (Priority: P1) 🎯 MVP

**Goal**: One submit queries enabled providers concurrently and shows one normalized,
de-duplicated, provider-attributed list with partial-failure isolation.

**Independent Test**: With BitSearch + Archive mocked, submit one query → both queried,
results combined/attributed, one provider failing still shows the other's results.

- [ ] T012 [P] [US1] BitSearch provider tests (Ktor MockEngine) in `data/discovery/src/test/java/com/pulldock/data/discovery/provider/BitSearchProviderTest.kt`: valid search/categories/detail, empty, missing optional fields, malformed JSON, invalid infohash, invalid magnet, HTTP 429/401/403/500, rate-limit header parsing, pagination.
- [ ] T013 [P] [US1] Internet Archive provider tests in `data/discovery/src/test/java/com/pulldock/data/discovery/provider/InternetArchiveProviderTest.kt`: advanced search, Archive-BitTorrent-only filtering, one/no/multiple torrents, missing file metadata, missing licence (→ UNKNOWN), invalid download URL, encoded filenames, HTML/invalid response, torrent-file transfer failure.
- [ ] T014 [P] [US1] Domain tests in `domain/discovery/src/test/java/com/pulldock/domain/discovery/`: category mapping, normalization, dedupe by infohash, different infohashes with same title stay distinct, ranking, unknown metadata (not zero), provider-failure mapping.
- [ ] T015 [P] [US1] Orchestration tests in `data/discovery/src/test/java/com/pulldock/data/discovery/DiscoveryRepositoryImplTest.kt`: all succeed; one fails; one times out; all empty; all fail; identical infohashes across providers; new search cancels old; global timeout.
- [ ] T016 [P] [US1] Implement `BitSearchProvider.kt` (+ DTOs/mapper) in `data/discovery/src/main/java/com/pulldock/data/discovery/provider/` per FR-010..FR-019 (documented REST only; detail on demand; magnet from detail validated; optional session-only `x-api-key`; no aggressive 401/403/429 retry).
- [ ] T017 [P] [US1] Implement `InternetArchiveProvider.kt` (+ DTOs/mapper) in `data/discovery/src/main/java/com/pulldock/data/discovery/provider/` per FR-020..FR-028 (`format:"Archive BitTorrent"`; real `.torrent` from metadata `files`; licence→rights, absent=UNKNOWN).
- [ ] T018 [US1] Implement `ProviderRegistry` registration + `DiscoveryRepositoryImpl.search()` fan-out/normalize/dedupe/rank in `data/discovery/src/main/java/com/pulldock/data/discovery/` (depends T011, T016, T017).
- [ ] T019 [US1] Implement `DiscoverScreen.kt`, `DiscoverViewModel.kt`, `DiscoverUiState.kt`, `DiscoverUiEvent.kt` in `feature/discovery/src/main/java/com/pulldock/feature/discovery/` using `PdSearchField`/`PdCard`/`PdFilterChips`/`PdStatusBadge`/`PdStates` (submit on submit, not per keystroke).
- [ ] T020 [US1] Wire Discover as a primary destination + routes in `app/src/main/java/com/pulldock/navigation/PullDockDestination.kt` and `RootNavHost.kt` (one-tap reachable; existing destinations unchanged).
- [ ] T021 [US1] Compose UI tests in `feature/discovery/src/androidTest/java/com/pulldock/feature/discovery/`: search input/submit, category, loading/empty/partial/error, result cards, provider attribution, a11y semantics.

**Checkpoint**: US1 independently functional — MVP.

---

## Phase 4: User Story 2 — Review & add via existing Add flow (Priority: P1)

**Goal**: A selected result enters the existing Add Torrent flow (magnet or Archive temp
`.torrent`); transfer appears in the existing unified queue only after explicit start.

**Independent Test**: From mocked results, Add opens the existing Add flow (magnet pre-filled;
Archive temp file), no transfer until start, duplicate reused.

- [ ] T022 [P] [US2] Handoff integration test in `feature/discovery/src/test/java/com/pulldock/feature/discovery/DiscoveryHandoffTest.kt` (existing Add-flow fake): magnet → add `source`; Archive → `file://` uri; no transfer until start; duplicate reuse.
- [ ] T023 [P] [US2] Temp torrent lifecycle test in `data/discovery/src/test/java/com/pulldock/data/discovery/provider/TemporaryTorrentFileTest.kt`: bounded size, HTML rejection, https origin, cleanup on success/back.
- [ ] T024 [US2] Implement `details/ResultDetailsScreen.kt` in `feature/discovery/src/main/java/com/pulldock/feature/discovery/` (full details, rights/licence, actions; no Add when no usable torrent).
- [ ] T025 [US2] Implement `resolveDownloadDescriptor()` + magnet validation (btih hex/base32) in `data/discovery/src/main/java/com/pulldock/data/discovery/DiscoveryRepositoryImpl.kt` (depends T018).
- [ ] T026 [US2] Implement `TemporaryTorrentFile` (download via `core:storage` `StorageRepository.createTempFile`, bounded size, real-torrent check, `file://` uri, idempotent `cleanup()`) in `data/discovery/src/main/java/com/pulldock/data/discovery/provider/` per FR-055/FR-056.
- [ ] T027 [US2] Implement `DiscoverViewModel.onAddClicked()` → emit `AddToPullDock(source)`; app navigates via existing `Routes.addSource(source)`; trigger temp cleanup on leaving Add (success/back/cancel) (depends T024, T025, T026).
- [ ] T028 [US2] Compose UI tests in `feature/discovery/src/androidTest/java/com/pulldock/feature/discovery/`: details, Add-to-PullDock action, no-torrent-no-action, multiple-torrent choice.

**Checkpoint**: US1 + US2 independently functional.

---

## Phase 5: User Story 3 — Filter, sort, paginate (Priority: P2)

**Goal**: Category/provider/min-seeders/size/date filters, sort orders, and independent
per-provider load-more; unsupported filters labeled client-side.

**Independent Test**: Multi-page mocks → per-provider advance, no repeats, upstream vs client
filter labeling, sort respected.

- [ ] T029 [P] [US3] Pagination/filter/sort tests in `data/discovery/src/test/java/com/pulldock/data/discovery/`: per-provider advance, no repeated results, upstream vs client filter labeling, sort orders.
- [ ] T030 [US3] Implement `SearchFilters` application + per-provider pagination state in `DiscoveryRepositoryImpl.loadMore()` in `data/discovery/src/main/java/com/pulldock/data/discovery/DiscoveryRepositoryImpl.kt` (depends T018).
- [ ] T031 [US3] Implement `filters/FilterSheet.kt`, sort controls, and load-more in `feature/discovery/src/main/java/com/pulldock/feature/discovery/results/ResultsScreen.kt` (distinguish applied-upstream vs client-side).
- [ ] T032 [US3] Compose UI tests in `feature/discovery/src/androidTest/java/com/pulldock/feature/discovery/`: filters, sort, load-more.

**Checkpoint**: US1–US3 independently functional.

---

## Phase 6: User Story 4 — Availability, history, discovery settings (Priority: P2)

**Goal**: Torznab (P1) adapter + per-provider availability, local search history, discovery
settings (providers, Torznab endpoints, adult opt-in, clear history/cache, privacy).

**Independent Test**: No Torznab → search works, no error; enable Torznab → caps checked;
adult default-off; clear history removes it locally.

- [ ] T033 [P] [US4] Torznab provider tests in `data/discovery/src/test/java/com/pulldock/data/discovery/provider/TorznabProviderTest.kt`: capabilities, general RSS, category parsing, supported/unsupported types, XML namespaces, seeders/peers, magnet, missing optional attrs, malformed XML, oversized XML, auth failure, timeout.
- [ ] T034 [P] [US4] Config/history store tests in `data/discovery/src/test/java/com/pulldock/data/discovery/`: no secrets persisted, clear history, config round-trip.
- [ ] T035 [P] [US4] Implement `TorznabProvider.kt` (`XmlPullParser`, caps-before-use, https-only, bounded size) in `data/discovery/src/main/java/com/pulldock/data/discovery/provider/` per FR-029..FR-034.
- [ ] T036 [US4] Implement `TorznabConfigStore` wiring + `settings/DiscoverySettingsScreen.kt` in `feature/discovery/src/main/java/com/pulldock/feature/discovery/settings/` (enable providers, Torznab endpoint mgmt, adult opt-in, clear history, clear cache, privacy) (depends T010, T035).
- [ ] T037 [US4] Implement a per-result "Hide" / "Report source" action (persist hidden result ids in the DataStore-backed store; exclude hidden results from result lists) in feature/discovery/src/main/java/com/pulldock/feature/discovery/components/ per FR-065.
- [ ] T038 [US4] Implement `components/ProviderStatusRow.kt` + availability/degraded UI + `history/SearchHistoryUi.kt` (repeat/delete-one/clear-all) in `feature/discovery/src/main/java/com/pulldock/feature/discovery/`.
- [ ] T039 [US4] Compose UI tests in `feature/discovery/src/androidTest/java/com/pulldock/feature/discovery/`: no-Torznab success, adult default-off, history clear.

**Checkpoint**: US1–US4 independently functional.

---

## Phase 7: User Story 5 — Honest states, resilience, accessibility (Priority: P2)

**Goal**: All UI states with actionable messages and rate-limit-respecting retry; usable with
TalkBack, large fonts, RTL, adaptive layouts, light/dark.

**Independent Test**: Simulate each provider state → distinct accurate message, partial never
replaced by fatal; TalkBack + large fonts → semantics/layout intact.

- [ ] T040 [US5] Map `DiscoveryError` → user messages and cover all states (initial, empty query, loading, results, empty, partial, one/all provider failed, rate-limited, disabled, offline, timeout, no-supported-providers, loading-more, refreshing, cancelled) in `feature/discovery/src/main/java/com/pulldock/feature/discovery/DiscoverUiState.kt`; retry respects rate limits (FR-067..FR-069).
- [ ] T041 [US5] Accessibility + light/dark + RTL + large-font + adaptive pass across Discover/results/details (semantics, 48dp targets) in `feature/discovery/src/main/java/com/pulldock/feature/discovery/`.
- [ ] T042 [P] [US5] Compose UI tests for all states + a11y semantics in `feature/discovery/src/androidTest/java/com/pulldock/feature/discovery/`.
- [ ] T043 [US5] Offline/timeout/cancel tests in `data/discovery/src/test/java/com/pulldock/data/discovery/` and `feature/discovery/src/test/java/com/pulldock/feature/discovery/`: no request offline; bounded timeout; new query cancels old.

**Checkpoint**: All user stories independently functional.

---

## Phase 8: Polish & Cross-Cutting Concerns

- [ ] T044 [P] Finalize `docs/THIRD_PARTY_LICENSES.md` + dependency assessment for Ktor/kotlinx (if not completed in T004).
- [ ] T045 [P] Update `AGENTS.md` §2.3 status and `docs/KNOWN_LIMITATIONS.md` (track on-device torrent verification as a discovery risk; no second engine/queue).
- [ ] T046 Run `quickstart.md` scenarios S1–S10 on a device/emulator; record actual results.
- [ ] T047 Run regression + build + lint: `./gradlew :app:assembleDebug`, the new module tests, existing torrent/download/add/nav/queue/storage tests, and `./gradlew :app:lintDebug`; fix issues (do not weaken existing tests).
- [ ] T048 [P] Final cross-check: ADR index updated; confirm no second queue/engine, no scraping/backend deps, no embedded key, credentials not logged, adult off by default, FR-065 hide/report action present, and FR-066 content-safety (no CSAM/CSAM-adjacent or non-consensual content surfaced).

---

## Dependencies & Execution Order

### Phase dependencies
- Setup (P1) → Foundational (P2) → US1 (P3) → US2 (P4) → US3 (P5) → US4 (P6) → US5 (P7) → Polish (P8).
- Foundational blocks all stories. US1 is the MVP and unlocks the Discover surface.

### Story dependencies
- **US1 (P1)**: after Foundational; no story deps. MVP.
- **US2 (P1)**: after US1 (needs results to select); reuses existing Add flow.
- **US3 (P2)**: after US1 (needs results); independent of US2.
- **US4 (P2)**: after US1 (registry); adds Torznab + settings/history.
- **US5 (P2)**: after US1; spans all states; final hardening.

### Within each story
- Tests (T0xx) written first and FAIL before implementation.
- Models/contracts (Foundational) before providers; providers before repository wiring; repository before ViewModel/UI.

### Parallel opportunities
- Setup: T002, T003, T004 [P].
- Foundational: T005, T006, T009, T010 [P].
- US1: tests T012–T015 [P]; providers T016, T017 [P].
- US4: T033, T034, T035 [P].
- Polish: T043, T044, T047 [P].

---

## Parallel Example: US1

```bash
# Tests first (all [P]):
Task: "BitSearch provider tests in data/discovery/src/test/.../BitSearchProviderTest.kt"
Task: "Internet Archive provider tests in data/discovery/src/test/.../InternetArchiveProviderTest.kt"
Task: "Domain tests in domain/discovery/src/test/..."
Task: "Orchestration tests in data/discovery/src/test/.../DiscoveryRepositoryImplTest.kt"

# Then providers (both [P]):
Task: "Implement BitSearchProvider.kt"
Task: "Implement InternetArchiveProvider.kt"
```

---

## Implementation Strategy

### MVP first (US1 only)
1. Setup (P1) → Foundational (P2) → US1 (P3).
2. STOP and VALIDATE US1 independently (search + combined results + partial failure).
3. Demo if ready.

### Incremental delivery
1. Setup + Foundational → foundation ready.
2. US1 → validate → demo (MVP).
3. US2 → validate → demo (search-to-download).
4. US3 → validate → demo (refine).
5. US4 → validate → demo (Torznab/settings/history).
6. US5 → validate → demo (states/a11y).
7. Polish → regression + quickstart + docs.

### Notes
- [P] = different files, no incomplete dependency.
- Each story is independently completable and testable; stop at checkpoints to validate.
- Never weaken existing tests; never introduce a second queue/engine; never persist/log secrets.
