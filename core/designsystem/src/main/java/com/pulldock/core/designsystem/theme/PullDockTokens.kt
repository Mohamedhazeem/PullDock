package com.pulldock.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Spacing scale shared by every screen and component. */
@Immutable
data class PdSpacing(
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 16.dp,
    val lg: Dp = 24.dp,
    val xl: Dp = 32.dp,
)

/** Corner radius scale. */
@Immutable
data class PdRadius(
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
)

/** Elevation scale for cards, sheets, and dialogs. */
@Immutable
data class PdElevation(
    val low: Dp = 2.dp,
    val medium: Dp = 6.dp,
    val high: Dp = 12.dp,
)

/**
 * Semantic status colors. Status is never conveyed by color alone (FR-069): pair these
 * with icons or text labels wherever they are used.
 */
@Immutable
data class PdStatusColors(
    val success: Color,
    val warning: Color,
    val info: Color,
)

/**
 * PullDock non-Material design tokens. Provided through [LocalPdTokens] by
 * [PullDockTheme]; components read tokens from the composition local instead of
 * hardcoding dimensions so light/dark and future density adjustments stay consistent.
 */
@Immutable
data class PdTokens(
    val spacing: PdSpacing,
    val radius: PdRadius,
    val elevation: PdElevation,
    val status: PdStatusColors,
)

/** Composition-local access to the active [PdTokens] set. */
val LocalPdTokens = staticCompositionLocalOf {
    PdTokens(
        spacing = PdSpacing(),
        radius = PdRadius(),
        elevation = PdElevation(),
        status = PdStatusColors(
            success = Color(0xFF2E7D32),
            warning = Color(0xFFB26A00),
            info = Color(0xFF005BA1),
        ),
    )
}

/** Status colors for the light theme. */
private fun lightStatusColors() = PdStatusColors(
    success = Color(0xFF226C3B),
    warning = Color(0xFF8C5300),
    info = Color(0xFF005BA1),
)

/** Status colors for the dark theme. */
private fun darkStatusColors() = PdStatusColors(
    success = Color(0xFF8BD69B),
    warning = Color(0xFFFFB871),
    info = Color(0xFF9FCBFF),
)

/** Builds the token set matching a [ColorScheme] variant. */
fun pullDockTokens(lightTheme: Boolean): PdTokens = PdTokens(
    spacing = PdSpacing(),
    radius = PdRadius(),
    elevation = PdElevation(),
    status = if (lightTheme) lightStatusColors() else darkStatusColors(),
)
