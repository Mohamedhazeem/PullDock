package com.pulldock.data.download

import com.pulldock.core.database.dao.UsageDailyDao
import com.pulldock.core.database.entity.UsageDailyEntity
import com.pulldock.domain.download.model.UsageDaily
import com.pulldock.domain.download.model.UsageDelta
import com.pulldock.domain.download.repository.UsageRepository
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Room-backed per-day usage history with zero-filled gaps and 30-day retention. */
@Singleton
class UsageRepositoryImpl @Inject constructor(
    private val dao: UsageDailyDao,
) : UsageRepository {

    private var lastPruneDay: LocalDate? = null
    private val writeMutex = Mutex()

    override fun observe(days: Int): Flow<List<UsageDaily>> {
        val since = LocalDate.now().minusDays((days - 1).coerceAtLeast(0).toLong())
        return dao.observeSince(since.format(FORMATTER)).map { rows ->
            val byDay = rows.associateBy { runCatching { LocalDate.parse(it.day, FORMATTER) }.getOrNull() }
            generateSequence(since) { it.plusDays(1L) }
                .takeWhile { !it.isAfter(LocalDate.now()) }
                .map { day -> byDay[day]?.toDomain(day) ?: zero(day) }
                .toList()
        }
    }

    override suspend fun addDelta(delta: UsageDelta) {
        val day = LocalDate.now()
        val key = day.format(FORMATTER)
        writeMutex.withLock {
            val existing = dao.get(key)
            val merged = if (existing == null) {
                UsageDailyEntity(day = key)
            } else {
                existing
            }
            dao.upsert(
                merged.copy(
                    wifiRxBytes = merged.wifiRxBytes + delta.wifiRxBytes,
                    wifiTxBytes = merged.wifiTxBytes + delta.wifiTxBytes,
                    mobileRxBytes = merged.mobileRxBytes + delta.mobileRxBytes,
                    mobileTxBytes = merged.mobileTxBytes + delta.mobileTxBytes,
                    activeTransferMs = merged.activeTransferMs + delta.activeTransferMs,
                    appOpenMs = merged.appOpenMs + delta.appOpenMs,
                ),
            )
        }
        pruneIfNewDay(day)
    }

    override suspend fun prune(days: Int) {
        val cutoff = LocalDate.now().minusDays(days.toLong())
        dao.deleteOlderThan(cutoff.format(FORMATTER))
        lastPruneDay = LocalDate.now()
    }

    private suspend fun pruneIfNewDay(day: LocalDate) {
        if (lastPruneDay == day) return
        dao.deleteOlderThan(day.minusDays(RETENTION_DAYS.toLong()).format(FORMATTER))
        lastPruneDay = day
    }

    private fun UsageDailyEntity.toDomain(day: LocalDate) = UsageDaily(
        day = day,
        wifiRxBytes = wifiRxBytes,
        wifiTxBytes = wifiTxBytes,
        mobileRxBytes = mobileRxBytes,
        mobileTxBytes = mobileTxBytes,
        activeTransferMs = activeTransferMs,
        appOpenMs = appOpenMs,
    )

    private fun zero(day: LocalDate) = UsageDaily(day, 0, 0, 0, 0, 0, 0)

    private companion object {
        val FORMATTER: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
        const val RETENTION_DAYS = 30
    }
}