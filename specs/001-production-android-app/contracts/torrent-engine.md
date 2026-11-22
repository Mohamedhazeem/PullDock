# Contract: TorrentEngine

**Module**: `:domain:torrent` (interface) · `:data:torrent` (implementation)
**Source**: [plan.md](../plan.md) §Torrent Architecture

```kotlin
interface TorrentEngine {

    /** Add a magnet URI or torrent file reference. Returns an engine task id. */
    suspend fun addTorrent(source: TorrentSource): TorrentResult<EngineTaskId>

    /** Fetch metadata asynchronously; times out with a typed error. */
    suspend fun fetchMetadata(taskId: EngineTaskId, timeout: Duration): TorrentResult<TorrentMetadata>

    /** Select/prioritize files. Safe to call before or during download. */
    suspend fun selectFiles(taskId: EngineTaskId, selection: FileSelection): TorrentResult<Unit>

    /** Enable/disable sequential downloading (used for streaming). */
    suspend fun setSequential(taskId: EngineTaskId, enabled: Boolean): TorrentResult<Unit>

    suspend fun start(taskId: EngineTaskId): TorrentResult<Unit

    /** Idempotent. */
    suspend fun pause(taskId: EngineTaskId): TorrentResult<Unit

    /** Idempotent; no-op if already running. */
    suspend fun resume(taskId: EngineTaskId): TorrentResult<Unit

    suspend fun recheck(taskId: EngineTaskId): TorrentResult<Unit>

    suspend fun setFilePriority(taskId: EngineTaskId, priorities: Map<FileIndex, Priority>): TorrentResult<Unit>

    suspend fun setLimits(taskId: EngineTaskId, limits: TransferLimits): TorrentResult<Unit>

    /** Remove the task from the engine. Does not delete files. */
    suspend fun remove(taskId: EngineTaskId): TorrentResult<Unit>

    /** Opaque, versioned resume blob; null if unavailable. */
    suspend fun exportResumeData(taskId: EngineTaskId): TorrentResult<ResumeBlob?>

    /** Restore from a resume blob. */
    suspend fun importResumeData(taskId: EngineTaskId, blob: ResumeBlob): TorrentResult<Unit>

    /** Request a loopback streaming URL for a media file, if eligible. */
    suspend fun requestStreamingBridge(taskId: EngineTaskId, fileIndex: Int): TorrentResult<StreamingHandle>

    /** Hot, throttled by the adapter; events carry no native types. */
    fun observeEvents(taskId: EngineTaskId?): Flow<TorrentEvent>

    /** Cooperative cancellation of in-flight work for a task. */
    fun cancel(taskId: EngineTaskId)

    /** Idempotent shutdown of the session. */
    suspend fun close()
}
```

## Types

```kotlin
sealed interface TorrentResult<out T> {
    data class Success<T>(val value: T) : TorrentResult<T>
    data class Failure(val error: DownloadError) : TorrentResult<T>
}

sealed interface TorrentEvent {
    data class MetadataAvailable(val metadata: TorrentMetadata) : TorrentEvent
    data class Progress(val taskId: EngineTaskId, val downloadedBytes: Long, val totalBytes: Long?, val speed: Long) : TorrentEvent
    data class PeerCount(val taskId: EngineTaskId, val peers: Int) : TorrentEvent
    data class PieceVerified(val taskId: EngineTaskId, val failed: Int) : TorrentEvent
    data class StateChanged(val taskId: EngineTaskId, val state: DownloadState) : TorrentEvent
    data class Error(val taskId: EngineTaskId, val error: DownloadError) : TorrentEvent
    data class SeedingStats(val taskId: EngineTaskId, val uploadedBytes: Long, val ratio: Double) : TorrentEvent
}

data class TorrentSource(val kind: Kind, val value: String) {
    enum class Kind { MAGNET, TORRENT_URI }
}

data class TorrentMetadata(
    val name: String,
    val infohash: String,
    val totalSize: Long,
    val files: List<TorrentFile>,
    val trackers: List<String>,
)

data class TorrentFile(val index: Int, val path: String, val size: Long, val priority: Int, val selected: Boolean)
data class FileSelection(val selectedIndexes: Set<Int>, val priority: Map<Int, Priority>)
data class ResumeBlob(val version: Int, val bytes: ByteArray)
data class StreamingHandle(val url: String, val token: String, val fileIndex: Int)
```

## Concurrency and lifecycle

- All calls are marshalled to a single engine thread; safe to call from `Dispatchers.Io`.
- `pause`/`resume`/`close` are idempotent.
- `cancel(taskId)` is cooperative and non-blocking; `close()` is idempotent.
- The adapter translates alerts into `TorrentEvent`s and never exposes alert structs.
- `close()` releases the session and all handles; called from the process-death path.
