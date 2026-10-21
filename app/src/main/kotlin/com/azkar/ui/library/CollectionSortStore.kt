package com.azkar.ui.library

import androidx.compose.runtime.Stable
import androidx.datastore.preferences.core.stringPreferencesKey
import com.azkar.settings.SettingsStore
import com.azkar.ui.library.model.CollectionSort

/**
 * Reads and writes the order the shelf is sorted by.
 *
 * The sort used to be rebuilt from nothing every time the library opened, which meant the shelf
 * forgot the user's choice the moment they closed it — the one thing a sort control is for. It is a
 * preference about the shelf rather than about the app, so it lives with the shelf's own state
 * rather than in the settings screen, and it is a *store* rather than a field on the state holder
 * for the reason [com.azkar.ui.shell.ThemePreferenceStore] gives: the write is fire and forget on
 * the settings store's scope, and a test needs to be able to read back what it wrote instead of
 * waiting on an unrelated flow to notice it land.
 *
 * The stored form is the enum's name, so a sort that this build does not know about is rejected
 * rather than half-applied.
 */
@Stable
class CollectionSortStore(
    private val settings: SettingsStore,
) {
    /**
     * The stored order for this run, defaulting to [CollectionSort.MOST_ITEMS].
     *
     * The default is the full shelves first, because that is the order a library user browses in:
     * the biggest collections are the ones worth opening, and a shelf sorted by when a collection
     * was last touched puts the emptiest ones in front.
     */
    suspend fun read(): CollectionSort = settings.read(key).storedSort()

    /**
     * Stores [sort] for the next run. Fire and forget: a lost write costs one sort choice, and
     * blocking the click that made it would be a worse trade than the choice.
     */
    fun persist(sort: CollectionSort) {
        settings.write { this[key] = sort.name }
    }

    /**
     * Stores [sort] and returns once the write has landed.
     *
     * `internal` because nothing in the app needs this — [persist] exists because the shelf has
     * already repainted by the time the write completes, and blocking it would stall the click. A
     * test reading the value back has no such repaint to hide behind, and waiting on the settings
     * flow to prove the write committed makes it depend on a signal it does not care about.
     */
    internal suspend fun persistAndAwait(sort: CollectionSort) {
        settings.edit { this[key] = sort.name }
    }

    /**
     * Parses a stored name, rejecting anything this build does not recognise.
     *
     * An unknown value — a hand-edited file, a build that renamed a constant, a write that was cut
     * off mid-file — falls back to the default rather than failing the launch, because a shelf in
     * the wrong order is a smaller problem than no shelf.
     */
    private fun String?.storedSort(): CollectionSort =
        this?.run {
            CollectionSort.entries.firstOrNull { it.name == this }
        } ?: CollectionSort.MOST_ITEMS

    /**
     * Writes [name] verbatim, without checking that this build recognises it.
     *
     * Only a test can need this: the whole point of [read] is that an unrecognised name resolves to
     * the default, and no production path can write one. Seeding it has to go through the production
     * key or the test would be asserting against a key nothing reads.
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
         * everyone's saved order, which the suite pinning the literal is the thing to catch.
         */
        val key = stringPreferencesKey("library_collection_sort")
    }
}
