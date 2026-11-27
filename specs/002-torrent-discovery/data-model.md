# Phase 1 Data Model: Torrent Discovery and Multi-Provider Search

**Feature**: `002-torrent-discovery` | **Date**: 2026-11-28 | **Plan**: [plan.md](./plan.md)

Pure-domain entities for `:domain:discovery` (no Android/Room/HTTP/engine types). Field
names below are the intended domain shape; exact Kotlin declarations are produced at
implementation. All optional fields are nullable; nothing is fabricated (spec FR-049..FR-053).

## 1. TorrentSearchResult

A normalized, provider-attributed result.

| Field | Type | Notes / validation |
|---|---|---|
| resultId | String | Stable id; provider id + provider-native id (or infohash) |
| providerId | String | Always present (FR-007) |
| providerName | String | Display name |
| title | String | Non-blank; sanitized for display |
| description | String? | HTML-stripped/plain; nullable |
| category | SearchCategory | Application-level category |
| originalProviderCategory | String? | Provider-native category label/id |
| infoHash | String? | Normalized lowercase btih (40-hex/32-base32) when known |
| sizeBytes | Long? | Unknown when absent (never 0 as "unknown") |
| seeders | Int? | Unknown when absent (never 0 as "unknown") |
| leechers | Int? | Unknown when absent |
| publishedAt | Instant? | Unknown when absent (never "now") |
| providerVerificationClaim | Boolean? | Provider claim only; not a trust/legality signal |
| sourcePageUrl | String? | Validated https URL |
| resultSourceType | ResultSourceType | MAGNET or TORRENT_FILE |
| rightsStatus | RightsStatus | CONFIRMED / UNKNOWN / RESTRICTED / DISPUTED |
| availableActions | Set<ResultAction> | e.g. ADD, OPEN_SOURCE, COPY_INFOHASH, SHARE |
| contributingSources | List<ContributingSource> | ≥1; provider id + name + source URL |

## 2. ContributingSource

One provider's contribution to a (possibly merged) result.

| Field | Type | Notes |
|---|---|---|
| providerId | String | |
| providerName | String | |
| sourcePageUrl | String? | Validated https |
| nativeId | String? | Provider-native identifier |

Invariant: a merged result's `contributingSources` preserves every provider (FR-043).

## 3. TorrentResultDetails

Fetched on demand for one result (FR-053).

| Field | Type | Notes |
|---|---|---|
| resultId | String | |
| providerId / providerName | String | |
| title | String | |
| description | String? | Sanitized |
| category | SearchCategory | |
| infoHash | String? | |
| sizeBytes / seeders / leechers | Long? / Int? / Int? | Unknown when absent |
| publishedAt | Instant? | |
| sourcePageUrl | String? | |
| rightsStatus | RightsStatus | |
| licence | String? | e.g. licenseurl; null = unknown |
| downloadDescriptor | DownloadDescriptor? | Null when no usable torrent (no Add action) |
| availableActions | Set<ResultAction> | |

## 4. DownloadDescriptor (validated, for handoff)

| Field | Type | Notes |
|---|---|---|
| kind | DescriptorKind | MAGNET or TORRENT_FILE |
| magnetUri | String? | Required when MAGNET; validated (btih) before handoff |
| torrentFileRef | String? | `file://` uri from a validated temp file when TORRENT_FILE |
| requiresUserChoice | Boolean | True when multiple Archive torrents (FR-024) |

## 5. SearchCategory (enum)

ALL, MOVIES, TV_SERIES, DOCUMENTARIES, ANIME, BOOKS, EBOOKS, AUDIOBOOKS, MUSIC, SOFTWARE,
GAMES, DOCUMENTS, OTHER, ADULT. ADULT is available only after opt-in (FR-035/FR-064).

## 6. SearchFilters

| Field | Type | Notes |
|---|---|---|
| category | SearchCategory | |
| providerIds | Set<String> | Enabled subset |
| minSeeders | Int? | Client- or provider-applied |
| minSizeBytes / maxSizeBytes | Long? | Where metadata exists |
| dateFrom / dateTo | Instant? | Where metadata exists |
| sort | SortOrder | RELEVANCE / SEEDERS / NEWEST / LARGEST / SMALLEST |
| appliedUpstream | Set<String> | Which filters the provider actually applied (FR-048) |

## 7. ProviderCapabilities

| Field | Type | Notes |
|---|---|---|
| providerId | String | |
| searchTypes | Set<SearchType> | MOVIE/TV/MUSIC/BOOK/GENERAL |
| categories | Map<SearchCategory, String> | App category → provider-native id |
| supportsPagination | Boolean | |
| requiresAuth | Boolean | |

## 8. ProviderStatus

| Field | Type | Notes |
|---|---|---|
| providerId / providerName | String | |
| state | ProviderState | SUCCESS / EMPTY / FAILED / RATE_LIMITED / DISABLED / TIMEOUT |
| resultCount | Int? | When SUCCESS |
| error | DiscoveryError? | When FAILED/RATE_LIMITED/TIMEOUT |
| lastCapabilityCheck | Instant? | For Torznab |

## 9. SearchPageState

| Field | Type | Notes |
|---|---|---|
| query | String | |
| filters | SearchFilters | |
| overall | OverallSearchState | IDLE/LOADING/RESULTS/PARTIAL/EMPTY/ALL_FAILED/OFFLINE/LOADING_MORE/REFRESHING/CANCELLED |
| perProvider | List<ProviderStatus> | |
| results | List<TorrentSearchResult> | Combined, deduped, ranked |
| hasMoreByProvider | Map<String, Boolean> | Independent pagination (FR-046) |

## 10. DiscoveryError (sealed)

Categories (each with sanitized `detail`): `Input`, `Network` (incl. `Offline`), `Provider`,
`RateLimited`, `Parse`, `Timeout`, `Disabled`, `NoSupportedProviders`. Distinct from the
persisted `DownloadError` (NFR-001, FR-070). Each maps to a user message.

## 11. ProviderConfiguration (Torznab)

| Field | Type | Notes |
|---|---|---|
| providerId | String | User-assigned stable id |
| displayName | String | |
| endpoint | String | Validated **https** URL (FR-034) |
| enabled | Boolean | |
| lastCapabilityCheck | Instant? | |
| supportedCategories | Map<SearchCategory, String> | From caps |
| apiKey | String? (memory only) | **Never persisted**; session-only (FR-042) |

## 12. SearchHistoryEntry

| Field | Type | Notes |
|---|---|---|
| query | String | |
| category | SearchCategory? | |
| usedAt | Instant | |

Local only (DataStore); user-deletable; may be disabled (FR-061).

## Validation rules (summary)

- Magnet: must be `magnet:` with `xt=urn:btih:` (40-hex or 32-base32); else typed
  `DiscoveryError.Input` (mirrors `MagnetValidator`).
- URLs: https only for provider endpoints and Archive `.torrent` download; reject other
  schemes/hosts (SSRF guard).
- Infohash: normalized lowercase; unknown stays null.
- Response size: bounded; content-type checked; HTML never accepted as a torrent.
- Titles/descriptions: sanitized (HTML stripped) before display/storage.
- Archive: only items with `format:"Archive BitTorrent"`; `.torrent` located from metadata
  `files` (never fabricated); multiple → user choice.

## State transitions (search lifecycle)

```text
IDLE ──submit(non-empty)──▶ LOADING ──all done, ≥1 result──▶ RESULTS
  │                            │
  │                            ├──all done, 0 results────────▶ EMPTY
  │                            ├──all failed──────────────────▶ ALL_FAILED
  │                            ├──offline─────────────────────▶ OFFLINE
  │                            └──superseded by new query──────▶ CANCELLED (old job)
RESULTS/PARTIAL ──loadMore──▶ LOADING_MORE ──▶ RESULTS (per-provider advance)
RESULTS ──refresh──▶ REFRESHING ──▶ RESULTS
RESULTS ──new submit──▶ LOADING (old results cleared; old job cancelled)
```

Invariants: a cancelled/superseded search never writes results; unknown metadata is never
coerced to zero/now; a merged result keeps all contributing sources; no transfer exists until
the user completes the existing Add flow and starts it.