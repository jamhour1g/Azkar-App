package com.azkar.domain.service

import com.azkar.domain.model.Tag
import com.azkar.domain.repo.TagRepository

/**
 * The application's entry point for everything the UI does with tags.
 *
 * Mirrors [RemembranceService]: reads go straight through, writes are made atomic.
 */
class TagService(
    private val repository: TagRepository,
    private val transactions: TransactionRunner,
) {
    /** Deletes [tag]. */
    suspend fun delete(tag: Tag) = transactions.run { repository.delete(tag) }

    /** Deletes the tag identified by [id], doing nothing when [id] is `null`. */
    suspend fun deleteById(id: Long?) = transactions.run { repository.deleteById(id) }

    /** Saves [tag] and returns the stored result. */
    suspend fun save(tag: Tag): Tag = transactions.run { repository.save(tag) }

    /** Returns the tag identified by [id], or `null` when [id] is `null` or unknown. */
    suspend fun findById(id: Long?): Tag? = repository.findById(id)

    /** Returns the tag named [name], compared case-insensitively, or `null` when unknown. */
    suspend fun findByNameIgnoreCase(name: String): Tag? = repository.findByNameIgnoreCase(name)

    /** Returns every known tag. */
    suspend fun findAll(): List<Tag> = repository.findAll()

    /** Returns the tags whose name contains [fragment], compared case-insensitively. */
    suspend fun findByNameContainingIgnoreCase(fragment: String): List<Tag> =
        repository.findByNameContainingIgnoreCase(fragment)
}
