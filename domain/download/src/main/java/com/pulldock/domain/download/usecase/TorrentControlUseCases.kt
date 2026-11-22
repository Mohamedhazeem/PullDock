package com.pulldock.domain.download.usecase

import com.pulldock.core.model.DownloadError
import com.pulldock.domain.download.repository.DownloadRepository
import com.pulldock.domain.torrent.TorrentEngine
import com.pulldock.domain.torrent.TorrentResult
import com.pulldock.domain.torrent.model.FileSelection
import com.pulldock.domain.torrent.model.SeedingPolicy
import javax.inject.Inject

/**
 * Applies the user's seeding policy to a torrent (T057, FR-011/FR-012). The engine
 * enforces stop conditions; the persisted copy lives on the torrent detail row.
 */
class SetTorrentSeedingPolicy @Inject constructor(
    private val repository: DownloadRepository,
    private val engine: TorrentEngine,
) {
    suspend operator fun invoke(taskId: String, policy: SeedingPolicy): UseCaseResult<Unit> {
        val task = repository.getTask(taskId)
            ?: return UseCaseResult.Failure(DownloadError.Persistence(DownloadError.PersistenceReason.DB_ERROR))
        val engineTaskId = task.engineTaskId
            ?: return UseCaseResult.Failure(DownloadError.Persistence(DownloadError.PersistenceReason.RECOVERY_DATA_INVALID))
        return when (val result = engine.setSeedingPolicy(engineTaskId, policy)) {
            is TorrentResult.Success -> UseCaseResult.Success(Unit)
            is TorrentResult.Failure -> UseCaseResult.Failure(result.error)
        }
    }
}

/**
 * Applies a file selection to a running or waiting torrent (T067, FR-004). Selection
 * changes after start update priorities and are validated by the engine against the
 * metadata file list.
 */
class ApplyTorrentFileSelection @Inject constructor(
    private val repository: DownloadRepository,
    private val engine: TorrentEngine,
) {
    suspend operator fun invoke(taskId: String, selection: FileSelection): UseCaseResult<Unit> {
        val task = repository.getTask(taskId)
            ?: return UseCaseResult.Failure(DownloadError.Persistence(DownloadError.PersistenceReason.DB_ERROR))
        val engineTaskId = task.engineTaskId
            ?: return UseCaseResult.Failure(DownloadError.Persistence(DownloadError.PersistenceReason.RECOVERY_DATA_INVALID))
        return when (val result = engine.selectFiles(engineTaskId, selection)) {
            is TorrentResult.Success -> UseCaseResult.Success(Unit)
            is TorrentResult.Failure -> UseCaseResult.Failure(result.error)
        }
    }
}
