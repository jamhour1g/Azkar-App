package com.azkar.domain.repo

import com.azkar.domain.model.CollectionSummary
import com.azkar.domain.model.RemembranceCollection
import com.azkar.domain.model.Tag

/**
 * The port through which collections are stored and retrieved.
 *
 * Like the other repositories, absence is `null` and failure is a
 * [com.azkar.domain.exception.DataAccessException].
 *
 * Extends [CollectionHomeRepository], [CollectionMembershipRepository] and [CollectionOrderRepository]
 * rather than declaring their methods itself. One concern per interface is what has kept this one
 * readable; and because they are *supertypes* rather than siblings, every existing
 * `CollectionRepository` parameter — the service, the test fixtures, the data adapters — keeps working
 * without a second argument. A sibling would have needed one, since Kotlin rejects an intersection
 * type on a constructor parameter.
 */
interface CollectionRepository :
    CollectionHomeRepository,
    CollectionMembershipRepository,
    CollectionOrderRepository {
    /** Returns every known collection, each with its members and tags loaded. */
    suspend fun findAll(): List<RemembranceCollection>

    /** Returns the collection identified by [id], or `null` when [id] is `null` or unknown. */
    suspend fun findById(id: Long?): RemembranceCollection?

    /**
     * Returns the collection identified by [id] as a [CollectionSummary], or `null` when there is
     * none.
     *
     * A label, a cover and a count, without the members. Everything that puts a collection beside a
     * reminder wants exactly those things — "Evening Azkar, 12 passages" — and answering it through
     * [findById] meant loading every passage of the collection, in both locales with its explanation
     * and tags, to read a single integer off the end of it.
     */
    suspend fun findSummary(id: Long): CollectionSummary?

    /** Inserts [collection] when it has no id, otherwise updates it. */
    suspend fun save(collection: RemembranceCollection): RemembranceCollection

    /**
     * Deletes the collection identified by [id], doing nothing when [id] is `null`.
     *
     * The only deletion there is. It takes the identifier rather than the aggregate because every
     * caller in this codebase has an id in hand and a stale aggregate would be a second, worse source
     * of truth for what to delete.
     *
     * Deleting a collection does not delete the picture its cover names; the storage layer sweeps
     * files no row refers to, because a cover is not a row's to clean up at the moment the row goes.
     */
    suspend fun deleteById(id: Long?)

    /**
     * The name of every cover file any collection currently points at.
     *
     * Asked for one value per collection rather than one per row so the answer is a set of names to
     * subtract from what is on disk: a file nothing here mentions is a picture no collection can still
     * be showing, and there is nothing left that would ever remove it.
     */
    suspend fun findAllCoverImagePaths(): Set<String>

    /**
     * Points the collection's cover at [coverImagePath], or back at the generated wash when `null`.
     *
     * The name of a file in the application's own covers directory rather than a path or a URL the
     * caller supplies, so that what is stored cannot name something outside it and a picture that
     * has been swept away is a value the loader can recognise as stale.
     */
    suspend fun setCoverImage(
        collectionId: Long,
        coverImagePath: String?,
    )

    /** Pins the collection to the top of the shelf, or unpins it. */
    suspend fun setPinned(
        collectionId: Long,
        pinned: Boolean,
    )

    /**
     * Replaces the collection's labels with exactly [tags], folding names and dropping blanks.
     *
     * The row identifiers remain a storage concern; passing [Tag] also preserves the language of a
     * newly typed label when storage has to create its row.
     */
    suspend fun setTags(
        collectionId: Long,
        tags: Collection<Tag>,
    )

    /** Records that the collection identified by [id] was opened just now. */
    suspend fun touchLastOpened(id: Long)

    /**
     * Stars or unstars the collection identified by [id].
     *
     * A statement of its own rather than a field of [save] for the same reason [touchLastOpened] is:
     * starring is not an edit to the collection, so a rename must not be the moment the star moves,
     * and a full-row write would make the two inseparable.
     */
    suspend fun setFavorite(
        id: Long,
        favorite: Boolean,
    )
}

/**
 * Whether a collection is one of the few the Home screen offers.
 *
 * Split out of [CollectionRepository] so that port describes storing collections rather than where
 * they are displayed, and so the two reads that only the Home screen makes are not carried by every
 * other caller of the port.
 */
interface CollectionHomeRepository {
    /**
     * Puts [id] on the Home screen or takes it off again.
     *
     * A statement of its own rather than a field of [save], because being on Home is a per-device
     * preference and a full-row write would let a rename quietly drop the collection off the rail.
     */
    suspend fun setHomePinned(
        id: Long,
        pinned: Boolean,
    )

    /**
     * The collections the user put on the Home screen, most recently pinned first.
     *
     * A dedicated read rather than a filter over [CollectionRepository.findAll] because the rail only
     * needs the few it will show, and loading every member of every collection to pick three of them
     * is the wrong shape of query.
     */
    suspend fun findHomePinned(): List<RemembranceCollection>

    /**
     * Every collection as a [CollectionSummary], the ones the user pinned first and the rest after.
     *
     * This is what the rail actually reads, and it replaces asking for the pinned collections and then
     * for all the others: the whole question the rail has — "which few, in what order, how big is
     * each" — is one statement and one pass, where the two reads it replaced were two statements and
     * two full hydrations of every member of every collection.
     *
     * Pinned collections come first because that is what the rail is for; among them the stamp decides,
     * since "the ones I added most recently" is what pinning means here. The rest follow in the order the
     * shelf shows them.
     */
    suspend fun findSummaries(): List<CollectionSummary>
}

/**
 * What is *in* a collection, and in what order.
 *
 * Split out of [CollectionRepository] to keep membership, the one part of a collection that is edited
 * through a picker rather than a form, from the aggregate's own fields.
 *
 * The methods take plain identifiers rather than aggregates, in batches, and return how many rows
 * they actually changed. There is deliberately no single-item variant — the only caller is a picker
 * over a selection, so a one-item batch is what it already has.
 */
interface CollectionMembershipRepository {
    /** Appends each of [remembranceIds], returning how many were not already members. */
    suspend fun addMembers(
        collectionId: Long,
        remembranceIds: List<Long>,
    ): Int

    /** Removes each of [remembranceIds], returning how many were members. */
    suspend fun removeMembers(
        collectionId: Long,
        remembranceIds: List<Long>,
    ): Int

    /** Moves the member at [fromIndex] to [toIndex], renumbering the positions between them. */
    suspend fun moveMember(
        collectionId: Long,
        fromIndex: Int,
        toIndex: Int,
    )
}
