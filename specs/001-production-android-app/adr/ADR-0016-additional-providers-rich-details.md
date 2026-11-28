# ADR-0016: Additional Discovery Providers and Rich Details

**Status**: Accepted | **Date**: 2026-11-28

## Context
Feature 002 shipped BitSearch, Internet Archive, and Torznab. Research (2026) identified
documented/public APIs that fit PullDock's no-scraping constraint, and result details dropped
provider files/images.

## Decision
- Add provider adapters (data layer, behind `TorrentSearchProvider`):
  - **YourBittorrent** (JSON `api/search.json`), **EZTV** (JSON `api/get-torrents`, TV; recent
    listing filtered client-side), **AniBT** (built-in Torznab config, always appended).
  - **YTS** (official JSON `movies-api.accel.li/api/v2/list_movies.json`, movies; magnet from hash).
  - **Knaben** (official JSON `api.knaben.org/v2/search`, multi; ~1 req/2s throttle; adult excluded).
  - **The Pirate Bay via apibay** (JSON `q.php`/`t.php`/`f.php`) — **default-off**, enabled via
  `ProviderToggleRepository` (DataStore), clearly labeled unofficial.
- Rejected: **TorrentFunk** (no documented no-key JSON API; HTML only), **1337x/RuTracker**
  (scraping/login-gated), Python packages (runtime/license burden). 1337x/TGX exposed only via
  user-configured Jackett/Prowlarr Torznab.
- Rich details: `TorrentResultDetails` gains `files: List<TorrentFile>` and `images: List<String>`;
  mapped from BitSearch `files`, Archive metadata `files`, apibay `descr`+`f.php`+images, EZTV
  screenshots. Details UI renders description, file list, and images via **Coil** (Apache-2.0),
  https-host-allowlisted and adult-gated; images are details-only.
- Excluded: 1337x, RuTracker (scraping/login-gated) — forbidden by constitution/spec.

## Alternatives
- Scrapers (py1337x, rutracker-api) — rejected (no-scraping).
- No image loader — rejected (user requires images); Coil chosen (Apache-2.0, pinned).

## Consequences
- More providers per search (parallel, bounded); TPB off by default keeps the common path lean.
- New dependency: Coil (Apache-2.0) — added to `docs/THIRD_PARTY_LICENSES.md`.
- No DB/schema change; no second engine/queue; handoff unchanged.