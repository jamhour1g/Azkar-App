package com.azkar.i18n

import androidx.compose.runtime.Stable
import androidx.datastore.preferences.core.stringPreferencesKey
import com.azkar.settings.SettingsStore
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.annotations.VisibleForTesting
import java.util.Locale

/**
 * Reads and writes the persisted language.
 *
 * This was the top-level function pair that `ThemePreferenceStore` no longer is, and it moved for the
 * same reason: one object that owns a storage key and the rule for an unusable stored value, so a
 * test can reach both without reaching for the settings file itself.
 *
 * Half of a locale's lifecycle is deliberately *not* here. [activateLocale] sets a process-wide JVM
 * default, which is not this store's business and not something a settings-backed object should claim
 * to own. So [LocaleStore] decides *which* locale and [activateLocale] decides that everything
 * downstream should believe it — [readAndActivate] is only the composition of the two, which is what
 * `Main` calls before the window opens.
 *
 * [fallback] is a constructor parameter rather than a file-level `val` for the same reason it was a
 * `val` at all: [activateLocale] overwrites the JVM default, so a value read later would just observe
 * our own previous write. Resolving it once at construction keeps that guarantee — production
 * constructs this before anything has activated a locale — while letting a test say what the fallback
 * is instead of depending on the machine's own default locale.
 */
@Stable
class LocaleStore(
    private val settings: SettingsStore,
    private val fallback: Locale = defaultFallback(),
    private val logger: KLogger = KotlinLogging.logger {},
) {
    /**
     * The stored language, or [fallback] when nothing usable has been stored.
     *
     * Does not activate anything. Reading is the question "what was chosen"; activating is a
     * separate decision that only [readAndActivate] and [activateLocale] make.
     *
     * No `runCatching` of its own, for the same reason [com.azkar.ui.shell.ThemePreferenceStore.read]
     * does not carry one: [SettingsStore] answers a failed read with an empty snapshot already, and
     * a wrapper here would make a missing value and an unreadable store look the same.
     */
    suspend fun read(): Locale =
        (settings.read(key).localeOrNull() ?: fallback).also {
            logger.info { "Restored interface language preference (${it.toLanguageTag()})" }
        }

    /**
     * Resolves the language, falls back, and points Compose at the result.
     *
     * Blocking by design, and called once on the main thread before the window opens: the title bar and
     * the very first frame are both built from the locale, so it has to be known before composition
     * starts. This replaces a synchronous persisted-settings read that was equally blocking.
     */
    suspend fun readAndActivate(): Locale = read().apply { activateLocale() }

    /** Stores [locale] for the next run. Fire and forget: a lost write costs one language choice. */
    fun persist(locale: Locale) {
        logger.info { "Saving interface language preference (${locale.toLanguageTag()})" }
        settings.write("interface language preference=${locale.toLanguageTag()}") {
            this[key] = locale.toLanguageTag()
        }
    }

    /**
     * Stores [locale] and returns once the write has landed.
     *
     * `internal` because nothing in the app needs this — [persist] exists because the language dialog
     * has already retranslated the tree by the time the write completes, and blocking it would stall
     * the click. A test reading the value back has no such repaint to hide behind.
     */
    @VisibleForTesting
    internal suspend fun persistAndAwait(locale: Locale) {
        settings.edit { this[key] = locale.toLanguageTag() }
    }

    /**
     * Writes [tag] verbatim, without checking that it names a language.
     *
     * Only a test can need this: the shapes rejected by [localeOrNull] — `und`, an empty string — are
     * exactly the ones no production write can produce.
     */
    @VisibleForTesting
    internal suspend fun putStoredTag(tag: String) {
        settings.edit { this[key] = tag }
    }

    internal companion object {
        /**
         * The on-disk key, visible so a test can seed a malformed tag under the production key.
         *
         * `internal` because the stored name is a storage-format concern: renaming it silently resets
         * everyone's saved language, which the suite pinning the literal `"locale"` is what catches.
         */
        @VisibleForTesting
        val key = stringPreferencesKey("locale")
    }
}

/**
 * Points Compose at [locale].
 *
 * `org.jetbrains.compose.resources` resolves against the JVM default locale on desktop rather than
 * against one passed in, and both its `ResourceEnvironment` and the `LocalComposeEnvironment` that
 * would override it are `internal`, so there is no supported way to say "render in Arabic" other
 * than this. `Locale.setDefault` emits no notification, so the caller still has to force the tree to
 * rebuild — `AzkarApp` does that with `key(state.locale)`.
 *
 * Top-level rather than a method on [LocaleStore]: this is the process-wide half of the language
 * lifecycle, and putting it on a store over the settings file would imply the JVM default is stored
 * state, which it is not.
 */
fun Locale.activateLocale() {
    Locale.setDefault(this)
}

/**
 * The language to start on when nothing usable has been stored.
 *
 * ROOT is refused rather than honoured: it has no translations, so honouring it would render the app
 * in a language the resources do not have.
 */
private fun defaultFallback(): Locale = Locale.getDefault().takeIf { it != Locale.ROOT } ?: ENGLISH

/**
 * Parses a stored language tag, rejecting the two shapes that mean "no language".
 *
 * `Locale.forLanguageTag` answers `und` (undetermined) for a malformed tag, and an empty string
 * would otherwise round-trip to the ROOT locale, which has no translations.
 */
internal fun String?.localeOrNull(): Locale? {
    if (this.isNullOrBlank()) return null
    return Locale
        .forLanguageTag(this)
        .takeIf { it.toLanguageTag() != "und" }
}
