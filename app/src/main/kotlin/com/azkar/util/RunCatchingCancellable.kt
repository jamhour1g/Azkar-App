package com.azkar.util

import kotlinx.coroutines.CancellationException

/** Captures failures while allowing coroutine cancellation to propagate. */
internal inline fun <T> runCatchingCancellable(block: () -> T): Result<T> =
    runCatching(block).onFailure { if (it is CancellationException) throw it }
