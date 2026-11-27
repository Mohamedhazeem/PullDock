# ADR-0013: Extractor fallback layer (sniffer + HLS + FFmpegKit remux)

**Status**: Accepted (implementation authorized by project owner 2026-11-27) | **Date**: 2026-11-27

## Context

ADR-0012 wired yt-dlp (pinned, in-process via Chaquopy) as the extractor runtime with
`ProviderId.DIRECT` as a catch-all for arbitrary authorized http(s) URLs. In practice yt-dlp
only succeeds where it has a working site extractor; for many tube/redirect sites its generic
extractor fails and, without a guard, saved the page HTML (~110 B) as a "completed" video.

A junk guard (size floor + HTML sniff) was added first so a webpage is never published as
media. This ADR adds the actual fallback that recovers real media for sites yt-dlp cannot
handle, within the existing Chaquopy architecture and without remote code execution.

Verified facts (2026-11-27):
- `curl_cffi` impersonation is not viable here: PullDock is Python 3.12 and the only Android
  wheels need `cffi>=2.0` (Chaquopy repo tops out at `cffi 1.17.1`). Not adopted.
- `hlsfetch==0.3.0` (pure Python) resolves in Chaquopy: `httpx`/`httpcore`/`h11`/`certifi`
  from PyPI (pure-Python wheels) + `cryptography 42.0.8 cp312 arm64` from the Chaquopy repo.
  Confirmed by a successful `:app:assembleDebug` (dependency gate).
- `dev.ffmpegkit-maintained:ffmpeg-kit-full:8.1.7` (LGPL-3.0, arm64-v8a + x86_64, SDK35 /
  16 KB pages) is a drop-in for the retired `com.arthenica:ffmpeg-kit-*` (same package/API).

## Decision

1. **Generic sniffer** in `app/src/main/python/pulldock_extractor.py` (stdlib `urllib.request`):
   when `probe()` throws or yields no usable formats, fetch the page HTML (browser UA, 5 MB cap,
   20 s timeout) and extract the best direct `.mp4` (highest `_NNNp`) or `.m3u8`.
2. **Synthetic format ids** carry the real media URL: `sniff-mp4:<url>` and `sniff-hls:<url>`.
   `download()` routes these prefixes to `_download_direct` (streamed, junk-guarded) or
   `_download_hls` (`hlsfetch.download(..., remux=False)` → raw `.ts`).
3. **Kotlin remux**: `ChaquopyExtractorRuntime.download()` detects `"raw": true` and remuxes the
   raw HLS file to `.mp4` via FFmpegKit (`-c copy -movflags +faststart`), deleting the raw file;
   failure returns a typed error and never publishes the raw/partial file.
4. **SSRF guard**: loopback/private/link-local hosts are rejected before any request.
5. `ProviderId.DIRECT` and the `MediaFormat`/`MediaDescriptor` shapes are unchanged.

## Deviation from ADR-0003

ADR-0003 requires FFmpeg to be invoked only through `:engines:media-ffmpeg`. This ADR consumes
FFmpegKit directly in `:app` (`ChaquopyExtractorRuntime.remuxToMp4`) because the remux must be
adjacent to the extractor runtime that produces the raw HLS file. This is a scoped exception for
the extractor fallback path only; it does not change the torrent/queue FFmpeg story. Revisit if
`:engines:media-ffmpeg` gains a shared remux API.

## Alternatives

- **curl_cffi impersonation**: rejected — wheel gap on Python 3.12 (see Context).
- **Playwright/Selenium/media-dlp**: rejected — cannot run under Chaquopy/Android.
- **yt-dlp-ge plugin**: rejected — needs a JS runtime (deno/node/bun/quickjs) absent on Android.
- **Pure-Python AES substitute for cryptography**: rejected — vendoring the official wheel keeps
  parity; the `--find-links` fallback covers `cryptography` + `cffi`.
- **Cloud extractors (Apify)**: rejected — remote code/data exfiltration (AGENTS.md §17).

## Consequences

- APK/AAB grows (~20 MB per ABI for ffmpeg-kit-full) plus the hlsfetch/cryptography wheels.
- LGPL-3.0 notice for FFmpegKit must ship; dependency assessment updated.
- Sites that embed direct media URLs (or HLS manifests) now download; JS-rendered or DRM/anti-bot
  sites still fail with typed errors (`SNIFF_FAILED` / `HLS_FAILED`).
- Cancellation of the blocking Python download is not preemptive (existing yt-dlp limitation).