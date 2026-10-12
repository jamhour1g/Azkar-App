package com.azkar.i18n

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
import java.util.Locale
import kotlin.io.path.deleteIfExists

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
 * The scope is a real [CoroutineScope] on [Dispatchers.IO] rather than a `runTest` one: the value
 * under test arrives through file IO, and virtual time would not advance it.
 */
val LocaleStoreSuite by testSuite("The stored language") {
    test("a fresh install falls back") {
        withLocaleFile { file, scope ->
            // The fallback is stated rather than inherited, because production derives it from the
            // machine's default locale and a test asserting on the machine it runs on is a test that
            // passes on the author's laptop and fails on CI.
            val store = localeStore(file, scope, fallback = ARABIC)

            assertThat(store.read()).isEqualTo(ARABIC)
        }
    }

    test("a stored language is what the next run opens with") {
        withLocaleFile { file, scope ->
            val store = localeStore(file, scope)
            store.persistAndAwait(ARABIC)

            val copy = file.resolveSibling("copy.preferences_pb")
            Files.copy(file, copy)

            assertThat(localeStore(copy, scope).read()).isEqualTo(ARABIC)
        }
    }

    test("a stored language round-trips a region the app never offers") {
        withLocaleFile { file, scope ->
            val store = localeStore(file, scope)
            // `en-GB` is not in [supportedLocales], so it can only reach the file by being written
            // directly. It is still a legal tag, and refusing it would throw away a stored value a
            // hand-edited file or an older build could legitimately have left behind.
            val british = Locale.forLanguageTag("en-GB")
            store.persistAndAwait(british)

            val copy = file.resolveSibling("copy.preferences_pb")
            Files.copy(file, copy)

            assertThat(localeStore(copy, scope).read()).isEqualTo(british)
        }
    }

    test("a malformed tag falls back rather than launching in a language with no translations") {
        withLocaleFile { file, scope ->
            val store = localeStore(file, scope, fallback = ENGLISH)
            // `Locale.forLanguageTag` answers `und` for anything it cannot parse. Accepting that
            // would render the app undetermined, which is the same as no translations at all.
            store.putStoredTag("not a tag")

            assertThat(store.read()).isEqualTo(ENGLISH)
        }
    }

    test("an empty tag falls back") {
        withLocaleFile { file, scope ->
            val store = localeStore(file, scope, fallback = ENGLISH)
            // An empty string round-trips through `Locale.forLanguageTag` to ROOT, which has no
            // translations, so a blank write has to be treated as "nothing stored".
            store.putStoredTag("")

            assertThat(store.read()).isEqualTo(ENGLISH)
        }
    }

    test("readAndActivate is the only thing that moves the JVM default") {
        withLocaleFile { file, scope ->
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

/**
 * Runs [block] against a fresh file and a scope, cancelling the scope and deleting the file after.
 *
 * The same shape as the theme store's and the notification switches' fixtures, so a reader has one
 * temp-file idiom to know.
 */
private inline fun withLocaleFile(block: (Path, CoroutineScope) -> Unit) {
    val directory = Files.createTempDirectory("azkar-locale")
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    try {
        block(directory.resolve("settings.preferences_pb"), scope)
    } finally {
        scope.cancel()
        runCatching { Files.list(directory).forEach { it.deleteIfExists() } }
        runCatching { directory.deleteIfExists() }
    }
}
