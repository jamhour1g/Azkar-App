package com.azkar.settings

import androidx.compose.runtime.Stable
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/**
 * The logger this file's own code writes to.
 *
 * A property of the file rather than an inline default on each declaration, because two
 * declarations here log — the class and its companion — and a logger reached by name is a
 * singleton. Two inline defaults would be two loggers under two names, and a test that
 * captured one of them would never see the other's lines. One default, one logger, one name.
 */
private val defaultLogger = KotlinLogging.logger {}

/**
 * The app's persistent settings, backed by Jetpack DataStore.
 *
 * The class deliberately knows nothing about what is being stored — no locale, no notification
 * keys. Keys stay defined next to the state that owns them, so this cannot become a place that
 * domain concepts are imported into.
 *
 * The file sits under `~/.azkar/`, matching the database's own directory convention.
 */
@Stable
class SettingsStore(
    private val dataStore: DataStore<Preferences>,
    private val scope: CoroutineScope,
    private val logger: KLogger = defaultLogger,
) {
    /**
     * Applies [block] on the store's own scope, so a caller that only ever writes does not have to
     * own one.
     *
     * Fire-and-forget by design: the caller has already moved its own state on, and [changes] is
     * what tells it whether the write landed. A caller that needs to know the outcome awaits [edit].
     */
    fun write(block: MutablePreferences.() -> Unit) {
        scope.launch { edit(block) }
    }

    /**
     * Every stored value, on load and again after each committed write.
     *
     * Read failures are answered by [degradingToEmpty], the same policy [read] applies, so the two
     * ways of asking this store a question cannot disagree about what a failure means.
     */
    val changes: Flow<Preferences> =
        dataStore.data.degradingToEmpty(logger).distinctUntilChanged()

    /** The stored value of [key], suspending until the store has been read once. */
    suspend fun <T> read(key: Preferences.Key<T>): T? = dataStore.data.degradingToEmpty(logger).first()[key]

    /**
     * Applies [block] to the stored values as a single transaction.
     *
     * Grouping the writes is what keeps a multi-setting change from being observed half-applied —
     * the master notification switch writing all five bells is the case that matters.
     *
     * A write can still fail, and the caller has already moved its own state on, so the failure is
     * logged with the key, the only fact that says what was lost, and left to be corrected by the
     * next [changes] emission. DataStore commits atomically, so unlike the `java.util.prefs` flush
     * it replaces, a failure here cannot leave a partially applied setting behind.
     *
     * Swallows the failure deliberately, unlike [degradingToEmpty]: a caller awaiting this has
     * usually already repainted, and there is nothing useful it could do with the exception.
     */
    suspend fun edit(block: MutablePreferences.() -> Unit) {
        runCatching { dataStore.edit(block) }
            .onFailure { failure -> logger.error(failure) { "Unable to persist a setting change" } }
    }

    companion object {
        /**
         * Names the system property that redirects the settings directory.
         *
         * A `val` initialiser read `user.home` the moment this class was first loaded, which meant
         * the path was fixed before anything could ask for a different one. Resolving it per call
         * instead means the harness that does not own a `user.home` — a CI worker, a container, a
         * test that wants its own scratch directory — has a way to say so.
         */
        const val SETTINGS_HOME_PROPERTY: String = "azkar.settings.home"

        private const val DIRECTORY = ".azkar"
        private const val FILE_NAME = "settings.preferences_pb"

        /**
         * Builds a store over [file], defaulting to the app's own settings file.
         *
         * The default is what production resolves: `appModule` asks for `SettingsStore.create()`
         * with no argument, so the path this class chose is the only place it is written down.
         * A caller with a different file — the screenshot harness, a test — passes one, and has to
         * supply a [scope] as well because DataStore outlives any single screen; the default scope
         * is a process-lifetime one, and a caller with a shorter lifetime (a test) can pass its own
         * so the store's work is tied to that lifetime.
         *
         * This used to sit beside an `instance()` accessor over a `by lazy` field, which made the
         * locator indistinguishable from the seam: the tests that wanted a scratch file had to know
         * about the private singleton to avoid it. With the container deciding who gets a store,
         * `create` is the only way in.
         */
        fun create(
            file: Path = settingsFile(),
            scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            logger: KLogger = defaultLogger,
        ): SettingsStore {
            createDirectory(file, logger)
            return SettingsStore(
                dataStore =
                    PreferenceDataStoreFactory.create(
                        // DataStore's default throws `CorruptionException` on a file it cannot
                        // parse, and keeps throwing: without this the store is permanently
                        // unreadable. Replacing the file with an empty one costs the settings and
                        // keeps the app usable, which is the trade every caller of `degradingToEmpty`
                        // is already making by hand for the IO half of the same failure.
                        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
                        scope = scope,
                        produceFile = { file.toFile() },
                    ),
                scope = scope,
                logger = logger,
            )
        }

        /**
         * The app's settings file: [SETTINGS_HOME_PROPERTY] when it is set, `~/.azkar` otherwise.
         *
         * A function rather than a `val` so the property is read when the path is asked for, not
         * when the class happens to load, and so no test can be surprised by a path that was
         * resolved from the developer's own home directory before the test got a say.
         */
        private fun settingsFile(): Path =
            Path.of(
                System.getProperty(SETTINGS_HOME_PROPERTY)
                    ?: Path.of(System.getProperty("user.home"), DIRECTORY).toString(),
                FILE_NAME,
            )

        /**
         * Creates the settings directory, or fails the way the data layer's own config does.
         *
         * DataStore will not create a missing parent, and a failure here is not recoverable by a
         * retry — the path is wrong. Logging then throwing keeps the useful stack frame, which a
         * field initialiser would swallow.
         */
        private fun createDirectory(
            file: Path,
            logger: KLogger,
        ) {
            val parent = file.parent ?: return
            runCatching { Files.createDirectories(parent) }
                .onFailure { failure ->
                    if (failure is IOException) {
                        logger.error(failure) { "Unable to create the settings directory [$parent]" }
                        throw IllegalStateException("Unable to create the settings directory [$parent]", failure)
                    }
                }
        }
    }
}

/**
 * Answers a read that failed on IO with an empty snapshot, and lets anything else through.
 *
 * A settings problem must not take down the window, and every caller already has a sensible default
 * for a value that is missing, so degrading is the right answer. The emptied snapshot flows on, so
 * the next successful write reconciles the state on its own.
 *
 * Only [IOException] degrades. The documented DataStore idiom is the same, and the reason matters:
 * swallowing everything turns a defect in a caller's decoding into "your settings reset themselves",
 * which is both wrong and very hard to trace. An exception that is not an IO failure is a bug, and
 * a bug should arrive in the test run rather than as a silently empty store.
 *
 * This lives on the flow rather than on either of the two callers because it used to be written on
 * only one of them. [SettingsStore.changes] degraded and [SettingsStore.read] did not, so each
 * consumer had to wrap its own read — and which of them protected a given call depended on which
 * API that consumer happened to reach for.
 */
private fun Flow<Preferences>.degradingToEmpty(logger: KLogger): Flow<Preferences> =
    catch { failure ->
        if (failure is IOException) {
            logger.error(failure) { "Unable to read the stored settings" }
            emit(emptyPreferences())
        } else {
            throw failure
        }
    }
