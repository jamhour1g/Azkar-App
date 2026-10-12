package com.azkar.domain.repo

import com.azkar.domain.model.RecitationProgress
import com.azkar.domain.model.RecitationSummary

/**
 * The port through which recitation progress is stored and retrieved.
 *
 * Like the other repositories, absence is an empty list and failure is a
 * [com.azkar.domain.exception.DataAccessException].
 *
 * Progress is written as *deltas* rather than as whole rows because the only way a count changes is a
 * tap, and a tap that read the row first would race with the user's other taps on another screen.
 * [bump] adds inside the transaction instead, so the count a user sees is always the count that was
 * actually stored.
 */
interface RecitationRepository {
    /** Returns the progress recorded against every member of [collectionId]. */
    suspend fun findByCollection(collectionId: Long): List<RecitationProgress>

    /** Returns the progress recorded for [remembranceId] in each collection that holds it. */
    suspend fun findByRemembrance(remembranceId: Long): List<RecitationProgress>

    /**
     * Folds the progress of every named collection into one [RecitationSummary], keyed by collection.
     *
     * A separate read from [findByCollection] because the caller wants all of them at once — the
     * Home rail shows several side by side — and reading them one at a time would be a round trip
     * per card for a figure that one grouped query answers outright.
     *
     * A collection with no recited members is absent from the map rather than present with a zero,
     * so a caller can tell "nothing recorded" from "not one of the ones I asked about".
     */
    suspend fun findSummaries(collectionIds: List<Long>): Map<Long, RecitationSummary>

    /**
     * Adds each value of [deltas] to the matching member's count, clamping at zero.
     *
     * Returns how many members changed. Members with no row yet get one, so a first recitation of a
     * passage is stored the same way as any other.
     */
    suspend fun bump(
        collectionId: Long,
        deltas: Map<Long, Int>,
    ): Int

    /** Overwrites one member's count, discarding whatever it was. */
    suspend fun setCount(
        collectionId: Long,
        remembranceId: Long,
        count: Int,
    )

    /** Forgets every member's progress in [collectionId], returning how many rows were dropped. */
    suspend fun reset(collectionId: Long): Int
}
