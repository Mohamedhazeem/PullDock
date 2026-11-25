package com.pulldock.core.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// PullDock "warm paper" palette. Material 3 roles carry the theme; semantic status
// colors live in PullDockTokens because they are app-level roles.

private val PaperLight = Color(0xFFF6F2EA)
private val SurfaceLight = Color(0xFFFCF9F3)
private val SurfaceLowLight = Color(0xFFF1ECE3)
private val SurfaceContainerLight = Color(0xFFEAE4DA)
private val SurfaceHighLight = Color(0xFFE2DBD0)
private val OnSurfaceLight = Color(0xFF1D1B18)
private val OnSurfaceVariantLight = Color(0xFF625C54)
private val OutlineLight = Color(0xFF8A8279)
private val OutlineVariantLight = Color(0xFFD8D0C4)

private val TerracottaLight = Color(0xFFB4512F)
private val OnTerracottaLight = Color(0xFFFFFFFF)
private val TerracottaContainerLight = Color(0xFFF7D9CC)
private val OnTerracottaContainerLight = Color(0xFF3A1408)

private val SageLight = Color(0xFF4E5D50)
private val OnSageLight = Color(0xFFFFFFFF)
private val SageContainerLight = Color(0xFFDCE6DD)
private val OnSageContainerLight = Color(0xFF142018)

private val OchreLight = Color(0xFF7A5A2E)
private val OnOchreLight = Color(0xFFFFFFFF)
private val OchreContainerLight = Color(0xFFF1E1C4)
private val OnOchreContainerLight = Color(0xFF2A1D08)

private val ErrorLight = Color(0xFFB3261E)
private val OnErrorLight = Color(0xFFFFFFFF)
private val ErrorContainerLight = Color(0xFFF9DEDC)
private val OnErrorContainerLight = Color(0xFF410E0B)

private val PaperDark = Color(0xFF12110F)
private val SurfaceDark = Color(0xFF1A1815)
private val SurfaceLowDark = Color(0xFF211E1A)
private val SurfaceContainerDark = Color(0xFF27231E)
private val SurfaceHighDark = Color(0xFF302B25)
private val OnSurfaceDark = Color(0xFFF2EDE5)
private val OnSurfaceVariantDark = Color(0xFFB9B1A6)
private val OutlineDark = Color(0xFF8E867B)
private val OutlineVariantDark = Color(0xFF3D3832)

private val TerracottaDark = Color(0xFFE99372)
private val OnTerracottaDark = Color(0xFF3A1408)
private val TerracottaContainerDark = Color(0xFF5C2A18)
private val OnTerracottaContainerDark = Color(0xFFF7D9CC)

private val SageDark = Color(0xFFB7C7B9)
private val OnSageDark = Color(0xFF1B2A1F)
private val SageContainerDark = Color(0xFF354438)
private val OnSageContainerDark = Color(0xFFDCE6DD)

private val OchreDark = Color(0xFFD9B77A)
private val OnOchreDark = Color(0xFF2F2410)
private val OchreContainerDark = Color(0xFF4A3A1D)
private val OnOchreContainerDark = Color(0xFFF1E1C4)

private val ErrorDark = Color(0xFFFFB4AB)
private val OnErrorDark = Color(0xFF690005)
private val ErrorContainerDark = Color(0xFF93000A)
private val OnErrorContainerDark = Color(0xFFFFDAD6)

/** PullDock light color scheme. */
val PullDockLightColorScheme = lightColorScheme(
    primary = TerracottaLight,
    onPrimary = OnTerracottaLight,
    primaryContainer = TerracottaContainerLight,
    onPrimaryContainer = OnTerracottaContainerLight,
    secondary = SageLight,
    onSecondary = OnSageLight,
    secondaryContainer = SageContainerLight,
    onSecondaryContainer = OnSageContainerLight,
    tertiary = OchreLight,
    onTertiary = OnOchreLight,
    tertiaryContainer = OchreContainerLight,
    onTertiaryContainer = OnOchreContainerLight,
    error = ErrorLight,
    onError = OnErrorLight,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight,
    background = PaperLight,
    onBackground = OnSurfaceLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceContainerLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    surfaceContainerLowest = SurfaceLight,
    surfaceContainerLow = SurfaceLowLight,
    surfaceContainer = SurfaceContainerLight,
    surfaceContainerHigh = SurfaceHighLight,
    surfaceContainerHighest = SurfaceHighLight,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
)

/** PullDock dark color scheme. */
val PullDockDarkColorScheme = darkColorScheme(
    primary = TerracottaDark,
    onPrimary = OnTerracottaDark,
    primaryContainer = TerracottaContainerDark,
    onPrimaryContainer = OnTerracottaContainerDark,
    secondary = SageDark,
    onSecondary = OnSageDark,
    secondaryContainer = SageContainerDark,
    onSecondaryContainer = OnSageContainerDark,
    tertiary = OchreDark,
    onTertiary = OnOchreDark,
    tertiaryContainer = OchreContainerDark,
    onTertiaryContainer = OnOchreContainerDark,
    error = ErrorDark,
    onError = OnErrorDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark,
    background = PaperDark,
    onBackground = OnSurfaceDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceContainerDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    surfaceContainerLowest = PaperDark,
    surfaceContainerLow = SurfaceLowDark,
    surfaceContainer = SurfaceContainerDark,
    surfaceContainerHigh = SurfaceHighDark,
    surfaceContainerHighest = SurfaceHighDark,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
)