package com.pulldock.data.download

import android.content.Context
import android.net.Uri
import com.pulldock.core.database.dao.LibraryItemDao
import com.pulldock.core.database.entity.LibraryItemEntity
import com.pulldock.core.model.DownloadType
import com.pulldock.domain.download.model.DownloadTask
import com.pulldock.domain.download.repository.DownloadRepository
import com.pulldock.domain.download.repository.LibraryItem
import com.pulldock.domain.download.repository.LibraryQuery
import com.pulldock.domain.download.repository.LibrarySection
import com.pulldock.domain.download.repository.LibrarySort
import com.pulldock.domain.download.repository.MediaLibraryRepository
import com.pulldock.domain.download.repository.ReconcileReport
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Room-backed library (FR-042/FR-043, data-model.md §4.6). Files are checked against
 * the filesystem/MediaStore on reconciliation; missing files are marked unavailable
 * rather than deleted or presented as playable.
 */
@Singleton
class MediaLibraryRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: LibraryItemDao,
    private val downloadRepository: DownloadRepository,
) : MediaLibraryRepository {

    override fun observe(query: LibraryQuery): Flow<List<LibraryItem>> =
        dao.observeAll().map { entities ->
            entities
                .map(::toDomain)
                .filter { matchesSection(it, query.section) && matchesSearch(it, query.searchText) }
                .sortedWith(compare(query.sortBy))
        }

    override suspend fun get(itemId: String): LibraryItem? = dao.get(itemId)?.let(::toDomain)

    /** Records a finalized task output; called by the completion flow. */
    suspend fun record(task: DownloadTask, fileUri: String, thumbnailUri: String? = null) {
        val existing = dao.findByUri(fileUri)
        dao.upsert(
            LibraryItemEntity(
                id = existing?.id ?: task.id,
                fileUri = fileUri,
                type = task.type.name,
                title = task.displayName,
                sizeBytes = task.totalBytes,
                resolution = task.resolution,
                durationSeconds = null,
                source = task.source.takeIf { it.length <= MAX_SOURCE_LENGTH }?.let(::sanitizeSource),
                completedAt = (task.completedAt ?: Instant.now()).toEpochMilli(),
                thumbnailUri = thumbnailUri,
                fileAvailable = true,
                sourceHash = task.sourceHash,
            ),
        )
    }

    override suspend fun recordFromTask(taskId: String): LibraryItem? {
        val task = downloadRepository.getTask(taskId) ?: return null
        val uri = task.outputUri ?: return null
        record(task, uri)
        return get(taskId)
    }

    override suspend fun findBySourceHash(hash: String): LibraryItem? =
        dao.findBySourceHash(hash)?.let(::toDomain)

    /** Marks records whose file no longer exists; never deletes them (FR-043). */
    override suspend fun reconcile(): ReconcileReport {
        val items = dao.observeAll().first().map(::toDomain)
        var missing = 0
        var ok = 0
        items.forEach { item ->
            val available = exists(item.fileUri)
            if (item.fileAvailable != available) dao.setAvailable(item.id, available)
            if (available) ok++ else missing++
        }
        return ReconcileReport(missing = missing, orphaned = 0, ok = ok)
    }

    override suspend fun delete(itemId: String, deleteFile: Boolean) {
        val item = dao.get(itemId) ?: return
        if (deleteFile) runCatching { deleteUri(item.fileUri) }
        dao.delete(itemId)
        // The library row uses the download task id; remove the Home transfer too.
        runCatching { downloadRepository.removeTask(itemId, deleteFile) }
    }

    private fun exists(uriString: String): Boolean = try {
        val uri = Uri.parse(uriString)
        when (uri.scheme) {
            "file" -> File(uri.path ?: return false).exists()
            "content" -> context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { true } ?: false
            else -> false
        }
    } catch (_: Exception) {
        false
    }

    private fun deleteUri(uriString: String): Boolean = try {
        val uri = Uri.parse(uriString)
        when (uri.scheme) {
            "file" -> File(uri.path ?: return false).delete()
            "content" -> android.provider.DocumentsContract.deleteDocument(context.contentResolver, uri)
            else -> false
        }
    } catch (_: Exception) {
        false
    }

    private fun toDomain(entity: LibraryItemEntity) = LibraryItem(
        id = entity.id,
        type = DownloadType.entries.firstOrNull { it.name == entity.type } ?: DownloadType.TORRENT,
        title = entity.title,
        fileUri = entity.fileUri,
        sizeBytes = entity.sizeBytes,
        resolution = entity.resolution,
        durationSeconds = entity.durationSeconds,
        source = entity.source,
        completedAt = Instant.ofEpochMilli(entity.completedAt),
        thumbnailUri = entity.thumbnailUri,
        fileAvailable = entity.fileAvailable,
        sourceHash = entity.sourceHash,
    )

    private fun matchesSection(item: LibraryItem, section: LibrarySection): Boolean = when (section) {
        LibrarySection.ALL -> true
        LibrarySection.VIDEO -> item.fileUri.hasExtension(VIDEO_EXTENSIONS)
        LibrarySection.AUDIO -> item.fileUri.hasExtension(AUDIO_EXTENSIONS)
        LibrarySection.OTHER -> !item.fileUri.hasExtension(VIDEO_EXTENSIONS + AUDIO_EXTENSIONS)
    }

    private fun matchesSearch(item: LibraryItem, search: String?): Boolean =
        search.isNullOrBlank() || item.title.contains(search.trim(), ignoreCase = true)

    private fun compare(sort: LibrarySort): Comparator<LibraryItem> = when (sort) {
        LibrarySort.NEWEST_FIRST -> compareByDescending { it.completedAt }
        LibrarySort.OLDEST_FIRST -> compareBy { it.completedAt }
        LibrarySort.TITLE_ASC -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }
        LibrarySort.TITLE_DESC -> compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.title }
        LibrarySort.SIZE_DESC -> compareByDescending { it.sizeBytes ?: 0L }
    }

    /** Never persist raw private URLs (data-model.md §4.7): keep only scheme/host. */
    private fun sanitizeSource(source: String): String = runCatching {
        val uri = Uri.parse(source)
        if (uri.scheme == null) "<local>" else "${uri.scheme}://${uri.host ?: "<unknown>"}"
    }.getOrDefault("<local>")

    private fun String.hasExtension(extensions: Set<String>): Boolean =
        extensions.any { endsWith(".$it", ignoreCase = true) }

    companion object {
        private const val MAX_SOURCE_LENGTH = 4_096
        private val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "webm", "avi", "mov", "m4v")
        private val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "flac", "ogg", "wav", "opus")
    }
}
