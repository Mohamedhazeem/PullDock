package com.pulldock

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
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
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settingsDataStore: SettingsDataStore

    /** Persisted appearance preference; SYSTEM (default) until the user changes it. */
    private var themePreference by mutableStateOf(ThemePreference.SYSTEM)

    /** Source extracted from a share/view intent; consumed once by the add flow. */
    private var pendingSource by mutableStateOf<String?>(null)

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
            PullDockTheme(darkTheme = darkTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    RootNavHost(
                        useRail = false,
                        pendingSource = pendingSource,
                        onPendingSourceConsumed = { pendingSource = null },
                    )
                }
            }
        }
    }

    /** Incoming magnet/file intents while the activity is already running (singleTop). */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingSource = extractSource(intent)
    }

    /** Pulls a magnet, http(s) media URL, or torrent document URI out of an intent (FR-001/FR-002). */
    private fun extractSource(intent: Intent?): String? {
        val action = intent?.action ?: return null
        val data = intent.data?.toString()
        return when (action) {
            Intent.ACTION_SEND -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()
                listOfNotNull(text, data).firstOrNull(::isSupportedSource)
            }
            Intent.ACTION_VIEW -> data?.takeIf(::isSupportedSource)
            else -> null
        }
    }

    /** Only sources the add flow can actually classify are forwarded. */
    private fun isSupportedSource(value: String): Boolean = listOf(
        "magnet:", "http://", "https://", "content://", "file://",
    ).any { value.startsWith(it, ignoreCase = true) }

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
