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

    /** Returns the published file URI, or null when no verified output could be published. */
    suspend fun finalize(taskId: String): String? {
        val entity = taskDao.getById(taskId) ?: return null
        if (entity.state != DownloadState.VERIFYING.name) return null

        val root = context.getExternalFilesDir(null) ?: return fail(entity)
        val folder = storagePreferences.currentDownloadFolder()
        val saveRoot = if (folder.isNullOrBlank()) root else java.io.File(root, folder).takeIf { it.exists() || it.mkdirs() } ?: root
        val candidate = findOutput(taskId, saveRoot) ?: return fail(entity)
        val filename = FilenameSanitizer.sanitize(candidate.name).ifBlank { "download" }
        val finalizer = FileFinalizer(saveRoot) { it.exists() && it.length() > 0L }
        return when (val result = finalizer.publish(candidate, filename)) {
            is FileFinalizer.Result.Published -> {
                val uri = File(result.path).toURI().toString()
                val now = System.currentTimeMillis()
                val thumbnail = thumbnailFor(result.path, taskId)
                if (taskDao.completeWithOutput(taskId, uri, File(result.path).name, now) > 0) {
                    library.record(
                        task = TaskMappers.toDomain(entity).copy(
                            state = DownloadState.COMPLETED,
                            outputUri = uri,
                            completedAt = Instant.ofEpochMilli(now),
                        ),
                        fileUri = uri,
                        thumbnailUri = thumbnail,
                    )
                    uri
                } else {
                    null
                }
            }
            is FileFinalizer.Result.Rejected -> fail(entity)
        }
    }

    /** Publishes a verified extractor temp file, then completes the task. */
    suspend fun finalizeMedia(taskId: String, tempPath: String): String? {
        val entity = taskDao.getById(taskId) ?: return null
        if (entity.state != DownloadState.RUNNING.name) return null
        val temp = java.io.File(tempPath)
        if (!temp.isFile || temp.length() <= 0L) return fail(entity)

        val root = context.getExternalFilesDir(null) ?: return fail(entity)
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
                if (taskDao.completeMediaWithOutput(taskId, uri, java.io.File(result.path).name, now) > 0) {
                    library.record(
                        task = TaskMappers.toDomain(entity).copy(
                            state = DownloadState.COMPLETED,
                            outputUri = uri,
                            completedAt = Instant.ofEpochMilli(now),
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
        val directory = File(context.cacheDir, "thumbnails").apply { mkdirs() }
        val output = File(directory, "$taskId.jpg")
        return runCatching {
            FileOutputStream(output).use { stream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
            }
            output.toURI().toString()
        }.getOrNull()
    }

    private suspend fun findOutput(taskId: String, root: File): File? {
        val paths = torrentDao.getFiles(taskId).map { it.path }
        val fromMetadata = paths.mapNotNull { relative ->
            File(root, relative).takeIf { it.isFile }
        }
        return fromMetadata.maxByOrNull { it.length() }
            ?: root.walkTopDown()
                .filter { it.isFile && !it.absolutePath.contains("/datastore/") && it.length() > 0L }
                .maxByOrNull { it.length() }
    }
}
