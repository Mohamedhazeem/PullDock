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
    val xxl: Dp = 48.dp,
)

/** Corner radius scale. */
@Immutable
data class PdRadius(
    val sm: Dp = 10.dp,
    val md: Dp = 14.dp,
    val lg: Dp = 20.dp,
    val xl: Dp = 28.dp,
)

/** Elevation scale for cards, sheets, and dialogs. */
@Immutable
data class PdElevation(
    val low: Dp = 0.dp,
    val medium: Dp = 2.dp,
    val high: Dp = 6.dp,
)

/** Motion durations. Keep transitions short and purposeful. */
@Immutable
data class PdMotion(
    val fast: Int = 120,
    val base: Int = 200,
    val slow: Int = 320,
    val emphasized: Int = 450,
)

/** Responsive layout constants. */
@Immutable
data class PdLayout(
    val maxContentWidth: Dp = 840.dp,
    val cardMinWidth: Dp = 320.dp,
    val gridMinWidth: Dp = 360.dp,
)

/**
 * Semantic status colors. Status is never conveyed by color alone: pair these with
 * icons or text labels wherever they are used.
 */
@Immutable
data class PdStatusColors(
    val success: Color,
    val warning: Color,
    val info: Color,
    val neutral: Color,
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
    val motion: PdMotion,
    val layout: PdLayout,
    val status: PdStatusColors,
)

/** Composition-local access to the active [PdTokens] set. */
val LocalPdTokens = staticCompositionLocalOf {
    PdTokens(
        spacing = PdSpacing(),
        radius = PdRadius(),
        elevation = PdElevation(),
        motion = PdMotion(),
        layout = PdLayout(),
        status = PdStatusColors(
            success = Color(0xFF226C3B),
            warning = Color(0xFF8C5300),
            info = Color(0xFF005BA1),
            neutral = Color(0xFF625C54),
        ),
    )
}

private fun lightStatusColors() = PdStatusColors(
    success = Color(0xFF226C3B),
    warning = Color(0xFF8C5300),
    info = Color(0xFF005BA1),
    neutral = Color(0xFF625C54),
)

private fun darkStatusColors() = PdStatusColors(
    success = Color(0xFF8BD69B),
    warning = Color(0xFFFFB871),
    info = Color(0xFF9FCBFF),
    neutral = Color(0xFFB9B1A6),
)

/** Builds the token set matching a [ColorScheme] variant. */
fun pullDockTokens(lightTheme: Boolean): PdTokens = PdTokens(
    spacing = PdSpacing(),
    radius = PdRadius(),
    elevation = PdElevation(),
    motion = PdMotion(),
    layout = PdLayout(),
    status = if (lightTheme) lightStatusColors() else darkStatusColors(),
)