package com.pulldock.data.download

import com.pulldock.core.datastore.ConsentStore
import com.pulldock.domain.download.repository.ConsentRepository
import com.pulldock.domain.download.repository.ConsentState
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Consent contract implementation (US1). The required legal version is bundled at
 * build time; a mismatch with the stored acknowledgement forces re-consent.
 */
@Singleton
class ConsentRepositoryImpl @Inject constructor(
    private val store: ConsentStore,
) : ConsentRepository {

    override fun observe(): Flow<ConsentState> = store.acknowledgedVersion.map { version ->
        ConsentState(acknowledgedVersion = version, requiredVersion = REQUIRED_VERSION)
    }.distinctUntilChanged()

    override suspend fun current(): ConsentState = ConsentState(
        acknowledgedVersion = store.acknowledgedVersion.first(),
        requiredVersion = REQUIRED_VERSION,
    )

    override suspend fun acknowledge(version: String) {
        store.acknowledge(version)
    }

    private companion object {
        /** Bundled legal/consent text version (US1 scenario 3: bump forces re-prompt). */
        const val REQUIRED_VERSION = "2026-11-22.1"
    }
}
