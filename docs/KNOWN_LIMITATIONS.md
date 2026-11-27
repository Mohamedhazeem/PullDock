# Known Limitations (T179, FR-035/FR-075/FR-076)

Honest, user-visible constraints. Nothing here is hidden in the UI.

## Background execution

- Android 15 (API 35) gives `dataSync` foreground services a **6-hour budget per 24
  hours**; on exhaustion the service stops promptly and tasks recover via the normal
  resume path. Bringing the app to the foreground resets the budget.
- API 34+ long transfers use user-initiated data-transfer jobs (quota-exempt).
- PullDock never auto-starts a restricted service after boot: boot recovery only
  marks interrupted tasks; transfers resume on user action.
- OEM battery optimizations (Doze, Battery Saver, vendor killers) can interrupt
  transfers; recovery is automatic from persisted state and engine resume data.

## Media extraction

- The extractor runtime is **pinned per app release** — no in-app executable updates
  (FR-058). Provider fixes arrive with app updates.
- The free runtime tier has **no TLS fingerprint impersonation**: bot-protected or
  age-gated sources may require user-supplied cookies; PullDock does not bypass
  DRM, paywalls, authentication, or private accounts (FR-020).
- Provider compatibility is recorded per provider with dated verification results;
  a failing provider degrades alone (FR-062).
- **T080 bundled runtime**: implemented 2026-11-25 (Chaquopy 17.2.0 / Python 3.12 /
  yt-dlp 2026.11.23, ADR-0012). On-device provider verification is in progress;
  direct media, HLS, and social platforms (YouTube Shorts, X/Twitter, Instagram,
  Facebook) have been verified on-device, while anti-bot failures remain possible per
  provider.
- Merge-required video+audio formats are downloaded as separate streams and merged
  on-device with FFmpegKit (VP9+AAC in MP4, VP8/VP9+Opus/Vorbis in WebM).

## Devices

- **64-bit only** (arm64-v8a, x86_64); 32-bit devices are not supported (FR-076). CI
  verifies no 32-bit ABI is packaged.

## Distribution

- v1 is private/sideloaded. Store eligibility is unresolved pending a policy
  assessment; no store claims are made (FR-075).

## Open device verification

- Torrent on-device PoC (T075/T060): engine compiles against libtorrent4j 2.1.0-39;
  runtime init/transfer/resume verification needs a 64-bit device or emulator.
- Instrumentation suites (connected tests) and migration-from-fixture runs need a device.
- Library/player UI (T156–T158, T165) is implemented as of 2026-11-24; the remaining
  Slice 6 gaps are the streaming-bridge lifecycle owner (T160), thumbnails (T164), and
  device verification (T163). Torrent streaming is still unverified on a device.
