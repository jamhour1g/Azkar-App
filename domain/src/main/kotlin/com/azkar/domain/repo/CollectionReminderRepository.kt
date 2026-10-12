package com.azkar.domain.repo

import com.azkar.domain.model.CollectionReminder
import java.time.Instant

/**
 * The port through which collection reminders are stored and retrieved.
 *
 * As with the other repositories, absence is `null` or an empty list and failure is a
 * [com.azkar.domain.exception.DataAccessException].
 *
 * There is no surrogate key. A collection either has a reminder or it does not, and a schedule the
 * user can hold twice for the same collection would be two rows racing to be the one that fires —
 * so the collection's own id is the key and a second row cannot be written at all.
 */
interface CollectionReminderRepository {
    /** Returns every reminder, paused ones included. */
    suspend fun findAll(): List<CollectionReminder>

    /** Returns the reminder for [collectionId], or `null` when the user has not asked for one. */
    suspend fun findByCollection(collectionId: Long): CollectionReminder?

    /**
     * Stores [reminder], replacing whatever was there for its collection.
     *
     * Returns the stored row, so a caller learns the value the database kept rather than assuming
     * the one it sent.
     */
    suspend fun save(reminder: CollectionReminder): CollectionReminder

    /** Removes the reminder for [collectionId], returning how many rows went. */
    suspend fun deleteByCollection(collectionId: Long): Int

    /**
     * Records that [collectionId]'s reminder fired at [at].
     *
     * Separate from [save] because a firing is not an edit the user made: it must not disturb the
     * schedule, and it must not be possible to lose by saving a stale copy of the reminder that was
     * read before the firing.
     */
    suspend fun markTriggered(
        collectionId: Long,
        at: Instant,
    ): Int
}
