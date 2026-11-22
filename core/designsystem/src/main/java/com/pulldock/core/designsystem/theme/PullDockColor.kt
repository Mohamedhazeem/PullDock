package com.pulldock.core.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// PullDock palette — "berth" blues with warm mooring accents. Tonal roles follow
// Material 3; status colors are defined in PullDockTokens.kt because they are app-level
// semantic roles (success/warning/info), not Material scheme slots.

private val HarborBluePrimaryLight = Color(0xFF00639B)
private val HarborBlueOnPrimaryLight = Color(0xFFFFFFFF)
private val HarborBluePrimaryContainerLight = Color(0xFFCFE5FF)
private val HarborBlueOnPrimaryContainerLight = Color(0xFF001D33)

private val DockSteelSecondaryLight = Color(0xFF4F6071)
private val DockSteelOnSecondaryLight = Color(0xFFFFFFFF)
private val DockSteelSecondaryContainerLight = Color(0xFFD3E5F7)
private val DockSteelOnSecondaryContainerLight = Color(0xFF0B1D2B)

private val MooringTertiaryLight = Color(0xFF695779)
private val MooringOnTertiaryLight = Color(0xFFFFFFFF)
private val MooringTertiaryContainerLight = Color(0xFFF1DAFF)
private val MooringOnTertiaryContainerLight = Color(0xFF231533)

private val SurfaceLight = Color(0xF7F9FC)
private val OnSurfaceLight = Color(0xFF191C20)
private val SurfaceVariantLight = Color(0xFFDEE3EB)
private val OnSurfaceVariantLight = Color(0xFF41484F)
private val OutlineLight = Color(0xFF71787F)
private val OutlineVariantLight = Color(0xFFC1C7D0)

private val HarborBluePrimaryDark = Color(0xFF97CBFF)
private val HarborBlueOnPrimaryDark = Color(0xFF003354)
private val HarborBluePrimaryContainerDark = Color(0xFF004A78)
private val HarborBlueOnPrimaryContainerDark = Color(0xFFCFE5FF)

private val DockSteelSecondaryDark = Color(0xFFB7C9DB)
private val DockSteelOnSecondaryDark = Color(0xFF223340)
private val DockSteelSecondaryContainerDark = Color(0xFF38495A)
private val DockSteelOnSecondaryContainerDark = Color(0xFFD3E5F7)

private val MooringTertiaryDark = Color(0xFFD5BEE4)
private val MooringOnTertiaryDark = Color(0xFF392A49)
private val MooringTertiaryContainerDark = Color(0xFF504061)
private val MooringOnTertiaryContainerDark = Color(0xFFF1DAFF)

private val SurfaceDark = Color(0xFF111417)
private val OnSurfaceDark = Color(0xFFE1E2E6)
private val SurfaceVariantDark = Color(0xFF41474F)
private val OnSurfaceVariantDark = Color(0xFFC1C7D0)
private val OutlineDark = Color(0xFF8B9199)
private val OutlineVariantDark = Color(0xFF41474F)

/** PullDock light color scheme (FR-065: consistent design system, light and dark). */
val PullDockLightColorScheme = lightColorScheme(
    primary = HarborBluePrimaryLight,
    onPrimary = HarborBlueOnPrimaryLight,
    primaryContainer = HarborBluePrimaryContainerLight,
    onPrimaryContainer = HarborBlueOnPrimaryContainerLight,
    secondary = DockSteelSecondaryLight,
    onSecondary = DockSteelOnSecondaryLight,
    secondaryContainer = DockSteelSecondaryContainerLight,
    onSecondaryContainer = DockSteelOnSecondaryContainerLight,
    tertiary = MooringTertiaryLight,
    onTertiary = MooringOnTertiaryLight,
    tertiaryContainer = MooringTertiaryContainerLight,
    onTertiaryContainer = MooringOnTertiaryContainerLight,
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFAFAFD),
    onBackground = OnSurfaceLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
)

/** PullDock dark color scheme. */
val PullDockDarkColorScheme = darkColorScheme(
    primary = HarborBluePrimaryDark,
    onPrimary = HarborBlueOnPrimaryDark,
    primaryContainer = HarborBluePrimaryContainerDark,
    onPrimaryContainer = HarborBlueOnPrimaryContainerDark,
    secondary = DockSteelSecondaryDark,
    onSecondary = DockSteelOnSecondaryDark,
    secondaryContainer = DockSteelSecondaryContainerDark,
    onSecondaryContainer = DockSteelOnSecondaryContainerDark,
    tertiary = MooringTertiaryDark,
    onTertiary = MooringOnTertiaryDark,
    tertiaryContainer = MooringTertiaryContainerDark,
    onTertiaryContainer = MooringOnTertiaryContainerDark,
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF111417),
    onBackground = OnSurfaceDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
)
