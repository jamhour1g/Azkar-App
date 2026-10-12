package com.azkar.domain.service

import com.azkar.domain.exception.TagNameTakenException
import com.azkar.domain.model.Tag
import com.azkar.domain.model.TagSummary
import com.azkar.domain.model.TagUsage
import com.azkar.domain.model.impl.TagImpl
import com.azkar.domain.repo.TagRepository

/**
 * The application's entry point for everything the UI does with tags.
 *
 * Mirrors [RemembranceService]: reads go straight through, writes are made atomic.
 *
 * The one place a tag write is checked before it is attempted. [rename] and [create] are the only
 * operations where the domain has a rule the database merely enforces — `tag.name` is unique, but
 * "somebody else already holds this name" is a statement about the user's edit, and by the time the
 * driver raises it there is nothing left to attach it to.
 */
class TagService(
    private val repository: TagRepository,
    private val transactions: TransactionRunner,
) {
    /** Deletes the tag identified by [id], doing nothing when [id] is `null`. */
    suspend fun deleteById(id: Long?) = transactions.run { repository.softDelete(id) }

    /**
     * Brings back the tag identified by [id], with everything it was carrying, doing nothing when [id]
     * is `null`.
     */
    suspend fun restore(id: Long?) = transactions.run { repository.restore(id) }

    /** Saves [tag] and returns the stored result. */
    suspend fun save(tag: Tag): Tag = transactions.run { repository.save(tag) }

    /**
     * Renames the tag [id] to [newName] and returns the stored result.
     *
     * The conflict check and the write share one transaction rather than being two calls, so that two
     * renames racing for the same name cannot both pass the check and leave one of them to fail on the
     * index. `TagImpl` folds the name before it reaches the repository, which is what lets the
     * comparison here be an equality against the index rather than a `LOWER(...)` scan.
     *
     * Renaming a tag onto the name it already has is not a conflict and succeeds: the lookup that
     * detects the clash ignores the tag being renamed, and returning early when the folded names match
     * means an editor the user opened and confirmed without typing is not reported as an error.
     */
    suspend fun rename(
        id: Long,
        newName: String,
    ): Tag =
        transactions.run {
            val renamed = TagImpl(id = id, name = newName)
            val clash = repository.findByNameIgnoreCase(renamed.name)
            if (clash != null && clash.id != id) {
                throw TagNameTakenException(renamed.name)
            }
            repository.save(renamed)
        }

    /**
     * Adds a tag called [name] and returns it.
     *
     * The same check as [rename] for the same reason, and one transaction for the same reason: a name
     * is taken or it is not, and the answer and the write have to agree about which.
     *
     * Only live tags block the name. A tag the user deleted is not in the way, and retyping its name
     * is how a deleted tag comes back — see `ExposedTagRepository.save`, which revives the tombstone
     * rather than inserting over it. That is the one behaviour here that is not obvious from the code
     * above it, and it is why this is not just [save] with a check bolted on: what "add" means when
     * the name is free-but-recently-used is a question about the delete, not about the insert.
     *
     * A blank [name] is rejected by `TagImpl`, which is where the rule belongs rather than in a guard
     * here — a tag with no name is not a tag, and every path that builds one should have to answer for
     * it.
     */
    suspend fun create(name: String): Tag =
        transactions.run {
            val created = TagImpl(name = name)
            if (repository.findByNameIgnoreCase(created.name) != null) {
                throw TagNameTakenException(created.name)
            }
            repository.save(created)
        }

    /** Returns the live tag identified by [id], or `null` when [id] is `null`, unknown or deleted. */
    suspend fun findById(id: Long?): Tag? = repository.findById(id)

    /** Returns the live tag named [name], or `null` when unknown or deleted. */
    suspend fun findByNameIgnoreCase(name: String): Tag? = repository.findByNameIgnoreCase(name)

    /** Returns every live tag. */
    suspend fun findAll(): List<Tag> = repository.findAll()

    /** Returns the live tags whose name contains [fragment]. */
    suspend fun findByNameContainingIgnoreCase(fragment: String): List<Tag> =
        repository.findByNameContainingIgnoreCase(fragment)

    /** Every live tag with its usage counts. */
    suspend fun findSummaries(): List<TagSummary> = repository.findSummaries()

    /** Every soft-deleted tag with its usage counts. */
    suspend fun findDeletedSummaries(): List<TagSummary> = repository.findDeletedSummaries()

    /**
     * The shelves and remembrances carrying [id], or `null` when it names no live tag.
     *
     * A read, so it goes straight through without taking a transaction — the same as every other
     * read here. There is no rule for the domain to enforce on the way in, and wrapping it would
     * only add a boundary the database does not need for a single joined statement.
     */
    suspend fun findUsage(id: Long?): TagUsage? = repository.findUsage(id)
}
