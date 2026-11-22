package com.pulldock.data.extractor

import com.pulldock.core.model.DownloadError
import com.pulldock.core.model.ProviderId
import com.pulldock.domain.extractor.model.ProviderHealthState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Staleness and degradation semantics (T128/T129, FR-061/FR-062, US8 scenarios). */
class StalenessAndDegradationTest {

    @Test
    fun `network failure during a check is VERSION_CHECK_UNAVAILABLE, never stale`() {
        assertEquals(
            ProviderHealthState.VERSION_CHECK_UNAVAILABLE,
            StalenessDetector.afterCheck(
                checkError = DownloadError.Network(DownloadError.NetworkReason.OFFLINE),
                manifestSaysUpdateRecommended = false,
                manifestSaysUpdateRequired = false,
            ),
        )
    }

    @Test
    fun `manifest signals produce update states without a check error`() {
        assertEquals(
            ProviderHealthState.UPDATE_RECOMMENDED,
            StalenessDetector.afterCheck(null, manifestSaysUpdateRecommended = true, manifestSaysUpdateRequired = false),
        )
        assertEquals(
            ProviderHealthState.UPDATE_REQUIRED,
            StalenessDetector.afterCheck(null, manifestSaysUpdateRecommended = true, manifestSaysUpdateRequired = true),
        )
        assertEquals(
            ProviderHealthState.CURRENT,
            StalenessDetector.afterCheck(null, manifestSaysUpdateRecommended = false, manifestSaysUpdateRequired = false),
        )
    }

    @Test
    fun `provider resolve errors map to distinct health states`() {
        assertEquals(
            ProviderHealthState.PLATFORM_CHANGE_SUSPECTED,
            StalenessDetector.afterResolve(DownloadError.ExtractorEngine(DownloadError.ExtractorReason.PROVIDER_CHANGED)),
        )
        assertEquals(
            ProviderHealthState.AUTHORIZATION_REQUIRED,
            StalenessDetector.afterResolve(DownloadError.ExtractorEngine(DownloadError.ExtractorReason.AUTHORIZATION_REQUIRED)),
        )
        assertEquals(
            ProviderHealthState.PROVIDER_TEMPORARILY_UNAVAILABLE,
            StalenessDetector.afterResolve(DownloadError.Network(DownloadError.NetworkReason.OFFLINE)),
        )
    }

    @Test
    fun `degradation disables exactly one provider and re-enables on recovery`() {
        val degradation = ProviderDegradation()
        degradation.disable(ProviderId.YOUTUBE)

        assertFalse(degradation.isUsable(ProviderId.YOUTUBE))
        assertTrue(degradation.isUsable(ProviderId.INSTAGRAM)) // isolation (SC-007)
        assertEquals(
            ProviderHealthState.PROVIDER_UNSUPPORTED,
            degradation.healthState(ProviderId.YOUTUBE, ProviderHealthState.CURRENT),
        )

        degradation.enable(ProviderId.YOUTUBE)
        assertTrue(degradation.isUsable(ProviderId.YOUTUBE))
        assertTrue(degradation.snapshot().isEmpty())
    }
}
