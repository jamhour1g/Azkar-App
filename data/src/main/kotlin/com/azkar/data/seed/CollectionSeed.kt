package com.azkar.data.seed

import com.azkar.data.repo.exposed.now
import com.azkar.data.table.CollectionMemberTable
import com.azkar.data.table.CollectionTable
import com.azkar.data.table.CollectionTagTable
import com.azkar.data.table.CollectionTranslationTable
import com.azkar.data.table.TagTable
import com.azkar.data.table.TagTranslationTable
import com.azkar.domain.model.CollectionKind
import com.azkar.domain.model.Tag
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.time.OffsetDateTime

/** Inserts shipped collections using explicit seed keys, never tag-derived membership. */
internal object CollectionSeed {
    fun load(
        remembranceIds: Map<String, Long> = emptyMap(),
        logger: KLogger = KotlinLogging.logger {},
    ) {
        val catalogue = OfficialCollectionCatalogue.entries

        // Belt and braces rather than the real gate — [LibraryBootstrapper] decides whether to call
        // this at all. This only catches a run that half-died and is being retried.
        if (CollectionTable.selectAll().limit(1).any()) {
            logger.debug { "Collection table is not empty; skipping collection seed" }
            return
        }

        val tagIds = resolveTagIds(catalogue)

        val timestamp = now()
        val collections = insertCollections(catalogue, timestamp)
        insertTranslations(collections, timestamp)
        insertTagTranslations(timestamp)
        insertLabels(collections, tagIds)
        insertMembership(collections, remembranceIds)

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
                        row[CollectionTable.nameLocaleCode] = entry.nameLocale
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

    /** Writes collection wording in the same transaction as its membership. */
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

    /** Writes supplied wording for seed tags without inventing translations for user tags. */
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

    /** Writes the resource's explicit member order, numbered from zero. */
    private fun insertMembership(
        collections: List<Pair<OfficialCollectionCatalogue.Entry, Long>>,
        remembranceIds: Map<String, Long>,
    ) {
        // Catalogue-only fixtures can create empty shelves; production supplies every inserted key.
        if (remembranceIds.isEmpty()) {
            return
        }
        val links =
            collections.flatMap { (entry, collectionId) ->
                entry.members.mapIndexed { position, key ->
                    MembershipLink(collectionId, remembranceIds.getValue(key), position)
                }
            }
        CollectionMemberTable.batchInsert(links, shouldReturnGeneratedValues = false) { link ->
            this[CollectionMemberTable.collectionId] = link.collectionId
            this[CollectionMemberTable.remembranceId] = link.remembranceId
            this[CollectionMemberTable.position] = link.position
        }
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
