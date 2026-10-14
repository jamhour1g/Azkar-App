package com.azkar.i18n

import androidx.datastore.preferences.core.stringPreferencesKey
import com.azkar.settings.SettingsStore
import com.azkar.settings.copyForNextLaunch
import com.azkar.settings.withSettingsFile
import de.infix.testBalloon.framework.core.testSuite
import kotlinx.coroutines.CoroutineScope
import org.assertj.core.api.Assertions.assertThat
import java.nio.file.Path
import java.util.Locale

/**
 * The stored language, against a real DataStore on a real file.
 *
 * The theme store has the suite that caught a choice that never reached disk, and the language was
 * one store away from the same bug with none of the coverage: nothing here used to assert that
 * choosing Arabic survives the window closing, so a write that quietly went nowhere would have shipped
 * as "the language setting doesn't stick" with a green build.
 *
 * The round trip is asserted through a copy of the file rather than the same store, because DataStore
 * allows one instance per file per process and what a second run of the app reads is what actually
 * landed on disk.
 *
 * Each test gets its own file and its own store, so nothing reaches the developer's real settings.
 * The scope is the shared [withSettingsFile] one — a real [CoroutineScope] on
 * [kotlinx.coroutines.Dispatchers.IO] rather than a `runTest` one, because the value under test
 * arrives through file IO and virtual time would not advance it.
 */
val LocaleStoreSuite by testSuite("The stored language") {
    test("a fresh install falls back") {
        withSettingsFile { file, scope ->
            // The fallback is stated rather than inherited, because production derives it from the
            // machine's default locale and a test asserting on the machine it runs on is a test that
            // passes on the author's laptop and fails on CI.
            val store = localeStore(file, scope, fallback = ARABIC)

            assertThat(store.read()).isEqualTo(ARABIC)
        }
    }

    test("a stored language is what the next run opens with") {
        withSettingsFile { file, scope ->
            val store = localeStore(file, scope)
            store.persistAndAwait(ARABIC)

            // No waiting here: `edit` returns once DataStore has committed the file, so the copy
            // cannot race the write.
            assertThat(localeStore(copyForNextLaunch(file), scope).read()).isEqualTo(ARABIC)
        }
    }

    test("a stored language round-trips a region the app never offers") {
        withSettingsFile { file, scope ->
            val store = localeStore(file, scope)
            // `en-GB` is not in [supportedLocales], so it can only reach the file by being written
            // directly. It is still a legal tag, and refusing it would throw away a stored value a
            // hand-edited file or an older build could legitimately have left behind.
            val british = Locale.forLanguageTag("en-GB")
            store.persistAndAwait(british)

            assertThat(localeStore(copyForNextLaunch(file), scope).read()).isEqualTo(british)
        }
    }

    test("a malformed tag falls back rather than launching in a language with no translations") {
        withSettingsFile { file, scope ->
            val store = localeStore(file, scope, fallback = ENGLISH)
            // `Locale.forLanguageTag` answers `und` for anything it cannot parse. Accepting that
            // would render the app undetermined, which is the same as no translations at all.
            store.putStoredTag("not a tag")

            assertThat(store.read()).isEqualTo(ENGLISH)
        }
    }

    test("an empty tag falls back") {
        withSettingsFile { file, scope ->
            val store = localeStore(file, scope, fallback = ENGLISH)
            // An empty string round-trips through `Locale.forLanguageTag` to ROOT, which has no
            // translations, so a blank write has to be treated as "nothing stored".
            store.putStoredTag("")

            assertThat(store.read()).isEqualTo(ENGLISH)
        }
    }

    test("readAndActivate is the only thing that moves the JVM default") {
        withSettingsFile { file, scope ->
            val original = Locale.getDefault()
            try {
                val store = localeStore(file, scope)
                store.persistAndAwait(ARABIC)

                // `read` answers the question and nothing more. This is the property that lets
                // `Main` decide when to touch a process-wide value, and a store that activated as a
                // side effect of being asked would make that decision impossible to place.
                assertThat(store.read()).isEqualTo(ARABIC)
                assertThat(Locale.getDefault()).isEqualTo(original)

                store.readAndActivate()
                assertThat(Locale.getDefault()).isEqualTo(ARABIC)
            } finally {
                // The JVM default is process-wide, so a test that moved it and did not put it back
                // would decide the language of every test that runs after it.
                Locale.setDefault(original)
            }
        }
    }

    test("the stored key is the name the file format already has") {
        // Written out rather than read back off [LocaleStore.key]: renaming the constant would
        // silently reset everyone's saved language, and this is where that should be noticed.
        assertThat(LocaleStore.key).isEqualTo(stringPreferencesKey("locale"))
    }

    test("localeOrNull rejects the shapes that mean no language, and nothing else") {
        // The parser is the part with real logic, and it is pure, so it is asserted directly rather
        // than through a file: a round trip cannot distinguish "rejected" from "parsed to something
        // that then fell back to the same place".
        assertThat("".localeOrNull()).isNull()
        assertThat("   ".localeOrNull()).isNull()
        assertThat(null.localeOrNull()).isNull()
        assertThat("not a tag".localeOrNull()).isNull()
        assertThat("en".localeOrNull()).isEqualTo(ENGLISH)
        assertThat("ar".localeOrNull()).isEqualTo(ARABIC)
        assertThat("en-GB".localeOrNull()).isEqualTo(Locale.forLanguageTag("en-GB"))
    }
}

/** The locale store over a store built for [file] and [scope], with the fallback stated. */
private fun localeStore(
    file: Path,
    scope: CoroutineScope,
    fallback: Locale = ENGLISH,
): LocaleStore = LocaleStore(SettingsStore.create(file, scope), fallback)
