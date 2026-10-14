package com.azkar.ui.home

import androidx.datastore.preferences.core.booleanPreferencesKey
import com.azkar.settings.SettingsStore
import de.infix.testBalloon.framework.core.testSuite
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.deleteIfExists

/**
 * The notification switches, against a real DataStore on a real file.
 *
 * Each test gets its own file and its own store, so nothing is shared and nothing reaches the
 * developer's actual settings. The scope is a real [CoroutineScope] on [Dispatchers.IO] rather than
 * a `runTest` one: the value under test arrives through a `Flow` fed by file IO, and virtual time
 * would not advance it.
 *
 * The bug this suite exists for was a local `val flags` in the old `loadPersistedSettings` that
 * shadowed the backing store, leaving it empty until a switch was touched. It then held exactly one
 * entry, and the master switch — derived from `flags.values.any { it }` — turned itself off as soon
 * as that single bell was turned off, while the other four were still on.
 */
val NotificationSettingsSuite by testSuite("The notification switches") {
    test("a fresh install starts with every bell on and the master switch on") {
        withSettings { state ->
            state.awaitFlags()

            assertThat(state.flags).hasSize(PrayerKey.entries.size)
            assertThat(state.flags.values).containsOnly(true)
            assertThat(state.globalEnabled).isTrue()
        }
    }

    test("turning one bell off leaves the master switch on while the others are on") {
        withSettings { state ->
            state.awaitFlags()
            state.onPrayerToggled(PrayerKey.FAJR, false)
            state.awaitFlags { it[PrayerKey.FAJR] == false }

            assertThat(state.flags.values.count { it }).isEqualTo(PrayerKey.entries.size - 1)
            assertThat(state.globalEnabled).isTrue()
        }
    }

    test("turning the last bell off turns the master switch off") {
        withSettings { state ->
            state.awaitFlags()
            PrayerKey.entries.forEach { state.onPrayerToggled(it, false) }
            state.awaitFlags { flags -> flags.values.none { it } }

            assertThat(state.globalEnabled).isFalse()
        }
    }

    test("turning a bell back on turns the master switch back on") {
        withSettings { state ->
            state.awaitFlags()
            PrayerKey.entries.forEach { state.onPrayerToggled(it, false) }
            state.awaitFlags { flags -> flags.values.none { it } }
            state.onPrayerToggled(PrayerKey.ISHA, true)
            state.awaitFlags { it[PrayerKey.ISHA] == true }

            assertThat(state.globalEnabled).isTrue()
        }
    }

    test("the master switch off turns every bell off, and re-enabling it restores them all") {
        withSettings { state ->
            state.awaitFlags()
            state.onGlobalToggleChanged(false)
            state.awaitFlags { flags -> flags.values.none { it } }
            assertThat(state.globalEnabled).isFalse()

            state.onGlobalToggleChanged(true)
            state.awaitFlags { flags -> flags.values.all { it } }
            assertThat(state.globalEnabled).isTrue()
        }
    }

    test("a bell reaches the file, and a fresh store reads it back out of a copy") {
        withSettingsFile { file, scope ->
            val first = NotificationSettingsState(SettingsStore.create(file, scope), scope)
            scope.launch { first.observe() }
            first.awaitFlags()
            first.onPrayerToggled(PrayerKey.ASR, false)
            first.awaitFlags { it[PrayerKey.ASR] == false }

            // DataStore allows one instance per file per process, so the round trip goes through a
            // copy: a store built on the copy parses what was actually written to disk, which is
            // the thing a second run of the app would read.
            val copy = file.resolveSibling("copy.preferences_pb")
            Files.copy(file, copy)

            val stored =
                runBlocking {
                    SettingsStore.create(copy, scope).changes.first()
                }

            assertThat(stored[storedFlagKey(PrayerKey.ASR)]).isFalse()
            assertThat(stored[storedFlagKey(PrayerKey.ISHA)]).isNull()
        }
    }

    test("the master switch is not stored, so a half-open one cannot survive a restart") {
        withSettingsFile { file, scope ->
            val state = NotificationSettingsState(SettingsStore.create(file, scope), scope)
            scope.launch { state.observe() }
            state.awaitFlags()
            state.onGlobalToggleChanged(false)
            state.awaitFlags { flags -> flags.values.none { it } }
            assertThat(state.globalEnabled).isFalse()

            val copy = file.resolveSibling("copy.preferences_pb")
            Files.copy(file, copy)
            val stored = runBlocking { SettingsStore.create(copy, scope).changes.first() }

            // Master off with a bell still on used to be reachable, and then persisted as-is. The
            // master switch is derived now, so only the five bells are ever written.
            assertThat(stored.asMap().keys.map { it.name })
                .containsExactlyInAnyOrderElementsOf(PrayerKey.entries.map { "prayer.${it.id}.enabled" })
        }
    }
}

/**
 * Runs [block] against a state on a fresh file, cancelling the scope and deleting the file after.
 */
private inline fun withSettings(block: (NotificationSettingsState) -> Unit) =
    withSettingsFile { file, scope ->
        val state = NotificationSettingsState(SettingsStore.create(file, scope), scope)
        scope.launch { state.observe() }
        block(state)
    }

/**
 * Runs [block] against a fresh file and a scope, for the tests that open a second store over the
 * same file to stand in for the next run of the app.
 */
private inline fun withSettingsFile(block: (Path, CoroutineScope) -> Unit) {
    val directory = Files.createTempDirectory("azkar-settings")
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    try {
        block(directory.resolve("settings.preferences_pb"), scope)
    } finally {
        scope.cancel()
        runCatching { Files.list(directory).forEach { it.deleteIfExists() } }
        runCatching { directory.deleteIfExists() }
    }
}

/**
 * Suspends until [state] has observed a complete snapshot satisfying [matches], then returns it.
 *
 * The store emits asynchronously, so asserting straight after a toggle would race the file write.
 * The timeout keeps a regression here failing rather than hanging.
 */
private suspend fun NotificationSettingsState.awaitFlags(
    matches: (Map<PrayerKey, Boolean>) -> Boolean = { true },
): Map<PrayerKey, Boolean> {
    val deadline = System.currentTimeMillis() + TIMEOUT_MILLIS
    while (System.currentTimeMillis() < deadline) {
        if (flags.size == PrayerKey.entries.size && matches(flags)) return flags
        delay(POLL_INTERVAL_MILLIS)
    }
    throw AssertionError("Timed out waiting for the flags to settle; last seen was $flags")
}

/**
 * The on-disk key for a prayer's switch.
 *
 * Built here rather than reused from the state so the test pins the stored key name itself — a
 * rename is a storage-format change, and this is where it should be noticed.
 */
private fun storedFlagKey(key: PrayerKey) = booleanPreferencesKey("prayer.${key.id}.enabled")

private const val TIMEOUT_MILLIS = 5_000L
private const val POLL_INTERVAL_MILLIS = 10L
