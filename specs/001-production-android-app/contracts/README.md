# Contracts Index

Design contracts for PullDock. These are interface/protocol definitions, not implementations.

| Contract | File | Layer |
|---|---|---|
| Torrent engine | [torrent-engine.md](torrent-engine.md) | `:domain:torrent` |
| Media extractor + provider | [media-extractor.md](media-extractor.md) | `:domain:extractor` |
| Repositories and queue | [repositories.md](repositories.md) | `:domain:download` |
| Streaming bridge | [streaming-bridge.md](streaming-bridge.md) | `:domain:torrent` / `:feature:player` |
| Update manifest | [update-manifest.schema.json](update-manifest.schema.json) | `:data:extractor` |

Rules:

- Contracts expose asynchronous operations, typed results, cancellation, and observable events.
- Contracts MUST NOT expose JNI, libtorrent, yt-dlp, FFmpeg, process, or native data types.
- Adapters translate native/engine types into these contracts at the boundary.
