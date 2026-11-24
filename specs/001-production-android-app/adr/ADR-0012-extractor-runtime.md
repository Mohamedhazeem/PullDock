# ADR-0012: Bundled extractor runtime (T080)

**Status**: Accepted (implementation authorized by project owner 2026-11-25) | **Date**: 2026-11-25

## Context

All five providers were recognized but `resolve()`/`formats()`/`download()` were stubs
returning `RUNTIME_INCOMPATIBLE`. ADR-0002 requires an in-process CPython runtime with
yt-dlp pinned at build time; `AGENTS.md` §17 forbids downloading or executing extractor
code from a remote server at runtime.

Verified facts (2026-11-25):
- Chaquopy Gradle plugin 17.2.0 supports AGP 7.3–9.5 (project: AGP 9.4.1) and minSdk ≥ 24
  (project: 26). ABI filters already restrict packaging to arm64-v8a + x86_64.
- Build machine has Python 3.12.3. Chaquopy Python 3.12 is 64-bit-only, matching the
  project's ABI policy. Python 3.13 (ADR-0002) is not available on this build machine.
- yt-dlp latest on PyPI: 2026.11.23 (Unlicense). The catalog placeholder 2026.11.18 does
  not exist on PyPI and is replaced by the verified version.
- yt-dlp-ejs 0.8.0 is not pinned yet: its redistribution license could not be confirmed,
  so it is deliberately excluded until reviewed.

## Decision

1. Apply Chaquopy 17.2.0 to the `:app` module only; Python 3.12; pin `yt-dlp==2026.11.23`
   through the Chaquopy `pip` block so the package is baked into signed releases at build
   time. No runtime download/import of remote executable code.
2. Python bridge lives in `app/src/main/python/pulldock_extractor.py` and exposes typed
   JSON to Kotlin; no Python types cross into `:domain:*` or UI layers.
3. Kotlin runtime adapter implements a domain-facing interface; `:data:extractor` uses it
   for resolve, formats and download. Provider failures remain typed and isolated.
4. Formats requiring post-processing (video-only + separate audio) are excluded in v1
   because no licensed FFmpeg binary is bundled yet; progressive formats are downloaded
   and verified. This is an explicit, documented limitation — not a silent fallback.

## Alternatives

- **Remote yt-dlp download at runtime**: rejected — violates `AGENTS.md` §17 and FR-058.
- **Pure-Kotlin scrapers**: rejected — brittle, provider-specific, and would change
  ADR-0002 without an architecture decision.
- **Bundling FFmpeg now**: deferred — license/ABI/size review required (ADR-0003).

## Integrity and distribution

- Versions are pinned in `gradle/libs.versions.toml` and in the Chaquopy `pip` block.
- Dependency verification metadata is not configured in this repository yet; integrity
  currently relies on Maven Central/PyPI TLS plus pinned coordinates. This is recorded as
  a known gap, not claimed as verified signing.
- No credentials, cookies or private URLs are persisted (data-model.md §4.7).

## Consequences

- APK size grows by the CPython runtime plus yt-dlp (~20–40 MB per ABI).
- The build machine must have matching Python 3.12 for Chaquopy's pip step.
- Some sites may still fail due to anti-bot challenges; failures surface as typed errors.
- Worker/process isolation and background execution remain governed by ADR-0005.
- 2026-11-25 follow-up: `ProviderId.DIRECT` handles other authorized http(s) URLs
  (audio, images, plain files) through the same pinned runtime; the player has a music
  mode for audio files. FFmpeg merging is still deferred.
