package com.azkar.data.repo.exposed

import com.azkar.data.mapping.toTag
import com.azkar.data.repo.exposed.LikeUtils.foldedContains
import com.azkar.data.table.RemembranceTagTable
import com.azkar.data.table.TagTable
import com.azkar.data.table.entity.TagEntity
import com.azkar.data.table.entity.updateAndFlush
import com.azkar.domain.model.Tag
import com.azkar.domain.model.impl.TagImpl
import com.azkar.domain.repo.TagRepository
import com.azkar.domain.service.TransactionRunner
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere

/**
 * Exposed-backed [TagRepository].
 *
 * Names are stored folded, so the lookups below fold the name they are handed and compare it
 * directly — no `LOWER()` in the predicate, and the plain unique index on `tag.name` serves them.
 * See [Tag.fold] for why the index can be plain and still be case-insensitive in practice.
 *
 * Every method runs inside [tx]; none of them opens a transaction of its own, so a caller that
 * already owns one joins it instead of starting a second.
 */
class ExposedTagRepository(
    private val transactions: TransactionRunner,
) : TagRepository {
    /** A tag that was never persisted carries no id, and there is nothing to delete. */
    override suspend fun delete(tag: Tag) {
        val id = tag.id ?: return
        deleteById(id)
    }

    override suspend fun deleteById(id: Long?) {
        val target = id ?: return
        transactions.tx("Failed to delete tag id=$target") {
            // `remembrance_tag.tag_id` cascades, so detaching first is not required — but
            // doing it explicitly keeps the join table tidy on drivers that defer cascades.
            // A set-based delete rather than an entity per row: there is nothing to read off
            // these links, and the entity path would mean one statement per remembrance.
            RemembranceTagTable.deleteWhere { RemembranceTagTable.tagId eq target }
            TagEntity.findById(target)?.delete()
        }
    }

    override suspend fun save(tag: Tag): Tag =
        transactions.tx("Failed to save tag") {
            val existingId = tag.id
            // `tag.name` is written by TagImpl too, but folding here means the value that
            // comes back from the row and the value that went into it cannot disagree even
            // if some other implementation of Tag is ever handed to this repository.
            val name = Tag.fold(tag.name)
            if (existingId == null) {
                val timestamp = now()
                val inserted =
                    TagEntity.new {
                        this.name = name
                        createdAt = timestamp
                        updatedAt = timestamp
                    }
                TagImpl(id = inserted.id.value, name = name, createdAt = inserted.createdAt.toInstant())
            } else {
                findTag(existingId)
                    ?.updateAndFlush {
                        this.name = name
                        updatedAt = now()
                    }?.toTag()
                    ?: error("Tag $existingId vanished during save")
            }
        }

    override suspend fun findById(id: Long?): Tag? {
        val target = id ?: return null
        return transactions.tx("Failed to find tag id=$target") { findTag(target)?.toTag() }
    }

    override suspend fun findByNameIgnoreCase(name: String): Tag? =
        transactions.tx("Failed to find tag by name '$name'") { findTagByName(name)?.toTag() }

    override suspend fun findAll(): List<Tag> =
        transactions.tx("Failed to find all tags") {
            TagEntity.all().orderBy(TagTable.id to SortOrder.ASC).map { it.toTag() }
        }

    override suspend fun findByNameContainingIgnoreCase(fragment: String): List<Tag> =
        transactions.tx("Failed to search tags by name '$fragment'") {
            TagEntity
                .find { TagTable.name.foldedContains(fragment) }
                .orderBy(TagTable.id to SortOrder.ASC)
                .map { it.toTag() }
        }

    /**
     * Resolves a tag name to its id, inserting the tag when it is not there yet.
     *
     * Lives here rather than in the remembrance repository because tags are a shared
     * aggregate: two remembrances naming the same tag must land on the same row. Must be
     * called from inside a transaction — the caller already owns one, which is why this
     * deliberately bypasses [transactions] and is not `suspend`.
     */
    fun findOrCreateId(name: String): Long {
        findTagByName(name)?.let { return it.id.value }
        val timestamp = now()
        return TagEntity
            .new {
                this.name = Tag.fold(name)
                createdAt = timestamp
                updatedAt = timestamp
            }.id.value
    }

    private fun findTag(id: Long): TagEntity? = TagEntity.findById(id)

    /**
     * Resolves a name to its row, folding first.
     *
     * The predicate is a plain equality against the unique index rather than `LOWER(name) = …`,
     * which is only the same lookup because everything in the column is already folded.
     */
    private fun findTagByName(name: String): TagEntity? =
        TagEntity
            .find { TagTable.name eq Tag.fold(name) }
            .orderBy(TagTable.id to SortOrder.ASC)
            .firstOrNull()
}
