package com.pulldock.core.common.dispatchers

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject

/**
 * Application-wide access to the coroutine dispatchers, so callers never bind to
 * [Dispatchers] directly and tests can inject deterministic replacements.
 *
 * Threading rules enforced across PullDock (see `specs/001-production-android-app/plan.md`
 * §Performance Design): file, network, engine, and process work runs on [io]; CPU-bound
 * mapping and computation on [default]; UI-adjacent work on [main]. Never block a
 * dispatcher with blocking I/O; never use `GlobalScope`.
 */
interface AppDispatchers {

    /** Disk and network I/O, engine calls, and process work. */
    val io: CoroutineDispatcher

    /** CPU-bound work such as mapping, hashing, and parsing. */
    val default: CoroutineDispatcher

    /** Main/UI thread. Never used for blocking or long-running work. */
    val main: CoroutineDispatcher
}

/**
 * Production binding. Constructed through `@Inject` so Hilt wires it wherever
 * [AppDispatchers] is requested; tests replace it with their own implementation.
 */
class DefaultAppDispatchers @Inject constructor() : AppDispatchers {
    override val io: CoroutineDispatcher = Dispatchers.IO
    override val default: CoroutineDispatcher = Dispatchers.Default
    override val main: CoroutineDispatcher = Dispatchers.Main
}
