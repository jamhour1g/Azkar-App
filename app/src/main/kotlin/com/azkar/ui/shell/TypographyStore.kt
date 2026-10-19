package com.azkar.ui.shell

import androidx.compose.runtime.Stable
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.azkar.settings.SettingsStore
import com.azkar.ui.theme.AppTypography

/**
 * Reads and writes the reader's typographic choice: which face, and how large.
 *
 * The same shape and the same reasoning as [ThemePreferenceStore] — one object owning one key pair
 * so a caller can be handed the typography store without also being handed the [SettingsStore] it
 * sits on, and the on-disk key exposed internally so a suite seeds real values under the real key
 * instead of inventing its own.
 *
 * The two halves are stored separately rather than as one JSON blob, which is the pattern
 * [com.azkar.ui.notifications.NotificationPreferencesRepository] uses and not the one the theme store
 * uses. It is the better one here because the two values change for unrelated reasons: a reader who
 * has found the size they like keeps the face they found last week, and there is no reason to rewrite
 * one when only the other moved.
 */
@Stable
class TypographyStore(
    private val settings: SettingsStore,
) {
    /**
     * The stored choice, clamped on the way in.
     *
     * Blocking by design and read once on the main thread before the window opens, exactly like
     * [ThemePreferenceStore.read]: `AzkarTheme` applies the result at composition time, so showing
     * one face and repainting into another is the failure mode to avoid.
     */
    suspend fun read(): AppTypography {
        val family = settings.read(familyKey).orEmpty()
        val scale = settings.read(scaleKey) ?: AppTypography.DEFAULT_SCALE_PERCENT
        return AppTypography(family = family, scalePercent = scale).coerced()
    }

    /** Stores [typography] for the next run. Fire and forget: a lost write costs one choice. */
    fun persist(typography: AppTypography) {
        settings.write {
            this[familyKey] = typography.family
            this[scaleKey] = typography.scalePercent
        }
    }

    /** [TypographyStore.persist], awaited. `internal` for the same reason as its counterpart's. */
    internal suspend fun persistAndAwait(typography: AppTypography) {
        settings.edit {
            this[familyKey] = typography.family
            this[scaleKey] = typography.scalePercent
        }
    }

    /**
     * Writes a stored value verbatim, without checking this build recognises it.
     *
     * Only a suite can need this. [read] is the thing that turns an unusable value into a usable one,
     * so the only way to test that it does is to write one it will not recognise — and the family
     * half of it is the half that goes stale, when a settings file is copied to a machine that does
     * not have the face installed. The app itself never writes a family it has not seen on this
     * machine, because the picker only offers what [com.azkar.ui.theme.SystemFontCatalogue] lists.
     */
    internal suspend fun putStoredFamily(name: String) {
        settings.edit { this[familyKey] = name }
    }

    internal companion object {
        /**
         * The face name, empty string for "use the bundled pairing".
         *
         * `internal` rather than public because the literal is a storage-format concern: renaming it
         * silently drops everyone's chosen face, and a suite pinning the literal is the thing that
         * catches the rename.
         */
        val familyKey = stringPreferencesKey("typography_family")

        /** The text size as a whole percentage. Kept in step with [AppTypography.SCALE_STEPS]. */
        val scaleKey = intPreferencesKey("typography_scale")
    }
}
