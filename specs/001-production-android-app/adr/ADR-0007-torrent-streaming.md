# ADR-0007: Torrent streaming bridge

**Status**: Accepted | **Date**: 2026-11-22

## Context

Users want to play eligible torrent media before download completes. This requires serving
partial file data to a player safely.

## Decision

Provide a loopback-only HTTP bridge with a per-session unpredictable token, byte-range
support, path-traversal defense, and lifecycle-bound shutdown. Owned by `:feature:player`;
fed by `:data:torrent` using sequential download.

## Alternatives

- **LAN-accessible server**: rejected — exposes content beyond the device.
- **Only completed-file playback**: rejected — streaming is a v1 requirement.
- **Direct file handle to player**: rejected — partial data and seeking are unsafe.

## Trade-offs

- Adds a network surface, however local.
- Seeking beyond available data requires careful buffering behavior.

## Consequences

- Bind to `127.0.0.1` only; never `0.0.0.0`.
- Token required; only the requested file index is served; traversal rejected.
- Bridge terminates on stop, player destruction, or torrent removal.
- No data is fabricated; missing pieces pause playback.
