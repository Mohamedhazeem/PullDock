package com.pulldock.di

import com.pulldock.domain.extractor.ExtractorRuntime
import com.pulldock.extractor.ChaquopyExtractorRuntime
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds the bundled Chaquopy runtime to the domain-facing extractor contract (ADR-0012). */
@Module
@InstallIn(SingletonComponent::class)
abstract class ExtractorRuntimeModule {
    @Binds
    @Singleton
    abstract fun bindExtractorRuntime(impl: ChaquopyExtractorRuntime): ExtractorRuntime
}
