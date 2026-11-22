package com.pulldock.core.database

import androidx.room.RoomDatabase

/**
 * PullDock's durable source of application state (constitution VII).
 *
 * Slice 0 ships the skeleton: the module, KSP/Room configuration, and this class are in
 * place, but the `@Database` annotation cannot be applied yet — Room's processor
 * requires at least one entity, and the download/torrent/extractor/recovery entities
 * arrive with Slice 1 (T029). T031 applies `@Database(entities = [...], version = 1,
 * exportSchema = true)`, after which schema exports land under `schemas/` and feed the
 * mandatory migration test harness (T032). Destructive migration fallback is forbidden:
 * failed migrations must mark tasks `NEEDS_USER_ACTION`, never delete data.
 */
abstract class PullDockDatabase : RoomDatabase() {

    companion object {
        const val DATABASE_NAME: String = "pulldock.db"
    }
}
