package com.azkar.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A [DataStore] over a [MutableStateFlow], for the tests that are about behaviour rather than bytes.
 *
 * Every settings suite used to reach for a real file, and each one then had to carry the same three
 * things with it: a temporary directory, a `CoroutineScope` on [kotlinx.coroutines.Dispatchers.IO],
 * and a teardown that cancels that scope and deletes the directory without racing each other. That
 * machinery is the right answer for one question — *did this reach the disk* — and the wrong answer
 * for every other question, which is why the four suites that shared it also shared a set of bugs:
 * a busy-poll for the write to land, a `finally` that could unlink a file out from under a live
 * store, and no way at all to reach a store that fails.
 *
 * This answers those other questions. A write commits synchronously, so a test that toggles a switch
 * asserts on the result rather than waiting for it; a test that wants the write to be visible on a
 * real file still uses `SettingsStore.create`, which is a different fixture for a different
 * question.
 *
 * The failure switches are the reason this exists rather than a `PreferencesStore` double. Mocking
 * `DataStore` is genuinely unpleasant — `edit` is a lambda, so verifying it means reimplementing it
 * — and a test that wants "the read failed" has no way to ask a real file to fail. Setting
 * [readFailure] is the whole point.
 */
class FakePreferencesDataStore(
    initial: Preferences = emptyPreferences(),
) : DataStore<Preferences> {
    private val snapshots = MutableStateFlow(initial)

    /**
     * DataStore serialises `updateData`, so two concurrent `edit` calls interleave as read-modify-write
     * pairs rather than losing one. A test asserting that a multi-key write lands whole would catch a
     * fake that quietly let the second call start from a stale snapshot.
     */
    private val commits = Mutex()

    /**
     * Thrown from [data] while set; `null`, the default, means the store reads normally.
     *
     * Read at collection time rather than when the flow is built, so a test can let a read fail and
     * then let it succeed again without rebuilding anything. An [java.io.IOException] is what the
     * store's read path degrades on; anything else is what it must *not* degrade on, and a suite that
     * pins both halves is the only way that distinction stays written down.
     */
    var readFailure: Throwable? = null

    /** Thrown from [updateData] while set, which is what a write that could not be committed looks like. */
    var writeFailure: Throwable? = null

    /**
     * How many more writes fail with [writeFailure] before it stops failing.
     *
     * [Int.MAX_VALUE] — the default — means a write fails for as long as [writeFailure] is set: a
     * disk that is broken, which is what most of the suites that use this need. A smaller number
     * models a failure that heals on its own: the first attempt runs into a handle that is still
     * open — a virus scanner reading the settings file is the usual one on Windows — and the next
     * attempt lands.
     */
    var writeFailuresRemaining: Int = Int.MAX_VALUE

    /**
     * How many writes have been attempted, whatever their outcome.
     *
     * This is what makes the retry policy observable. A store that swallows a failure says
     * nothing about how hard it tried, so without a count a test cannot tell a write that was
     * retried and landed from one that was retried and gave up.
     */
    var writeAttempts: Int = 0

    /** The snapshot a write has committed so far, without going through [data] and its failure switch. */
    val stored: Preferences get() = snapshots.value

    override val data: Flow<Preferences> =
        flow {
            readFailure?.let { failure -> throw failure }
            emitAll(snapshots)
        }

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        commits.withLock {
            writeAttempts += 1
            writeFailure?.let { failure ->
                if (writeFailuresRemaining > 0) {
                    writeFailuresRemaining -= 1
                    throw failure
                }
            }
            transform(snapshots.value).also { committed -> snapshots.value = committed }
        }
}
