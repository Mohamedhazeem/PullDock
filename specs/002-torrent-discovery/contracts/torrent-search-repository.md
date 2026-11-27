# Contract: TorrentSearchRepository

Pure-Kotlin contract in `:domain:discovery`. Implemented by `DiscoveryRepositoryImpl` in
`:data:discovery`. All functions are `suspend`, run off the main thread via injected
`AppDispatchers`, and are cancellable.

```kotlin
interface TorrentSearchRepository {
    /** Executes a new search across enabled providers concurrently; cancels any prior search. */
    suspend fun search(query: TorrentSearchQuery): SearchPageState

    /** Loads the next page for providers that have more data; preserves existing results. */
    suspend fun loadMore(query: TorrentSearchQuery): SearchPageState

    /** Re-runs the current query (refresh); stale results are not presented as fresh. */
    suspend fun refresh(query: TorrentSearchQuery): SearchPageState

    /** Fetches details for one result on demand (never for a whole page). */
    suspend fun getDetails(result: TorrentSearchResult): TorrentResultDetails

    /** Returns a validated download descriptor for a result, or null when none is usable. */
    suspend fun resolveDownloadDescriptor(details: TorrentResultDetails): DownloadDescriptor?
}

data class TorrentSearchQuery(
    val text: String,
    val filters: SearchFilters,
)
```

## Behavior guarantees

- **Concurrency & isolation**: providers are queried concurrently; one provider's failure is
  captured in its `ProviderStatus` and does not fail others (FR-037..FR-040).
- **Timeouts**: per-provider ~8s under a bounded overall deadline (FR-041).
- **Cancellation**: a new `search`/`loadMore` supersedes in-flight work; stale results never
  overwrite newer ones (FR-042).
- **Dedup & rank**: results are de-duplicated and ranked per FR-043..FR-045 before return.
- **Detail on demand**: `getDetails` is called only for an opened/selected result (FR-053).
- **No fabrication**: absent metadata stays null (FR-052).
- **Errors**: failures surface as typed `DiscoveryError` inside `ProviderStatus`/`SearchPageState`,
  never as crashes (FR-067..FR-070).

## Provider configuration (data-layer, not domain)

The repository reads enabled providers and Torznab config from `:data:discovery`
(`ProviderRegistry`, `TorznabConfigStore`); the domain contract above is configuration-agnostic.