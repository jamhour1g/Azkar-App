package com.azkar.data.seed

import com.azkar.data.repo.exposed.now
import com.azkar.data.table.CollectionMemberTable
import com.azkar.data.table.CollectionTable
import com.azkar.data.table.CollectionTagTable
import com.azkar.data.table.RemembranceTable
import com.azkar.data.table.RemembranceTagTable
import com.azkar.data.table.TagTable
import com.azkar.domain.model.CollectionKind
import com.azkar.domain.model.Tag
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.time.OffsetDateTime

private val logger = KotlinLogging.logger {}

/**
 * Writes the shipped collections and the memberships that go with them into an empty database.
 *
 * Membership is written as ordinary [CollectionMemberTable] rows rather than left to be resolved from a
 * tag at read time, because there is now one answer to "what is in this collection" and it lives in
 * one table. Resolving it from a tag on every read meant a second place to spell a collection's
 * contents, one the user could never correct, and a shelf that quietly emptied itself when a tag was
 * renamed in a dataset release.
 *
 * The cost is honest and worth naming: shipping azkar in a later release no longer flows into these
 * collections by itself, because nothing is left watching for it. That has to be a migration somebody
 * writes, which is a good property — it shows up in a diff instead of being a no-op on installs that
 * already exist.
 *
 * Runs exactly once, on the first launch, from [LibraryBootstrapper]. It does not upsert and does not
 * recognise rows it wrote before: it has no stable handle on a collection to recognise one by, because
 * the handle the user could also have edited has since been removed from the model on purpose.
 *
 * Must be called from inside a transaction, after the remembrances are in: a collection seeded
 * against a database with no tags or no remembrances in it comes up empty and stays empty.
 */
internal object CollectionSeed {
    fun load() {
        val catalogue = OfficialCollectionCatalogue.entries

        // Belt and braces rather than the real gate — [LibraryBootstrapper] decides whether to call
        // this at all. This only catches a run that half-died and is being retried.
        if (CollectionTable.selectAll().limit(1).any()) {
            logger.debug { "Collection table is not empty; skipping collection seed" }
            return
        }

        val tagIds = resolveTagIds(catalogue)
        warnAboutTagsTheDatasetNoLongerCarries(catalogue, tagIds)

        val timestamp = now()
        val collections = insertCollections(catalogue, timestamp)
        insertLabels(collections, tagIds)
        insertMembership(collections, tagIds)

        logger.info { "Seeded ${collections.size} official collection(s)" }
    }

    /**
     * The id of each tag the catalogue names, keyed by its folded name.
     *
     * Folded because the tag table stores every name folded, and because the catalogue spells its tags
     * the way a person reads them — the two are allowed to disagree about capitalisation.
     */
    private fun resolveTagIds(entries: List<OfficialCollectionCatalogue.Entry>): Map<String, Long> {
        val wanted = entries.mapNotNull { it.tagName }.map(Tag::fold).toSet()
        if (wanted.isEmpty()) {
            return emptyMap()
        }
        return TagTable
            .select(TagTable.id, TagTable.name)
            .where { TagTable.name inList wanted.toList() }
            .associate { it[TagTable.name] to it[TagTable.id].value }
    }

    /**
     * Says so when a collection's tag is not in the dataset, which is the one way this seed can
     * quietly produce an empty shelf.
     *
     * A warning rather than a failure: the tag may have been renamed, or dropped on purpose, and the
     * collection is still worth having as an empty shelf the user can fill.
     */
    private fun warnAboutTagsTheDatasetNoLongerCarries(
        entries: List<OfficialCollectionCatalogue.Entry>,
        tagIds: Map<String, Long>,
    ) {
        val missing = entries.mapNotNull { it.tagName }.filter { Tag.fold(it) !in tagIds }.distinct()
        if (missing.isNotEmpty()) {
            logger.warn {
                "Collection seed found no tag for: ${missing.joinToString()}. " +
                    "Those collections will be seeded empty."
            }
        }
    }

    /** Each catalogue entry paired with the id of the row it just became. */
    private fun insertCollections(
        entries: List<OfficialCollectionCatalogue.Entry>,
        timestamp: OffsetDateTime,
    ): List<Pair<OfficialCollectionCatalogue.Entry, Long>> =
        entries.map { entry ->
            val id =
                CollectionTable
                    .insert { row ->
                        row[CollectionTable.name] = entry.name
                        row[CollectionTable.description] = entry.description
                        row[CollectionTable.kind] = CollectionKind.OFFICIAL
                        row[CollectionTable.iconKey] = entry.iconKey
                        // Nothing is chosen on the user's behalf. A collection they have not pinned, not
                        // opened, not put on Home and not starred reads as exactly that, rather than as
                        // "all of those at seed time".
                        row[CollectionTable.pinnedAt] = null
                        row[CollectionTable.homePinnedAt] = null
                        row[CollectionTable.createdAt] = timestamp
                        row[CollectionTable.updatedAt] = timestamp
                        row[CollectionTable.lastOpenedAt] = null
                    }[CollectionTable.id]
                    .value
            entry to id
        }

    /**
     * Gives each collection its own tag, so a shipped shelf arrives categorised rather than anonymous
     * and the card's icon has something to derive from before the user has touched anything.
     */
    private fun insertLabels(
        collections: List<Pair<OfficialCollectionCatalogue.Entry, Long>>,
        tagIds: Map<String, Long>,
    ) {
        val links =
            collections.mapNotNull { (entry, collectionId) ->
                val tagId = entry.tagName?.let { tagIds[Tag.fold(it)] } ?: return@mapNotNull null
                LabelLink(collectionId, tagId)
            }
        CollectionTagTable.batchInsert(links, shouldReturnGeneratedValues = false) { link ->
            this[CollectionTagTable.collectionId] = link.collectionId
            this[CollectionTagTable.tagId] = link.tagId
        }
    }

    /**
     * Writes one row per member, numbered from zero.
     *
     * Ordered by remembrance id rather than left to whatever order the join returns, so the shelf's
     * sequence is the same on every machine that seeds the same dataset.
     */
    private fun insertMembership(
        collections: List<Pair<OfficialCollectionCatalogue.Entry, Long>>,
        tagIds: Map<String, Long>,
    ) {
        val tagIdByCollection =
            collections.mapNotNull { (entry, collectionId) ->
                entry.tagName?.let { tagIds[Tag.fold(it)] }?.let { collectionId to it }
            }
        val memberIds = remembranceIdsByTag(tagIdByCollection.map { it.second }.distinct())

        val links =
            tagIdByCollection.flatMap { (collectionId, tagId) ->
                memberIds[tagId].orEmpty().mapIndexed { position, remembranceId ->
                    MembershipLink(collectionId, remembranceId, position)
                }
            }
        CollectionMemberTable.batchInsert(links, shouldReturnGeneratedValues = false) { link ->
            this[CollectionMemberTable.collectionId] = link.collectionId
            this[CollectionMemberTable.remembranceId] = link.remembranceId
            this[CollectionMemberTable.position] = link.position
        }
    }

    private fun remembranceIdsByTag(tagIds: List<Long>): Map<Long, List<Long>> {
        if (tagIds.isEmpty()) {
            return emptyMap()
        }
        return (RemembranceTagTable innerJoin RemembranceTable)
            .select(RemembranceTagTable.tagId, RemembranceTable.id)
            .where { RemembranceTagTable.tagId inList tagIds }
            .orderBy(RemembranceTagTable.tagId to SortOrder.ASC, RemembranceTable.id to SortOrder.ASC)
            .groupBy(
                { it[RemembranceTagTable.tagId].value },
                { it[RemembranceTable.id].value },
            )
    }

    private data class LabelLink(
        val collectionId: Long,
        val tagId: Long,
    )

    private data class MembershipLink(
        val collectionId: Long,
        val remembranceId: Long,
        val position: Int,
    )
}
