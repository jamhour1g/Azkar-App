package com.azkar

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * The dispatchers the suites hand to the code under test.
 *
 * They live in an `object` rather than as top-level `val`s because a top-level property has no
 * enclosing class, and detekt's `InjectDispatcher` only recognises a dispatcher that reaches the
 * code under test as a class property or a parameter — the same shape a test would use to swap in
 * its own scheduler.
 */
internal object TestDispatchers {
    /**
     * Runs work on the calling thread, so a test can assert on what a coroutine did without
     * pumping `runTest`'s virtual clock first.
     */
    val unconfined: CoroutineDispatcher = Dispatchers.Unconfined
}
