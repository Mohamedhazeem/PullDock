# Contract: Discovery → Existing Add Flow Handoff

The discovery feature MUST reuse the existing Add Torrent pipeline; it MUST NOT implement a
second ingestion path or touch the engine/storage directly (spec FR-054..FR-060).

## Handoff path

```text
ResultDetailsScreen
  └─ user taps "Add to PullDock"
       └─ DiscoveryViewModel.onAddClicked(result)
            └─ repository.resolveDownloadDescriptor(details) -> DownloadDescriptor?
                 ├─ MAGNET        -> source = magnetUri (validated btih)
                 └─ TORRENT_FILE  -> source = file:// uri (validated temp .torrent)
            └─ emit DiscoverEvent.AddToPullDock(source)
                 └─ app navigation: navController.navigate(Routes.addSource(source))
                      └─ existing AddDownloadScreen/AddDownloadViewModel
                           ├─ "magnet:"   -> submitTorrent -> AddTorrent(MAGNET, source)
                           └─ "file://"   -> onTorrentFilePicked -> AddTorrent(TORRENT_URI, source)
                                └─ AddTorrent: MagnetValidator/uri validation,
                                   findBySourceHash duplicate guard, metadata fetch,
                                   file selection, destination, explicit start,
                                   unified queue (unchanged)
```

`Routes.addSource(source)` already URL-encodes the source into `add?source={source}`, and
`AddDownloadViewModel` already classifies `magnet:`, `content://`, and `file://` inputs
(verified in the repository). No change to the Add flow is required.

## DownloadDescriptor → source mapping

| Descriptor kind | Source string handed to add route | Validation before handoff |
|---|---|---|
| MAGNET | the magnet URI | `MagnetValidator` rules (btih hex/base32) |
| TORRENT_FILE | `file://` uri of a temp file | bounded size; real torrent (not HTML); https origin; path confined to app cache |

`null` descriptor (e.g., Archive item with no usable `.torrent`) ⇒ no Add action is shown.

## Temporary `.torrent` lifecycle (Archive)

```kotlin
interface TemporaryTorrentFile {
    val uri: String          // file:// uri consumed by the Add flow
    suspend fun sizeBytes(): Long
    suspend fun cleanup()    // idempotent; safe on success/back/cancel
}
```

- Created only after the user taps Add (FR-026).
- Owned by `:data:discovery`; discovery triggers `cleanup()` when the user leaves the Add
  flow (success or back/cancel) or via a bounded cache sweep (FR-056).
- Cleanup failure is sanitized-logged and never corrupts other state.

## Invariants

- No transfer exists until the user completes the existing Add flow and explicitly starts
  (FR-060).
- Duplicate active/completed sources are reported by the existing `AddTorrent` duplicate
  guard (`DUPLICATE_INFOHASH`); discovery adds no parallel dedupe (FR-058).
- Seeding/network/consent defaults are unchanged and enforced by the existing flow (FR-059).
- Discovery never calls `TorrentEngine`, libtorrent4j, Room, or storage directly (FR-004,
  NFR-002).