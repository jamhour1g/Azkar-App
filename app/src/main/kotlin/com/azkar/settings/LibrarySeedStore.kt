package com.azkar.settings

import androidx.compose.runtime.Stable
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import com.azkar.domain.repo.LibrarySeedState
import org.jetbrains.annotations.VisibleForTesting

/**
 * The two pieces of per-installation bookkeeping the dataset bootstrap needs, kept in the settings file
 * rather than in the database.
 *
 * Both answers used to be inferred from the data and could not be. "Has the shipped dataset been
 * loaded?" was answered by looking for rows, which meant a user who emptied a collection would have
 * the seed run again over their library, and "which collection is the starred mirror?" was answered by
 * matching a catalogue key, which is exactly the identity the user is now free to edit. Storing the
 * answer is what lets the seed run once and stay run.
 *
 * Neither answer belongs in a table. Both outlive the database on purpose — a row would be deleted
 * along with the data it is supposed to outlive — but outliving it is not the same as still being
 * right about it. This file survives a database that has been deleted underneath it, and a gate
 * that survives describing a dataset that is no longer there is what leaves an installation with
 * an empty library and no way back.
 *
 * That is what [forget] is for. The two answers are dropped together, never one at a time: keeping
 * the shelf id while clearing the gate would have the bootstrap build a new starred shelf and never
 * record its number, and clearing the id while keeping the gate would leave the stars pointing at a
 * row that no longer exists.
 *
 * @see com.azkar.data.config.LibraryBootstrapper for the one-shot gate this drives
 */
@Stable
class LibrarySeedStore(
    private val settings: SettingsStore,
) : LibrarySeedState {
    /**
     * Whether the shipped dataset has already been written.
     *
     * Defaults to `false` when the key is absent, which is what a first launch and what a deleted
     * settings file both mean: nothing has been loaded yet. Treating an absent key as `true` would be
     * the one unrecoverable mistake here, because there would be no azkar and no way back to it.
     *
     * An answer on its own is not enough to skip the seed, and this class does not pretend it is:
     * the key can outlive the database it describes, so the caller reconciles it against the database
     * before acting on it. See [com.azkar.data.config.LibraryBootstrapper].
     */
    override suspend fun isSeeded(): Boolean = settings.read(seededKey) ?: false

    /**
     * Records that the dataset is in place.
     *
     * Awaited rather than fire-and-forget, unlike the stores above: the caller is about to trust this
     * value on the next launch, and a lost write would run the seed a second time over a library the
     * user has since edited.
     */
    override suspend fun markSeeded() {
        settings.edit { this[seededKey] = true }
    }

    /**
     * Drops both answers.
     *
     * Removes the keys rather than writing a false over the gate: an absent key already means
     * "nothing loaded yet", and leaving the file holding one is a way for a reader to tell a
     * cleared answer from an answered question.
     */
    override suspend fun forget() {
        settings.edit {
            remove(seededKey)
            remove(favoritesCollectionIdKey)
            remove(uncategorizedCollectionIdKey)
            remove(reminderPicksCollectionIdKey)
        }
    }

    /**
     * The id of the collection that mirrors the user's stars, or `null` when it has never been created.
     *
     * `null` is a real answer rather than a failure: it means the bootstrap has not run yet, and the
     * shelf is created on the way past. Callers must not fall back to searching by name, because a
     * renamed shelf would then be found twice.
     */
    override suspend fun favoritesCollectionId(): Long? = settings.read(favoritesCollectionIdKey)

    /** Points the mirror at [collectionId] and returns once the write has landed. */
    override suspend fun setFavoritesCollectionId(collectionId: Long) {
        settings.edit { this[favoritesCollectionIdKey] = collectionId }
    }

    override suspend fun uncategorizedCollectionId(): Long? = settings.read(uncategorizedCollectionIdKey)

    override suspend fun setUncategorizedCollectionId(collectionId: Long) {
        settings.edit { this[uncategorizedCollectionIdKey] = collectionId }
    }

    /**
     * The id of the "Reminder picks" shelf, or `null` when the user has not picked a passage yet.
     *
     * The shelf is created on the first pick, so `null` is the ordinary answer on a fresh
     * installation and on one that has never asked for a single-passage reminder.
     */
    override suspend fun reminderPicksCollectionId(): Long? = settings.read(reminderPicksCollectionIdKey)

    override suspend fun setReminderPicksCollectionId(collectionId: Long) {
        settings.edit { this[reminderPicksCollectionIdKey] = collectionId }
    }

    override suspend fun clearReminderPicksCollectionId() {
        settings.edit { remove(reminderPicksCollectionIdKey) }
    }

    internal companion object {
        /**
         * On-disk keys, `internal` for the same reason [com.azkar.i18n.LocaleStore]'s is: the literal
         * is a storage-format concern, and the suite pinning it is what catches a rename that would
         * silently re-seed every library.
         */
        @VisibleForTesting
        val seededKey = booleanPreferencesKey("seed_completed")

        @VisibleForTesting
        val favoritesCollectionIdKey = longPreferencesKey("favorites_collection_id")

        @VisibleForTesting
        val uncategorizedCollectionIdKey = longPreferencesKey("uncategorized_collection_id")

        @VisibleForTesting
        val reminderPicksCollectionIdKey = longPreferencesKey("reminder_picks_collection_id")
    }
}
