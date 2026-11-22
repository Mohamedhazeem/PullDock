package com.pulldock.core.database

import androidx.room.migration.Migration

/**
 * Central migration registry (data-model.md §4.4). Every schema version change adds
 * one `Migration` here plus a test in `core/database/src/androidTest` that opens the
 * previous version's schema fixture. v1 is the initial schema, so the list starts
 * empty; it must never be bypassed with `fallbackToDestructiveMigration`.
 */
object Migrations {

    /** All registered migrations, in order. Empty for v1 (initial schema). */
    val ALL: List<Migration> = emptyList()
}
