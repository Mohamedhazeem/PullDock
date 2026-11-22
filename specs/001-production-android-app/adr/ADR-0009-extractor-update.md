# ADR-0009: Extractor update and compatibility architecture

**Status**: Accepted | **Date**: 2026-11-22

## Context

Extractor components rot independently; providers change without notice. The app must detect
incompatibility, communicate it honestly, and never execute remote code.

## Decision

Separate update metadata from executable components:

- `ExtractorRuntimeInfo` records the pinned component inventory and validates the combination
  at startup and before extraction.
- `ProviderCompatibilityRegistry` tracks per-provider health.
- A remote signed metadata manifest (schema in
  [contracts/update-manifest.schema.json](../contracts/update-manifest.schema.json)) is checked
  non-blockingly, verified with an app-pinned key, expiration/rollback protected, and cached
  last-known-good.
- Executable extractor changes ship only in signed application releases.

## Alternatives

- **In-app extractor updates**: rejected — remote code execution; Android discourages it and
  Play policy prohibits it.
- **Local-only health checks**: rejected — cannot react to provider changes without an app
  update.
- **Trust a signed remote binary**: rejected — a signature is necessary but not sufficient;
  code runs with app privileges.

## Trade-offs

- Larger APK and a pinned runtime.
- Update checks add a network dependency, handled non-blockingly and cache-first.

## Consequences

- Incompatible yt-dlp/EJS/JS/Python/FFmpeg combinations are detected and reported as typed
  errors, not crashes.
- Provider failures are isolated; one provider can be disabled without breaking others.
- Stale-version detection does not equate network errors with staleness or age with failure.
- Active downloads are persisted before an update; unrecoverable tasks are marked
  `NEEDS_USER_ACTION`; a failed install preserves the current installation and data.
- Remote metadata can never introduce extraction logic.
