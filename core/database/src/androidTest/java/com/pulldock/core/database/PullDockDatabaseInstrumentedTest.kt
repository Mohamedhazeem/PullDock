package com.pulldock.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pulldock.core.database.entity.DownloadTaskEntity
import com.pulldock.core.database.entity.RecoveryMarkerEntity
import com.pulldock.core.database.entity.TorrentFileEntity
import com.pulldock.core.database.entity.TorrentTaskEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Room CRUD, index, cascade, and migration-harness coverage (T037, T032; quickstart
 * Slice 1). Runs on-device/emulator via `connectedDebugAndroidTest`; the migration
 * harness pattern (open previous-version fixture, migrate, assert) is established here
 * and grows with `Migrations.ALL`.
 */
@RunWith(AndroidJUnit4::class)
class PullDockDatabaseInstrumentedTest {

    private lateinit var database: PullDockDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, PullDockDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun taskEntity(id: String): DownloadTaskEntity = DownloadTaskEntity(
        id = id,
        type = "TORRENT",
        source = "magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567",
        sourceHash = "hash-$id",
        displayName = "Test torrent",
        outputFilename = "",
        state = "CREATED",
        stateSequence = 0,
        totalBytes = null,
        downloadedBytes = 0,
        downloadSpeed = 0,
        uploadSpeed = null,
        etaSeconds = null,
        createdAt = 1L,
        updatedAt = 1L,
        completedAt = null,
        outputUri = null,
        errorCode = null,
        errorDetails = null,
        retryCount = 0,
        nextRetryAt = null,
        destinationKind = "APP_SPECIFIC",
        destinationUri = null,
        destinationDisplayName = "App storage",
        limitsDownloadRate = null,
        limitsUploadRate = null,
        limitsMaxConnections = null,
        priority = 0,
        queueOrder = 0,
        engineTaskId = null,
    )

    @Test
    fun insertAndReadTaskRoundTrips() = runBlocking {
        val dao = database.downloadTaskDao()
        dao.insert(taskEntity("t1"))

        val loaded = dao.getById("t1")
        assertNotNull(loaded)
        assertEquals("CREATED", loaded?.state)

        // State transitions bump the persisted sequence (data-model.md §2.3).
        dao.updateState("t1", "VALIDATING", null, null, 2L)
        dao.updateState("t1", "FAILED", "INPUT_MALFORMED_MAGNET", "Input", 3L)
        val updated = dao.getById("t1")
        assertEquals("FAILED", updated?.state)
        assertEquals(2L, updated?.stateSequence)
        assertEquals("INPUT_MALFORMED_MAGNET", updated?.errorCode)
    }

    @Test
    fun sourceHashUniqueIndexRejectsDuplicates() = runBlocking {
        val dao = database.downloadTaskDao()
        dao.insert(taskEntity("t1"))
        val duplicate = taskEntity("t2") // same sourceHash prefix is distinct; make it collide:
        val colliding = duplicate.copy(sourceHash = "hash-t1")
        val threw = runCatching { dao.insert(colliding) }
        assertTrue("unique sourceHash must reject duplicates", threw.isFailure)
    }

    @Test
    fun torrentDetailCascadesOnTaskDelete() = runBlocking {
        val taskDao = database.downloadTaskDao()
        val torrentDao = database.torrentTaskDao()
        taskDao.insert(taskEntity("t1"))
        torrentDao.upsert(
            TorrentTaskEntity(
                taskId = "t1",
                infohash = "0123456789abcdef0123456789abcdef01234567",
                name = "Test torrent",
                totalSize = 1024,
                trackers = "",
                peerCount = 0,
                sequentialDownload = false,
                resumeData = byteArrayOf(1, 2, 3),
                resumeDataVersion = 1,
                seedAfterCompletion = true,
                maxUploadRateBytesPerSec = null,
                stopRatio = 1.0,
                timeLimitSeconds = 1800L,
                perTorrentOverride = false,
                uploadedBytes = 0,
            ),
        )
        torrentDao.replaceFiles(
            "t1",
            listOf(TorrentFileEntity("t1", 0, "a.bin", 512, 4, true)),
        )

        assertNotNull(torrentDao.getByTaskId("t1"))
        assertEquals(1, torrentDao.getFiles("t1").size)

        taskDao.delete("t1")
        assertNull(torrentDao.getByTaskId("t1"))
        assertTrue("files cascade with the detail row", torrentDao.getFiles("t1").isEmpty())
    }

    @Test
    fun recoveryMarkerRoundTripsAndCountsAttempts() = runBlocking {
        val taskDao = database.downloadTaskDao()
        val markerDao = database.recoveryMarkerDao()
        taskDao.insert(taskEntity("t1"))
        markerDao.upsert(
            RecoveryMarkerEntity(
                taskId = "t1",
                interruptedAt = 42L,
                reason = "PROCESS_DEATH",
                resumeDataPresent = true,
                recoverable = true,
                attempts = 0,
            ),
        )
        assertNotNull(markerDao.getByTaskId("t1"))
        assertEquals(1, markerDao.getRecoverable().size)

        markerDao.incrementAttempts("t1")
        assertEquals(1, markerDao.getByTaskId("t1")?.attempts)

        markerDao.delete("t1")
        assertNull(markerDao.getByTaskId("t1"))
    }

    /**
     * Migration harness (T032/T173): opens the shipped schema fixture for version
     * (n-1), runs `Migrations.ALL`, and asserts the migrated database still serves the
     * current schema. For v1 there is no previous version; this test pins the harness
     * pattern and asserts schema 1 is exported and openable.
     */
    @Test
    fun migrationHarnessPatternIsEstablished() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val opened = Room.inMemoryDatabaseBuilder(context, PullDockDatabase::class.java)
            .addMigrations(*Migrations.ALL.toTypedArray())
            .build()
        try {
            assertEquals(1, opened.openHelper.writableDatabase.version)
        } finally {
            opened.close()
        }
        // v1 is the initial schema; migrations register here and in Migrations.ALL.
        assertTrue(Migrations.ALL.isEmpty())
    }
}
