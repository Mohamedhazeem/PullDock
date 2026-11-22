package com.pulldock.data.download

import com.pulldock.core.datastore.NetworkPolicyStore
import com.pulldock.core.model.TransferLimits
import com.pulldock.domain.download.model.DownloadPolicy
import com.pulldock.domain.download.repository.NetworkPolicyRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** DataStore-backed policy contract implementation (US7). */
@Singleton
class NetworkPolicyRepositoryImpl @Inject constructor(
    private val store: NetworkPolicyStore,
) : NetworkPolicyRepository {

    override fun observe(): Flow<DownloadPolicy> =
        store.policy.map(::toPolicy).distinctUntilChanged()

    override suspend fun current(): DownloadPolicy = toPolicy(store.policy.first())

    override suspend fun update(policy: DownloadPolicy) {
        store.update(
            maxConcurrent = policy.maxConcurrent,
            torrentMaxConcurrent = policy.torrentMaxConcurrent,
            extractorMaxConcurrent = policy.extractorMaxConcurrent,
            maxRetries = policy.maxRetries,
            wifiOnly = policy.wifiOnly,
            meteredAllowed = policy.meteredAllowed,
            batteryThresholdPercent = policy.batteryThresholdPercent,
        )
    }

    private fun toPolicy(snapshot: com.pulldock.core.datastore.PolicySnapshot): DownloadPolicy {
        val defaults = DEFAULT
        return DownloadPolicy(
            maxConcurrent = snapshot.maxConcurrent,
            torrentMaxConcurrent = snapshot.torrentMaxConcurrent,
            extractorMaxConcurrent = snapshot.extractorMaxConcurrent,
            defaultLimits = TransferLimits.NONE,
            maxRetries = snapshot.maxRetries,
            initialBackoffSeconds = defaults.initialBackoffSeconds,
            maxBackoffSeconds = defaults.maxBackoffSeconds,
            backoffMultiplier = defaults.backoffMultiplier,
            wifiOnly = snapshot.wifiOnly,
            meteredAllowed = snapshot.meteredAllowed,
            batteryThresholdPercent = snapshot.batteryThresholdPercent,
        )
    }

    private companion object {
        val DEFAULT = DownloadPolicy()
    }
}
