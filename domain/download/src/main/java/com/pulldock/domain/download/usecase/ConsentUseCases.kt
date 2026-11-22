package com.pulldock.domain.download.usecase

import com.pulldock.domain.download.repository.ConsentRepository
import com.pulldock.domain.download.repository.ConsentState
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/**
 * Consent gate access (US1). Download features are unusable until the current legal
 * version is acknowledged; a version bump re-prompts (US1 scenario 3).
 */
class ObserveConsent @Inject constructor(
    private val repository: ConsentRepository,
) {
    operator fun invoke(): Flow<ConsentState> = repository.observe()
}

/** Persists the acknowledgement for the given legal version (US1 scenario 4). */
class AcknowledgeConsent @Inject constructor(
    private val repository: ConsentRepository,
) {
    suspend operator fun invoke(version: String) = repository.acknowledge(version)
}
