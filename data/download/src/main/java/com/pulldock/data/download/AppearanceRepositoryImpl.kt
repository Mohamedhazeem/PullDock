package com.pulldock.data.download

import com.pulldock.core.datastore.SettingsDataStore
import com.pulldock.core.datastore.ThemePreference
import com.pulldock.domain.download.repository.AppearanceRepository
import com.pulldock.domain.download.repository.AppearanceTheme
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Appearance contract implementation over the settings DataStore. */
@Singleton
class AppearanceRepositoryImpl @Inject constructor(
    private val settings: SettingsDataStore,
) : AppearanceRepository {

    override fun observeTheme(): Flow<AppearanceTheme> =
        settings.settings.map { it.theme.toDomain() }.distinctUntilChanged()

    override suspend fun setTheme(theme: AppearanceTheme) {
        settings.setTheme(theme.toStore())
    }
}

private fun ThemePreference.toDomain(): AppearanceTheme = when (this) {
    ThemePreference.SYSTEM -> AppearanceTheme.SYSTEM
    ThemePreference.LIGHT -> AppearanceTheme.LIGHT
    ThemePreference.DARK -> AppearanceTheme.DARK
}

private fun AppearanceTheme.toStore(): ThemePreference = when (this) {
    AppearanceTheme.SYSTEM -> ThemePreference.SYSTEM
    AppearanceTheme.LIGHT -> ThemePreference.LIGHT
    AppearanceTheme.DARK -> ThemePreference.DARK
}
