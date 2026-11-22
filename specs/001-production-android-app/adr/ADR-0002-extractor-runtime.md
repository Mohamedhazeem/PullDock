# ADR-0002: Extractor runtime — Chaquopy Python + pinned yt-dlp

**Status**: Accepted | **Date**: 2026-11-22

## Context

Media extraction must run on-device (local-first), support yt-dlp and yt-dlp-ejs, and be
verifiable. There is no maintained way to run yt-dlp on Android without bundling a runtime.

## Decision

Bundle an in-process CPython 3.13 runtime via a Chaquopy-based library with a pinned yt-dlp.
Ship arm64-v8a and x86_64. Update the extractor only through signed application releases.

## Alternatives

- **Remote extraction service**: rejected — breaks local-first and privacy.
- **youtubedl-android**: unmaintained; no in-app update path.
- **Bundle a generic Linux Python binary**: rejected — Android is not glibc; feasibility risk.
- **Defer extraction**: rejected — extraction is a core product capability.

## Trade-offs

- ~60-80 MB AAR and 64-bit-only coverage.
- yt-dlp is pinned at library build time; no in-app update.
- Free tier lacks TLS fingerprint impersonation; protected sites may need cookies or a paid
  variant (a product/cost decision, recorded in research.md as D4).

## Consequences

- `:engines:extractor-ytdlp` owns runtime bootstrap, validation, and teardown.
- `:data:extractor` owns provider registry, compatibility, and error mapping.
- Extractor health is explicit (ADR-0009); the app never claims success without a descriptor.
- JS runtime and licenses must be verified before pinning.
