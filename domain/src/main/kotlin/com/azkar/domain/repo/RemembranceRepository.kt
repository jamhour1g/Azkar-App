package com.azkar.domain.repo

import com.azkar.domain.model.Remembrance
import java.util.Locale

/**
 * The port through which remembrances are stored and retrieved.
 *
 * The domain declares what it needs; `com.azkar.data` decides how that is satisfied. Every
 * operation is `suspend` because each one is backed by storage, and the caller should not have to
 * know that — hopping to a worker thread is the data layer's job, not every call site's.
 *
 * Implementations report absence as `null` and failure as a
 * [com.azkar.domain.exception.DataAccessException]; neither is expressed by throwing.
 */
interface RemembranceRepository {
    /** Inserts [remembrance] when it has no [Remembrance.id], otherwise updates it. */
    suspend fun save(remembrance: Remembrance): Remembrance

    /** Deletes [remembrance]. */
    suspend fun delete(remembrance: Remembrance)

    /** Deletes the remembrance identified by [id]. */
    suspend fun deleteById(id: Long)

    /** Returns the remembrance identified by [id], or `null` when no such row exists. */
    suspend fun findById(id: Long): Remembrance?

    /** Returns every known remembrance. */
    suspend fun findAll(): List<Remembrance>

    /**
     * Returns whichever remembrance comes first, or `null` when there are none.
     *
     * Exists so that "give me one" is a question the storage layer can answer cheaply. Asking it as
     * `findAll().firstOrNull()` at the call site reads the entire table and assembles the full
     * aggregate of every row in it — translations, explanations and tags for each — only to discard
     * all but one, so the cost of showing a single hadith grows with the size of the library.
     */
    suspend fun findFirst(): Remembrance?

    /** Returns the remembrances carrying a tag named [tagName], compared case-insensitively. */
    suspend fun findByTagNameIgnoreCase(tagName: String): List<Remembrance>

    /** Returns the remembrances marked as favorites. */
    suspend fun findFavorites(): List<Remembrance>

    /** Returns the remembrances matching [query] in the [locale]'s translations. */
    suspend fun search(
        locale: Locale,
        query: String,
    ): List<Remembrance>

    /**
     * Marks the remembrance identified by [id] as a favorite.
     *
     * @throws com.azkar.domain.exception.RemembranceNotFoundException when no such row exists
     */
    suspend fun markFavorite(id: Long)

    /**
     * Clears the favorite flag of the remembrance identified by [id].
     *
     * @throws com.azkar.domain.exception.RemembranceNotFoundException when no such row exists
     */
    suspend fun unmarkFavorite(id: Long)
}
