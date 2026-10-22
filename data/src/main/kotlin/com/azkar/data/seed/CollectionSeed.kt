package com.azkar.data.seed

import com.azkar.data.repo.exposed.now
import com.azkar.data.table.CollectionMemberTable
import com.azkar.data.table.CollectionTable
import com.azkar.data.table.CollectionTagTable
import com.azkar.data.table.CollectionTranslationTable
import com.azkar.data.table.RemembranceTable
import com.azkar.data.table.RemembranceTagTable
import com.azkar.data.table.TagTable
import com.azkar.data.table.TagTranslationTable
import com.azkar.domain.model.CollectionKind
import com.azkar.domain.model.Tag
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.time.OffsetDateTime

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
    fun load(logger: KLogger = KotlinLogging.logger {}) {
        val catalogue = OfficialCollectionCatalogue.entries

        // Belt and braces rather than the real gate — [LibraryBootstrapper] decides whether to call
        // this at all. This only catches a run that half-died and is being retried.
        if (CollectionTable.selectAll().limit(1).any()) {
            logger.debug { "Collection table is not empty; skipping collection seed" }
            return
        }

        val tagIds = resolveTagIds(catalogue)
        warnAboutTagsTheDatasetNoLongerCarries(logger, catalogue, tagIds)

        val timestamp = now()
        val collections = insertCollections(catalogue, timestamp)
        insertTranslations(collections, timestamp)
        insertTagTranslations(timestamp)
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
        logger: KLogger,
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
     * Writes the wording of each collection in every language the catalogue carries for it.
     *
     * In the same transaction as the rows themselves, and for the same reason membership is: a shelf
     * that exists in one language only for the first launch of an installation is the exact state this
     * whole change exists to remove, and a translation written on the next read would arrive too late
     * to matter to the screen that noticed.
     *
     * The English row duplicates the canonical columns, which [OfficialCollectionCatalogue.entry]
     * explains is deliberate rather than redundant.
     */
    private fun insertTranslations(
        collections: List<Pair<OfficialCollectionCatalogue.Entry, Long>>,
        timestamp: OffsetDateTime,
    ) {
        val rows =
            collections.flatMap { (entry, collectionId) ->
                entry.translations.map { (locale, pair) ->
                    TranslationRow(
                        collectionId = collectionId,
                        localeCode = locale,
                        name = pair.name,
                        description = pair.description,
                        timestamp = timestamp,
                    )
                }
            }
        if (rows.isEmpty()) {
            return
        }
        CollectionTranslationTable.batchInsert(rows, shouldReturnGeneratedValues = false) { row ->
            this[CollectionTranslationTable.collectionId] = row.collectionId
            this[CollectionTranslationTable.localeCode] = row.localeCode
            this[CollectionTranslationTable.name] = row.name
            this[CollectionTranslationTable.description] = row.description
            this[CollectionTranslationTable.createdAt] = row.timestamp
            this[CollectionTranslationTable.updatedAt] = row.timestamp
        }
    }

    /**
     * Gives every shipped label the wording [OfficialTagVocabulary] holds for it.
     *
     * Over the whole tag table rather than over [resolveTagIds]'s handful of catalogue tags, because
     * the vocabulary covers a label no collection is seeded from: `muslim data` is the publisher's own
     * tag and never names a shelf, but it is on every row the reader can reach. Reading the ids here
     * rather than passing them in keeps that difference from being the caller's problem.
     *
     * Written here rather than where the tag rows are made ([SeedDataLoader.insertTags]) because this
     * is the object that already owns "what the shipped library says". Anything the vocabulary does
     * not carry — a tag the user has since added, a slug nobody has worded — is simply not written.
     */
    private fun insertTagTranslations(timestamp: OffsetDateTime) {
        val idsByName =
            TagTable
                .select(TagTable.id, TagTable.name)
                .associate { Tag.fold(it[TagTable.name]) to it[TagTable.id].value }
        val rows =
            idsByName.flatMap { (foldedName, tagId) ->
                OfficialTagVocabulary.wordingFor(foldedName).map { (locale, name) ->
                    TagTranslationRow(tagId, locale, name, timestamp)
                }
            }
        if (rows.isEmpty()) {
            return
        }
        TagTranslationTable.batchInsert(rows, shouldReturnGeneratedValues = false) { row ->
            this[TagTranslationTable.ownerId] = row.tagId
            this[TagTranslationTable.localeCode] = row.localeCode
            this[TagTranslationTable.text] = row.text
            this[TagTranslationTable.createdAt] = row.timestamp
            this[TagTranslationTable.updatedAt] = row.timestamp
        }
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

    private data class TranslationRow(
        val collectionId: Long,
        val localeCode: String,
        val name: String,
        val description: String?,
        val timestamp: OffsetDateTime,
    )

    /** [TagTranslationTable] carries one column of prose, so it needs no description of its own. */
    private data class TagTranslationRow(
        val tagId: Long,
        val localeCode: String,
        val text: String,
        val timestamp: OffsetDateTime,
    )

    private data class MembershipLink(
        val collectionId: Long,
        val remembranceId: Long,
        val position: Int,
    )
}
