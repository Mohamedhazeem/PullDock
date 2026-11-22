# ADR-0001: Torrent engine — libtorrent4j

**Status**: Accepted | **Date**: 2026-11-22

## Context

PullDock needs a maintained BitTorrent engine on Android with magnet and `.torrent` support,
file selection, sequential download, resume data, piece verification, alerts, and streaming.

## Decision

Use `libtorrent4j` behind a `TorrentEngine` contract in `:domain:torrent`, implemented in
`:data:torrent` over `:engines:torrent-libtorrent`. Pin the version in the catalog after a
proof of concept.

## Alternatives

- **Libretorrent (app)**: a product, not an embeddable engine.
- **Writing a custom libtorrent JNI layer**: high cost, no advantage.
- **JavaTorrent / other JVM torrent libs**: weaker Android support and streaming control.

## Trade-offs

- Native library adds APK size and ABI packaging complexity.
- Newer libtorrent4j releases may raise the minimum Android version (Libretorrent downgraded
  to keep Android 8.1 support); minSdk 26 + 64-bit must be verified in the PoC.
- JNI lifecycle must be explicit and idempotent.

## Consequences

- `:engines:torrent-libtorrent` owns the session, alert loop, and cleanup.
- Resume data is stored as an opaque versioned blob.
- Streaming is supported via sequential download and a loopback bridge (ADR-0007).
- License and redistribution terms must be documented before pinning.
