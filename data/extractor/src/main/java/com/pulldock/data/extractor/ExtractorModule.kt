package com.pulldock.data.extractor

import com.pulldock.domain.extractor.MediaExtractor
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds the extractor engine contract to the provider-registry implementation. */
@Module
@InstallIn(SingletonComponent::class)
abstract class ExtractorModule {

    @Binds
    @Singleton
    abstract fun bindMediaExtractor(impl: MediaExtractorImpl): MediaExtractor
}
