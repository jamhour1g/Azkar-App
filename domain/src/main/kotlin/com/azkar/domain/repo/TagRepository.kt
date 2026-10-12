package com.azkar.domain.repo

import com.azkar.domain.model.Tag

/**
 * The port through which tags are stored and retrieved.
 *
 * Like [RemembranceRepository], absence is `null` and failure is a
 * [com.azkar.domain.exception.DataAccessException].
 */
interface TagRepository {
    /** Deletes [tag]. */
    suspend fun delete(tag: Tag)

    /** Deletes the tag identified by [id], doing nothing when [id] is `null`. */
    suspend fun deleteById(id: Long?)

    /** Inserts [tag] when it has no [Tag.id], otherwise updates it. */
    suspend fun save(tag: Tag): Tag

    /** Returns the tag identified by [id], or `null` when [id] is `null` or unknown. */
    suspend fun findById(id: Long?): Tag?

    /** Returns the tag named [name], compared case-insensitively, or `null` when unknown. */
    suspend fun findByNameIgnoreCase(name: String): Tag?

    /** Returns every known tag. */
    suspend fun findAll(): List<Tag>

    /** Returns the tags whose name contains [fragment], compared case-insensitively. */
    suspend fun findByNameContainingIgnoreCase(fragment: String): List<Tag>
}
