# ADR-0010: Security boundaries and no remote code execution

**Status**: Accepted | **Date**: 2026-11-22

## Context

PullDock handles untrusted URLs, torrent metadata, filenames, and subprocess output, and it
can expose a device IP to torrent peers. A threat-model-driven design is required.

## Decision

Enforce boundaries: UI → domain → data → engines. Only engines touch native/process types.
Apply: URL scheme allowlist and SSRF guards; torrent filename sanitization and destination
confinement; argument-array subprocess invocation; loopback-only streaming; sanitized
logging; pinned dependencies; minimum permissions; and a hard prohibition on remote dynamic
code execution.

## Alternatives

- **Shell-string process invocation**: rejected — injection risk.
- **Trusting filenames/URLs**: rejected — path traversal and SSRF.
- **Remote extractor updates**: rejected — Android DCL guidance and Play policy.

## Trade-offs

- More validation code and platform-specific security tests.
- SSRF guard must allow legitimate media CDNs without weakening protection.

## Consequences

- No secrets (tokens, cookies, credentials, private URLs) are persisted or logged.
- Path traversal, absolute paths, reserved names, and unicode/case collisions are rejected.
- Streaming is loopback-only with a token and traversal defense.
- Executable updates happen only via signed application releases.
- The app requests the minimum permissions and documents the torrent peer-IP exposure.
