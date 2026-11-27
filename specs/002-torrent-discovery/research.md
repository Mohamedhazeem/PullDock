# Phase 0 Research: Torrent Discovery and Multi-Provider Search

**Feature**: `002-torrent-discovery` | **Date**: 2026-11-28 | **Plan**: [plan.md](./plan.md)

Each decision records the choice, rationale, and rejected alternatives. Items marked
"verify at implementation" are external API facts that must be confirmed against the
provider's live documentation and encoded as fixtures; they do not block planning.

## D1. HTTP client for provider requests

- **Decision**: Use **Ktor Client** with the **OkHttp** engine, **kotlinx-serialization**
  JSON, and Ktor **ContentNegotiation**. Add the Kotlin serialization Gradle plugin. Pin
  versions in `gradle/libs.versions.toml` at implementation time (latest stable compatible
  with Kotlin 2.4.20 / AGP 9.4.1 / minSdk 26). Confine all HTTP to `:data:discovery`.
- **Rationale**: The repository has no HTTP client today (only `ConnectivityMonitor`), so one
  must be introduced. Ktor gives a small, coroutine-first client with a mockable `MockEngine`
  for offline provider tests, first-class kotlinx-serialization (already the Kotlin-idiomatic
  choice), and per-request timeout control. OkHttp engine reuses a mature connection pool.
- **Alternatives**: (a) Raw `HttpURLConnection` — no mocking story, verbose; rejected.
  (b) Retrofit + kotlinx-serialization — heavier, annotation-processing, and would duplicate
  a serialization/HTTP stack alongside Ktor; rejected to avoid two stacks. (c) OkHttp alone
  with manual JSON — workable but loses coroutine ergonomics and MockEngine; rejected.

## D2. XML parsing for Torznab

- **Decision**: Parse Torznab RSS/XML with the platform `android.util.XmlPullParser`
  (`Xml.newPullParser()`), with a bounded input stream and a maximum response size.
- **Rationale**: No dependency, hardened against XXE/entity expansion by default, and
  sufficient for RSS. Keeps the dependency surface minimal (constitution XII).
- **Alternatives**: Jsoup/DOM — heavier and unnecessary for RSS; rejected. A JSON-only
  Torznab assumption — invalid, Torznab is XML; rejected.

## D3. Module boundaries (ADR-0014)

- **Decision**: Add `:domain:discovery`, `:data:discovery`, `:feature:discovery`; record in
  **ADR-0014** and update `AGENTS.md` §2 and `settings.gradle.kts`.
- **Rationale**: Discovery is an independent domain with its own provider integrations and
  user surface — a real boundary (ADR-0008), not premature micro-modularization. Mirrors the
  existing ring structure and build conventions.
- **Alternatives**: Fold into `:domain:download`/`:feature:home` — couples discovery to the
  transfer domain and bloats Home; rejected. A single `:feature:discovery` containing data —
  violates presentation→domain←data; rejected.

## D4. Provider-adapter architecture & registry

- **Decision**: A `TorrentSearchProvider` contract; `BitSearchProvider`,
  `InternetArchiveProvider`, `TorznabProvider` implement it independently; a
  `ProviderRegistry` resolves enabled providers that support the requested category/search
  type. Adding a provider must not change the UI or engine (FR-005).
- **Rationale**: Isolates provider specifics (DTOs, auth, pagination) behind one contract;
  enables per-provider failure isolation, timeouts, and independent tests.
- **Alternatives**: A single monolithic searcher with `when(provider)` branches — untestable
  and error-prone; rejected.

## D5. Concurrency, timeouts, cancellation

- **Decision**: Run provider searches with `coroutineScope { }` + `async` on the injected
  `AppDispatchers.io`; each provider call wrapped in `withTimeout(~8s)` under a bounded
  overall deadline; a new query cancels the prior `Job` (ViewModel scope) so stale results
  cannot overwrite newer ones (FR-037..FR-042).
- **Rationale**: Structured concurrency + injected dispatchers is the repo standard; bounded
  timeouts prevent one slow endpoint from freezing the UI; cancellation is the mechanism the
  spec requires for superseded queries.
- **Alternatives**: `GlobalScope`/unstructured launches — forbidden; rejected. Sequential
  provider calls — slow; rejected.

## D6. Deduplication and ranking

- **Decision**: Dedupe by (1) normalized infohash (lowercase 40-hex / 32-base32 btih), then
  (2) exact provider id, then (3) conservative title + exact size. Merge same-infohash
  results preserving all contributing providers/source URLs. Rank by relevance → category →
  completeness → seeders → freshness; unknown ≠ zero (FR-043..FR-045).
- **Rationale**: Matches the spec's ordering and the existing `MagnetValidator` btih
  normalization so discovery dedupe is consistent with the transfer layer's `sourceHash`.
- **Alternatives**: Title-only merge — would wrongly merge distinct releases; rejected.

## D7. Handoff into the existing Add flow

- **Decision**: Magnet results navigate to the existing add route with the magnet as the
  `source` argument (the existing `Routes.addSource(...)` / `add?source={source}` deep link
  the `AddDownloadViewModel` already classifies). Archive results are downloaded to an
  app-private temp file via `core:storage` `StorageRepository.createTempFile`, validated as a
  real torrent (bounded size, not HTML), then handed to the same add route as a `file://`
  URI (the existing `TORRENT_URI` path). No new ingestion path; `AddTorrent` performs
  validation, duplicate detection (`findBySourceHash`), metadata fetch, file selection,
  destination, and explicit start (FR-054..FR-060).
- **Rationale**: Reuses the exact, already-tested pipeline and its duplicate/consent
  behavior; the existing `AddDownloadViewModel` already accepts `magnet:` and `content://`/
  `file://` sources.
- **Alternatives**: Calling `TorrentEngine`/`AddTorrent` directly from discovery — bypasses
  the review UI and violates boundaries; rejected. A second ingestion path — forbidden;
  rejected.

## D8. Temporary `.torrent` file lifecycle

- **Decision**: A `TemporaryTorrentFile` handle in `:data:discovery` (download → bounded
  size → torrent-magic/`d8:`-structure sanity check → `file://` uri). Cleanup is triggered
  when the user leaves the Add flow (success, back/cancel) or via a bounded cache sweep;
  cleanup failure is logged (sanitized) and never corrupts other state (FR-056).
- **Rationale**: The existing Add flow consumes a URI but does not own discovery's temp file,
  so discovery must own cleanup; app-private cache + explicit deletion satisfies scoped
  storage and privacy.
- **Alternatives**: Persist the `.torrent` in Room — unnecessary durable state; rejected.
  Rely on OS cache eviction — unbounded/uncertain; rejected.

## D9. Search history and Torznab configuration persistence

- **Decision**: Search history and Torznab provider config (display name, endpoint, enabled,
  last capability result, categories) persist in the shared **DataStore** (`core:datastore`).
  API keys are **session-only in memory**, never persisted (clarified). No Room change.
- **Rationale**: DataStore is the repo's preferences mechanism; avoids a schema migration and
  keeps secrets out of durable storage.
- **Alternatives**: Room table for history — schema change + migration for no benefit;
  rejected. Persisting keys — forbidden by security policy; rejected.

## D10. In-memory result cache

- **Decision**: Short-lived TTL cache (default 2–5 min, configurable) keyed by normalized
  query + category, provider results kept separate; auth/rate-limit failures are not cached as
  valid; stale entries are never presented as fresh (FR-062).
- **Rationale**: Reduces duplicate upstream calls within a session without durable state.
- **Alternatives**: No cache — more requests, more rate-limit risk; rejected. Durable cache —
  staleness/privacy risk; rejected.

## D11. Category mapping

- **Decision**: Application-level `SearchCategory` with per-provider mapping functions.
  BitSearch numeric map (Other=1…Adult=10, Documentaries via movie/TV terms). Internet
  Archive via mediatype/subject/title/creator/format restricted to `format:"Archive
  BitTorrent"`. Torznab via the fetched capability document (never global numeric assumptions)
  (FR-035/FR-036).
- **Rationale**: Provider IDs are not portable; capability-driven mapping is the only correct
  Torznab approach.
- **Alternatives**: A single global numeric category — wrong across providers; rejected.

## D12. Adult content gating

- **Decision**: Adult off by default; a dedicated "Adult" category appears only after a
  one-time explicit opt-in; results clearly labeled; disable anytime; no auto thumbnails;
  provider adult classification treated as non-authoritative (FR-064..FR-066).
- **Rationale**: Spec + clarification; keeps adult content out of ordinary browsing.
- **Alternatives**: Always-visible Adult chip — fails default-off; rejected.

## D13. Error typing

- **Decision**: A `DiscoveryError` sealed hierarchy (Input, Network/Offline, Provider,
  RateLimited, Parse, Timeout, Disabled, NoSupportedProviders) with sanitized details, mapped
  to user messages; kept distinct from the persisted `DownloadError` (FR-070, NFR-001).
- **Rationale**: Discovery failures are not transfer failures and must not be persisted as
  task errors; typed errors satisfy constitution XI.
- **Alternatives**: Reusing `DownloadError` — conflates discovery with transfer state;
  rejected.

## D14. Testing strategy

- **Decision**: Ktor `MockEngine` for provider JSON/XML fixtures; JUnit + coroutines-test for
  domain/orchestration; Compose UI tests for screens; existing Add-flow test/fake for handoff;
  full regression of torrent/download/add/nav/queue/storage (NFR-011, spec Testing
  Requirements).
- **Rationale**: Offline, deterministic, and matches existing test tooling.
- **Alternatives**: Live-provider tests — flaky, rate-limited, non-deterministic; rejected.

## D15. External API facts — verify at implementation (fixtures, not guesses)

- **Decision**: Confirm and encode as fixtures during implementation: exact BitSearch response
  field names/types and rate-limit headers; Internet Archive Advanced Search field semantics
  and the documented Archive-BitTorrent download-URL construction + redirect behavior;
  Torznab per-indexer capability/category variance and minimal RSS structure. Do not invent
  endpoints/fields (spec "Unresolved items").
- **Rationale**: These are provider-owned details that change; fixtures keep tests stable and
  the code honest.
- **Alternatives**: Hardcoding assumed fields — brittle and dishonest; rejected.

## D16. Dependency version pinning & licenses

- **Decision**: Add Ktor + kotlinx-serialization to the version catalog with pinned versions;
  record license/maintenance review (Apache-2.0 for Ktor/kotlinx) and update
  `docs/THIRD_PARTY_LICENSES.md`; verify 64-bit ABI compatibility (pure JVM/Kotlin, no native
  ABI impact).
- **Rationale**: Constitution XII + AGENTS.md §17.
- **Alternatives**: Unpinned/dynamic versions — forbidden; rejected.

## Outstanding (non-blocking)

All NEEDS CLARIFICATION from the spec are resolved (see spec `## Clarifications`). Remaining
items are D15 external API facts to verify with fixtures at implementation time.