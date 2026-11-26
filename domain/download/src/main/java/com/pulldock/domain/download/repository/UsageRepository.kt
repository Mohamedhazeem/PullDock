package com.pulldock.domain.download.repository

import com.pulldock.domain.download.model.UsageDaily
import com.pulldock.domain.download.model.UsageDelta
import kotlinx.coroutines.flow.Flow

/** Per-day usage history (data usage + running time), retained for 30 days. */
interface UsageRepository {

    /** Observes the last [days] daily buckets (oldest first), including zero-filled gaps. */
    fun observe(days: Int): Flow<List<UsageDaily>>

    /** Accumulates [delta] into the current local day. */
    suspend fun addDelta(delta: UsageDelta)

    /** Deletes rows older than [days] (default 30). */
    suspend fun prune(days: Int = 30)
}