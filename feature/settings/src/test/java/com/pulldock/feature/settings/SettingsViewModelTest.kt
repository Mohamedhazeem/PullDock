package com.pulldock.feature.settings

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
        val vm = SettingsViewModel(policyRepo, appearanceRepo)
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
        val vm = SettingsViewModel(policyRepo, appearanceRepo)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.setTheme(AppearanceTheme.DARK)
        advanceUntilIdle()
        assertEquals(AppearanceTheme.DARK, appearanceRepo.flow.value)
        assertEquals(AppearanceTheme.DARK, vm.uiState.value.theme)
    }
}
