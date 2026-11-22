package com.pulldock

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.pulldock.core.datastore.SettingsDataStore
import com.pulldock.core.datastore.ThemePreference
import com.pulldock.core.designsystem.theme.PullDockTheme
import com.pulldock.navigation.RootNavHost
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * Single activity hosting the Compose shell. The activity owns no state that must
 * survive process death — durable state lives in Room/DataStore through the domain and
 * data layers. The appearance preference from [SettingsDataStore] drives the theme.
 */
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settingsDataStore: SettingsDataStore

    /** Persisted appearance preference; SYSTEM (default) until the user changes it. */
    private var themePreference by mutableStateOf(ThemePreference.SYSTEM)

    /** Source extracted from a share/view intent; consumed once by the add flow. */
    private var pendingSource: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingSource = extractSource(intent)
        observeThemePreference()
        setContent {
            val darkTheme = when (themePreference) {
                ThemePreference.SYSTEM -> isSystemInDarkTheme()
                ThemePreference.LIGHT -> false
                ThemePreference.DARK -> true
            }
            val windowSize = calculateWindowSizeClass(this)
            PullDockTheme(darkTheme = darkTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    RootNavHost(
                        useRail = windowSize.widthSizeClass >= WindowWidthSizeClass.Medium,
                    )
                }
            }
        }
    }

    /** Pulls a magnet URI or torrent document URI out of a share/view intent (FR-001). */
    private fun extractSource(intent: Intent?): String? = when (intent?.action) {
        Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()?.takeIf {
            it.startsWith("magnet:", ignoreCase = true)
        } ?: intent.data?.toString()
        Intent.ACTION_VIEW -> intent.data?.toString()
        else -> null
    }

    /** Observes settings only while visible (lifecycle-respectful collection). */
    private fun observeThemePreference() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                settingsDataStore.settings.collect { settings ->
                    themePreference = settings.theme
                }
            }
        }
    }
}
