package com.azkar.ui.home

import androidx.compose.runtime.snapshotFlow
import androidx.datastore.preferences.core.booleanPreferencesKey
import com.azkar.settings.FakePreferencesDataStore
import com.azkar.settings.SettingsStore
import com.azkar.settings.copyForNextLaunch
import com.azkar.settings.withInMemorySettings
import com.azkar.settings.withSettingsFile
import de.infix.testBalloon.framework.core.testSuite
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import java.io.IOException

/**
 * The notification switches, against the settings store.
 *
 * The bug this suite exists for was a local `val flags` in the old `loadPersistedSettings` that
 * shadowed the backing store, leaving it empty until a switch was touched. It then held exactly one
 * entry, and the master switch — derived from `flags.values.any { it }` — turned itself off as soon
 * as that single bell was turned off, while the other four were still on.
 *
 * Most of this runs against the in-memory store rather than a real file, which it did not used to.
 * None of these tests is asking whether a byte reached the disk — they are asking how the master
 * switch and five rows relate to each other — and paying for file IO to ask that bought three
 * problems at once: a scratch directory and a scope per test, a busy-poll for the write to land, and
 * no way to write a test whose store fails. Only the last test here genuinely needs a file, because
 * only it is about durability.
 */
val NotificationSettingsSuite by testSuite("The notification switches") {
    test("a fresh install starts with every bell on and the master switch on") {
        withSwitches { state, _ ->
            assertThat(state.flags).hasSize(PrayerKey.entries.size)
            assertThat(state.flags.values).containsOnly(true)
            assertThat(state.globalEnabled).isTrue()
        }
    }

    test("turning one bell off leaves the master switch on while the others are on") {
        withSwitches { state, _ ->
            state.onPrayerToggled(PrayerKey.FAJR, false)
            state.awaitFlags { it[PrayerKey.FAJR] == false }

            assertThat(state.flags.values.count { it }).isEqualTo(PrayerKey.entries.size - 1)
            assertThat(state.globalEnabled).isTrue()
        }
    }

    test("turning the last bell off turns the master switch off") {
        withSwitches { state, _ ->
            PrayerKey.entries.forEach { state.onPrayerToggled(it, false) }
            state.awaitFlags { flags -> flags.values.none { it } }

            assertThat(state.globalEnabled).isFalse()
        }
    }

    test("turning a bell back on turns the master switch back on") {
        withSwitches { state, _ ->
            PrayerKey.entries.forEach { state.onPrayerToggled(it, false) }
            state.awaitFlags { flags -> flags.values.none { it } }

            state.onPrayerToggled(PrayerKey.ISHA, true)
            state.awaitFlags { it[PrayerKey.ISHA] == true }

            assertThat(state.globalEnabled).isTrue()
        }
    }

    test("the master switch off turns every bell off, and re-enabling it restores them all") {
        withSwitches { state, _ ->
            state.onGlobalToggleChanged(false)
            state.awaitFlags { flags -> flags.values.none { it } }
            assertThat(state.globalEnabled).isFalse()

            state.onGlobalToggleChanged(true)
            state.awaitFlags { flags -> flags.values.all { it } }
            assertThat(state.globalEnabled).isTrue()
        }
    }

    test("the master switch is not stored, so a half-open one cannot survive a restart") {
        withSwitches { state, dataStore ->
            state.onGlobalToggleChanged(false)
            state.awaitFlags { flags -> flags.values.none { it } }

            // Only the five bells are ever written, so the master switch is derived on read and the
            // two can never disagree about what was stored.
            assertThat(
                dataStore.stored
                    .asMap()
                    .keys
                    .map { it.name },
            ).containsExactlyInAnyOrderElementsOf(PrayerKey.entries.map { "prayer.${it.id}.enabled" })
        }
    }

    test("a failed write leaves the stored switches alone rather than half applied") {
        withSwitches { state, dataStore ->
            state.onPrayerToggled(PrayerKey.ASR, false)
            state.awaitFlags { it[PrayerKey.ASR] == false }

            dataStore.writeFailure = IOException("the disk went away")
            state.onGlobalToggleChanged(true)

            // The master switch mirrors five rows in one transaction, so a write that cannot be
            // committed must leave every row as it was. DataStore commits atomically, which is what
            // makes "half applied" a state this store cannot reach.
            assertThat(dataStore.stored[storedFlagKey(PrayerKey.ASR)]).isFalse()

            // And the failed transaction added nothing. FAJR was never written in the first place —
            // an unset key reads as "on" — so five rows in the file here would mean the transaction
            // landed and the failure was swallowed somewhere else entirely.
            assertThat(dataStore.stored[storedFlagKey(PrayerKey.FAJR)]).isNull()
            assertThat(
                dataStore.stored
                    .asMap()
                    .keys
                    .map { it.name },
            ).containsExactly("prayer.${PrayerKey.ASR.id}.enabled")

            // And the rendered rows do not move either. Nothing here writes optimistically, so a
            // failed write leaves the screen agreeing with the file instead of showing a switch that
            // was never stored.
            assertThat(state.flags[PrayerKey.FAJR]).isTrue()
        }
    }

    test("a bell reaches the file, and a fresh store reads it back out of a copy") {
        withSettingsFile { file, scope ->
            val store = SettingsStore.create(file, scope)
            val first = NotificationSettingsState(store, scope)
            scope.launch { first.observe() }
            first.awaitFlags()

            first.onPrayerToggled(PrayerKey.ASR, false)

            // Waiting on the store's own emission rather than on the file existing. This is the
            // replacement for an `awaitFile` helper whose claim — "the file is there, so the write
            // landed" — was wrong: existence proves *some* commit happened, not the one just made,
            // and the suite only passed because the toggle under test happened to be the first write
            // ever made to that file. DataStore re-emits only after committing, so waiting for the
            // value is waiting for the disk.
            store.changes.first { it[storedFlagKey(PrayerKey.ASR)] == false }

            // DataStore allows one instance per file per process, so the round trip goes through a
            // copy: a store built on the copy parses what was actually written to disk, which is
            // the thing a second run of the app would read.
            val stored = SettingsStore.create(copyForNextLaunch(file), scope).changes.first()

            assertThat(stored[storedFlagKey(PrayerKey.ASR)]).isFalse()
            assertThat(stored[storedFlagKey(PrayerKey.ISHA)]).isNull()
        }
    }
}

/** Runs [block] against switches on the in-memory store, observing from the start. */
private inline fun withSwitches(block: (NotificationSettingsState, FakePreferencesDataStore) -> Unit) =
    withInMemorySettings { scope, store, dataStore ->
        val state = NotificationSettingsState(store, scope)
        scope.launch { state.observe() }
        block(state, dataStore)
    }

/**
 * Suspends until [state] has observed a complete snapshot satisfying [matches], then returns it.
 *
 * [snapshotFlow] rather than the polling loop this replaced. That loop re-read a `mutableStateOf` on
 * a timer, which is both a busy-wait and a way of writing the same assertion twice — a snapshot is
 * only ever emitted when the state it reads actually changes, so the wait ends on the change itself.
 * The timeout is kept: without it a state that never settles hangs the run instead of failing it.
 */
private suspend fun NotificationSettingsState.awaitFlags(
    matches: (Map<PrayerKey, Boolean>) -> Boolean = { true },
): Map<PrayerKey, Boolean> = withTimeout(TIMEOUT_MILLIS) { snapshotFlow { flags }.first(matches) }

/**
 * The on-disk key for a prayer's switch.
 *
 * Built here rather than reused from the state so the test pins the stored key name itself — a
 * rename is a storage-format change, and this is where it should be noticed.
 */
private fun storedFlagKey(key: PrayerKey) = booleanPreferencesKey("prayer.${key.id}.enabled")

private const val TIMEOUT_MILLIS = 5_000L
