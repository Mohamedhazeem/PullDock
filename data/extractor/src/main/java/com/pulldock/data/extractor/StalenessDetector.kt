package com.pulldock.data.extractor

import com.pulldock.core.model.DownloadError
import com.pulldock.domain.extractor.model.ProviderHealthState

/**
 * Staleness detection (T128, FR-061): network errors are NEVER reported as staleness.
 * Only verified manifest signals or validated provider failures produce update /
 * platform-change states; a failed check is `VERSION_CHECK_UNAVAILABLE`.
 */
object StalenessDetector {

    /** Health state after a manifest check with the given outcome. */
    fun afterCheck(checkError: DownloadError?, manifestSaysUpdateRecommended: Boolean, manifestSaysUpdateRequired: Boolean): ProviderHealthState = when {
        checkError != null -> ProviderHealthState.VERSION_CHECK_UNAVAILABLE
        manifestSaysUpdateRequired -> ProviderHealthState.UPDATE_REQUIRED
        manifestSaysUpdateRecommended -> ProviderHealthState.UPDATE_RECOMMENDED
        else -> ProviderHealthState.CURRENT
    }

    /** Health state after a provider resolve attempt with the given typed error. */
    fun afterResolve(error: DownloadError?): ProviderHealthState = when (error) {
        null -> ProviderHealthState.CURRENT
        is DownloadError.Network -> ProviderHealthState.PROVIDER_TEMPORARILY_UNAVAILABLE
        is DownloadError.ExtractorEngine -> when (error.reason) {
            DownloadError.ExtractorReason.PROVIDER_CHANGED -> ProviderHealthState.PLATFORM_CHANGE_SUSPECTED
            DownloadError.ExtractorReason.AUTHORIZATION_REQUIRED -> ProviderHealthState.AUTHORIZATION_REQUIRED
            DownloadError.ExtractorReason.RATE_LIMITED -> ProviderHealthState.PROVIDER_TEMPORARILY_UNAVAILABLE
            DownloadError.ExtractorReason.RUNTIME_INCOMPATIBLE -> ProviderHealthState.RUNTIME_INCOMPATIBLE
            else -> ProviderHealthState.PLATFORM_CHANGE_SUSPECTED
        }
        else -> ProviderHealthState.PLATFORM_CHANGE_SUSPECTED
    }
}
