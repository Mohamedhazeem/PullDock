package com.pulldock.data.download

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import com.pulldock.core.database.dao.DownloadTaskDao
import com.pulldock.core.database.dao.TorrentTaskDao
import com.pulldock.core.database.entity.DownloadTaskEntity
import com.pulldock.core.model.DownloadState
import com.pulldock.core.storage.FileFinalizer
import com.pulldock.core.storage.FilenameSanitizer
import com.pulldock.domain.download.repository.StoragePreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Finalizes a verified torrent transfer: selects the downloaded media file, publishes
 * it atomically through [FileFinalizer], records the library entry, and only then marks
 * the task COMPLETED (FR-030/FR-039, data-model.md §4.3). On failure the temp file is
 * preserved for recovery and the task becomes FAILED with a typed storage code.
 */
@Singleton
class CompletionFinalizer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val taskDao: DownloadTaskDao,
    private val torrentDao: TorrentTaskDao,
    private val library: MediaLibraryRepositoryImpl,
    private val storagePreferences: StoragePreferencesRepository,
) {

    /**
     * Resolve the download root. Prefers a top-level `/PullDock` on shared storage when the
     * user granted "All files access"; otherwise falls back to app-specific storage so a
     * download never fails just because the special permission is not granted.
     */
    private fun downloadRoot(): File? {
        val shared = File(android.os.Environment.getExternalStorageDirectory(), "PullDock")
        val sharedReady = android.os.Environment.isExternalStorageManager() &&
            (shared.exists() || shared.mkdirs())
        if (sharedReady) return shared
        val base = context.getExternalFilesDir(null) ?: return null
        return File(base, "PullDock").apply { mkdirs() }
    }

    /** Returns the published file URI, or null when no verified output could be published. */
    suspend fun finalize(taskId: String): String? {
        val entity = taskDao.getById(taskId) ?: return null
        if (entity.state != DownloadState.VERIFYING.name) return null

        val root = downloadRoot() ?: return fail(entity)
        val folder = storagePreferences.currentDownloadFolder()
        val saveRoot = if (folder.isNullOrBlank()) root else java.io.File(root, folder).takeIf { it.exists() || it.mkdirs() } ?: root

        // Publish every selected file (all files when none were explicitly selected),
        // so a multi-file torrent exposes each file as its own library item.
        val files = torrentDao.getFiles(taskId)
        val selected = files.filter { it.selected }.ifEmpty { files }
        if (selected.isEmpty()) return fail(entity)

        val published = mutableListOf<PublishedFile>()
        for (file in selected) {
            val candidate = File(saveRoot, file.path).takeIf { it.isFile } ?: continue
            val filename = FilenameSanitizer.sanitize(candidate.name).ifBlank { "download" }
            val finalizer = FileFinalizer(saveRoot) { it.exists() && it.length() > 0L }
            val result = finalizer.publish(candidate, filename)
            if (result is FileFinalizer.Result.Published) {
                val path = result.path
                published += PublishedFile(
                    uri = File(path).toURI().toString(),
                    size = File(path).length(),
                    thumbnail = thumbnailFor(path, "$taskId-${file.fileIndex}"),
                    title = File(path).name,
                    id = "$taskId:${file.fileIndex}",
                )
            }
        }
        if (published.isEmpty()) return fail(entity)

        val now = System.currentTimeMillis()
        val first = published.first()
        val totalBytes = published.sumOf { it.size }
        if (taskDao.completeWithOutput(taskId, first.uri, first.title, totalBytes, now) > 0) {
            val domainTask = TaskMappers.toDomain(entity).copy(
                state = DownloadState.COMPLETED,
                outputUri = first.uri,
                completedAt = Instant.ofEpochMilli(now),
                totalBytes = totalBytes,
                downloadedBytes = totalBytes,
            )
            published.forEach { file ->
                library.record(
                    task = domainTask,
                    fileUri = file.uri,
                    thumbnailUri = file.thumbnail,
                    id = file.id,
                    title = file.title,
                    sizeBytes = file.size,
                    groupId = entity.groupId,
                )
            }
            return first.uri
        }
        return null
    }

    /** A single published file from a (possibly multi-file) torrent. */
    private data class PublishedFile(
        val uri: String,
        val size: Long,
        val thumbnail: String?,
        val title: String,
        val id: String,
    )

    /** Publishes a verified extractor temp file, then completes the task. */
    suspend fun finalizeMedia(taskId: String, tempPath: String): String? {
        val entity = taskDao.getById(taskId) ?: return null
        if (entity.state != DownloadState.RUNNING.name) return null
        val temp = java.io.File(tempPath)
        if (!temp.isFile || temp.length() <= 0L) return fail(entity)

        val root = downloadRoot() ?: return fail(entity)
        val folder = storagePreferences.currentDownloadFolder()
        val saveRoot = if (folder.isNullOrBlank()) root else java.io.File(root, folder).takeIf { it.exists() || it.mkdirs() } ?: root
        val extension = temp.extension.ifBlank { "mp4" }
        val filename = FilenameSanitizer.sanitize(entity.displayName) + "." + extension
        return when (val result = FileFinalizer(saveRoot) { it.exists() && it.length() > 0L }.publish(temp, filename)) {
            is FileFinalizer.Result.Published -> {
                val uri = java.io.File(result.path).toURI().toString()
                val now = System.currentTimeMillis()
                val actualSize = java.io.File(result.path).length()
                val thumbnail = thumbnailFor(result.path, taskId)
                taskDao.updateMetadata(taskId, entity.displayName, actualSize, now)
                if (taskDao.completeMediaWithOutput(taskId, uri, java.io.File(result.path).name, actualSize, now) > 0) {
                    library.record(
                        task = TaskMappers.toDomain(entity).copy(
                            state = DownloadState.COMPLETED,
                            outputUri = uri,
                            completedAt = Instant.ofEpochMilli(now),
                            // The published file is the source of truth: the format's
                            // estimatedBytes is only an estimate and excludes the muxed
                            // audio, so persisting it shows a wrong size (e.g. 41.7 MB
                            // shown for a 48.6 MB file). Record the real byte count.
                            totalBytes = actualSize,
                            downloadedBytes = actualSize,
                        ),
                        fileUri = uri,
                        thumbnailUri = thumbnail,
                    )
                    uri
                } else null
            }
            is FileFinalizer.Result.Rejected -> fail(entity)
        }
    }

    private suspend fun fail(entity: DownloadTaskEntity): Nothing? {
        taskDao.updateState(
            id = entity.id,
            state = DownloadState.FAILED.name,
            errorCode = "STORAGE_FINALIZE_FAILED",
            errorDetails = "verified output unavailable",
            updatedAt = System.currentTimeMillis(),
        )
        return null
    }

    private fun thumbnailFor(path: String, taskId: String): String? {
        val file = File(path)
        if (!file.isFile || file.length() <= 0L) return null
        val imageSuffix = file.extension.lowercase()
        val bitmap: Bitmap? = when {
            imageSuffix in setOf("jpg", "jpeg", "png", "webp") ->
                runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
            else -> {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(file.absolutePath)
                    runCatching { retriever.frameAtTime }.getOrNull()
                        ?: runCatching { retriever.embeddedPicture }.getOrNull()
                            ?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                } catch (_: Throwable) {
                    null
                } finally {
                    runCatching { retriever.release() }
                }
            }
        } ?: return null
        val image = bitmap ?: return null
        val directory = File(context.cacheDir, "thumbnails").apply { mkdirs() }
        val output = File(directory, "$taskId.jpg")
        return runCatching {
            FileOutputStream(output).use { stream ->
                image.compress(Bitmap.CompressFormat.JPEG, 85, stream)
            }
            output.toURI().toString()
        }.getOrNull()
    }
}
