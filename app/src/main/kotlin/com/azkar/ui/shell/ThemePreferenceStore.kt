package com.azkar.ui.shell

import androidx.compose.runtime.Stable
import androidx.datastore.preferences.core.stringPreferencesKey
import com.azkar.settings.SettingsStore

/**
 * Reads and writes the stored colour preference.
 *
 * This started as a top-level function pair, and the reason it did not stay one is testability.
 * Two things a test wants from it:
 *
 *  - the on-disk key, as [key]. The suite used to rebuild `stringPreferencesKey("theme_preference")`
 *    by hand and then wait on `store.changes` to notice its own write landing, because the write was
 *    fire and forget on the store's scope and nothing else said it had finished. [persistAndAwait]
 *    removes the second half of that: the write returns when it is on disk, so the test reads back
 *    what it just wrote instead of inferring it from an unrelated flow.
 *  - one object to hand around, so a caller can be given the theme store without also being given
 *    the [SettingsStore] it happens to sit on. Passing the settings store around is what let the
 *    developer find the developer's own `settings.preferences_pb` through an API that looked
 *    otherwise harmless.
 *
 * The [SettingsStore] is still a constructor parameter rather than a file-level `instance()` captured
 * at class-init time, so the store is supplied by whoever starts the app instead of being bound as a
 * side effect of this file being loaded.
 */
@Stable
class ThemePreferenceStore(
    private val settings: SettingsStore,
) {
    /**
     * The stored colour preference for this run, defaulting to [ThemePreference.SYSTEM].
     *
     * Blocking by design and called once on the main thread before the window opens, exactly like
     * [com.azkar.i18n.LocaleStore.readAndActivate]. The palette is applied by `AzkarTheme` at composition time, so a
     * stored dark preference has to be known before the first frame; waiting for it inside the tree
     * would show one palette and then repaint into another.
     *
     * No `runCatching` of its own: [SettingsStore] already answers a failed read with an empty
     * snapshot, so there is nothing left to absorb. The wrapper this used to carry meant that the
     * two ways of reading a setting disagreed about failures, and this one could not tell an
     * unreadable store from a missing value.
     */
    suspend fun read(): ThemePreference = settings.read(key).themePreferenceOrSystemTheme()

    /** Stores [preference] for the next run. Fire and forget: a lost write costs one theme choice. */
    fun persist(preference: ThemePreference) {
        settings.write { this[key] = preference.name }
    }

    /**
     * Stores [preference] and returns once the write has landed.
     *
     * `internal` because nothing in the app needs this — [persist] exists because the settings screen
     * has already repainted by the time the write completes, and blocking it would stall the click. A
     * test reading the value back has no such repaint to hide behind, and waiting on the settings flow
     * to prove the write committed makes it depend on a signal it does not care about. Keeping it
     * non-public is what stops the fire-and-forget half from quietly becoming the default later.
     */
    internal suspend fun persistAndAwait(preference: ThemePreference) {
        settings.edit { this[key] = preference.name }
    }

    /**
     * Parses a stored name, rejecting anything this build does not recognise.
     *
     * An unknown value — a hand-edited file, a build that renamed a constant, a write that was cut off
     * mid-file — falls back to following the system rather than failing the launch, because a light
     * window is a smaller problem than no window.
     */
    private fun String?.themePreferenceOrSystemTheme(): ThemePreference =
        this?.run {
            ThemePreference.entries.firstOrNull { it.name == this }
        } ?: ThemePreference.SYSTEM

    /**
     * Writes [name] verbatim, without checking that this build recognises it.
     *
     * Only a test can need this: the whole point of [read] is that an unrecognised name resolves to
     * [ThemePreference.SYSTEM], and no production path can write one. Seeding it has to go through the
     * production key or the test would be asserting against a key nothing reads.
     */
    internal suspend fun putStoredName(name: String) {
        settings.edit { this[key] = name }
    }

    internal companion object {
        /**
         * The on-disk key, visible so a test can write an unrecognised value without inventing its
         * own.
         *
         * A suite that seeds a value has to use the same key production reads. `internal` rather than
         * public because the stored name is a storage-format concern: renaming it silently drops
         * everyone's saved theme, which the suite pinning the literal `"theme_preference"` is the
         * thing to catch.
         */
        val key = stringPreferencesKey("theme_preference")
    }
}
