# Implementation Plan: Torrent Discovery and Multi-Provider Search

**Branch**: `002-torrent-discovery` | **Date**: 2026-11-28 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/002-torrent-discovery/spec.md`

## Summary

Add an in-app **Discover** surface to the existing PullDock Android app that fans a single
user query out concurrently to multiple independent, documented-API-only torrent metadata
providers (BitSearch P0, Internet Archive P0, user-configured Torznab P1), normalizes and
de-duplicates the results, and hands a selected result into PullDock's **existing** Add
Torrent flow (magnet via the existing add route; Internet Archive `.torrent` fetched to
app-private temp storage and handed to the existing file-based add route). The resulting
transfer is managed by the existing unified queue. No scraping, no backend, no second
engine, no duplicate queue.

Technical approach: a new pure-Kotlin `:domain:discovery` (contracts + models + errors),
a `:data:discovery` (Ktor client + kotlinx.serialization for JSON, Android `XmlPullParser`
for Torznab, provider registry, per-provider adapters, mappers, in-memory TTL cache,
DataStore-backed history and Torznab config), and a `:feature:discovery` (Compose Discover,
results, details, filters, history, discovery settings) wired into the existing navigation
shell and design system. Networking is the only new platform capability and is isolated in
`:data:discovery`.

## Technical Context

**Language/Version**: Kotlin 2.4.20 (JVM target 17), Android minSdk 26, compileSdk 37.

**Primary Dependencies**: Existing — Hilt 2.60.1, Room 2.7.0, DataStore 1.1.7, Coroutines
1.11.0, Compose BOM 2026.09.00, Navigation 2.10.2, libtorrent4j 2.1.0-39 (unchanged).
New (pinned in `gradle/libs.versions.toml` at implementation time to latest stable compatible
with the above): a single HTTP client — **Ktor Client** with the **OkHttp** engine, plus
**kotlinx-serialization** JSON and Ktor content negotiation. No Retrofit. XML via the
platform `android.util.XmlPullParser` (no dependency).

**Storage**: No new durable storage. In-memory TTL cache for results; DataStore
(shared `core:datastore`) for search history and Torznab provider configuration; app-private
temp file (via existing `core:storage` `StorageRepository.createTempFile`) for a fetched
Archive `.torrent`. No Room schema change.

**Testing**: JUnit4 + kotlinx-coroutines-test (existing), Ktor `MockEngine` for provider
tests, Compose UI test infra (existing), and the existing Add-flow test/fake for handoff.
No live provider/site dependency in unit tests.

**Target Platform**: Android 8.0+ (API 26+), 64-bit ABIs only (arm64-v8a, x86_64).

**Project Type**: Mobile app (multi-module Android, Clean Architecture).

**Performance Goals**: Provider queries concurrent; per-provider timeout ~8s under a bounded
overall deadline; results list via lazy column; no network/parse on the main thread; app
remains responsive on mid-range devices.

**Constraints**: Pure-Kotlin domain (no Android/Room/HTTP/engine types); presentation never
touches data/engine; HTTPS-only provider endpoints; credentials session-only (never
persisted); no scraping/automation; no backend/Redis/remote DB; adult content off by default.

**Scale/Scope**: 3 new Gradle modules; ~3 provider adapters; 1 new app destination; results
held in memory (bounded by TTL/size); no schema migration.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Assessment |
|---|---|
| I Product/engineering identity | PASS — additive discovery layer; correctness/reliability/security prioritized. |
| II Platform/tech standards | PASS — Kotlin/Compose/M3/Hilt/DataStore/coroutines; new Ktor client justified (XII) and pinned. |
| III Clean Architecture & modules | PASS — `:domain:discovery` pure Kotlin; `:data:discovery` implements contracts; `:feature:discovery` → domain only; new modules justified by a real boundary (ADR-0014). |
| IV Kotlin standards | PASS — immutable models, sealed state/errors, structured concurrency, injected dispatchers, no `GlobalScope`. |
| V Compose/UI standards | PASS — `StateFlow` UI state, unidirectional events, reusable `Pd*` components, loading/empty/error/partial states, a11y. |
| VI Engine isolation | PASS — no engine/JNI types in discovery; handoff reuses existing Add flow; libtorrent untouched. |
| VII Reliability/persistence | PASS — no new durable state; existing queue/recovery reused; temp `.torrent` cleaned up. |
| VIII Lifecycle/background | PASS — search is UI-scoped, cancellable; no new services/foreground work. |
| IX Security & privacy | PASS — URL/scheme allowlist + SSRF guard, HTTPS-only, bounded responses, sanitized logs, no secrets persisted/logged, adult off by default. |
| X Testing | PASS — offline provider/orchestration/domain/Compose tests + regression; failure/boundary cases. |
| XI Error handling | PASS — typed `DiscoveryError` hierarchy, user-mappable, sanitized; distinct from persisted `DownloadError`. |
| XII Dependencies/ADRs | PASS — Ktor + serialization recorded in ADR-0015; module/provider architecture in ADR-0014; license/maintenance review before pinning. |

**Gate result**: PASS (no violations). Two new ADRs are required as part of implementation
(ADR-0014 discovery modules & provider-adapter architecture; ADR-0015 HTTP client). No
Complexity Tracking entries.

## Project Structure

### Documentation (this feature)

```text
specs/002-torrent-discovery/
├── plan.md              # This file
├── research.md          # Phase 0 decisions
├── data-model.md        # Phase 1 entities, validation, state
├── quickstart.md        # Phase 1 validation guide
├── contracts/           # Phase 1 interfaces
│   ├── README.md
│   ├── torrent-search-repository.md
│   ├── torrent-search-provider.md
│   └── discovery-handoff.md
├── checklists/requirements.md
└── tasks.md             # Phase 2 (/speckit.tasks — not created here)
```

### Source Code (repository root)

```text
app/                                  # composition root (unchanged responsibilities)
  src/main/java/com/pulldock/
    navigation/
      PullDockDestination.kt          # add DISCOVER primary destination
      RootNavHost.kt                  # register Discover + results/details routes
      MainActivity.kt                 # (unchanged) existing add?source deep-link reused

domain/discovery/                     # NEW — pure Kotlin (kotlin.jvm)
  src/main/java/com/pulldock/domain/discovery/
    TorrentSearchRepository.kt        # search / loadMore / refresh / getDetails
    TorrentSearchProvider.kt          # provider contract + ProviderCapabilities/Status
    model/                            # TorrentSearchResult, TorrentResultDetails,
                                      # SearchCategory, SearchFilters, SearchPageState,
                                      # RightsStatus, ResultSourceType
    error/DiscoveryError.kt           # typed discovery errors
    usecase/                          # SearchTorrents, LoadMoreResults, RefreshSearch

data/discovery/                       # NEW — android.library + Hilt
  src/main/java/com/pulldock/data/discovery/
    DiscoveryRepositoryImpl.kt
    DiscoveryModule.kt                # Hilt bindings
    registry/ProviderRegistry.kt
    provider/BitSearchProvider.kt
    provider/InternetArchiveProvider.kt
    provider/TorznabProvider.kt
    provider/dto/                     # provider-specific DTOs (@Serializable / XML)
    mapper/                           # DTO -> domain mappers
    http/                             # Ktor client provider, bounded response handling
    cache/SearchResultCache.kt        # in-memory TTL
    history/SearchHistoryStore.kt     # DataStore-backed
    config/TorznabConfigStore.kt      # DataStore-backed (no secrets)

feature/discovery/                    # NEW — android.library + Compose + Hilt
  src/main/java/com/pulldock/feature/discovery/
    DiscoverScreen.kt  DiscoverViewModel.kt
    DiscoverUiState.kt DiscoverUiEvent.kt
    results/ResultsScreen.kt  details/ResultDetailsScreen.kt
    filters/FilterSheet.kt    history/SearchHistoryUi.kt
    settings/DiscoverySettingsScreen.kt
    components/ResultCard.kt  ProviderStatusRow.kt

core/…                                # reuse; only core:datastore is consumed (history/config)
engines/…                             # untouched

# Tests (per module)
domain/discovery/src/test/…           # provider-registry, category mapping, dedupe, ranking
data/discovery/src/test/…             # provider mocks (Ktor MockEngine), mappers, cache
feature/discovery/src/test/…          # ViewModel; src/androidTest Compose UI
```

**Structure Decision**: Three new modules mirroring the existing `:domain:*` / `:data:*` /
`:feature:*` rings (ADR-0008). `:app` gains one primary destination and routes; existing
modules are otherwise untouched. Networking is confined to `:data:discovery`.

## Complexity Tracking

> No constitution violations; no entries required.