package com.pulldock.data.download

import com.pulldock.core.datastore.SettingsDataStore
import com.pulldock.core.storage.PathValidator
import com.pulldock.domain.download.repository.StoragePreferencesRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Download-folder preference (FR-038). Only relative, traversal-free subfolders of
 * app-specific storage are accepted — no broad storage permission is ever required.
 */
@Singleton
class StoragePreferencesRepositoryImpl @Inject constructor(
    private val settings: SettingsDataStore,
) : StoragePreferencesRepository {

    override fun observeDownloadFolder(): Flow<String?> =
        settings.settings.map { it.downloadFolder }.distinctUntilChanged()

    override suspend fun currentDownloadFolder(): String? = settings.settings.map { it.downloadFolder }.first()

    override suspend fun setDownloadFolder(relativePath: String?) {
        val validated = relativePath?.trim()?.takeIf { it.isNotBlank() }
        if (validated != null) {
            require(PathValidator.validateRelativePath(validated) == null) { "Unsafe download folder" }
        }
        settings.setDownloadFolder(validated)
    }
}

/** Media quality preference implementation over the shared settings DataStore. */
@Singleton
class MediaPreferencesRepositoryImpl @Inject constructor(
    private val settings: SettingsDataStore,
) : com.pulldock.domain.download.repository.MediaPreferencesRepository {

    override fun observeDefaultQuality(): Flow<Int?> =
        settings.settings.map { it.defaultQualityHeight }.distinctUntilChanged()

    override suspend fun currentDefaultQuality(): Int? = settings.settings.map { it.defaultQualityHeight }.first()

    override suspend fun setDefaultQuality(height: Int?) {
        settings.setDefaultQuality(height?.takeIf { it in MIN_HEIGHT..MAX_HEIGHT })
    }

    private companion object {
        const val MIN_HEIGHT = 144
        const val MAX_HEIGHT = 4320
    }
}

