# ADR-0011: Completion, library, and local playback wiring

**Status**: Accepted | **Date**: 2026-11-24

## Context

Slices 1–5 persisted task state and built the storage/streaming primitives, but no
production code connected them: engine alerts never reached Room, `FileFinalizer` was
never invoked, no `library_item` rows existed, and `:feature:library` /
`:feature:player` had no sources. A verified torrent stayed in `VERIFYING` forever with
a fully downloaded file on disk.

## Decision

1. The queue coordinator owns a single collector for `TorrentEngine.observeEvents(null)`.
   Progress, state, peer-count, upload-rate and metadata events are mapped to idempotent
   persisted updates (T104); task ids are resolved via a cached engine-id map.
2. `CompletionFinalizer` (`:data:download`) publishes a verified output with
   `FileFinalizer`, inserts the library row, and only then writes `COMPLETED` in one
   guarded Room write (data-model.md §4.3). On failure the temp file is preserved and
   the task is marked `FAILED` with `STORAGE_FINALIZE_FAILED`.
3. `library_item` is a new Room v2 table with a tested 1→2 migration — never destructive.
4. `MediaLibraryRepositoryImpl` reconciles records against actual files and marks
   missing entries unavailable instead of deleting them (FR-043).
5. Local playback uses Media3 `ExoPlayer` in `:feature:player` with throttled position
   persistence in DataStore. Track selection is shown only when more than one
   audio/subtitle track exists (FR-044).
6. The download folder is a validated relative subfolder of app-specific external
   storage, persisted in DataStore and selectable in Settings. No broad storage
   permission or SAF tree grant is required.

## Alternatives

- **MediaStore publication to shared storage**: rejected for this slice — requires
  broader permissions and interrupts on scoped-storage behavior; app-specific storage
  is sufficient for local playback and remains reconcilable.
- **Persisting progress percentages as completion**: rejected (constitution VII).
- **Playing directly from the torrent temp file**: rejected — incomplete data would be
  presented as complete (FR-030).

## Trade-offs

- Library playback is limited to files inside PullDock's own storage; files are not
  visible to other apps.
- Thumbnail caching (T164) and a lifecycle-owned streaming bridge (T160) remain open;
  this ADR does not change their requirements.

## Consequences

- `VERIFYING` and `POST_PROCESSING` are never reported as `COMPLETED`.
- Reconciliation keeps broken records visible but non-playable.
- A failed finalization never publishes an incomplete file.
- 2026-11-24 update: `library_item.sourceHash` (Room v4) prevents re-downloading an
  already-finalized source; Home shows only live transfers, and completed media lives
  exclusively in the Library. Player controls are overlaid on the Media3 surface.
