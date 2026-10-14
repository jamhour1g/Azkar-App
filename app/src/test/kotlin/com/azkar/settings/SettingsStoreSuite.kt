package com.azkar.settings

import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import de.infix.testBalloon.framework.core.testSuite
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import java.io.IOException
import java.nio.file.Files
import kotlin.io.path.deleteIfExists

/**
 * How the settings store answers a failure, which is the part no other suite could reach.
 *
 * Every other suite asks whether a value comes back. These ask what happens when it cannot, and they
 * need the in-memory store for it: a real file will only fail to parse if it is made to, and cannot
 * be told to fail a write at all. The two behaviours pinned here are also the two that were easiest
 * to lose silently — a read policy narrowed to `IOException` is invisible until something throws
 * something else, and a corruption handler is invisible until the day a half-written file appears.
 */
val SettingsStoreSuite by testSuite("The settings store") {
    test("a read that fails on IO answers empty rather than taking the window down") {
        withInMemorySettings { _, store, dataStore ->
            dataStore.readFailure = IOException("the disk went away")

            assertThat(store.read(TEST_KEY)).isNull()
        }
    }

    test("changes applies the same policy read does") {
        withInMemorySettings { _, store, dataStore ->
            dataStore.readFailure = IOException("the disk went away")

            // The two used to disagree: `changes` caught everything and `read` caught nothing, so a
            // consumer's fate depended on which API it happened to reach for. One policy, asserted
            // on both, is what stops them drifting apart again.
            assertThat(store.changes.first()).isEqualTo(emptyPreferences())
        }
    }

    test("a read that fails on anything but IO propagates, because that is a bug") {
        withInMemorySettings { _, store, dataStore ->
            dataStore.readFailure = IllegalStateException("a decoding defect, not a broken disk")

            // Swallowing this is how "your settings reset themselves" becomes the explanation for a
            // bug in a caller, and it is very hard to trace from there. The empty snapshot is for IO
            // and only for IO.
            assertThatThrownBy { runBlocking { store.read(TEST_KEY) } }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("decoding defect")
        }
    }

    test("changes propagates that same failure rather than hiding it") {
        withInMemorySettings { _, store, dataStore ->
            dataStore.readFailure = IllegalArgumentException("a bug downstream of the store")

            assertThatThrownBy { runBlocking { store.changes.first() } }
                .isInstanceOf(IllegalArgumentException::class.java)
        }
    }

    test("write lands before it returns, which is what lets a caller treat it as done") {
        withInMemorySettings { _, store, dataStore ->
            store.write { this[TEST_KEY] = "written" }

            assertThat(dataStore.stored[TEST_KEY]).isEqualTo("written")
        }
    }

    test("a corrupt file is replaced rather than bricking the store") {
        withSettingsFile { file, scope ->
            Files.writeString(file, "this is not a preferences file")

            // Without a corruption handler DataStore's default is to throw `CorruptionException`
            // and keep throwing — every later read of the same store fails the same way, so a
            // half-written file left the app unable to read its settings at all. The `.catch` in
            // `changes` did not help: it turns the throw into an empty snapshot, which looks like a
            // reset but never lets the store write again.
            val store = SettingsStore.create(file, scope)

            assertThat(store.read(TEST_KEY)).isNull()
            assertThat(store.changes.first()).isEqualTo(emptyPreferences())
        }
    }

    test("a store that met a corrupt file can still be written to, and the fix survives a restart") {
        withSettingsFile { file, scope ->
            Files.writeString(file, "this is not a preferences file")
            val store = SettingsStore.create(file, scope)

            store.edit { this[TEST_KEY] = "after the corruption" }

            // The claim worth pinning is not that the read was survivable but that the store was
            // still writable afterwards. A handler that resets the file but leaves the store refusing
            // to commit would pass the test above and still be a dead app.
            val nextLaunch = SettingsStore.create(copyForNextLaunch(file), scope)

            assertThat(nextLaunch.read(TEST_KEY)).isEqualTo("after the corruption")
        }
    }

    test("the settings home is a property, so no test can be redirected by loading order") {
        val home = Files.createTempDirectory("azkar-settings-home")
        val previous = System.getProperty(SettingsStore.SETTINGS_HOME_PROPERTY)
        try {
            System.setProperty(SettingsStore.SETTINGS_HOME_PROPERTY, home.toString())

            val store = SettingsStore.create()
            store.edit { this[TEST_KEY] = "in the redirected home" }

            // The path used to be resolved when the class loaded, which meant it was fixed before
            // anything could ask for a different one. Resolving it per call means a harness that
            // does not own a `user.home` — CI, a container, a scratch directory — can say so instead
            // of writing into the developer's real settings.
            assertThat(Files.exists(home.resolve(SETTINGS_FILE_NAME))).isTrue()
        } finally {
            // The property is process-wide, so a test that set it and did not put it back would
            // silently move the settings file for every test that ran after it.
            if (previous == null) {
                System.clearProperty(SettingsStore.SETTINGS_HOME_PROPERTY)
            } else {
                System.setProperty(SettingsStore.SETTINGS_HOME_PROPERTY, previous)
            }
            runCatching { Files.list(home).forEach { it.deleteIfExists() } }
            runCatching { home.deleteIfExists() }
        }
    }
}

private val TEST_KEY = stringPreferencesKey("settings.suite.test")
