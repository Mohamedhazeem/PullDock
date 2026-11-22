package com.pulldock.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext

/**
 * PullDock application theme (FR-065). Supplies the Material 3 color scheme, the shared
 * typography scale, and [LocalPdTokens] design tokens in one place so every screen and
 * component inherits a consistent look in light and dark.
 *
 * @param darkTheme whether the dark scheme is used; defaults to the system setting.
 * @param dynamicColor whether to use Android 12+ dynamic color; off by default to keep
 *   the PullDock brand consistent, since dynamic color would bypass the token palette.
 */
@Composable
fun PullDockTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> PullDockDarkColorScheme
        else -> PullDockLightColorScheme
    }

    CompositionLocalProvider(LocalPdTokens provides pullDockTokens(lightTheme = !darkTheme)) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = PullDockTypography,
            content = content,
        )
    }
}
