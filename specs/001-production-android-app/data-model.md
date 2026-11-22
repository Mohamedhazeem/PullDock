# Data Model: PullDock

**Feature**: `001-production-android-app` | **Date**: 2026-11-22 | **Plan**: [plan.md](plan.md)

This document defines the domain entities, their relationships, the download state machine,
invariants, and the Room schema. It is a design artifact, not an implementation.

## 1. Domain Entities

### 1.1 DownloadTask (common)

| Field | Type | Notes |
|---|---|---|
| `id` | String (UUID) | Primary key; stable across process death |
| `type` | `DownloadType` | `TORRENT` \| `EXTRACTOR` |
| `source` | String | Magnet URI, `.torrent` reference, or media URL (sanitized) |
| `sourceHash` | String | Deterministic hash for duplicate detection |
| `displayName` | String | User-visible name |
| `outputFilename` | String | Sanitized final filename |
| `state` | `DownloadState` | Current state |
| `progress` | `DownloadProgress` | Bytes/speed/ETA |
| `totalBytes` | Long? | May be unknown until metadata |
| `downloadedBytes` | Long | Monotonic per attempt |
| `downloadSpeed` | Long | Recent rate (B/s) |
| `uploadSpeed` | Long? | Torrent only; null otherwise |
| `etaSeconds` | Long? | Null when unreliable |
| `createdAt` | Instant | |
| `updatedAt` | Instant | |
| `completedAt` | Instant? | |
| `outputUri` | String? | Set on completion |
| `errorCode` | String? | Typed error code |
| `errorDetails` | String? | Sanitized, non-secret |
| `retryCount` | Int | |
| `nextRetryAt` | Instant? | |
| `destination` | `StorageDestination` | |
| `limits` | `TransferLimits` | Per-task overrides |
| `priority` | Int | User-set; higher runs first |
| `queueOrder` | Int | Manual order |
| `engineTaskId` | String? | Engine-side handle/id |

### 1.2 TorrentTaskDetails

| Field | Type | Notes |
|---|---|---|
| `taskId` | String | FK → DownloadTask.id (cascade) |
| `infohash` | String | Unique per torrent |
| `name` | String | Torrent name (untrusted, sanitized) |
| `totalSize` | Long | |
| `files` | List<TorrentFile> | index, path, size, priority, selected |
| `trackers` | List<String> | |
| `peerCount` | Int | |
| `sequentialDownload` | Boolean | |
| `resumeData` | Blob? | Opaque, versioned engine blob |
| `resumeDataVersion` | Int | For migration/validation |
| `seedingPolicy` | `SeedingPolicy` | |
| `uploadedBytes` | Long | |

### 1.3 ExtractorTaskDetails

| Field | Type | Notes |
|---|---|---|
| `taskId` | String | FK → DownloadTask.id (cascade) |
| `providerId` | `ProviderId` | YouTube, X, Instagram, Facebook, TikTok |
| `originalUrl` | String | Sanitized (no credentials) |
| `selectedFormat` | `MediaFormat`? | |
| `container` | String? | |
| `videoCodec` | String? | |
| `audioCodec` | String? | |
| `mergeRequired` | Boolean | |
| `processingStatus` | `ProcessingStatus` | NONE, PENDING, RUNNING, DONE, FAILED |
| `providerError` | String? | Typed provider error code |
| `runtimeInfoId` | String? | ExtractorRuntimeInfo at resolve time |

### 1.4 Value types

- **DownloadProgress**: `downloadedBytes`, `totalBytes?`, `speed`, `etaSeconds?`; `percent`
  is derived, never stored as authority.
- **DownloadType**: `TORRENT`, `EXTRACTOR`.
- **DownloadState**: see §2.
- **ProviderId**: `YOUTUBE`, `X`, `INSTAGRAM`, `FACEBOOK`, `TIKTOK`.
- **MediaFormat**: `id`, `resolution`, `container`, `videoCodec`, `audioCodec`,
  `estimatedBytes?`, `mergeRequired`, `processingEstimateSeconds?`.
- **MediaSource**: `url`, `providerId`, `title`, `thumbnailUrl?`, `durationSeconds?`,
  `authorizedContext` (user-attested/authorized source note).
- **StorageDestination**: `type` (`APP_SPECIFIC`, `MEDIA_STORE`, `SAF`), `uri`, `displayName`,
  `freeSpaceBytes?`.
- **TransferLimits**: `downloadRateBytesPerSec?`, `uploadRateBytesPerSec?`, `maxConnections?`.
- **SeedingPolicy**: `seedAfterCompletion`, `maxUploadRateBytesPerSec?`, `stopRatio?`,
  `timeLimitSeconds?`, `perTorrentOverride`.
- **DownloadPolicy**: `maxConcurrent`, `torrentMaxConcurrent`, `extractorMaxConcurrent`,
  `defaultLimits`, `retryPolicy`, `wifiOnly`, `meteredAllowed`, `batteryThresholdPercent?`.
  v1 defaults: `maxConcurrent = 3`, `torrentMaxConcurrent = 2`, `extractorMaxConcurrent = 1`,
  `maxRetries = 3`, `initialBackoff = 5s`, `maxBackoff = 15m`, `backoffMultiplier = 2.0` with
  ±20% jitter, `wifiOnly = false`, `meteredAllowed = true`, `batteryThresholdPercent = 15`
  (0 disables). Defaults are user-overridable and persisted.

### 1.5 Identifier and playback types

- **EngineTaskId**: opaque engine handle identifier (string); never parsed by domain.
- **ExtractorTaskId**: opaque extractor task identifier (string); never parsed by domain.
- **Track selection**: offered only when the media exposes more than one audio/subtitle track;
  otherwise the control is hidden and the default track is used.

## 2. Download State Machine

### 2.1 States

`CREATED`, `VALIDATING`, `RESOLVING`, `FETCHING_METADATA`, `AWAITING_FILE_SELECTION`,
`AWAITING_FORMAT_SELECTION`, `QUEUED`, `STARTING`, `RUNNING`, `PAUSING`, `PAUSED`,
`RETRY_WAIT`, `VERIFYING`, `POST_PROCESSING`, `COMPLETED`, `FAILED`, `CANCELLED`, `REMOVING`,
`NEEDS_PERMISSION`, `NEEDS_USER_ACTION`, `BLOCKED`.

### 2.2 Legal transitions

| From | To | Trigger |
|---|---|---|
| CREATED | VALIDATING | add request |
| VALIDATING | RESOLVING | torrent valid |
| VALIDATING | AWAITING_FILE_SELECTION | torrent needs selection |
| VALIDATING | FAILED | invalid input |
| RESOLVING | AWAITING_FORMAT_SELECTION | media URL resolved |
| RESOLVING | FAILED | resolve error |
| FETCHING_METADATA | AWAITING_FILE_SELECTION | metadata ok |
| FETCHING_METADATA | FAILED | metadata timeout/no trackers |
| AWAITING_FILE_SELECTION | QUEUED | selection confirmed |
| AWAITING_FILE_SELECTION | CANCELLED | user cancels |
| AWAITING_FORMAT_SELECTION | QUEUED | format chosen |
| AWAITING_FORMAT_SELECTION | CANCELLED | user cancels |
| QUEUED | STARTING | coordinator schedules |
| QUEUED | CANCELLED | user cancels |
| STARTING | RUNNING | engine started |
| STARTING | FAILED | start error |
| RUNNING | PAUSING | pause request |
| RUNNING | VERIFYING | transfer done |
| RUNNING | FAILED | engine error |
| RUNNING | CANCELLED | cancel request |
| PAUSING | PAUSED | engine paused |
| PAUSED | QUEUED | resume request |
| PAUSED | REMOVING | remove request |
| PAUSED | CANCELLED | cancel request |
| RETRY_WAIT | QUEUED | backoff elapsed |
| RETRY_WAIT | CANCELLED | user cancels |
| VERIFYING | POST_PROCESSING | verification ok, merge needed |
| VERIFYING | COMPLETED | verification ok, no merge |
| VERIFYING | FAILED | verification failed |
| POST_PROCESSING | COMPLETED | finalize ok |
| POST_PROCESSING | FAILED | merge/finalize failed |
| FAILED | QUEUED | retry request |
| FAILED | REMOVING | remove request |
| NEEDS_PERMISSION | QUEUED | permission granted |
| NEEDS_USER_ACTION | QUEUED | user resolves |
| BLOCKED | QUEUED | policy unblocks |
| COMPLETED | REMOVING | remove request |
| REMOVING | (deleted row) | removal done |
| Any | CANCELLED | user cancel (where legal) |

### 2.3 Transition rules

- `COMPLETED` cannot resume or retry; it can only be removed.
- `CANCELLED` cannot silently restart; a new task is required.
- A task in `AWAITING_FILE_SELECTION` or `AWAITING_FORMAT_SELECTION` cannot enter `RUNNING`.
- `POST_PROCESSING` and `VERIFYING` MUST NOT be reported as `COMPLETED`.
- Pause is idempotent; repeated start MUST NOT create a second engine session.
- Remove (delete task record) and delete file are separate user-confirmed operations.
- Every transition is persisted atomically with a sequence number and timestamp.

## 3. Invariants

1. `DownloadTask.id` is unique and immutable; engine detail rows cascade on delete.
2. `sourceHash` is unique; duplicate adds never create two active tasks for the same source.
3. State transitions obey §2.2; illegal transitions are rejected by the domain layer.
4. A task is `COMPLETED` only after verification and finalization succeed.
5. `progress.percent` is derived; `downloadedBytes` never exceeds `totalBytes` when known.
6. Pause/resume/cancel are idempotent and safe under concurrent requests.
7. Queue order and concurrency limits are enforced by the coordinator and persisted.
8. Engine resume data is present for any non-terminal torrent task that claims resumability.
9. No engine-specific type appears on `DownloadTask`.
10. No secret (token, cookie, credential, raw private URL) is persisted or logged.

## 4. Room Schema

### 4.1 Entities

| Entity | PK | Unique | Indexes | FK |
|---|---|---|---|---|
| `download_task` | `id` | `sourceHash` | `state`, `type`, `createdAt`, `priority`, `queueOrder` | — |
| `torrent_task` | `taskId` | `infohash` | `taskId` | `taskId → download_task.id` (cascade) |
| `extractor_task` | `taskId` | — | `providerId` | `taskId → download_task.id` (cascade) |
| `library_item` | `id` | `fileUri` | `type`, `completionDate` | — |
| `provider_status` | `providerId` | — | `providerId` | — |
| `extractor_runtime` | `component` | — | — | — |
| `update_manifest_cache` | `id` | — | `fetchedAt` | — |
| `recovery_marker` | `taskId` | — | `state` | `taskId → download_task.id` (cascade) |

### 4.2 Relations

- `download_task` 1—0..1 `torrent_task`
- `download_task` 1—0..1 `extractor_task`
- `download_task` 1—0..1 `recovery_marker`
- `library_item` references a completed `download_task` by id (logical, not FK-enforced to
  allow file-only records).

### 4.3 Transaction boundaries

- Insert task + engine detail + recovery marker: one transaction.
- State transition: one transaction (task row + sequence + timestamp).
- Finalize: write file atomically, then insert `library_item` in one transaction; on failure,
  mark `FAILED` and keep the temp file for recovery.
- Remove task + detail + marker: one transaction, after engine cancellation completes.

### 4.4 Migration strategy

- Versioned schema; explicit `Migration` per change; `AutoMigrationSpec` where safe.
- Every migration has a test from the previous version's database file.
- Destructive fallback is forbidden: a failed migration marks tasks `NEEDS_USER_ACTION`
  rather than deleting data.
- `resumeDataVersion` allows resume-data migration/expiration.

### 4.5 Recovery metadata

`recovery_marker` stores: `taskId`, `interruptedAt`, `reason`, `resumeDataPresent`,
`recoverable`, `attempts`. On startup, the coordinator reads markers and either restores the
engine session or marks `NEEDS_USER_ACTION`.

### 4.6 File consistency reconciliation

`reconcileLibrary()` compares `library_item.fileUri` to the filesystem/MediaStore. Missing
files → `library_item` marked unavailable (not deleted). Orphaned files without records are
reported, not silently imported.

### 4.7 Not persisted

Credentials, cookies, tokens, raw private URLs, raw engine alerts, raw subprocess output,
TLS fingerprints, transient progress bursts. Sanitized error codes and timestamps only.

## 5. Extractor Runtime and Compatibility Model

### 5.1 ExtractorRuntimeInfo (immutable)

| Field | Type |
|---|---|
| `component` | String (yt-dlp, yt-dlp-ejs, python, js-runtime, ffmpeg, ffprobe, abi) |
| `installedVersion` | String |
| `requiredRange` | String (semver range) |
| `runtimeCompatible` | Boolean |
| `channel` | String (stable) |
| `verificationStatus` | `VERIFIED`, `PENDING`, `FAILED`, `UNAVAILABLE` |
| `knownLimitations` | List<String> |
| `checkedAt` | Instant |

### 5.2 Compatibility rules

- yt-dlp ↔ yt-dlp-ejs version compatibility is validated explicitly.
- JS runtime must be present and usable for extractors that require it.
- FFmpeg and FFprobe must be executable for the target ABI.
- Python runtime must satisfy the pinned yt-dlp release requirements.
- Bundled native components must execute on the device ABI (arm64-v8a, x86_64).
- Individually valid versions do not imply a valid combination; the combination is verified.

### 5.3 ProviderCompatibilityRecord

| Field | Type |
|---|---|
| `providerId` | ProviderId |
| `urlPatterns` | List<String> |
| `capabilities` | List<String> |
| `installedExtractorVersion` | String |
| `requiredRuntimeVersions` | String |
| `lastVerifiedAt` | Instant |
| `verificationMethod` | String |
| `result` | `PASS`, `FAIL`, `PARTIAL`, `UNVERIFIED` |
| `knownLimitations` | List<String> |
| `policyAssessment` | String (authorized basis) |

### 5.4 Provider health states

`CURRENT`, `UPDATE_RECOMMENDED`, `UPDATE_REQUIRED`, `PLATFORM_CHANGE_SUSPECTED`,
`PROVIDER_TEMPORARILY_UNAVAILABLE`, `PROVIDER_UNSUPPORTED`, `AUTHORIZATION_REQUIRED`,
`VERSION_CHECK_UNAVAILABLE`, `RUNTIME_INCOMPATIBLE`.

## 6. Update Manifest Model

| Field | Type |
|---|---|
| `schemaVersion` | Int |
| `publishedAt` | Instant |
| `expiresAt` | Instant |
| `recommendedAppVersion` | String? |
| `minimumAppVersion` | String? |
| `approvedExtractorVersions` | Map<component, version range> |
| `approvedRuntimeVersions` | Map<component, version range> |
| `providerStatus` | Map<ProviderId, health/disabled> |
| `releaseNotes` | String? |
| `signature` | Base64 (over canonical payload) |
| `algorithm` | String |

Verification: app-pinned public key, canonical payload, expiration, rollback protection,
HTTPS, parse limits, cached last-known-good. See [contracts/update-manifest.schema.json](contracts/update-manifest.schema.json).
