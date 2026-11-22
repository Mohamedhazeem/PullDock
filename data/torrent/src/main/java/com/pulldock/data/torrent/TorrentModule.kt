package com.pulldock.data.torrent

import com.pulldock.core.common.dispatchers.AppDispatchers
import com.pulldock.domain.torrent.TorrentEngine
import com.pulldock.engines.torrent.LibtorrentSession
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds the torrent engine contract and session ownership (ADR-0001). */
@Module
@InstallIn(SingletonComponent::class)
abstract class TorrentModule {

    @Binds
    @Singleton
    abstract fun bindTorrentEngine(impl: TorrentEngineImpl): TorrentEngine

    companion object {
        /** The engine module is pure Kotlin (no DI annotations) — provided here. */
        @Provides
        @Singleton
        fun provideLibtorrentSession(dispatchers: AppDispatchers): LibtorrentSession =
            LibtorrentSession(dispatchers)
    }
}
