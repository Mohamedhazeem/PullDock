package com.pulldock.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Central migration registry (data-model.md §4.4). Every schema version change adds
 * one `Migration` here plus a test in `core/database/src/androidTest`. Destructive
 * fallback is never permitted — a failed migration surfaces as a typed failure.
 */
object Migrations {

    /** v2 adds the completed-media library table required by FR-042/FR-043. */
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `library_item` (
                    `id` TEXT NOT NULL PRIMARY KEY,
                    `fileUri` TEXT NOT NULL,
                    `type` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `sizeBytes` INTEGER,
                    `durationSeconds` INTEGER,
                    `source` TEXT,
                    `completedAt` INTEGER NOT NULL,
                    `thumbnailUri` TEXT,
                    `fileAvailable` INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_library_item_fileUri` ON `library_item` (`fileUri`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_library_item_type` ON `library_item` (`type`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_library_item_completedAt` ON `library_item` (`completedAt`)")
        }
    }

    /** v3 records the connected seed count alongside peers (US4 telemetry). */
    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `torrent_task` ADD COLUMN `seedCount` INTEGER NOT NULL DEFAULT 0")
        }
    }

    /** v4 stores the source hash so an already-downloaded file is never fetched twice. */
    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `library_item` ADD COLUMN `sourceHash` TEXT")
        }
    }

    /** v5 records the selected media resolution on tasks and library items. */
    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `download_task` ADD COLUMN `resolution` TEXT")
            db.execSQL("ALTER TABLE `library_item` ADD COLUMN `resolution` TEXT")
        }
    }

    /** v6 introduces grouped downloads: the group table and child `groupId` columns. */
    private val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `download_group` (
                    `id` TEXT NOT NULL PRIMARY KEY,
                    `type` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `source` TEXT,
                    `thumbnailUri` TEXT,
                    `createdAt` INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL("ALTER TABLE `download_task` ADD COLUMN `groupId` TEXT")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_download_task_groupId` ON `download_task` (`groupId`)")
            db.execSQL("ALTER TABLE `library_item` ADD COLUMN `groupId` TEXT")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_library_item_groupId` ON `library_item` (`groupId`)")
        }
    }

    /** All registered migrations, in declaration order. */
    val ALL: List<Migration> =
        listOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
}
