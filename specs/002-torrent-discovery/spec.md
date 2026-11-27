# Feature Specification: Torrent Discovery and Multi-Provider Search

**Feature Branch**: `002-torrent-discovery`

**Created**: 2026-11-28

**Status**: Draft

**Input**: User description: "Add an integrated, API-only torrent discovery experience to the existing PullDock Android application. A user searches once, sees normalized results from multiple independent providers (BitSearch, Internet Archive, and user-configured Torznab indexers), reviews a result, and hands the selected source into PullDock's existing Add Torrent flow and unified queue. No scraping, no new backend, no second torrent engine, no duplicate queue."

> Naming note: this is an additive feature to the existing `001-production-android-app` system. It does not modify `001`, the constitution, or `AGENTS.md`. See "Conflict with 001 scope" in Assumptions.

## Product Summary

PullDock already ingests torrents from magnet URIs and `.torrent` documents, fetches metadata, lets the user select files and a destination, and manages the transfer through one unified, persistent queue backed by libtorrent4j. Today a user must already possess a magnet link or a `.torrent` file from elsewhere.

Torrent Discovery adds the missing first step: an in-app **Discover** surface where the user searches for a title once and receives a single, normalized result list assembled concurrently from multiple independent, **documented-API-only** metadata providers. The user reviews a result, taps **Add to PullDock**, and is handed into the **existing** Add Torrent review flow — the same metadata fetch, file selection, destination confirmation, explicit start, unified queue, recovery, and detail experience that manual magnet/`.torrent` entry already provides.

Core product principle: **Torrent Discovery is a discovery layer, not a new download system.** It MUST reuse PullDock's existing torrent ingestion, review, engine, storage, queue, recovery, and detail functionality. It MUST NOT introduce a second torrent engine, a second queue, a backend service, or any web-scraping capability.

The feature is local-first and privacy-conscious: search queries are sent only to the external providers the user has enabled, results are held in memory with a short TTL, and no search history or query is transmitted to any PullDock-operated service (none exists).

### Relationship to existing capabilities (reuse, do not rebuild)

The following already exist and MUST be reused unchanged: magnet input, `.torrent` document input, torrent metadata retrieval, file selection, destination selection, libtorrent4j transfers, the unified queue, pause/resume/recheck/priority/limits, persistence and recovery, storage/finalization, and download detail. Discovery only adds the "find a source" step in front of this pipeline. The `:feature:queue` module remains intentionally unused; Home remains the queue surface and MUST NOT be repurposed for discovery.

## Clarifications

### Session 2026-11-28

- Q: Should Torrent Discovery proceed as an additive post-v1 feature, given that `001-production-android-app` lists torrent discovery as out of scope for v1? → A: Proceed as an additive post-v1 feature; `001` is left unchanged (its out-of-scope list describes the v1 release boundary, not a permanent prohibition).
- Q: Should the Torznab-compatible indexer provider (priority P1) be included in this feature's first release, or deferred to a later slice? → A: Include Torznab in this feature's first release via the same provider-adapter architecture, fully usable with zero Torznab configuration.
- Q: How should optional provider API credentials (BitSearch/Torznab API keys) be persisted, given no secure credential store exists? → A: Keep credentials session-only in memory; never persist them; users re-enter after process death. No secure store is added in this feature.
- Q: Should the Torznab adapter allow deliberately configured local-network plain-HTTP endpoints, or require HTTPS for every endpoint? → A: Require HTTPS for all Torznab endpoints in this feature; local-network plain-HTTP endpoints are deferred.
- Q: When an Internet Archive item contains more than one `.torrent` file, how should the app decide which torrent to offer? → A: Present the available torrent files (with size/date) and require the user to choose before Add; no silent auto-selection.
- Q: When the user enables adult content, how should it be exposed in Discover? → A: A dedicated, clearly labeled "Adult" category that appears only after a one-time explicit opt-in and can be turned off at any time.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Search once, get combined results from multiple providers (Priority: P1)

A user opens Discover, types a title, optionally picks a category, and submits. PullDock queries every enabled provider that supports the request concurrently and shows one combined, de-duplicated list. Each card states which provider returned it and shows only the metadata that provider actually supplied.

**Why this priority**: This is the minimum valuable slice. Without a single search that fans out to multiple providers and returns an honest combined list, the feature has no reason to exist. It is independently valuable even before any handoff to the download flow.

**Independent Test**: With BitSearch and Internet Archive enabled and mocked, submit one query; verify both providers are queried, results are normalized into one list, each result is attributed to its provider, and a failure in one provider still shows the other's results.

**Acceptance Scenarios**:

1. **Given** two enabled providers and a non-empty query, **When** the user submits, **Then** both providers are queried concurrently and their results appear in one combined list.
2. **Given** a result returned by a specific provider, **When** the card is shown, **Then** the provider name/badge is visible and only metadata the provider supplied is displayed.
3. **Given** one provider returns results and another fails or times out, **When** results are shown, **Then** the successful provider's results are displayed and the failed provider is reported separately without blocking the successful results.
4. **Given** two providers return the same infohash, **When** results are combined, **Then** they appear as one result that preserves both contributing providers and source attribution.
5. **Given** a provider omits an optional field (e.g. seeders), **When** the result is shown, **Then** the missing value is shown as unknown and is never fabricated as zero, today's date, or a default.

---

### User Story 2 — Review a result and add it through the existing Add Torrent flow (Priority: P1)

A user opens a result's details, reviews title, provider, size, infohash, dates, rights/licence where present, and taps **Add to PullDock**. For a magnet result the magnet is handed to the existing Add flow; for an Internet Archive result the actual `.torrent` file is retrieved and handed to the existing file-based Add flow. The user then performs the existing metadata review, file selection, destination confirmation, and explicit start, and the transfer appears in the existing unified queue.

**Why this priority**: The discovery-to-download handoff is the feature's purpose. It is the second half of the P1 MVP and is only meaningful once US1 supplies a selectable result.

**Independent Test**: From a mocked magnet result, tap Add and verify the existing Add flow opens with the source pre-filled; from a mocked Archive result, verify the `.torrent` is fetched to app-private temp storage and the file-based Add flow opens. Confirm no new queue entry exists until the user explicitly starts.

**Acceptance Scenarios**:

1. **Given** a magnet result, **When** the user taps **Add to PullDock**, **Then** the existing Add Torrent flow opens with that magnet and performs its normal validation and metadata fetch.
2. **Given** an Internet Archive result with a valid `.torrent` file, **When** the user taps **Add to PullDock**, **Then** the `.torrent` is downloaded to app-private temporary storage, validated as a real torrent file, and the existing file-based Add flow opens on it.
3. **Given** the user is in the existing Add flow, **When** they select files, confirm the destination, and start, **Then** the transfer is created and managed by the existing unified queue with all existing controls (pause/resume/recheck/priority/limits/recovery).
4. **Given** the selected source is already an active or completed torrent, **When** the user attempts to add it, **Then** the existing duplicate handling reports it without creating a second transfer.
5. **Given** an Archive result has no usable `.torrent` file, **When** the details are shown, **Then** no **Add to PullDock** action is offered.
6. **Given** the user cancels the Add flow, **When** they return, **Then** no transfer exists and any temporary `.torrent` file is cleaned up.

---

### User Story 3 — Filter, sort, paginate, and refine results (Priority: P2)

A user narrows results by category and provider, sets a minimum-seeders or size/date filter where the provider supplies that metadata, changes the sort order, and loads more results. Filters the providers cannot apply upstream are clearly distinguished from those actually applied.

**Why this priority**: Refinement makes large result sets usable, but the feature is already valuable without it (US1/US2). It depends on results existing first.

**Independent Test**: With a multi-page mocked result set, apply a category and a minimum-seeders filter, change sort, and load more; verify per-provider pagination advances independently, no duplicates are emitted, and unsupported filters are labeled as client-side.

**Acceptance Scenarios**:

1. **Given** a result set, **When** the user selects a category, **Then** providers that support that category are queried with the mapped category and results reflect it.
2. **Given** providers with more pages, **When** the user loads more, **Then** each provider advances its own pagination independently and previously emitted results are not repeated.
3. **Given** a filter a provider cannot apply upstream, **When** it is active, **Then** the UI distinguishes it from filters applied at the provider.
4. **Given** a chosen sort order, **When** results are displayed, **Then** ordering follows that sort and unknown metadata is not treated as zero.

---

### User Story 4 — Provider availability, history, and discovery settings (Priority: P2)

A user sees which providers are available or degraded, configures optional Torznab indexers and (where safely supported) an optional API credential, sets an adult-content preference (off by default), reviews/clears local search history, and clears the in-memory cache — all locally, with no account or synchronization.

**Why this priority**: Manageability and transparency are required for a responsible multi-provider feature, but a first release is usable with the two P0 providers at their defaults and no Torznab configuration.

**Independent Test**: With no Torznab configured, verify search works and shows no errors; enable a Torznab endpoint, verify a capability check runs; toggle adult content and verify it is excluded from ordinary searches until enabled; clear history and verify it is removed locally.

**Acceptance Scenarios**:

1. **Given** no Torznab indexer configured, **When** the user searches, **Then** the search succeeds using the built-in providers and no error is shown for the empty Torznab configuration.
2. **Given** a configured Torznab endpoint, **When** the user saves it, **Then** its capabilities are checked and its supported categories are recorded.
3. **Given** adult content is disabled (default), **When** the user runs an ordinary search, **Then** adult results are excluded and the adult category is not offered.
4. **Given** the user has search history, **When** they clear it, **Then** the history is removed from local storage and is not recoverable from any external service.
5. **Given** an optional API credential is supported, **When** the user enters it, **Then** it is never written to ordinary preferences or the database and is not present in logs or result objects.

---

### User Story 5 — Honest states, resilience, and accessibility (Priority: P2)

A user always sees an accurate state — initial, loading, results, empty, partial, provider-failed, rate-limited, offline, timeout, no-supported-providers, loading-more, refreshing, or cancelled — with an actionable message and a retry that respects provider rate limits. The screen is usable with TalkBack, large fonts, RTL, and adaptive layouts, in light and dark themes.

**Why this priority**: Honest failure communication and accessibility are production-quality requirements, layered on the core flow.

**Independent Test**: Simulate each provider state and verify a distinct, accurate message and that partial results are never replaced by a generic fatal error; enable TalkBack and large fonts and verify semantics and layout.

**Acceptance Scenarios**:

1. **Given** a provider is rate limited, **When** results are shown, **Then** a message names the provider and any available results from other providers remain visible.
2. **Given** all providers fail, **When** the search completes, **Then** a clear all-failed state with a rate-limit-respecting retry is shown, not a generic crash.
3. **Given** the device is offline, **When** the user submits, **Then** an offline state is shown and no request is attempted.
4. **Given** TalkBack and large fonts are enabled, **When** the user navigates Discover, **Then** controls have meaningful semantics and nothing is truncated or overlapping.

---

### Edge Cases

- A new query is submitted while a previous search is still running; the old search is cancelled/superseded and cannot overwrite the new results.
- The screen is rotated, backgrounded, or destroyed mid-search; the in-flight request is cancelled and no stale result overwrites a newer query.
- The app process is killed during a search; no persisted partial state masquerades as a fresh result.
- The network changes (Wi-Fi to cellular, offline, captive portal, VPN) during a search.
- One provider returns HTTP 429/401/403/500, malformed JSON/XML, an empty page, or an oversized body.
- BitSearch omits infohash, size, swarm data, or magnet URI; the detail endpoint fails.
- Internet Archive metadata has no `.torrent` file, multiple torrent files, an invalid/encoded filename, an HTML error page instead of a torrent, an unexpected redirect, or a corrupt/oversized torrent file.
- A Torznab endpoint is invalid, mis-authenticated, returns malformed RSS, omits size/seeds/magnet, or is later disabled/removed.
- A magnet has an invalid scheme, missing/invalid `xt`, duplicate `xt` values, bad percent-encoding, or invalid tracker parameters.
- Two distinct torrents share an identical title; the same infohash appears from two providers; a provider returns stale metadata.
- No providers are enabled, or the selected category has no supporting provider.
- Adult content is requested while the preference is disabled.
- A temporary `.torrent` file cannot be removed after use or failure.
- The existing queue is unavailable or the native engine fails to initialize when the user proceeds to add.

## Requirements *(mandatory)*

Requirements use stable identifiers. "MUST" is a hard requirement. Where an exact Kotlin type name, function signature, Gradle module path, route string, or dependency version is not yet fixed, the requirement states the required **outcome** and defers the concrete design to `/speckit-plan` and the relevant ADR; such items are marked *(design deferred to plan)* and MUST NOT be invented here.

### Functional Requirements

#### Discovery surface and navigation

- **FR-001**: PullDock MUST provide a Discover destination reachable from the app's main navigation that does not remove or redesign the existing Home, Library, Settings, onboarding, Add, download-detail, or player destinations.
- **FR-002**: The Discover surface MUST provide a single search input, an explicit submit action, category selection, provider availability/status, and local recent searches. Main search MUST execute on submit, not on every keystroke.
- **FR-003**: Search suggestions, if provided, MUST be grounded in real data (local history or provider categories) and MUST NOT be fabricated.
- **FR-004**: The Discover feature MUST NOT access Room, the native torrent engine, libtorrent4j, storage implementations, or provider HTTP clients directly from the presentation layer; it MUST depend only on discovery domain contracts.

#### Provider architecture and registry

- **FR-005**: The system MUST implement an extensible provider-adapter architecture in which each provider is an independent adapter behind a common domain contract, so that adding a provider does not require changing the Compose UI or the torrent engine.
- **FR-006**: The system MUST resolve the set of providers to query from a provider registry that reflects which providers are enabled and which support the requested category/search type.
- **FR-007**: Each result MUST always retain its source provider identity; anonymous or merged results that erase provider attribution MUST NOT be produced.
- **FR-008**: Provider adapters MUST be independently testable offline with mocked responses; normal unit tests MUST NOT depend on live torrent websites.
- **FR-009**: Discovery MUST be implemented in native Kotlin. The Chaquopy Python runtime MUST NOT be used for torrent search.

#### BitSearch provider (P0)

- **FR-010**: The system MUST integrate BitSearch through its documented REST API at base URL `https://bitsearch.eu/api/v1` (search `GET /search`, categories `GET /categories`, details `GET /torrent/{id}`), using only documented endpoints and parameters.
- **FR-011**: The BitSearch adapter MUST submit the user query with the selected category mapping and paging parameters, normalize the response, and surface seeders/leechers only when the response supplies them.
- **FR-012**: The BitSearch adapter MUST NOT call the detail endpoint for every search result; detail MUST be retrieved only when the user opens or selects a result.
- **FR-013**: The BitSearch adapter MUST prefer the provider's returned magnet URI from its detail endpoint, MUST validate it before handoff, and MUST NOT construct an unverified magnet URI from an incomplete response.
- **FR-014**: The BitSearch adapter MUST NOT fabricate seeders, size, creation date, or verification status; missing values MUST be represented as unknown.
- **FR-015**: Provider "verified" claims MUST be treated as a provider claim only and MUST NOT be represented as proof of copyright ownership, safety, authenticity, or legality.
- **FR-016**: BitSearch MUST be usable unauthenticated (documented ~200 requests/day per IP). The system MUST NOT embed a shared developer API key in the APK and MUST NOT commit credentials.
- **FR-017**: When an appropriate user-supplied BitSearch API key is configured, the adapter MAY send it via the documented `x-api-key` header. Credentials MUST NOT be logged, crash-reported, or placed in result models. Credential persistence is governed by FR-042.
- **FR-018**: The BitSearch adapter MUST NOT aggressively retry HTTP 401, 403, or 429 responses, and MUST read documented rate-limit response headers to inform retry/backoff and user messaging.
- **FR-019**: BitSearch category mapping MUST follow its documented taxonomy (Other=1, Movies=2, TV Series=3, Anime=4, Software=5, Games=6, Music=7, Audiobooks=8, Ebooks/Courses=9, Adult=10). Documentaries have no dedicated BitSearch category and MUST be served via supported movie/TV search with documentary-related terms; the system MUST NOT invent a documentary category ID.

#### Internet Archive provider (P0)

- **FR-020**: The system MUST integrate Internet Archive through its official Advanced Search API (`https://archive.org/advancedsearch.php`) and item metadata API (`https://archive.org/metadata/{identifier}`).
- **FR-021**: The Internet Archive search MUST restrict results to items with a usable Archive BitTorrent file (format restriction `format:"Archive BitTorrent"`) combined with the user's term using supported Advanced Search syntax; ordinary non-torrent items MUST NOT be shown as torrent results.
- **FR-022**: The Internet Archive search MUST request relevant metadata (at least identifier, title, creator, description, date, mediatype, subject, licenseurl, publicdate).
- **FR-023**: When a user opens an Archive result, the adapter MUST request official item metadata, inspect the `files` collection, and locate the actual `.torrent` file; it MUST NOT assume every item has a torrent and MUST NOT fabricate filenames or hardcode a single filename pattern without checking the metadata response.
- **FR-024**: When multiple relevant `.torrent` files exist for an Archive item, the system MUST present the available torrent files (with size and date where known) and require the user to choose one before Add; it MUST NOT silently auto-select. The chosen source MUST be explained to the user.
- **FR-025**: The Archive `.torrent` download URL MUST be constructed per Internet Archive's documented download conventions, validated, and restricted to the expected HTTPS origin; redirects MUST be handled safely and unexpected or insecure destinations rejected.
- **FR-026**: The `.torrent` file MUST be retrieved only after the user proceeds to add, downloaded to app-private temporary storage with a bounded size, and validated as an actual torrent file (not an HTML error page) before handoff.
- **FR-027**: The system MUST display available licence/attribution metadata and a rights status. Absence of a licence field means rights are **unknown**, not confirmed. The system MUST NOT describe all Archive material as public domain.
- **FR-028**: Internet Archive MUST be treated as a torrent-metadata source only; ordinary direct-download items MUST NOT be surfaced as torrent results.

#### Torznab-compatible indexers (P1)

- **FR-029**: The system MUST provide a reusable Torznab adapter for user-configured, compatible indexers using the standardized HTTP API (capabilities `GET /api?t=caps`, general search `GET /api?t=search&q=...`), parsing the capability document before using provider-specific category codes.
- **FR-030**: The Torznab adapter MUST support optional capabilities when available (movie, TV, audio/music, book, categories/subcategories, pagination) and MUST NOT assume every indexer supports every search type or that numeric category IDs are globally consistent.
- **FR-031**: The Torznab adapter MUST parse RSS/XML with Android's secure XML parsing facilities, extract supported fields (GUID, title, description, size, publication date, category, infohash, magnet URL, download URL, seeders, peers/leechers), handle missing optional fields gracefully, and safely handle namespaced XML fields.
- **FR-032**: The Torznab adapter MUST NOT crash on malformed XML, MUST bound response size, MUST NOT trust or auto-fetch arbitrary URLs from malformed responses, and MUST treat returned magnet/download URLs as untrusted.
- **FR-033**: The initial release MUST work with no Torznab configuration; an empty Torznab configuration MUST NOT cause errors. The system MUST NOT ship an unverified public indexer catalog.
- **FR-034**: Torznab endpoints MUST be validated and MUST use HTTPS; local-network plain-HTTP endpoints are out of scope for this feature (deferred) to preserve the repository's secure-transport boundary (ADR-0010). Torznab API keys MUST NOT be logged and MUST NOT be exposed through search-result objects.

#### Category system

- **FR-035**: The system MUST use an application-level category model independent of provider-specific IDs, with at least: All, Movies, TV Series, Documentaries, Anime, Books, Ebooks, Audiobooks, Music, Software, Games, Documents, Other, Adult. The Adult category MUST be available only after the adult-content opt-in (FR-064) and MUST be hidden otherwise.
- **FR-036**: Category mapping MUST be defined per provider (BitSearch per FR-019; Internet Archive via mediatype/subject/title/creator/format restricted to Archive BitTorrent; Torznab via the provider capability document). The system MUST NOT assume cross-provider numeric category equivalence.

#### Search orchestration and lifecycle

- **FR-037**: On a non-empty submitted query, the system MUST validate input, resolve the category, identify enabled providers supporting the search, query them concurrently, normalize results, de-duplicate, rank, and return combined results together with per-provider status.
- **FR-038**: Provider searches MUST run concurrently using structured concurrency and the repository's sanctioned dispatcher injection; the system MUST NOT use `GlobalScope` and MUST NOT introduce uncontrolled background requests.
- **FR-039**: One provider's failure MUST NOT fail the whole search; successful providers' results MUST still be shown and each provider's error MUST be recorded separately.
- **FR-040**: The system MUST provide an overall loading state and per-provider status, and SHOULD display results progressively as providers complete without blocking on a slow provider indefinitely.
- **FR-041**: Each provider request MUST have a bounded, configurable timeout (initial target ~8 seconds per provider) under a bounded overall search deadline; one slow endpoint MUST NOT freeze the feature.
- **FR-042**: Submitting a new query MUST cancel or supersede the previous search, prevent stale responses from overwriting newer results, and release in-flight resources; cancellation MUST propagate correctly. Optional provider API credentials MUST be held session-only in memory, MUST NOT be written to DataStore, Room, or any persistent store, and MUST be re-entered by the user after process death; no secure credential store is introduced by this feature.
- **FR-043**: Results MUST be de-duplicated by: (1) exact normalized infohash, then (2) exact provider-specific identifier, then (3) conservative title + exact-size match. Different infohashes MUST NOT be merged merely because titles match. When providers return the same infohash, the result MUST merge while preserving all contributing providers and source URLs and preferring an available validated download descriptor.
- **FR-044**: Default ranking MUST use, in order: exact title relevance, search-term relevance, category relevance, metadata completeness, seeders (when supplied), freshness (when supplied). Unknown metadata MUST NOT be treated as zero. A provider verification badge MUST NOT improve copyright or malware trust.
- **FR-045**: The system MUST support sorting by relevance, most seeders, newest, largest, and smallest.

#### Pagination and filters

- **FR-046**: The system MUST maintain independent per-provider pagination state; loading more MUST advance only providers with more data, MUST NOT repeat already-emitted results, MUST keep other providers' results intact, and MUST handle a provider reaching its last page independently.
- **FR-047**: The system MUST NOT invent an exact global total when provider totals are unknown or incomparable.
- **FR-048**: The system MUST support filters for category, provider selection, minimum seeders, minimum/maximum size (where metadata exists), and date range (where metadata exists), and MUST clearly distinguish filters actually applied upstream from those applied client-side.

#### Result model and honest metadata

- **FR-049**: The normalized result MUST represent at least: result id, provider id, provider name, title, description, category, original provider category, infohash, size, seeders, leechers, publication date, provider verification claim, source page URL, result source type, rights status, available actions, and contributing sources.
- **FR-050**: Result source type MUST be either magnet or torrent-file. Direct-download-only archive items MUST NOT be displayed as torrent results.
- **FR-051**: Rights status MUST be one of confirmed, unknown, restricted, or disputed, describing available rights metadata only — not a legal guarantee by PullDock.
- **FR-052**: All optional fields MUST be nullable and MUST NOT be invented: missing seeders = unknown (not zero); missing date = unknown (not today); missing licence = unknown rights; missing infohash = unavailable until details are fetched where supported.
- **FR-053**: Result detail MUST be fetched only for a result the user opens/selects, not for every result in a search page.

#### Integration with the existing torrent download flow

- **FR-054**: A selected magnet result (BitSearch or Torznab) MUST enter the existing Add Torrent flow by handing the validated magnet to the existing add entry point used for manual magnet input, which performs the existing validation, metadata fetch, file selection, destination confirmation, and explicit start. The discovery feature MUST NOT implement a second magnet-ingestion path and MUST NOT call libtorrent4j directly.
- **FR-055**: A selected Internet Archive result MUST enter the existing file-based Add flow by downloading the validated `.torrent` to app-private temporary storage and handing its reference to the same existing add entry point used for manually picked `.torrent` documents. The system MUST NOT create a second storage system.
- **FR-056**: Temporary `.torrent` files MUST be size-bounded, validated as real torrent files, and cleaned up safely after consumption or failure; cleanup failure MUST NOT corrupt other state.
- **FR-057**: After the user confirms and explicitly starts, the transfer MUST be managed by the existing unified queue with all existing operations (pause, resume, remove, recheck, file priority, transfer limits, progress, speed, ETA, recovery) and MUST NOT create a separate queue or task type.
- **FR-058**: Duplicate prevention MUST reuse the existing source-hash duplicate detection in the Add flow; a source already active or completed MUST be reported through the existing typed duplicate error without creating a second transfer. Discovery MUST NOT introduce a parallel duplicate mechanism.
- **FR-059**: Discovery MUST NOT change existing seeding defaults, network policy, consent, or legal acknowledgements; all established consent and upload settings remain authoritative and are enforced by the existing flow.
- **FR-060**: Searching MUST never start a transfer; a transfer exists only after the user explicitly proceeds through the existing Add flow and starts it.

#### Search history, caching, and preferences

- **FR-061**: Search history MUST be stored locally (via the existing preferences mechanism), MUST support repeat, delete-one, and clear-all, and MAY be disabled by the user. History MUST NOT be transmitted to any external backend.
- **FR-062**: Identical search requests MAY be served from a short-lived in-memory TTL cache (initial target 2–5 minutes, configurable) keyed by normalized query + category with provider-specific results kept separate; authentication/rate-limit failures MUST NOT be cached as valid results and stale results MUST NOT be presented as fresh.
- **FR-063**: The system MUST NOT persist complete search result sets in Room unless a concrete product requirement justifies it; any database schema change requires a tested migration. No new database server, Redis, or remote database is permitted.

#### Adult content handling

- **FR-064**: Adult content MUST be disabled by default. When the user enables it via a one-time explicit opt-in, a dedicated, clearly labeled "Adult" category MUST become available; when disabled, the Adult category MUST be hidden and not offered, and adult results MUST be excluded from ordinary searches. Results MUST be clearly identified as adult, and the user MUST be able to disable adult content at any time.
- **FR-065**: The system MUST NOT auto-load adult thumbnails or previews, MUST NOT treat a provider's adult classification as authoritative when its category metadata is unreliable, and MUST provide a way to hide/report problematic source results.
- **FR-066**: The system MUST NOT knowingly surface child sexual abuse material, non-consensual intimate imagery, sexual exploitation, or trafficking-related content.

#### Error handling and user-facing states

- **FR-067**: The system MUST implement and distinguish the states: initial, empty query, loading, results, empty results, partial results, one-provider-failed, all-providers-failed, provider-rate-limited, provider-disabled, offline, request-timeout, no-supported-providers-for-category, loading-more, refreshing, and cancelled.
- **FR-068**: When partial results exist, the system MUST NOT show a generic fatal error; it MUST show available results plus a per-provider status message (e.g., "BitSearch is temporarily rate limited. Showing available results from Internet Archive.").
- **FR-069**: Retry actions MUST respect provider rate limits and MUST NOT aggressively retry 401/403/429.
- **FR-070**: Provider, network, parse, timeout, and ingestion failures MUST map to typed errors with understandable, actionable user messages; internal diagnostics MUST be sanitized (no URLs with credentials, tokens, raw XML/HTML, or secrets).
- **FR-071**: The system MUST NOT bypass DRM, paywalls, authentication, private-account restrictions, or platform download restrictions, and MUST NOT implement CAPTCHA or anti-bot bypass.

#### Security and privacy (functional)

- **FR-072**: All provider results, magnet URIs, infohashes, URLs, filenames, and network responses MUST be treated as untrusted and validated (scheme allowlist, infohash format per the existing engine, content-type checks, bounded response size, HTML sanitization in descriptions, safe filename/path handling, path-traversal prevention).
- **FR-073**: Provider-supplied URLs MUST NOT be opened automatically; the system MUST NOT silently issue requests to arbitrary URLs from malformed provider responses; Torznab endpoint schemes MUST be restricted.
- **FR-074**: The system MUST NOT include analytics or telemetry for search queries, MUST NOT add advertising SDKs, MUST NOT require an account, and MUST explain that queries are sent to the selected external providers.

### Non-Functional Requirements

- **NFR-001**: The domain layer for discovery MUST remain pure Kotlin — no Android `Context`, Compose, Room, HTTP client, libtorrent4j, or provider-specific DTO types in domain contracts.
- **NFR-002**: Module boundaries MUST follow the existing Clean Architecture (presentation → domain ← data, engines isolated). New modules MUST be justified by a real boundary and recorded via ADR and the `AGENTS.md`/plan module map; premature micro-modules MUST be avoided. *(Exact module set and names deferred to plan.)*
- **NFR-003**: Dependency injection MUST follow existing Hilt conventions; dispatching MUST use the injected `AppDispatchers` only.
- **NFR-004**: Networking MUST reuse existing infrastructure where suitable; if a new HTTP client is required, exactly one client/serialization stack MUST be chosen (not both Ktor and Retrofit), all versions pinned in the version catalog, and compatibility with the current Kotlin/AGP/Gradle/minSdk/64-bit ABIs verified. Any new dependency requires license and maintenance review.
- **NFR-005**: The UI MUST use PullDock's existing warm-paper Material 3 design system, tokens, and shared `Pd*` components (e.g. search field, cards, filter chips, status badges, state/empty/loading/error components). Feature code MUST NOT hardcode colors, spacing, radii, or motion durations.
- **NFR-006**: The feature MUST support TalkBack, 48dp touch targets, large fonts, RTL, and adaptive layouts, in light and dark themes; every screen MUST handle loading, empty, success, partial, and error states and MUST NOT ship dead buttons or fake progress.
- **NFR-007**: Search MUST keep network and parsing work off the main thread, reuse connections, set request/response timeouts, bound response size, avoid unnecessary image loading and repeated detail requests, use a lazy list for results, and avoid expensive work in composables. The app MUST remain responsive on mid-range devices (target: smooth result scrolling without visible jank and no ANRs).
- **NFR-008**: Per-provider timeouts (~8s) and a bounded overall deadline MUST hold; provider latency MUST NOT freeze the screen.
- **NFR-009**: State that must survive configuration changes MUST be preserved (e.g. `rememberSaveable`/ViewModel state); in-flight requests MUST be cancelled on destruction; a process kill MUST NOT leave stale results presented as fresh.
- **NFR-010**: No scraping (no Playwright/Puppeteer/Selenium/BeautifulSoup/Scrapy/Jsoup/HtmlUnit/browser automation/HTML parsing), no private-endpoint reverse engineering, no CAPTCHA/anti-bot bypass, no new backend (no FastAPI/Node/Redis/Elasticsearch/PostgreSQL/MongoDB/remote DB), no cloud account, and no second torrent engine or queue.
- **NFR-011**: The feature MUST be independently testable without a live torrent swarm: provider integration via mocks/unit tests, UI via Compose tests, handoff via an existing Add-flow test/fake, and actual engine operation deferred to device verification.
- **NFR-012**: Existing behavior MUST NOT regress: discovery MUST NOT weaken existing tests, validation, consent, queue, storage, or recovery behavior.

### Key Entities

- **TorrentSearchResult**: A normalized, provider-attributed result. Attributes: result id, provider id/name, title, description, category, original provider category, infohash (nullable), size (nullable), seeders (nullable), leechers (nullable), publication date (nullable), provider verification claim, source page URL, result source type (magnet | torrent-file), rights status (confirmed | unknown | restricted | disputed), available actions, contributing sources. All optional fields nullable; nothing fabricated.
- **TorrentResultDetails**: Detail for one result, fetched on demand: full title, provider, description, category, size, infohash, seeders/leechers, publication date, source page, licence/rights, and the validated download descriptor (magnet or `.torrent` reference) plus available actions.
- **SearchCategory**: Application-level category (All, Movies, TV Series, Documentaries, Anime, Books, Ebooks, Audiobooks, Music, Software, Games, Documents, Other, Adult) with per-provider mappings.
- **SearchFilters**: Category, provider selection, minimum seeders, min/max size, date range, sort order, and an indicator of which filters were applied upstream vs client-side.
- **ProviderCapabilities**: What a provider supports (search types, categories/subcategories, pagination), parsed from the provider (e.g. Torznab caps) or fixed per built-in adapter.
- **ProviderStatus**: Per-provider state for a search (success with count, empty, failed with typed reason, rate-limited, disabled, timeout) and last capability-check result.
- **SearchPageState**: The overall search state plus per-provider results/status, supporting progressive display, load-more, refresh, and cancellation.
- **DiscoveryError**: Typed discovery error hierarchy (input, network/offline, provider, rate-limit, parse, timeout, disabled, no-supported-providers), user-mappable and sanitized; distinct from the persisted `DownloadError` used by the transfer pipeline.
- **ProviderConfiguration (Torznab)**: Display name, base API endpoint, enabled flag, last capability-check result, supported categories, optional API key (session-only in memory per FR-042). Endpoint credentials MUST NOT appear in result objects.
- **SearchHistoryEntry**: Local, user-deletable record of a past query (and optional category), used for repeat and suggestions.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A user can open Discover, search once, and see a combined, provider-attributed result list from at least two enabled providers within the bounded search deadline, with no manual retry caused by app defects.
- **SC-002**: When one provider fails, times out, or is rate limited, results from the remaining providers are still shown and the failure is reported per provider — in 100% of tested provider-failure combinations.
- **SC-003**: A selected magnet result opens the existing Add flow with the magnet pre-filled, and a selected Archive result opens the existing file-based Add flow on a validated `.torrent`, in 100% of tested handoffs; no second queue or engine is created.
- **SC-004**: After the user confirms and starts, the transfer appears in the existing unified queue and supports all existing controls, with no duplicate transfer for an already-active/completed source.
- **SC-005**: Missing metadata is shown as unknown (never zero/today/default) and rights status is never fabricated, in 100% of results that lack the field.
- **SC-006**: Duplicate infohashes from multiple providers appear once with all contributing providers preserved, and distinct torrents with identical titles remain distinct.
- **SC-007**: A new query during an active search cancels the old one and the old response never overwrites the new results.
- **SC-008**: All provider, network, parse, and ingestion failure cases map to distinct, accurate, actionable user states with no generic fatal error when partial results exist.
- **SC-009**: The Discover screen and result details are fully usable with TalkBack, large fonts, and RTL, in light and dark themes, with no truncated/overlapping controls.
- **SC-010**: No scraping, no new backend, no Redis/remote DB, no embedded developer API key, no logged credentials, and adult content off by default are all verifiably true.
- **SC-011**: Existing regression suites (torrent, download, add, navigation, queue, storage) pass unchanged, and new provider/orchestration/UI tests pass offline without live torrent sites.
- **SC-012**: With no Torznab configured, search succeeds using the built-in providers and shows no error for the empty configuration.

### Acceptance Criteria

The feature is accepted when all applicable criteria hold:

- Discovery: (1) accessible Discover screen; (2) single-input search; (3) BitSearch via documented REST; (4) Internet Archive via official search+metadata APIs; (5) only real Archive BitTorrent items shown; (6) concurrent provider search; (7) normalized results; (8) each result shows its source; (9) duplicate infohashes handled; (10) missing metadata represented honestly.
- Provider reliability: (11) one provider failing does not break others; (12) bounded timeouts; (13) rate limits handled; (14) invalid responses do not crash; (15) retryable search; (16) providers independently disableable.
- Torrent ingestion: (17) magnet result enters existing Add flow; (18) Archive `.torrent` enters existing file-based Add flow; (19) user reviews metadata; (20) user selects files via existing UI; (21) user confirms destination; (22) user explicitly starts; (23) transfer managed by existing unified queue; (24) no duplicate engine or queue.
- Architecture: (25) domain pure Kotlin; (26) feature UI does not access data/engine implementations; (27) adapters implement domain contracts; (28) Hilt wiring follows conventions; (29) existing navigation and design system preserved; (30) no unnecessary DB migration.
- Security/compliance: (31) no HTML scraping; (32) no scraping/automation dependencies; (33) no embedded developer key; (34) credentials not logged; (35) rights never fabricated; (36) adult off by default; (37) existing legal/consent intact.
- Infrastructure: (38) no FastAPI backend; (39) no Redis; (40) no external DB; (41) no new torrent engine; (42) existing build and regression tests pass.

## Testing Requirements

- **Provider unit tests (offline, mocked)** — BitSearch: valid search/categories/detail JSON; empty result; missing optional fields; malformed JSON; invalid infohash; invalid magnet; HTTP 429/401/403/500; rate-limit header parsing; pagination. Internet Archive: advanced-search response; Archive-BitTorrent-only filtering; valid identifier; metadata with one torrent; metadata with no torrent; multiple torrents requiring user choice; missing file metadata; missing licence; invalid download URL; encoded filenames; HTML/invalid response; torrent-file transfer failure. Torznab: capabilities; general RSS search; category parsing; supported/unsupported search types; XML namespaces; seeders/peers; magnet URL; missing optional attributes; malformed XML; oversized XML; auth failure; timeout.
- **Domain tests**: provider registry; category mapping; query validation; normalization; dedupe by infohash; different infohashes with same title; ranking; unknown metadata; provider-failure mapping; cancellation.
- **Search orchestration tests**: all succeed; one succeeds/one fails; one succeeds/one times out; all empty; all fail; a provider disabled; identical infohashes across providers; new search cancels old; load-more across providers; global timeout.
- **Integration tests**: registry injection; response normalization; repository binding; navigation into Discover; the discovery ViewModel; existing Add-flow handoff; temporary `.torrent` lifecycle; absence of any duplicate queue.
- **Compose UI tests**: Discover screen; search input/submit; category selection; provider selection; loading/empty/partial/error states; result cards; result details; Add-to-PullDock action; search history; adult default-off; accessibility semantics.
- **Regression**: existing `:domain:torrent`, `:domain:download`, `:data:torrent`, `:data:download`, the Add feature, navigation, queue behavior, Room migrations (if any), and storage handling MUST continue to pass; existing tests MUST NOT be weakened to accommodate discovery.
- **Device verification (tracked risk)**: actual torrent engine operation and the end-to-end search-to-download path remain subject to the repository's outstanding on-device verification; provider integration (mocks), UI (Compose), and handoff (Add-flow fake) are verified independently of a live swarm.

## Assumptions

- **Conflict with 001 scope (resolved)**: `001-production-android-app` lists "Built-in torrent search, indexing, discovery, or pirated-content catalogs" under Out of Scope (v1). Per the 2026-11-28 clarification, feature 002 proceeds as an additive, separately specified post-v1 capability; it does not modify `001`, and `001`'s out-of-scope list describes the v1 release boundary rather than a permanent prohibition.
- The two P0 providers (BitSearch, Internet Archive) ship enabled by default at their documented unauthenticated tiers; Torznab (P1) is included in this feature's first release (not deferred) as an optional, user-configured provider, and the feature is fully usable with no Torznab configuration.
- BitSearch is used unauthenticated by default and no shared developer API key is embedded. Per the 2026-11-28 clarification, optional user-supplied credentials (BitSearch/Torznab) are session-only in memory, are never persisted to DataStore/Room or any store, and must be re-entered after process death. Durable/secure credential storage is out of scope for this feature and would require a separate ADR and secure-storage mechanism.
- Torznab endpoints require HTTPS in this feature (resolved 2026-11-28). Deliberately configured local-network plain-HTTP endpoints are deferred to preserve the repository's secure-transport boundary (ADR-0010); this can be revisited via a future ADR if a concrete need arises.
- "Discover" placement (primary navigation item vs. a prominent Home entry point) is a plan-level UI decision; the requirement is one-tap reachability from main navigation without removing existing destinations.
- Exact discovery module names/paths, type names, function signatures, route strings, the chosen HTTP client/serialization stack, and dependency versions are decided at `/speckit-plan` and recorded via ADR and the module map; this spec intentionally does not invent them.
- Automated tests use owned, licensed, synthetic, or authorized fixtures and mocked provider responses; live provider sites and live swarms are not the sole test dependency.
- No user account, PullDock-operated backend, cloud upload, or search analytics is introduced.
- Rights metadata is displayed as provided; PullDock does not verify copyright, ownership, or legality, and provider "verified" claims are not represented as such.

### Unresolved items for research / `/speckit-clarify`

These external API details are uncertain and MUST be confirmed during research/planning rather than invented in this spec or in code:

- Exact BitSearch response field names/types and the precise rate-limit response headers and daily-quota behavior.
- Exact Internet Archive Advanced Search field semantics and the documented download-URL construction for Archive BitTorrent files, plus redirect behavior.
- Torznab per-indexer capability/category variance and the minimal required RSS structure across real indexers.
- Final Discover navigation placement (primary navigation destination vs. a prominent Home entry point) — a plan-level UI decision, not a spec ambiguity.