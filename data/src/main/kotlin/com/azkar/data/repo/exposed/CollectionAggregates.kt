package com.azkar.data.repo.exposed

import com.azkar.data.mapping.CollectionRow
import com.azkar.data.mapping.toTag
import com.azkar.data.repo.exposed.CollectionAggregates.membersByCollection
import com.azkar.data.repo.exposed.RemembranceAggregates.load
import com.azkar.data.table.CollectionMemberTable
import com.azkar.data.table.CollectionTagTable
import com.azkar.data.table.TagTable
import com.azkar.data.table.entity.CollectionEntity
import com.azkar.data.table.entity.RemembranceEntity
import com.azkar.data.table.entity.TagEntity
import com.azkar.domain.model.CollectionSummary
import com.azkar.domain.model.LocalizedPair
import com.azkar.domain.model.Remembrance
import com.azkar.domain.model.RemembranceCollection
import com.azkar.domain.model.Tag
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.util.Locale

/**
 * Turns a page of collection rows into the two shapes a shelf actually draws: whole aggregates, or
 * the summaries a rail counts.
 *
 * Membership is one table for every collection. There is no longer a second source of members to
 * reconcile against, so [load] resolves member ids in a single statement for a whole page and
 * bulk-loads every id it found through [RemembranceAggregates], which makes reading a whole shelf a
 * constant handful of statements instead of one round trip per card. Labels are the same story one
 * table over.
 *
 * This is an object rather than a set of methods on [ExposedCollectionRepository] because the assembly
 * is the one part of that repository that is not a statement it was asked to run: it is an algorithm
 * over rows somebody else chose. The repository supplies the parents — it owns the predicates, the
 * ordering and the transaction — and this turns them into either shape.
 *
 * It is deliberately *not* a per-method delegate. The version this replaces held a function for each
 * of the repository's reads, which meant `findAll` called `loadAll`, which called `load`: a hop
 * between two files that decided nothing. The difference here is that every entry point does real
 * work, and there are two of them rather than seven.
 *
 * Both reads go through the entity classes except where a join forces the statement to be written by
 * hand, which is why those two are marked where they appear.
 *
 * Every method must be called from inside the caller's transaction — the repository owns transaction
 * boundaries, so this opens none of its own.
 */
internal object CollectionAggregates {
    /** The full aggregate for a page of parents, loading all members and tags in bulk. */
    fun List<CollectionEntity>.load(): List<RemembranceCollection> {
        if (this.isEmpty()) {
            return emptyList()
        }
        val ids = this.map { it.id.value }
        val translations = translationsByCollection(ids)
        val rows = this.map { it.toCollectionRow(translations = translations[it.id.value].orEmpty()) }
        val memberIds = rows.membersByCollection()
        val aggregates = loadAggregates(memberIds.values.flatten().distinct())
        val tags = tagsByCollection(ids)

        return rows.map { parent ->
            parent.toCollection(
                // `associate` rather than `mapValues`, so the resolved order is the one returned and
                // the index lookup below does not have to re-establish it.
                members = memberIds[parent.id].orEmpty().mapNotNull { aggregates[it] },
                tags = tags[parent.id].orEmpty().toSet(),
            )
        }
    }

    /**
     * Every collection as a [CollectionSummary]: what to put on a card, and how big it is.
     *
     * The whole point of this read is what it does *not* do. [load] resolves member ids and then
     * bulk-loads every one of them as a whole [Remembrance] aggregate — both locales of the text,
     * both of the explanation, the tags — and the rail, which wants a name and a number, was paying
     * for all of it.
     *
     * So the counts come from [membersByCollection], which is the very function [load] uses to decide
     * membership, and the ids it returns are simply counted. That is deliberate: a count assembled
     * from its own SQL would be free to disagree with the member list beside it, and a rail that says
     * "12 passages" above a shelf holding eleven is the kind of bug that survives a long time because
     * it looks like nothing at all. Sharing the one function makes that impossible, and the ids cost
     * nothing next to the aggregates they stand in for.
     *
     * The wording is the one thing this read had no cheap answer for, and it now pays for a second
     * statement to get it. That is the price of a shelf that can be read in two languages: a name in
     * the wrong one is not a degraded name, it is the wrong label for the shelf, and the alternative
     * — resolving it per card at the point of drawing — would put a query in a composable.
     */
    fun Iterable<CollectionEntity>.summaries(): List<CollectionSummary> {
        val rows = this.toList()
        if (rows.isEmpty()) {
            return emptyList()
        }
        val ids = rows.map { it.id.value }
        val translations = translationsByCollection(ids)
        val collectionRows = rows.map { it.toCollectionRow(translations = translations[it.id.value].orEmpty()) }
        val counts = collectionRows.membersByCollection().mapValues { (_, memberIds) -> memberIds.size }

        return collectionRows
            .map { parent -> parent.toSummary(memberCount = counts[parent.id] ?: 0) }
            .sortedWith(RAIL_ORDER)
    }

    /**
     * The member ids of every collection, keyed by collection and in display order.
     *
     * One statement for the whole page, ordered by position, so the grouping preserves each
     * collection's arrangement without a second sort per collection.
     */
    private fun List<CollectionRow>.membersByCollection(): Map<Long, List<Long>> {
        val ids = this.map { it.id }
        if (ids.isEmpty()) {
            return emptyMap()
        }
        return CollectionMemberTable
            .selectAll()
            .where { CollectionMemberTable.collectionId inList ids }
            .orderBy(CollectionMemberTable.position to SortOrder.ASC)
            .groupBy(
                { it[CollectionMemberTable.collectionId].value },
                { it[CollectionMemberTable.remembranceId].value },
            )
    }

    /**
     * The tags of every collection among [ids], keyed by collection.
     *
     * Joined through `collection_tag` and ordered by the tag's own id so a collection's labels arrive
     * in the order they were first attached — a set has no order of its own, and alphabetical would
     * mean a shelf whose chips reshuffle when someone renames a tag elsewhere in the library.
     *
     * The only read that still returns rows rather than entities: `collection_tag` has no entity
     * class of its own that would carry the join, so the statement is spelled out and each tag row is
     * wrapped on the way past.
     *
     * The `deleted_at IS NULL` predicate is what a soft-deleted tag looks like to a collection: absent.
     * Its `collection_tag` row is deliberately still there — that is what makes restoring it put the
     * chip back — so without this the shelf would keep drawing a label the user believes they removed.
     */
    private fun tagsByCollection(ids: List<Long>): Map<Long, List<Tag>> {
        if (ids.isEmpty()) {
            return emptyMap()
        }
        // The join is asked first and its rows carry the tag ids, so the one statement that fetches the
        // labels is also what says which labels to fetch wording for. Asking the link table separately
        // would read the same rows twice to save a list comprehension.
        val joined =
            CollectionTagTable
                .innerJoin(TagTable)
                .select(CollectionTagTable.collectionId, TagTable.id, TagTable.name, TagTable.createdAt)
                .where { (CollectionTagTable.collectionId inList ids) and (TagTable.deletedAt eq null) }
                .orderBy(TagTable.id to SortOrder.ASC)
                .map { it to TagEntity.wrapRow(it) }
        val translations = LibraryTranslations.tagTranslations(joined.map { it.second.id.value }.distinct())
        return joined.groupBy({ it.first[CollectionTagTable.collectionId].value }) { (_, tag) ->
            tag.toTag(translations[tag.id.value].orEmpty())
        }
    }

    /**
     * The stored wording of the collections among [ids].
     *
     * A private alias rather than the call spelled out twice, so the shape of "one statement for the
     * whole page" has one name here and a reader does not have to check whether the two entry points
     * are reading the same thing.
     */
    private fun translationsByCollection(ids: List<Long>): Map<Long, Map<Locale, LocalizedPair>> =
        LibraryTranslations.collectionTranslations(ids)

    /** The whole aggregate for each of [ids], indexed by id for order-preserving reads. */
    private fun loadAggregates(ids: List<Long>): Map<Long, Remembrance> {
        if (ids.isEmpty()) {
            return emptyMap()
        }
        return RemembranceEntity
            .forIds(ids)
            .toList()
            .load()
            .mapNotNull { aggregate -> aggregate.id?.let { it to aggregate } }
            .toMap()
    }

    private fun CollectionEntity.toCollectionRow(translations: Map<Locale, LocalizedPair>) =
        CollectionRow(
            id = id.value,
            name = name,
            nameLocale = Locale.forLanguageTag(nameLocaleCode),
            description = description,
            kind = kind,
            iconKey = iconKey,
            coverImagePath = coverImagePath,
            pinnedAt = pinnedAt?.toInstant(),
            createdAt = createdAt.toInstant(),
            updatedAt = updatedAt.toInstant(),
            lastOpenedAt = lastOpenedAt?.toInstant(),
            homePinnedAt = homePinnedAt?.toInstant(),
            isFavorite = isFavorite,
            translations = translations,
        )

    private fun CollectionRow.toSummary(memberCount: Int) =
        CollectionSummary(
            id = id,
            name = name,
            nameLocale = nameLocale,
            description = description.orEmpty(),
            iconKey = iconKey.orEmpty(),
            coverImagePath = coverImagePath,
            memberCount = memberCount,
            homePinnedAt = homePinnedAt,
            translations = translations,
        )
}

/**
 * The order a Home rail wants: what the user pinned, most recently pinned first, then the rest by id.
 *
 * The stamp comes first as a *presence* test rather than as a value, because comparing an absent
 * `Instant` against a present one would sort the collections the user never chose among the ones they
 * did, ordered by a date that does not exist. Grouping by presence says that separately from ordering
 * by recency, and id then keeps everything else in the sequence the shelf already shows it in.
 */
private val RAIL_ORDER: Comparator<CollectionSummary> =
    compareBy<CollectionSummary> { it.homePinnedAt == null }
        .thenByDescending { it.homePinnedAt }
        .thenBy { it.id }
