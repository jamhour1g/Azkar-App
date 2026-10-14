package com.azkar.settings

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.deleteIfExists

/**
 * Runs [block] against a fresh settings file and a scope, and takes both away afterwards.
 *
 * One fixture rather than four. `ThemePreferenceStoreSuite`, `LocaleStoreSuite`,
 * `NotificationSettingsSuite` and `NotificationPreferencesRepositorySuite` each grew their own copy of
 * this while working on different bugs, and each copy had the same defect: teardown that cancelled
 * the scope and *then* deleted the directory, in that order. `cancel()` returns immediately and the
 * store's IO carries on for a moment, so the delete was unlinking a file out from under a reader
 * that had not finished. The copy in `ScreenSuite` already worked this out — it hangs the deletion
 * off `invokeOnCompletion` — and that is the version every DataStore suite now shares, because
 * four subtly different teardowns is four chances to reintroduce the same flake.
 *
 * The scope is a real one on [Dispatchers.IO] rather than a `runTest` one. The value under test
 * arrives through file IO, and virtual time does not advance it; the same reason
 * `TestScope.backgroundScope` exists, arrived at without it.
 */
inline fun withSettingsFile(block: (Path, CoroutineScope) -> Unit) {
    val directory = Files.createTempDirectory("azkar-settings")
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    try {
        block(directory.resolve(SETTINGS_FILE_NAME), scope)
    } finally {
        scope.deleteDirectoryWhenIdle(directory)
        scope.cancel()
    }
}

/**
 * Runs [block] against a [SettingsStore] over a [FakePreferencesDataStore] and a scope.
 *
 * The counterpart to [withSettingsFile], and the answer for everything that is not "did this reach
 * the disk". Nothing here touches the filesystem, so teardown is a bare [cancel] — there is no
 * directory to unlink and therefore nothing to race.
 *
 * [Dispatchers.Unconfined] because the point is that [SettingsStore.write] lands before it returns,
 * letting a test assert on the result of a toggle instead of polling for it. A store whose writes
 * were merely eventual would hide every mistake this fixture is meant to expose: a switch that moves
 * the state but never writes would still look right after a wait.
 *
 * The scope is handed out rather than kept because the state under test takes one — a test that is
 * told the file is in memory but is then handed the store alone cannot construct the thing it is
 * actually testing.
 */
inline fun withInMemorySettings(block: (CoroutineScope, SettingsStore, FakePreferencesDataStore) -> Unit) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    val dataStore = FakePreferencesDataStore()
    try {
        block(scope, SettingsStore(dataStore, scope), dataStore)
    } finally {
        scope.cancel()
    }
}

/**
 * A copy of [file] for a store that stands in for the next run of the app.
 *
 * DataStore allows one instance per file per process, so a round trip cannot be asserted by opening
 * a second store over the same path — the second one throws rather than reading. A copy parses what
 * was actually written to disk, which is the thing a second launch would read.
 *
 * [REPLACE_EXISTING] because a test is allowed to open the next launch twice, and the plain
 * `Files.copy` this replaced threw `FileAlreadyExistsException` rather than doing the obvious thing.
 */
fun copyForNextLaunch(file: Path): Path =
    file
        .resolveSibling("next-launch.preferences_pb")
        .also { copy -> Files.copy(file, copy, StandardCopyOption.REPLACE_EXISTING) }

/**
 * Removes [directory] once this scope's work has actually stopped.
 *
 * Cancelling a scope does not wait for it, so deleting straight after `cancel()` is a race that
 * mostly wins — and the losing version is a `SettingsStore` still reading the file it is being
 * deleted from, which answers a read failure by emitting an empty snapshot into Compose state on a
 * thread that is not composing. That is the intermittent "Unsupported concurrent change during
 * composition" `ScreenSuite` used to fail with.
 */
fun CoroutineScope.deleteDirectoryWhenIdle(directory: Path) {
    coroutineContext.job.invokeOnCompletion {
        runCatching { Files.list(directory).forEach { it.deleteIfExists() } }
        runCatching { directory.deleteIfExists() }
    }
}

// `@PublishedApi` because [withSettingsFile] is inline and reads this; a private one would make
// the whole fixture uncallable from another module.
@PublishedApi
internal const val SETTINGS_FILE_NAME = "settings.preferences_pb"
