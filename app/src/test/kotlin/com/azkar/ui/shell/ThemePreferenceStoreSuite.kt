package com.azkar.ui.shell

import androidx.datastore.preferences.core.stringPreferencesKey
import com.azkar.settings.SettingsStore
import com.azkar.settings.copyForNextLaunch
import com.azkar.settings.withSettingsFile
import de.infix.testBalloon.framework.core.testSuite
import kotlinx.coroutines.CoroutineScope
import org.assertj.core.api.Assertions.assertThat
import java.nio.file.Path

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
 * The scope is the shared [withSettingsFile] one — a real [CoroutineScope] on
 * [kotlinx.coroutines.Dispatchers.IO] rather than a `runTest` one, because the value under test
 * arrives through file IO and virtual time would not advance it.
 */
val ThemePreferenceStoreSuite by testSuite("The stored colour preference") {
    test("a fresh install follows the system") {
        withSettingsFile { file, scope ->
            val store = themePreferences(file, scope)

            assertThat(store.read()).isEqualTo(ThemePreference.SYSTEM)
        }
    }

    test("a stored preference is what the next run opens with") {
        withSettingsFile { file, scope ->
            val store = themePreferences(file, scope)
            store.persistAndAwait(ThemePreference.DARK)

            // No waiting here: `edit` returns once DataStore has committed the file, so the copy
            // cannot race the write. [persistAndAwait] exists precisely so this suite never has to
            // guess.
            val nextLaunch = themePreferences(copyForNextLaunch(file), scope)

            assertThat(nextLaunch.read()).isEqualTo(ThemePreference.DARK)
        }
    }

    test("a name this build does not know falls back to following the system") {
        withSettingsFile { file, scope ->
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
