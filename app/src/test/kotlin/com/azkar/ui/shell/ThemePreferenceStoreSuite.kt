package com.azkar.ui.shell

import androidx.datastore.preferences.core.stringPreferencesKey
import com.azkar.settings.SettingsStore
import de.infix.testBalloon.framework.core.testSuite
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.assertj.core.api.Assertions.assertThat
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.deleteIfExists

/**
 * The stored colour preference, against a real DataStore on a real file.
 *
 * This is the fix for a toggle that changed the palette and forgot: `selectTheme` moved the state
 * and never wrote it, and nothing read the key back, so the next launch always opened following the
 * system. The round trip is asserted through a copy of the file rather than the same store, because
 * DataStore allows one instance per file per process and what a second run of the app reads is what
 * actually landed on disk.
 *
 * Each test gets its own file and its own store, so nothing reaches the developer's real settings.
 * The scope is a real [CoroutineScope] on [Dispatchers.IO] rather than a `runTest` one: the value
 * under test arrives through file IO, and virtual time would not advance it.
 */
val ThemePreferenceStoreSuite by testSuite("The stored colour preference") {
    test("a fresh install follows the system") {
        withThemeFile { file, scope ->
            val store = themePreferences(file, scope)

            assertThat(store.read()).isEqualTo(ThemePreference.SYSTEM)
        }
    }

    test("a stored preference is what the next run opens with") {
        withThemeFile { file, scope ->
            val store = themePreferences(file, scope)
            store.persistAndAwait(ThemePreference.DARK)

            val copy = file.resolveSibling("copy.preferences_pb")
            Files.copy(file, copy)

            assertThat(themePreferences(copy, scope).read()).isEqualTo(ThemePreference.DARK)
        }
    }

    test("a name this build does not know falls back to following the system") {
        withThemeFile { file, scope ->
            val store = themePreferences(file, scope)
            // Seeded under the production key rather than through [ThemePreferenceStore.persist],
            // because no preference this build has can produce a name it does not know.
            store.putStoredName("SEPIA")

            // A light window beats no window: a hand-edited file or a renamed constant must not take
            // the launch down with it.
            assertThat(store.read()).isEqualTo(ThemePreference.SYSTEM)
        }
    }

    test("the stored key is the name the file format already has") {
        // The pinning is the point of this one, and it is why the suite does not simply assert on
        // [ThemePreferenceStore.key]: renaming the constant would silently drop everyone's saved
        // theme, so the name itself has to be written out here and compared.
        assertThat(ThemePreferenceStore.key)
            .isEqualTo(stringPreferencesKey("theme_preference"))
    }
}

/** The theme store over a store built for [file] and [scope]. */
private fun themePreferences(
    file: Path,
    scope: CoroutineScope,
): ThemePreferenceStore = ThemePreferenceStore(SettingsStore.create(file, scope))

/**
 * Runs [block] against a fresh file and a scope, cancelling the scope and deleting the file after.
 *
 * The same shape as the notification switches' fixture, so a reader has one temp-file idiom to know.
 */
private inline fun withThemeFile(block: (Path, CoroutineScope) -> Unit) {
    val directory = Files.createTempDirectory("azkar-theme")
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    try {
        block(directory.resolve("settings.preferences_pb"), scope)
    } finally {
        scope.cancel()
        runCatching { Files.list(directory).forEach { it.deleteIfExists() } }
        runCatching { directory.deleteIfExists() }
    }
}
