package com.pulldock.domain.download.usecase

import com.pulldock.domain.download.model.DownloadGroup
import com.pulldock.domain.download.model.DownloadTask
import com.pulldock.domain.download.repository.DownloadRepository
import com.pulldock.domain.download.repository.QueueFilter
import com.pulldock.domain.extractor.model.ExtractorTaskDetails
import com.pulldock.domain.torrent.model.TorrentTaskDetails
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

/** A download task with whatever engine-specific details exist for it. */
data class DownloadDetails(
    val task: DownloadTask,
    val torrent: TorrentTaskDetails?,
    val extractor: ExtractorTaskDetails?,
)

/** Observes the unified queue with a filter (FR-023). */
class ObserveDownloadQueue @Inject constructor(
    private val repository: DownloadRepository,
) {
    operator fun invoke(filter: QueueFilter): Flow<List<DownloadTask>> = repository.observeQueue(filter)
}

/** Observes the logical download groups (playlists, multi-file torrents). */
class ObserveGroups @Inject constructor(
    private val repository: DownloadRepository,
) {
    operator fun invoke(): Flow<List<DownloadGroup>> = repository.observeGroups()
}

/** Observes one task with its engine details (plan.md flow: detail→ObserveDownloadDetails). */
class ObserveDownloadDetails @Inject constructor(
    private val repository: DownloadRepository,
) {
    operator fun invoke(taskId: String): Flow<DownloadDetails?> =
        combine(
            repository.observeTask(taskId),
            repository.observeTorrentDetails(taskId),
            repository.observeExtractorDetails(taskId),
        ) { task, torrent, extractor ->
            if (task == null) null else DownloadDetails(task, torrent, extractor)
        }
}
