package com.azkar.updates

import kotlinx.coroutines.delay

internal const val UPDATE_CHECK_INTERVAL_MILLIS = 24 * 60 * 60 * 1_000L

/** Runs once immediately, then waits a full interval between checks. Cancellation ends the loop. */
internal suspend fun runUpdateCheckSchedule(
    check: suspend () -> Unit,
    intervalMillis: Long = UPDATE_CHECK_INTERVAL_MILLIS,
    wait: suspend (Long) -> Unit = ::delay,
) {
    require(intervalMillis > 0)
    while (true) {
        check()
        wait(intervalMillis)
    }
}

/** Remembers update toasts for this app session, so recurring checks do not repeat the same release. */
internal class UpdateToastGate {
    private val toastedVersions = mutableSetOf<String>()

    fun shouldToast(version: String): Boolean = toastedVersions.add(version)
}
