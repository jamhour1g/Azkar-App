package com.azkar.domain.repo

import com.azkar.domain.model.Tag
import com.azkar.domain.model.TagSummary
import com.azkar.domain.model.TagUsage

/**
 * The port through which tags are stored and retrieved.
 *
 * Like [RemembranceRepository], absence is `null` and failure is a
 * [com.azkar.domain.exception.DataAccessException].
 *
 * Every read here returns **live** tags only — a soft-deleted row is invisible to all of them, which is
 * the whole contract `tag.deleted_at` buys. [findDeletedSummaries] is the single deliberate exception,
 * and it exists so the management screen can show the user what they took out of circulation. A
 * caller that wants a deleted row has no other way to see one, and one that does not should not be
 * able to find one by accident.
 */
interface TagRepository {
    /**
     * Stamps the tag identified by [id] as deleted, doing nothing when [id] is `null`.
     *
     * The row and every edge pointing at it are kept — see `TagTable.deletedAt` for why. Restoring is
     * [restore], and it is lossless because nothing was detached.
     */
    suspend fun softDelete(id: Long?)

    /**
     * Clears the deletion stamp on the tag identified by [id], doing nothing when [id] is `null`.
     *
     * Restores the tag *and* the shelves that were carrying it, because their `collection_tag` and
     * `remembrance_tag` rows were never removed. A tag restored onto a shelf it left an hour ago comes
     * back to that shelf, which is the behaviour that makes deleting one worth a reversible action at
     * all.
     */
    suspend fun restore(id: Long?)

    /** Inserts [tag] when it has no [Tag.id], otherwise updates it. */
    suspend fun save(tag: Tag): Tag

    /** Returns the live tag identified by [id], or `null` when [id] is `null`, unknown or deleted. */
    suspend fun findById(id: Long?): Tag?

    /** Returns the live tag named [name], or `null` when unknown or deleted. */
    suspend fun findByNameIgnoreCase(name: String): Tag?

    /** Returns every live tag. */
    suspend fun findAll(): List<Tag>

    /** Returns the live tags whose name contains [fragment]. */
    suspend fun findByNameContainingIgnoreCase(fragment: String): List<Tag>

    /**
     * Every live tag with its usage counts, in id order.
     *
     * The read the management screen is built on. Counts come from the join tables rather than from
     * counting the caller's collections and remembrances, so the screen does not have to hold the
     * library to draw a list of names.
     */
    suspend fun findSummaries(): List<TagSummary>

    /**
     * Every soft-deleted tag with its usage counts, most recently deleted first.
     *
     * The other half of [findSummaries], and the only read in this interface that will return a deleted
     * row. Ordered by the stamp rather than by id so that "what did I just remove" is at the top of a
     * list the user is most likely to be looking at immediately after removing something.
     */
    suspend fun findDeletedSummaries(): List<TagSummary>

    /**
     * The collections and remembrances carrying the live tag [id], or `null` when [id] is `null`,
     * unknown or deleted.
     *
     * The drill-down behind the management screen's per-row usage panel, and the only read here that
     * follows an id out into other aggregates. [findSummaries] answers "how many shelves carry this
     * label" from the join tables alone; this answers "which", which cannot be read off a count and
     * has to reach into both sides of the join.
     *
     * `null` rather than an empty usage for an unknown tag, because "this tag is gone" and "this tag
     * is on nothing" are different answers and a panel that opened onto an empty list for a deleted
     * tag would read as though the user had just emptied their library.
     */
    suspend fun findUsage(id: Long?): TagUsage?
}
