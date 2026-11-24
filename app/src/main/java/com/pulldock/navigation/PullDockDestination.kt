package com.pulldock.navigation

import androidx.annotation.StringRes
import com.pulldock.R

/**
 * Typed route definitions for PullDock's top-level destinations (plan.md §Navigation
 * architecture). Route strings are stable identifiers used by deep links
 * (`pulldock://downloads/{id}` in a later slice), so they must not be renamed casually.
 *
 * The onboarding/consent gate is contributed by Slice 2 (T049) when `ConsentStore`
 * exists; feature screens from `:feature:*` register their destinations as their
 * slices land. Only destinations the shell actually renders exist here — no
 * speculative routes.
 */
sealed interface PullDockDestination {

    /** Stable route identifier used by the NavHost and deep links. */
    val route: String

    /** Title resource for the destination. */
    @get:StringRes
    val titleRes: Int

    /** Top-level destinations reachable from the main navigation. */
    enum class Primary(
        override val route: String,
        @get:StringRes override val titleRes: Int,
    ) : PullDockDestination {
        HOME("home", R.string.destination_home),
        LIBRARY("library", R.string.destination_library),
        SETTINGS("settings", R.string.destination_settings),
        ;

        companion object {
            /** Display order of the main navigation destinations. */
            val navigationOrder: List<Primary> = entries
        }
    }
}
