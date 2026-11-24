package com.pulldock.data.download

import com.pulldock.domain.download.queue.QueueCoordinator
import com.pulldock.domain.download.repository.AppearanceRepository
import com.pulldock.domain.download.repository.ConsentRepository
import com.pulldock.domain.download.repository.DownloadRepository
import com.pulldock.domain.download.repository.MediaLibraryRepository
import com.pulldock.domain.download.repository.MediaPreferencesRepository
import com.pulldock.domain.download.repository.NetworkPolicyRepository
import com.pulldock.domain.download.repository.StoragePreferencesRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds the domain persistence contracts to their data-layer implementations. */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindDownloadRepository(impl: DownloadRepositoryImpl): DownloadRepository

    @Binds
    @Singleton
    abstract fun bindQueueCoordinator(impl: QueueCoordinatorImpl): QueueCoordinator

    @Binds
    @Singleton
    abstract fun bindNetworkPolicyRepository(impl: NetworkPolicyRepositoryImpl): NetworkPolicyRepository

    @Binds
    @Singleton
    abstract fun bindConsentRepository(impl: ConsentRepositoryImpl): ConsentRepository

    @Binds
    @Singleton
    abstract fun bindAppearanceRepository(impl: AppearanceRepositoryImpl): AppearanceRepository

    @Binds
    @Singleton
    abstract fun bindMediaLibraryRepository(impl: MediaLibraryRepositoryImpl): MediaLibraryRepository

    @Binds
    @Singleton
    abstract fun bindStoragePreferencesRepository(impl: StoragePreferencesRepositoryImpl): StoragePreferencesRepository

    @Binds
    @Singleton
    abstract fun bindMediaPreferencesRepository(impl: MediaPreferencesRepositoryImpl): MediaPreferencesRepository
}
