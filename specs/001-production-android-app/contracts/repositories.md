# Contract: Repositories and Queue Coordinator

**Module**: `:domain:download` (interfaces) · `:data:download` (implementations)
**Source**: [plan.md](../plan.md) §Queue Architecture

```kotlin
interface DownloadRepository {
    fun observeTask(id: String): Flow<DownloadTask?>
    fun observeQueue(filter: QueueFilter): Flow<List<DownloadTask>>
    fun observeLibrary(query: LibraryQuery): Flow<List<LibraryItem>>
    suspend fun getTask(id: String): DownloadTask?
    suspend fun addTorrent(source: TorrentSource, destination: StorageDestination): DownloadTask
    suspend fun addMedia(descriptor: MediaDescriptor, format: MediaFormat, destination: StorageDestination): DownloadTask
    suspend fun updateState(id: String, to: DownloadState, error: DownloadError? = null): Boolean
    suspend fun updateProgress(id: String, progress: DownloadProgress): Unit
    suspend fun updateRetry(id: String, count: Int, nextRetryAt: Instant?): Unit
    suspend fun reorder(orderedIds: List<String>): Unit
    suspend fun removeTask(id: String, deleteFile: Boolean): Unit
    suspend fun findBySourceHash(hash: String): DownloadTask?
}

interface QueueCoordinator {
    val state: StateFlow<QueueState>
    suspend fun enqueue(task: DownloadTask): Unit
    suspend fun pause(id: String): Unit
    suspend fun resume(id: String): Unit
    suspend fun cancel(id: String): Unit
    suspend fun retry(id: String): Unit
    suspend fun move(ids: List<String>): Unit
    suspend fun remove(id: String, deleteFile: Boolean): Unit
    suspend fun recover(): RecoveryReport
    fun observeState(): Flow<QueueState>
}

interface MediaLibraryRepository {
    fun observe(query: LibraryQuery): Flow<List<LibraryItem>>
    suspend fun reconcile(): ReconcileReport
    suspend fun delete(itemId: String, deleteFile: Boolean): Unit
}

interface StorageRepository {
    suspend fun resolveDestination(request: DestinationRequest): StorageDestination
    suspend fun createTempFile(taskId: String): File
    suspend fun finalize(tempFile: File, destination: StorageDestination, filename: String): FinalizeResult
    suspend fun deleteFile(uri: String): Boolean
    suspend fun freeSpace(destination: StorageDestination): Long?
}

interface NetworkPolicyRepository {
    fun observe(): Flow<DownloadPolicy>
    suspend fun update(policy: DownloadPolicy): Unit
}

interface ExtractorRuntimeRepository {
    fun observe(): Flow<ExtractorRuntimeInfo>
    suspend fun validate(): ExtractorRuntimeInfo
}

interface ProviderCompatibilityRepository {
    fun observe(): Flow<Map<ProviderId, ProviderHealth>>
    suspend fun record(record: ProviderCompatibilityRecord): Unit
    suspend fun mark(providerId: ProviderId, state: ProviderHealthState): Unit
}

interface UpdateManifestRepository {
    /** Non-blocking; returns the cached last-known-good on failure. */
    suspend fun fetch(): UpdateManifestResult
    fun cached(): UpdateManifest?
}
```

## Types

```kotlin
data class QueueState(
    val tasks: List<DownloadTask>,
    val activeCount: Int,
    val queuedCount: Int,
    val globalLimit: Int,
    val perEngineLimits: Map<DownloadType, Int>,
)

data class RecoveryReport(val resumed: Int, val needsUserAction: Int, val failed: Int)
data class ReconcileReport(val missing: Int, val orphaned: Int, val ok: Int)
sealed interface UpdateManifestResult {
    data class Success(val manifest: UpdateManifest) : UpdateManifestResult
    data class Cached(val manifest: UpdateManifest) : UpdateManifestResult
    data class Unavailable(val reason: DownloadError) : UpdateManifestResult
}
```

## Queue rules

- One coordinator for both engine types; per-engine queues with independent caps.
- Fairness: round-robin across engines; global cap + per-engine caps.
- Retry: only retryable errors; bounded attempts; exponential backoff with jitter; persisted.
- Duplicates: `sourceHash` unique constraint plus a coordinator check.
- Recovery: `recover()` uses `RecoveryMarker`s; unrecoverable tasks → `NEEDS_USER_ACTION`.
- Events applied idempotently by task id; out-of-order resolved by persisted sequence.
- Engine-specific controls stay on engine detail records and are applied by adapters.
