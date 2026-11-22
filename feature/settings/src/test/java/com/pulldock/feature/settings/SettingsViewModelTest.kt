package com.pulldock.feature.settings

import com.pulldock.core.model.DownloadState
import com.pulldock.domain.download.model.DownloadPolicy
import com.pulldock.domain.download.repository.AppearanceRepository
import com.pulldock.domain.download.repository.AppearanceTheme
import com.pulldock.domain.download.repository.NetworkPolicyRepository
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Settings policy editing behavior (T054, US7). */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private class FakePolicyRepository : NetworkPolicyRepository {
        val flow = MutableStateFlow(DownloadPolicy())
        override fun observe(): Flow<DownloadPolicy> = flow
        override suspend fun current(): DownloadPolicy = flow.value
        override suspend fun update(policy: DownloadPolicy) {
            flow.value = policy
        }
    }

    private class FakeExtractor : com.pulldock.domain.extractor.MediaExtractor {
            override suspend fun resolve(url: String) = error("unused")
            override suspend fun availableFormats(descriptor: com.pulldock.domain.extractor.model.MediaDescriptor) = error("unused")
            override suspend fun download(descriptor: com.pulldock.domain.extractor.model.MediaDescriptor, format: com.pulldock.domain.extractor.model.MediaFormat) = error("unused")
            override fun observeEvents(taskId: String?) = kotlinx.coroutines.flow.MutableStateFlow<com.pulldock.domain.extractor.ExtractorEvent>(
                com.pulldock.domain.extractor.ExtractorEvent.StateChanged("x", DownloadState.QUEUED),
            )
            override fun cancel(taskId: String) = Unit
            override fun runtimeInfo(): com.pulldock.domain.extractor.model.ExtractorRuntimeInfo = com.pulldock.domain.extractor.model.ExtractorRuntimeInfo(
                component = "yt-dlp", installedVersion = "1", requiredRange = "", runtimeCompatible = false,
                channel = "stable", verificationStatus = com.pulldock.domain.extractor.model.RuntimeVerificationStatus.UNAVAILABLE,
                knownLimitations = emptyList(), checkedAt = Instant.EPOCH, inventoryId = "x",
            )
            override fun providerHealth(providerId: com.pulldock.core.model.ProviderId) = com.pulldock.domain.extractor.model.ProviderHealth(
                providerId, com.pulldock.domain.extractor.model.ProviderHealthState.RUNTIME_INCOMPATIBLE, null, null, null,
            )
            override suspend fun cancelAndAwait(taskId: String) = Unit
            override suspend fun close() = Unit
    }

    private fun stubProviderHealth() = com.pulldock.domain.extractor.usecase.ObserveProviderHealth(FakeExtractor())

    private class FakeAppearanceRepository : AppearanceRepository {
        val flow = MutableStateFlow(AppearanceTheme.SYSTEM)
        override fun observeTheme(): Flow<AppearanceTheme> = flow
        override suspend fun setTheme(theme: AppearanceTheme) {
            flow.value = theme
        }
    }

    @Test
    fun `policy edits persist through the repository`() = runTest(mainRule.testDispatcher) {
        val policyRepo = FakePolicyRepository()
        val appearanceRepo = FakeAppearanceRepository()
        val vm = SettingsViewModel(policyRepo, appearanceRepo, stubProviderHealth())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.setWifiOnly(true)
        advanceUntilIdle()
        assertTrue(policyRepo.flow.value.wifiOnly)

        vm.setBatteryThreshold(30)
        advanceUntilIdle()
        assertEquals(30, policyRepo.flow.value.batteryThresholdPercent)

        vm.setBatteryThreshold(200)
        advanceUntilIdle()
        assertEquals(100, policyRepo.flow.value.batteryThresholdPercent)
    }

    @Test
    fun `theme preference persists`() = runTest(mainRule.testDispatcher) {
        val policyRepo = FakePolicyRepository()
        val appearanceRepo = FakeAppearanceRepository()
        val vm = SettingsViewModel(policyRepo, appearanceRepo, stubProviderHealth())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.setTheme(AppearanceTheme.DARK)
        advanceUntilIdle()
        assertEquals(AppearanceTheme.DARK, appearanceRepo.flow.value)
        assertEquals(AppearanceTheme.DARK, vm.uiState.value.theme)
    }
}
