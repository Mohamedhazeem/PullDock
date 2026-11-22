package com.pulldock.feature.onboarding

import com.pulldock.domain.download.model.SourceHashing
import com.pulldock.domain.download.repository.ConsentRepository
import com.pulldock.domain.download.repository.ConsentState
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Consent gate behavior (T054, US1): gate state, versioned re-prompt, acknowledgement. */
@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private class FakeConsentRepository : ConsentRepository {
        val flow = MutableStateFlow(
            ConsentState(acknowledgedVersion = null, requiredVersion = "2026-11-22.1"),
        )
        var acknowledgedVersion: String? = null

        override fun observe() = flow
        override suspend fun current(): ConsentState = flow.value
        override suspend fun acknowledge(version: String) {
            acknowledgedVersion = version
            flow.value = ConsentState(acknowledgedVersion = version, requiredVersion = "2026-11-22.1")
        }
    }

    @Test
    fun `fresh install shows unacknowledged state and acknowledge persists the version`() = runTest(mainRule.testDispatcher) {
        val repo = FakeConsentRepository()
        val vm = OnboardingViewModel(
            observeConsent = com.pulldock.domain.download.usecase.ObserveConsent(repo),
            acknowledgeConsent = com.pulldock.domain.download.usecase.AcknowledgeConsent(repo),
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.consent.collect {} }
        advanceUntilIdle()

        assertFalse(vm.consent.value?.isAcknowledged ?: true)

        vm.acknowledge()
        advanceUntilIdle()

        assertEquals("2026-11-22.1", repo.acknowledgedVersion)
        assertTrue(vm.consent.value?.isAcknowledged == true)
    }

    @Test
    fun `a version bump forces re-consent (US1 scenario 3)`() = runTest(mainRule.testDispatcher) {
        val repo = FakeConsentRepository()
        repo.flow.value = ConsentState(acknowledgedVersion = "2026-01-01.1", requiredVersion = "2026-11-22.1")
        val vm = OnboardingViewModel(
            observeConsent = com.pulldock.domain.download.usecase.ObserveConsent(repo),
            acknowledgeConsent = com.pulldock.domain.download.usecase.AcknowledgeConsent(repo),
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.consent.collect {} }
        advanceUntilIdle()

        assertFalse(vm.consent.value?.isAcknowledged ?: true)
        advanceUntilIdle()
    }

    @Test
    fun `double acknowledgement is a no-op while in flight`() = runTest(mainRule.testDispatcher) {
        val repo = FakeConsentRepository()
        val vm = OnboardingViewModel(
            observeConsent = com.pulldock.domain.download.usecase.ObserveConsent(repo),
            acknowledgeConsent = com.pulldock.domain.download.usecase.AcknowledgeConsent(repo),
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.consent.collect {} }
        advanceUntilIdle()

        vm.acknowledge()
        vm.acknowledge()
        advanceUntilIdle()

        assertEquals("2026-11-22.1", repo.acknowledgedVersion)
    }
}
