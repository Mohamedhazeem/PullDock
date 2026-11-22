package com.pulldock.data.download

import com.pulldock.domain.download.queue.QueueCoordinator
import com.pulldock.domain.download.repository.ConsentRepository
import com.pulldock.domain.download.repository.DownloadRepository
import com.pulldock.domain.download.repository.NetworkPolicyRepository
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
}
