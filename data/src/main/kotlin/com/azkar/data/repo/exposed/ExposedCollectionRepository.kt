package com.azkar.data.repo.exposed

import com.azkar.data.repo.exposed.CollectionAggregates.load
import com.azkar.data.repo.exposed.CollectionAggregates.summaries
import com.azkar.data.table.CollectionMemberTable
import com.azkar.data.table.CollectionTable
import com.azkar.data.table.CollectionTagTable
import com.azkar.data.table.entity.CollectionEntity
import com.azkar.data.table.entity.CollectionMemberEntity
import com.azkar.data.table.entity.CollectionTagEntity
import com.azkar.data.table.entity.collectionMemberId
import com.azkar.data.table.entity.collectionTagId
import com.azkar.data.table.entity.updateAndFlush
import com.azkar.domain.exception.CollectionNotFoundException
import com.azkar.domain.model.CollectionSummary
import com.azkar.domain.model.RemembranceCollection
import com.azkar.domain.repo.CollectionRepository
import com.azkar.domain.service.TransactionRunner
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.time.OffsetDateTime

/**
 * Exposed-backed [CollectionRepository].
 *
 * One method per call the domain makes, each one a transaction and a translation of a driver
 * failure. Everything below that surface is the strategy those methods are built from, kept private
 * here rather than pushed into a loader and a writer pair.
 *
 * The pair used to exist, and the argument for it was that folding a repository's strategy back into
 * the repository would bury it under sixteen repetitions of the same three lines of transaction
 * boilerplate. That argument stopped being true once the pair collapsed into one call — see [tx] —
 * and with it went the reason for the classes themselves. Of the twenty-one functions they held,
 * thirteen read or wrote a single row and handed it straight back under a different name: a
 * `touchLastOpened` that called a `touchLastOpened`, a `setTags` that became `replaceTags`, a
 * `findAll` that became `loadAll`. That is a hop between two files, not a decision kept in one of
 * them, and the classes were `internal` and reached by no test except through here, so the split
 * bought no seam either.
 *
 * The strategy is what survives, and it is kept whole below: a collection is assembled out of four
 * tables in a fixed number of statements, a removal closes the gap it leaves behind, labels are
 * diffed against the ids they resolve to rather than the names typed, and a run of positions shifts
 * rather than swaps. None of that is visible in a method that only forwards, and all of it is now a
 * private method of the one type that needs it.
 *
 * Every method runs inside [transactions]; none of them opens a transaction of its own, so a
 * service-level transaction and the repository method nested inside it commit or roll back as one
 * unit rather than competing for the same rows.
 */
class ExposedCollectionRepository(
    private val transactions: TransactionRunner,
    private val tagRepository: ExposedTagRepository,
) : CollectionRepository {
    override suspend fun findAll(): List<RemembranceCollection> =
        transactions.tx("Failed to find all collections") {
            loadAll()
        }

    /** A collection that was never persisted carries no id, and there is nothing to look up. */
    override suspend fun findById(id: Long?): RemembranceCollection? {
        if (id == null) {
            return null
        }
        return transactions.tx("Failed to find collection id=$id") {
            loadOne(id)
        }
    }

    /**
     * Inserts or updates [collection] and reads it back.
     *
     * The reread is what returns the caller the generated id and the stored timestamps. Membership and
     * labels are *not* written here — [setTags] is the only statement that writes them, and
     * [addMembers] the only one that writes membership. Both are edited through their own statements
     * because they are edited by picking things rather than by filling in a form, and a full-aggregate
     * save would make a rename silently a membership edit.
     *
     * The aggregate this receives still *carries* a label set, and that set is what the method above
     * this one wrote on the way in. It is ignored here on purpose, which is worth stating because it
     * reads like an oversight: a caller who expects `save` to honour `collection.tags` gets a collection
     * with no labels and a row that disagrees with the object it was handed. Creating a collection with
     * labels is therefore two statements — this one, then [setTags] — which is what
     * `CollectionService.create` does.
     */
    override suspend fun save(collection: RemembranceCollection): RemembranceCollection =
        transactions.tx("Failed to save collection") {
            val timestamp = now()
            val existingId = collection.id
            val id = existingId ?: insert(collection, timestamp)
            if (existingId != null) {
                update(id, collection, timestamp)
            }
            loadOne(id) ?: error("Collection $id vanished during save")
        }

    /** Membership and labels cascade from the parent row, so deleting the collection is enough. */
    override suspend fun deleteById(id: Long?) {
        if (id == null) {
            return
        }
        transactions.tx("Failed to delete collection id=$id") {
            requireExists(id)
            CollectionTable.deleteWhere { CollectionTable.id eq id }
        }
    }

    /**
     * Appends every id that is not already a member.
     *
     * The pre-filter runs as one statement before the inserts, so a batch of fifty picks that overlap
     * an existing shelf costs one membership probe rather than fifty — and each insert would
     * otherwise be a no-op anyway, since the primary key already rejects the duplicates.
     */
    override suspend fun addMembers(
        collectionId: Long,
        remembranceIds: List<Long>,
    ): Int =
        transactions.tx("Failed to add ${remembranceIds.size} remembrances to collection id=$collectionId") {
            requireExists(collectionId)
            val timestamp = now()
            val pending = withoutExistingMembership(collectionId, remembranceIds).distinct()
            pending.count { addMember(collectionId, it, timestamp) }
        }

    /**
     * Removes every id that is currently a member.
     *
     * Each removal would close the gap it leaves behind, but doing that one id at a time renumbers
     * the same tail repeatedly. Renumbering once at the end is the same result for a fraction of the
     * statements, and the positions are not observable from outside the transaction.
     */
    override suspend fun removeMembers(
        collectionId: Long,
        remembranceIds: List<Long>,
    ): Int =
        transactions.tx("Failed to remove ${remembranceIds.size} remembrances from collection id=$collectionId") {
            requireExists(collectionId)
            val pending = removeMany(collectionId, remembranceIds)
            if (pending > 0) {
                compactPositions(collectionId)
            }
            pending
        }

    override suspend fun setCoverImage(
        collectionId: Long,
        coverImagePath: String?,
    ) = transactions.tx("Failed to set the cover image of collection id=$collectionId") {
        requireExists(collectionId)
        writeCoverImage(collectionId, coverImagePath, now())
    }

    /**
     * Replaces the collection's labels with exactly [tagNames].
     *
     * The tag rows themselves are resolved and created here, through
     * [ExposedTagRepository.findOrCreateId], so naming a tag the library has never seen works without
     * the caller having to know that a row gets written for it first.
     */
    override suspend fun setTags(
        collectionId: Long,
        tagNames: Collection<String>,
    ) = transactions.tx("Failed to set the tags of collection id=$collectionId") {
        requireExists(collectionId)
        replaceTags(collectionId, tagNames)
    }

    /**
     * Pins the collection to the top of the shelf or unpins it.
     *
     * Existence-checked first, like [setHomePinned], for the same reason: an update against a row
     * that is not there reports one row changed and reports success, and the shelf would simply never
     * show the collection as pinned.
     */
    override suspend fun setPinned(
        collectionId: Long,
        pinned: Boolean,
    ) {
        transactions.tx("Failed to pin or unpin collection id=$collectionId") {
            requireExists(collectionId)
            CollectionTable.update({ CollectionTable.id eq collectionId }) { row ->
                row[CollectionTable.pinnedAt] = if (pinned) now() else null
            }
        }
    }

    override suspend fun moveMember(
        collectionId: Long,
        fromIndex: Int,
        toIndex: Int,
    ) {
        transactions.tx("Failed to move a member of collection id=$collectionId from $fromIndex to $toIndex") {
            requireExists(collectionId)
            move(collectionId, fromIndex, toIndex)
        }
    }

    override suspend fun touchLastOpened(id: Long) {
        transactions.tx("Failed to record the last opening of collection id=$id") {
            requireExists(id)
            CollectionEntity.findById(id)?.updateAndFlush { lastOpenedAt = now() }
        }
    }

    /**
     * Moves the collection on or off the Home screen.
     *
     * An existence check first, because an update against a row that is not there reports one
     * row changed and reports success — the rail would simply never show it.
     */
    override suspend fun setHomePinned(
        id: Long,
        pinned: Boolean,
    ) {
        transactions.tx("Failed to move collection id=$id on or off the Home screen") {
            requireExists(id)
            CollectionEntity.findById(id)?.updateAndFlush { homePinnedAt = if (pinned) now() else null }
        }
    }

    override suspend fun setFavorite(
        id: Long,
        favorite: Boolean,
    ) {
        transactions.tx("Failed to star or unstar collection id=$id") {
            requireExists(id)
            CollectionEntity.findById(id)?.updateAndFlush { isFavorite = favorite }
        }
    }

    override suspend fun findHomePinned(): List<RemembranceCollection> =
        transactions.tx("Failed to find the collections shown on the Home screen") {
            loadHomePinned()
        }

    override suspend fun findSummaries(): List<CollectionSummary> =
        transactions.tx("Failed to summarise the collections") {
            summarizeAll()
        }

    override suspend fun findSummary(id: Long): CollectionSummary? =
        transactions.tx("Failed to summarise collection id=$id") {
            summarize(id)
        }

    override suspend fun findAllCoverImagePaths(): Set<String> =
        transactions.tx("Failed to find the cover images in use") {
            coverImagePaths()
        }

    /**
     * Reports a missing collection before anything is written.
     *
     * The write statements themselves are silent about a parent that is not there — a membership
     * insert fails on the foreign key, but a cover, a pin or a `last_opened_at` touch would just update
     * nothing and report success. Checking first keeps all of them honest.
     */
    private fun requireExists(id: Long) {
        if (CollectionEntity.findById(id) == null) {
            throw CollectionNotFoundException(id)
        }
    }

    /**
     * Every collection, each with its members and tags loaded, ordered by id.
     *
     * The query is the table's own columns and nothing else, so it takes entities straight from
     * [CollectionEntity] rather than going through `selectAll`. Turning those rows into aggregates —
     * the members and tags that go with them — is [CollectionAggregates]' job; what is left here is
     * the decision about *which* collections, which is the one this method actually makes.
     */
    private fun loadAll(): List<RemembranceCollection> =
        CollectionEntity
            .all()
            .orderBy(CollectionTable.id to SortOrder.ASC)
            .toList()
            .load()

    /** The collection with [id], with its members and tags loaded, or `null` when no such collection exists. */
    private fun loadOne(id: Long): RemembranceCollection? =
        CollectionEntity.findById(id)?.let { listOf(it).load() }?.firstOrNull()

    /**
     * The collections the user put on the Home screen, most recently pinned first.
     *
     * Ordered by the stamp itself rather than by id, because "the two I added most recently" is
     * what the rail means by pinned and recency is the whole of the preference.
     */
    private fun loadHomePinned(): List<RemembranceCollection> =
        CollectionEntity
            .find { CollectionTable.homePinnedAt.isNotNull() }
            .orderBy(CollectionTable.homePinnedAt to SortOrder.DESC)
            .toList()
            .load()

    /** Every collection as a [CollectionSummary]: what to put on a card, and how big it is. */
    private fun summarizeAll(): List<CollectionSummary> = CollectionEntity.all().summaries()

    private fun summarize(id: Long): CollectionSummary? =
        CollectionEntity.findById(id)?.let { listOf(it).summaries().firstOrNull() }

    /**
     * The name of every cover file any collection points at.
     *
     * One nullable column and no aggregation, because the caller wants a set to subtract from what is
     * on disk rather than a row per collection: a collection with no cover contributes nothing
     * instead of a null the caller would have to filter out.
     */
    private fun coverImagePaths(): Set<String> =
        CollectionEntity
            .find { CollectionTable.coverImagePath.isNotNull() }
            .mapNotNullTo(mutableSetOf()) { it.coverImagePath }

    /** Inserts the parent row and returns its generated id. */
    private fun insert(
        collection: RemembranceCollection,
        now: OffsetDateTime,
    ): Long =
        CollectionEntity
            .new {
                name = collection.name
                description = collection.description
                kind = collection.kind
                iconKey = collection.iconKey
                coverImagePath = collection.coverImagePath
                createdAt = now
                updatedAt = now
                lastOpenedAt = now
                // Not taken from the domain: a collection that has just been created cannot already be
                // pinned to the shelf, nor be on the Home screen. The only way there is for the user
                // to ask, after it exists.
                pinnedAt = null
                homePinnedAt = null
                // The same reasoning, and for a stronger reason: starring something requires having read
                // it first, so a collection created in this same breath cannot already be a favourite.
                // Written rather than left to the column default so that the intent survives someone
                // reading the insert alone, without this comment attached to the Home line above it.
                isFavorite = collection.isFavorite
            }.id.value

    /**
     * Updates the parent row in place.
     *
     * `created_at` is left alone because it records when the collection was made, not when it was last
     * touched, and `last_opened_at` is deliberately not written here either: it moves when the user
     * opens the collection, not when they rename it. `is_favorite` is left alone for the same reason
     * the Home pin is: a rename is not a starring, and a full-row write here would make the two
     * inseparable.
     */
    private fun update(
        id: Long,
        collection: RemembranceCollection,
        now: OffsetDateTime,
    ) {
        CollectionEntity.findById(id)?.updateAndFlush {
            name = collection.name
            description = collection.description
            kind = collection.kind
            iconKey = collection.iconKey
            coverImagePath = collection.coverImagePath
            updatedAt = now
        }
    }

    /** Appends [remembranceId] and returns `false` when it was already a member. */
    private fun addMember(
        collectionId: Long,
        remembranceId: Long,
        now: OffsetDateTime,
    ): Boolean {
        if (isMember(collectionId, remembranceId)) {
            return false
        }
        CollectionMemberEntity.new(collectionMemberId(collectionId, remembranceId)) {
            position = nextPosition(collectionId)
            createdAt = now
            updatedAt = now
        }
        return true
    }

    /**
     * Removes several members at once and returns how many were actually members.
     *
     * The positions are *not* closed up here — the caller does that once for the whole batch, because
     * closing the gap after each individual delete would renumber the same tail repeatedly.
     */
    private fun removeMany(
        collectionId: Long,
        remembranceIds: List<Long>,
    ): Int {
        if (remembranceIds.isEmpty()) {
            return 0
        }
        return CollectionMemberTable.deleteWhere {
            (CollectionMemberTable.collectionId eq collectionId) and
                (CollectionMemberTable.remembranceId inList remembranceIds)
        }
    }

    /**
     * Points the cover at [coverImagePath], or back at the generated wash when it is `null`.
     *
     * Bumped `updated_at`, unlike the display toggles above, because a new cover is a change to how
     * the collection looks rather than a per-device preference about where to show it — the shelf's
     * "recently updated" ordering is meant to notice it.
     */
    private fun writeCoverImage(
        collectionId: Long,
        coverImagePath: String?,
        now: OffsetDateTime,
    ) {
        CollectionTable.update({ CollectionTable.id eq collectionId }) { row ->
            row[CollectionTable.coverImagePath] = coverImagePath
            row[CollectionTable.updatedAt] = now
        }
    }

    /**
     * Replaces the collection's labels with exactly [tagNames], creating any tag row that does not
     * exist yet.
     *
     * Reconciles rather than deleting and reinserting, for the reason the remembrance's own tag
     * links do it that way: a wholesale delete would leave the tag rows orphaned and then recreate
     * the same join rows with fresh timestamps, so only the links that actually differ are touched.
     *
     * Blank names are dropped before anything is resolved, because a tag row is named by a non-empty
     * string and one of those would fail the write rather than simply mean nothing.
     *
     * The set is taken over resolved ids rather than over the names typed, and that is the difference
     * between this working and a duplicate-key failure. Names fold to lowercase on their way to a tag
     * row, so "Sleep" and "sleep" name one row between them; de-duplicating the names first would
     * leave both in the list, both resolve to the same id, and the second insert would ask a composite
     * primary key to hold two copies of one link. Collecting ids into a set makes that impossible
     * whatever the caller passed in, which is why this takes a [Collection] rather than trusting it
     * to have been cleaned up.
     */
    private fun replaceTags(
        collectionId: Long,
        tagNames: Collection<String>,
    ) {
        val desiredTagIds =
            tagNames
                .map(String::trim)
                .filter { it.isNotEmpty() }
                .map { tagRepository.findOrCreateId(it) }
                .toSet()
        val existingTagIds =
            CollectionTagTable
                .selectAll()
                .where { CollectionTagTable.collectionId eq collectionId }
                .map { it[CollectionTagTable.tagId].value }
                .toSet()
        (existingTagIds - desiredTagIds).forEach { tagId ->
            CollectionTagTable.deleteWhere {
                (CollectionTagTable.collectionId eq collectionId) and (CollectionTagTable.tagId eq tagId)
            }
        }
        (desiredTagIds - existingTagIds).forEach { tagId ->
            CollectionTagEntity.new(collectionTagId(collectionId, tagId)) {}
        }
    }

    /**
     * Moves the member at [fromIndex] to [toIndex].
     *
     * Every row between the two positions is renumbered rather than swapped, which keeps the sequence
     * dense: a swap would leave the moved item duplicated at one index and missing from the other, and
     * the next append would then read a position that is taken.
     */
    private fun move(
        collectionId: Long,
        fromIndex: Int,
        toIndex: Int,
    ) {
        val ordered = orderedMemberIds(collectionId)
        if (fromIndex !in ordered.indices || toIndex !in ordered.indices || fromIndex == toIndex) {
            return
        }
        val reordered = ordered.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
        reordered.forEachIndexed { position, remembranceId ->
            CollectionMemberEntity
                .findById(collectionMemberId(collectionId, remembranceId))
                ?.updateAndFlush { this.position = position }
        }
    }

    /** Rewrites the position column to `0..n-1` in the order the rows already have. */
    private fun compactPositions(collectionId: Long) {
        orderedMemberIds(collectionId).forEachIndexed { position, remembranceId ->
            CollectionMemberEntity
                .findById(collectionMemberId(collectionId, remembranceId))
                ?.updateAndFlush { this.position = position }
        }
    }

    /** The member ids of [remembranceIds] that are *not* already members, in the order given. */
    private fun withoutExistingMembership(
        collectionId: Long,
        remembranceIds: List<Long>,
    ): List<Long> {
        if (remembranceIds.isEmpty()) {
            return emptyList()
        }
        val existing =
            CollectionMemberTable
                .selectAll()
                .where {
                    (CollectionMemberTable.collectionId eq collectionId) and
                        (CollectionMemberTable.remembranceId inList remembranceIds)
                }.map { it[CollectionMemberTable.remembranceId].value }
                .toSet()
        return remembranceIds.filterNot { it in existing }
    }

    private fun isMember(
        collectionId: Long,
        remembranceId: Long,
    ): Boolean =
        CollectionMemberTable
            .selectAll()
            .where {
                (CollectionMemberTable.collectionId eq collectionId) and
                    (CollectionMemberTable.remembranceId eq remembranceId)
            }.limit(1)
            .any()

    /** The position one past the end, so appends land after everything already there. */
    private fun nextPosition(collectionId: Long): Int =
        CollectionMemberTable
            .selectAll()
            .where { CollectionMemberTable.collectionId eq collectionId }
            .maxOfOrNull { it[CollectionMemberTable.position] }
            ?.plus(1)
            ?: 0

    private fun orderedMemberIds(collectionId: Long): List<Long> =
        CollectionMemberTable
            .selectAll()
            .where { CollectionMemberTable.collectionId eq collectionId }
            .orderBy(CollectionMemberTable.position to SortOrder.ASC)
            .map { it[CollectionMemberTable.remembranceId].value }
}
