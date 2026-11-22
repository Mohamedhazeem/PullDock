package com.pulldock.domain.download.usecase

import com.pulldock.core.model.DownloadError

/**
 * Typed outcome for queue-facing use cases. Failure always carries a [DownloadError];
 * success never fakes data (constitution XI: failure is not hidden behind defaults).
 */
sealed interface UseCaseResult<out T> {
    data class Success<T>(val value: T) : UseCaseResult<T>
    data class Failure(val error: DownloadError) : UseCaseResult<Nothing>

    companion object {
        /** Maps an optional value into a typed failure when absent. */
        fun <T> of(value: T?, error: () -> DownloadError): UseCaseResult<T> =
            if (value != null) Success(value) else Failure(error())
    }
}
