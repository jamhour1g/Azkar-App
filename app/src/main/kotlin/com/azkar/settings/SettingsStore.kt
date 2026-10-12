package com.azkar.settings

import androidx.compose.runtime.Stable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
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

private val logger = KotlinLogging.logger {}

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
     * A read failure degrades to an empty snapshot rather than propagating: the alternative is a
     * settings problem taking down the window, and every caller already has a sensible default for
     * a value that is missing. The emptied snapshot flows on, so the next successful write
     * reconciles the state on its own.
     */
    val changes: Flow<Preferences> =
        dataStore.data
            .catch { failure ->
                logger.error(failure) { "Unable to read the stored settings" }
                emit(emptyPreferences())
            }.distinctUntilChanged()

    /** The stored value of [key], suspending until the store has been read once. */
    suspend fun <T> read(key: Preferences.Key<T>): T? = dataStore.data.first()[key]

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
     */
    suspend fun edit(block: MutablePreferences.() -> Unit) {
        runCatching { dataStore.edit(block) }
            .onFailure { failure -> logger.error(failure) { "Unable to persist a setting change" } }
    }

    companion object {
        private val SETTINGS_FILE: Path =
            Path.of(
                System.getProperty("user.home"),
                ".azkar",
                "settings.preferences_pb",
            )

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
            file: Path = SETTINGS_FILE,
            scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        ): SettingsStore {
            createDirectory(file)
            return SettingsStore(
                dataStore =
                    PreferenceDataStoreFactory.create(
                        scope = scope,
                        produceFile = { file.toFile() },
                    ),
                scope = scope,
            )
        }

        /**
         * Creates the settings directory, or fails the way the data layer's own config does.
         *
         * DataStore will not create a missing parent, and a failure here is not recoverable by a
         * retry — the path is wrong. Logging then throwing keeps the useful stack frame, which a
         * field initialiser would swallow.
         */
        private fun createDirectory(file: Path) {
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
