# ADR-0006: File storage and finalization

**Status**: Accepted | **Date**: 2026-11-22

## Context

Downloads must be stored safely under scoped storage, with atomic publication, collision
handling, and recovery from interruption.

## Decision

Use app-specific storage for temporary/partial files, MediaStore for completed public media,
and SAF for user-chosen destinations. A `FileFinalizer` writes to a temp file, verifies, then
atomically renames/publishes. Collision strategy: `name (n).ext`.

## Alternatives

- **`MANAGE_EXTERNAL_STORAGE`**: rejected — broad access for convenience.
- **Direct final-path writes**: rejected — interruption corrupts a "completed" file.
- **Store everything app-specific**: rejected — users expect public media in the library.

## Trade-offs

- Atomic finalization adds a temp-file step and cleanup work.
- SAF/MediaStore add platform complexity and failure modes.

## Consequences

- An incomplete file is never published as completed.
- Path traversal, absolute paths, reserved names, and case/unicode collisions are rejected.
- SAF permission revocation and unmounted volumes are handled with typed errors.
- `reconcileLibrary()` marks missing files instead of deleting records.
