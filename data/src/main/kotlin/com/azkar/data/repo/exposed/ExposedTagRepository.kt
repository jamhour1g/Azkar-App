package com.azkar.data.repo.exposed

import com.azkar.data.mapping.toSummary
import com.azkar.data.mapping.toTag
import com.azkar.data.repo.exposed.CollectionAggregates.summaries
import com.azkar.data.repo.exposed.LikeUtils.foldedContains
import com.azkar.data.repo.exposed.RemembranceAggregates.usageRows
import com.azkar.data.table.CollectionTable
import com.azkar.data.table.CollectionTagTable
import com.azkar.data.table.RemembranceTable
import com.azkar.data.table.RemembranceTagTable
import com.azkar.data.table.TagTable
import com.azkar.data.table.entity.CollectionEntity
import com.azkar.data.table.entity.CollectionTagEntity
import com.azkar.data.table.entity.RemembranceEntity
import com.azkar.data.table.entity.RemembranceTagEntity
import com.azkar.data.table.entity.TagEntity
import com.azkar.data.table.entity.updateAndFlush
import com.azkar.domain.model.Tag
import com.azkar.domain.model.TagSummary
import com.azkar.domain.model.TagUsage
import com.azkar.domain.model.impl.TagImpl
import com.azkar.domain.repo.TagRepository
import com.azkar.domain.service.TransactionRunner
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.neq

/**
 * Exposed-backed [TagRepository].
 *
 * Names are stored folded, so the lookups below fold the name they are handed and compare it
 * directly — no `LOWER()` in the predicate, and the plain unique index on `tag.name` serves them.
 * See [Tag.fold] for why the index can be plain and still be case-insensitive in practice.
 *
 * Every method runs inside [tx]; none of them opens a transaction of its own, so a caller that
 * already owns one joins it instead of starting a second.
 *
 * ## The live/deleted split
 *
 * `tag.deleted_at` is what makes deleting a tag something other than a cascade, so this class is the
 * place that split has to be enforced, and it is enforced twice: in the **queries** below, which carry
 * `deletedAt eq null`, and in the **helpers**, which come in a live and an all-rows flavour. The
 * second is the one that is easy to get wrong. A live-only `findTag` is what every read should use; the
 * three writers that must be able to see a tombstone — [softDelete], [restore] and [findOrCreateId] —
 * ask for [findTagRow] instead, because a deleted tag is exactly the row they are looking for.
 *
 * A read that used the wrong one of these does not fail. It returns `null`, or quietly stops finding
 * the tag the user is looking at, and nothing anywhere reports it. Hence two names rather than a flag:
 * `findTag` and `findTagRow` are called from disjoint sets of methods by construction.
 */
class ExposedTagRepository(
    private val transactions: TransactionRunner,
) : TagRepository {
    /**
     * Stamps [id] as deleted and leaves everything else alone.
     *
     * Notably *not* detaching the join rows, which is what the hard delete this replaced had to do and
     * what makes this one worth having: `collection_tag` and `remembrance_tag` still point at the tag,
     * so [restore] puts a collection's chips back exactly as they were rather than asking the user to
     * re-apply a label to every shelf they had put it on.
     *
     * Already-deleted is not an error and is not re-stamped. The second press of a delete button on a
     * row that has already gone should leave the original timestamp alone, so the "deleted most
     * recently" ordering the management screen shows still reflects the first removal.
     */
    override suspend fun softDelete(id: Long?) {
        val target = id ?: return
        transactions.tx("Failed to delete tag id=$target") {
            val row = findTagRow(target) ?: return@tx
            if (row.deletedAt != null) {
                return@tx
            }
            val timestamp = now()
            row.updateAndFlush {
                deletedAt = timestamp
                updatedAt = timestamp
            }
        }
    }

    /**
     * Clears the stamp, bringing the tag back to every shelf it was on.
     *
     * Nothing to re-attach, which is the whole reason [softDelete] kept the edges. A tag that was
     * never on anything comes back on nothing, and one that was on nine collections comes back on nine
     * collections.
     */
    override suspend fun restore(id: Long?) {
        val target = id ?: return
        transactions.tx("Failed to restore tag id=$target") {
            val row = findTagRow(target) ?: return@tx
            if (row.deletedAt == null) {
                return@tx
            }
            row.updateAndFlush {
                deletedAt = null
                updatedAt = now()
            }
        }
    }

    override suspend fun save(tag: Tag): Tag =
        transactions.tx("Failed to save tag") {
            val existingId = tag.id
            // `tag.name` is written by TagImpl too, but folding here means the value that
            // comes back from the row and the value that went into it cannot disagree even
            // if some other implementation of Tag is ever handed to this repository.
            val name = Tag.fold(tag.name)
            // A deleted tag keeps its row, so a plain insert below would run into `uq_tag__name`
            // the first time anybody re-typed a name they had just deleted, and fail with a
            // constraint violation for what is a perfectly ordinary "add it back". Reviving is what
            // the user means by that, and it is the only outcome that can bring the tag's shelves
            // back with it — the join rows are still pointing at that id. See [reviveByName].
            if (existingId == null) {
                reviveByName(name) ?: run {
                    val timestamp = now()
                    val inserted =
                        TagEntity.new {
                            this.name = name
                            createdAt = timestamp
                            updatedAt = timestamp
                        }
                    TagImpl(id = inserted.id.value, name = name, createdAt = inserted.createdAt.toInstant())
                }
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
            liveRows().orderBy(TagTable.id to SortOrder.ASC).map { it.toTag() }
        }

    override suspend fun findByNameContainingIgnoreCase(fragment: String): List<Tag> =
        transactions.tx("Failed to search tags by name '$fragment'") {
            TagEntity
                .find { (TagTable.name.foldedContains(fragment)) and isLive }
                .orderBy(TagTable.id to SortOrder.ASC)
                .map { it.toTag() }
        }

    override suspend fun findSummaries(): List<TagSummary> =
        transactions.tx("Failed to read tag summaries") {
            summarize(liveRows().orderBy(TagTable.id to SortOrder.ASC).toList())
        }

    override suspend fun findDeletedSummaries(): List<TagSummary> =
        transactions.tx("Failed to read deleted tag summaries") {
            // Ordered by the stamp rather than by id so that the tag the user just removed is the one at
            // the top of the list they are most likely to be looking at, and `DESC` over a column that is
            // non-null on every row of this query puts the most recent deletion there by construction.
            summarize(
                TagEntity
                    .find { TagTable.deletedAt neq null }
                    .orderBy(TagTable.deletedAt to SortOrder.DESC, TagTable.id to SortOrder.DESC)
                    .toList(),
            )
        }

    /**
     * The shelves and remembrances carrying the tag [id], or `null` when it names no live tag.
     *
     * Four statements, in a fixed order, and each one is a plain single-table read folded in Kotlin —
     * the same trade [summarize] makes, for the same reason. There is no `GROUP BY` here because
     * unlike the counts this read has to produce the rows themselves, so there is nothing to aggregate
     * away. What it does not do is load the collections' members or the remembrances' explanations:
     * a panel row wants a name, a count and a preview, and [CollectionAggregates.summaries] and
     * [RemembranceAggregates.usageRows] are what produce those without assembling either aggregate.
     *
     * The liveness test is [findTag] rather than [findTagRow], because the panel is reachable only
     * from a live row and answering `null` for a tombstone is what lets the screen tell "you deleted
     * this" apart from "this tag is on nothing".
     */
    override suspend fun findUsage(id: Long?): TagUsage? {
        val target = id ?: return null
        return transactions.tx("Failed to read usage of tag id=$target") {
            val row = findTag(target) ?: return@tx null
            val collectionIds =
                CollectionTagEntity
                    .find { CollectionTagTable.tagId eq target }
                    .map { it.collectionId.value }
                    .toList()
            val remembranceIds =
                RemembranceTagEntity
                    .find { RemembranceTagTable.tagId eq target }
                    .map { it.remembranceId.value }
                    .toList()
            TagUsage(
                // Reuses the same two join reads the list makes, so the counts in the panel header can
                // never disagree with the counts on the row that opened it.
                tag = summarize(listOf(row)).single(),
                collections = CollectionEntity.find { CollectionTable.id inList collectionIds }.summaries(),
                remembrances = RemembranceEntity.find { RemembranceTable.id inList remembranceIds }.usageRows(),
            )
        }
    }

    /**
     * Resolves a tag name to its id, inserting the tag when it is not there yet.
     *
     * Lives here rather than in the remembrance repository because tags are a shared
     * aggregate: two remembrances naming the same tag must land on the same row. Must be
     * called from inside a transaction — the caller already owns one, which is why this
     * deliberately bypasses [transactions] and is not `suspend`.
     *
     * A name belonging to a **soft-deleted** tag revives that tag rather than inserting a second row
     * for it. Two reasons, and the second is the one that matters: the unique index on `tag.name` is
     * over the whole table and would reject the insert outright, so the alternative is not "two tags
     * with the same name" but "a constraint violation"; and reviving hands the user back the shelves
     * the deleted tag was still attached to, so re-typing a label they had only just removed restores
     * their organisation rather than quietly discarding it.
     */
    fun findOrCreateId(name: String): Long {
        val folded = Tag.fold(name)
        val existing = findTagRowByName(folded)
        if (existing != null) {
            if (existing.deletedAt != null) {
                existing.updateAndFlush {
                    deletedAt = null
                    updatedAt = now()
                }
            }
            return existing.id.value
        }
        val timestamp = now()
        return TagEntity
            .new {
                this.name = folded
                createdAt = timestamp
                updatedAt = timestamp
            }.id.value
    }

    /**
     * Brings the deleted tag called [name] back, or returns `null` when nothing is holding the name.
     *
     * The other half of [findOrCreateId]: that one is asked for an id by a writer that is about to
     * attach the tag to something, this one by [save] for a caller that has just been handed a tag
     * with no id and needs to know whether the name is free or merely taken by a tombstone. Split
     * because the two answer different questions — one has to resolve the name either way, the other
     * is only interested in the case where an insert would fail — and sharing a single method would
     * make [save] insert by way of a function whose contract is "this name now has an id".
     *
     * `null` also covers a name held by a *live* tag. [save] must not be reviving one of those, and
     * the uniqueness that stops it is the caller's to enforce: the domain checks the name with
     * [com.azkar.domain.service.TagService] before it gets here.
     */
    private fun reviveByName(name: String): Tag? {
        val row = findTagRowByName(name)
        if (row == null || row.deletedAt == null) {
            return null
        }
        return row
            .updateAndFlush {
                deletedAt = null
                updatedAt = now()
            }.toTag()
    }

    /**
     * The live rows, in whatever order the caller asks for.
     *
     * A `SIZED_ITERABLE` rather than a `List` because both of its callers chain an `orderBy` onto it,
     * which is the one thing that turns it into rows again. Naming it does the work of keeping the
     * `deleted_at IS NULL` predicate in one place; there are six reads on this class and a sixth
     * hand-written copy of it is where a deleted tag would leak back into the library.
     */
    private fun liveRows() = TagEntity.find { isLive }

    /**
     * The live row with this id.
     *
     * `deleted_at IS NULL` as a second predicate rather than a Java-side `?.takeIf`, because the filter
     * has to hold for the write path too: [save] uses this to decide which row it is updating, and a
     * row this returns must never be a tombstone.
     */
    private fun findTag(id: Long): TagEntity? =
        TagEntity
            .find { (TagTable.id eq id) and isLive }
            .firstOrNull()

    /** The row with this id whether or not it is deleted. For the writers, never for a read. */
    private fun findTagRow(id: Long): TagEntity? = TagEntity.findById(id)

    /**
     * Resolves a name to its row, folding first.
     *
     * The predicate is a plain equality against the unique index rather than `LOWER(name) = …`,
     * which is only the same lookup because everything in the column is already folded.
     */
    private fun findTagByName(name: String): TagEntity? =
        TagEntity
            .find { (TagTable.name eq Tag.fold(name)) and isLive }
            .orderBy(TagTable.id to SortOrder.ASC)
            .firstOrNull()

    /**
     * [findTagByName] without the liveness test, for [findOrCreateId]'s revive-then-insert path.
     *
     * The counterpart to [findTagRow], and the reason deleting and re-typing a label round-trips
     * instead of failing on the unique index.
     */
    private fun findTagRowByName(name: String): TagEntity? =
        TagEntity
            .find { TagTable.name eq Tag.fold(name) }
            .orderBy(TagTable.id to SortOrder.ASC)
            .firstOrNull()

    /**
     * Pairs the given rows with their usage counts, in one pass over each join table.
     *
     * Two `GROUP BY`-shaped reads rather than a correlated subquery per tag, because the screen asks
     * for every tag at once and a per-tag subquery would be one statement per row on the list.
     *
     * The counts are folded in Kotlin off rows already fetched, the same trade [RecitationProgress]'
     * summary read makes and for the same reason: the join tables are a few hundred rows, and this
     * keeps each statement a plain single-table read rather than an aggregate whose result is then
     * matched back to the tag rows by key.
     */
    private fun summarize(rows: List<TagEntity>): List<TagSummary> {
        if (rows.isEmpty()) {
            return emptyList()
        }
        val ids = rows.map { it.id.value }
        val collectionCounts =
            CollectionTagEntity
                .find { CollectionTagTable.tagId inList ids }
                .groupBy({ it.tagId.value }, { Unit })
                .mapValues { (_, links) -> links.size }
        val remembranceCounts =
            RemembranceTagEntity
                .find { RemembranceTagTable.tagId inList ids }
                .groupBy({ it.tagId.value }, { Unit })
                .mapValues { (_, links) -> links.size }
        return rows.map { row ->
            val id = row.id.value
            row.toSummary(
                collectionCount = collectionCounts[id] ?: 0,
                remembranceCount = remembranceCounts[id] ?: 0,
            )
        }
    }

    private companion object {
        /**
         * `deleted_at IS NULL`, spelled once for every read on this class.
         *
         * The reason it is a constant rather than repeated is that this predicate is the entire
         * definition of "live" in this module, and a copy that drifted from it would be invisible: it
         * would return a deleted tag, which renders as an ordinary chip on an ordinary shelf.
         */
        private val isLive
            get() = TagTable.deletedAt eq null
    }
}
