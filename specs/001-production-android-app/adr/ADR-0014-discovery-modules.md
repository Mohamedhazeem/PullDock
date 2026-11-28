# ADR-0014: Discovery modules and provider-adapter architecture

**Status**: Accepted | **Date**: 2026-11-28

## Context

Feature 002 adds torrent discovery (BitSearch, Internet Archive, user-configured Torznab)
without duplicating the existing torrent ingestion, engine, storage, or queue. The repository
uses a multi-module Clean Architecture (ADR-0008) with `:domain:*` pure Kotlin, `:data:*`
implementations, `:feature:*` presentation, and `:engines:*` native runtimes.

## Decision

Add three modules mirroring the existing rings:

- `:domain:discovery` — pure Kotlin: `TorrentSearchRepository`, `TorrentSearchProvider`,
  `ProviderRegistry`, models, and a typed `DiscoveryError` hierarchy. No Android/Room/HTTP/
  engine types.
- `:data:discovery` — `DiscoveryRepositoryImpl`, a `ProviderRegistry`, independent provider
  adapters (`BitSearchProvider`, `InternetArchiveProvider`, `TorznabProvider`), DTOs/mappers,
  in-memory TTL cache, and DataStore-backed history/Torznab config. The only layer allowed to
  perform network I/O.
- `:feature:discovery` — Compose Discover/results/details/filters/history/settings, depending
  only on `:domain:discovery` and `:core:*`.

Discovery hands a selected source into the existing Add flow via the existing
`add?source={source}` route (magnet or `file://` temp `.torrent`); it never calls the engine,
Room, or storage directly and adds no second queue.

## Alternatives

- Fold discovery into `:domain:download`/`:feature:home` — couples discovery to the transfer
  domain and bloats Home; rejected.
- A single `:feature:discovery` containing data — violates presentation→domain←data; rejected.

## Consequences

- Adding a provider = new adapter + registry registration; no UI/engine change.
- Provider failure is isolated per adapter; one failure never fails the whole search.
- `AGENTS.md` §2 and `settings.gradle.kts` updated; `:feature:queue` remains unused.