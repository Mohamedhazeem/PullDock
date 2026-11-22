package com.pulldock.domain.download.repository

import kotlinx.coroutines.flow.Flow

/** Appearance preference values (kept domain-side so features never touch DataStore). */
enum class AppearanceTheme { SYSTEM, LIGHT, DARK }

/**
 * Appearance settings contract (T015/T047). The implementation delegates to
 * SettingsDataStore in core:datastore via the data layer.
 */
interface AppearanceRepository {
    fun observeTheme(): Flow<AppearanceTheme>
    suspend fun setTheme(theme: AppearanceTheme): Unit
}
