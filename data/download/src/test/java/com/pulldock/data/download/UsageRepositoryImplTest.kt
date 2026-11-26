package com.pulldock.data.download

import com.pulldock.core.database.dao.UsageDailyDao
import com.pulldock.core.database.entity.UsageDailyEntity
import com.pulldock.domain.download.model.UsageDelta
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Usage accumulation (read-modify-write) and 30-day retention. */
class UsageRepositoryImplTest {

    private class FakeDao : UsageDailyDao {
        val rows = LinkedHashMap<String, UsageDailyEntity>()
        val state = MutableStateFlow<List<UsageDailyEntity>>(emptyList())
        override suspend fun upsert(entity: UsageDailyEntity) {
            rows[entity.day] = entity
            state.value = rows.values.sortedBy { it.day }
        }
        override suspend fun get(day: String) = rows[day]
        override fun observeSince(sinceDay: String): Flow<List<UsageDailyEntity>> =
            state.map { list -> list.filter { it.day >= sinceDay } }
        override suspend fun deleteOlderThan(cutoffDay: String) {
            rows.keys.removeAll { it < cutoffDay }
            state.value = rows.values.sortedBy { it.day }
        }
    }

    @Test
    fun `addDelta accumulates into the current day`() = runTest {
        val dao = FakeDao()
        val repo = UsageRepositoryImpl(dao)
        val today = LocalDate.now().toString()

        repo.addDelta(UsageDelta(wifiRxBytes = 100, activeTransferMs = 1_000))
        repo.addDelta(UsageDelta(wifiRxBytes = 50, mobileTxBytes = 20, appOpenMs = 500))

        val row = dao.get(today)!!
        assertEquals(150, row.wifiRxBytes)
        assertEquals(20, row.mobileTxBytes)
        assertEquals(1_000, row.activeTransferMs)
        assertEquals(500, row.appOpenMs)
    }

    @Test
    fun `prune removes rows older than retention`() = runTest {
        val dao = FakeDao()
        val repo = UsageRepositoryImpl(dao)
        val old = LocalDate.now().minusDays(40).toString()
        dao.upsert(UsageDailyEntity(day = old, wifiRxBytes = 1))

        repo.prune(30)

        assertTrue(dao.rows.keys.none { it == old })
    }
}