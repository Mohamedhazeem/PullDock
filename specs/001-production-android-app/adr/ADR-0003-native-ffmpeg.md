# ADR-0003: Native FFmpeg packaging

**Status**: Accepted | **Date**: 2026-11-22

## Context

Separate audio/video streams must be merged, and media must be probed/verified. This needs
an Android-compatible FFmpeg/FFprobe.

## Decision

Use a maintained Android FFmpeg build (or self-built binaries) packaged per ABI
(arm64-v8a, x86_64), invoked only through `:engines:media-ffmpeg` with argument arrays.

## Alternatives

- **Retired FFmpegKit**: not maintained.
- **Download FFmpeg at runtime**: rejected — remote executable code and licensing risk.
- **Skip merging**: rejected — separate-stream sources are in scope.

## Trade-offs

- Adds native size and license obligations (GPL/LGPL depends on build flags).
- Process invocation must be bounded, cancellable, and reaped.

## Consequences

- `:engines:media-ffmpeg` returns typed exit codes and bounded, sanitized output.
- Licenses and notices are documented; binary provenance is recorded.
- Merge failure preserves any existing valid file and marks the task `FAILED`.
