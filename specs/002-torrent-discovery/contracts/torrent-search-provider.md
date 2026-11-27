# Contract: TorrentSearchProvider

Pure-Kotlin contract in `:domain:discovery`. Each provider adapter
(`BitSearchProvider`, `InternetArchiveProvider`, `TorznabProvider`) implements it
independently in `:data:discovery`. Adapters own provider DTOs, auth, pagination, and
mapping; no provider types leak into the domain (FR-005, NFR-001).

```kotlin
interface TorrentSearchProvider {
    val id: String
    val displayName: String

    /** Categories/search types this provider supports (from caps or fixed per adapter). */
    suspend fun capabilities(): ProviderCapabilities

    /** One page of normalized results for the query; bounded and cancellable. */
    suspend fun search(query: TorrentSearchQuery, page: ProviderPage): ProviderPageResult

    /** Details for one native result id; null when unavailable. */
    suspend fun details(nativeId: String): TorrentResultDetails?

    /** Optional validated download descriptor (magnet or temp .torrent file ref). */
    suspend fun downloadDescriptor(nativeId: String): DownloadDescriptor?
}

data class ProviderPage(val number: Int, val cursor: String?)

data class ProviderPageResult(
    val results: List<TorrentSearchResult>,
    val hasMore: Boolean,
    val nextPage: ProviderPage?,
)
```

## Per-adapter obligations

- **BitSearchProvider**: documented REST only; `GET /search`, `/categories`, `/torrent/{id}`;
  detail fetched only on demand; magnet taken from detail and validated; optional
  `x-api-key` (session-only); no aggressive 401/403/429 retry; reads rate-limit headers
  (FR-010..FR-019).
- **InternetArchiveProvider**: Advanced Search restricted to `format:"Archive BitTorrent"`;
  metadata `files` inspected for the real `.torrent`; multiple → `requiresUserChoice`;
  download URL built per documented conventions, https, redirects validated; licence/rights
  surfaced; absent licence = UNKNOWN (FR-020..FR-028).
- **TorznabProvider**: `t=caps` parsed before use; `t=search` with mapped categories;
  `XmlPullParser` with bounded size; missing optional fields handled; malformed XML never
  crashes; https endpoints only (FR-029..FR-034).

## Registry

```kotlin
interface ProviderRegistry {
    fun enabled(): List<TorrentSearchProvider>
    fun supporting(category: SearchCategory): List<TorrentSearchProvider>
}
```

Adding a provider = new adapter + registry registration; no UI/engine change (FR-005).