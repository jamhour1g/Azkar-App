package com.azkar.data.repo.exposed

import com.azkar.data.repo.exposed.LikeUtils.lowerCaseContains
import com.azkar.data.repo.exposed.RemembranceAggregates.load
import com.azkar.data.table.ExplanationTranslationTable
import com.azkar.data.table.RemembranceTable
import com.azkar.data.table.RemembranceTagTable
import com.azkar.data.table.RemembranceTranslationTable
import com.azkar.data.table.TagTable
import com.azkar.data.table.entity.ExplanationTranslationEntity
import com.azkar.data.table.entity.RemembranceEntity
import com.azkar.data.table.entity.RemembranceTagEntity
import com.azkar.data.table.entity.RemembranceTranslationEntity
import com.azkar.data.table.entity.remembranceTagId
import com.azkar.data.table.entity.updateAndFlush
import com.azkar.domain.exception.RemembranceNotFoundException
import com.azkar.domain.model.Remembrance
import com.azkar.domain.model.Tag
import com.azkar.domain.repo.RemembranceRepository
import com.azkar.domain.service.TransactionRunner
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.notInList
import org.jetbrains.exposed.v1.jdbc.SizedIterable
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.time.OffsetDateTime
import java.util.Locale

/**
 * Exposed-backed [RemembranceRepository].
 *
 * One method per call the domain makes, each one a transaction and a translation of a driver
 * failure. Everything below that surface is the strategy those methods are built from, kept private
 * here rather than pushed into a loader and a writer pair.
 *
 * The pair used to exist for the reason the collection repository documents, and it stopped being
 * worth it for the same reason: the transaction boilerplate collapsed into one call ([tx]), and the
 * functions left in the classes were a `setFavorite` that set the column, a `findById` that became
 * `loadOne`. What survives the fold is the part worth naming — reconciling two locale sets against
 * what is stored, diffing tag links against resolved ids rather than the names typed — and it is all
 * still here, below the methods that need it.
 *
 * Assembling the aggregate itself lives in [RemembranceAggregates] rather than in this file, because
 * [ExposedCollectionRepository] needs the very same assembly to load a collection's members and
 * neither type owns it alone.
 *
 * The reads here are written in the table DSL rather than as entity queries, and that is not an
 * oversight. Each of them is a predicate or an ordering over the parent table, handed to
 * [RemembranceAggregates.load] as rows for it to wrap and fan out from — a three-table join in
 * `findByTagNameIgnoreCase`, a single-column filter in `findFavorites`. Restating any of them as
 * `RemembranceEntity.find { }` would be the same query with a different spelling, and the entity
 * classes earn their place where a row is read and written field by field.
 *
 * Every method runs inside [transactions]; none of them opens a transaction of its own, so a
 * service-level transaction and the repository method nested inside it commit or roll back as one
 * unit rather than competing for the same rows.
 */
class ExposedRemembranceRepository(
    private val tagRepository: ExposedTagRepository,
    private val transactions: TransactionRunner,
) : RemembranceRepository {
    override suspend fun save(remembrance: Remembrance): Remembrance =
        transactions.tx("Failed to save remembrance") {
            val timestamp = now()
            val existingId = remembrance.id
            val id = existingId ?: insert(remembrance, timestamp)
            if (existingId != null) {
                update(id, remembrance, timestamp)
            }
            writeFavorite(id, remembrance.isFavorite)
            applyTranslations(id, remembrance.translations, timestamp)
            applyTags(id, remembrance.tags, timestamp)
            loadOne(id) ?: error("Remembrance $id vanished during save")
        }

    /** A remembrance that was never persisted carries no id, and there is nothing to delete. */
    override suspend fun delete(remembrance: Remembrance) {
        val id = remembrance.id ?: return
        deleteById(id)
    }

    override suspend fun deleteById(id: Long) {
        transactions.tx("Failed to delete remembrance id=$id") {
            // Translations, explanations and tag links all cascade from `remembrance`, so the
            // parent row is the whole of the delete. It used not to be: the `favorite` row was
            // only *referenced* by the remembrance and had to be cleared by hand, which is what
            // the `is_favorite` column is for. A missing id deletes nothing and reports
            // success, as it did before.
            RemembranceEntity.findById(id)?.delete()
        }
    }

    override suspend fun findById(id: Long): Remembrance? =
        transactions.tx("Failed to find remembrance id=$id") { loadOne(id) }

    override suspend fun findAll(): List<Remembrance> =
        transactions.tx("Failed to find all remembrances") {
            load(RemembranceTable.selectAll().orderBy(RemembranceTable.id to SortOrder.ASC))
        }

    override suspend fun findFirst(): Remembrance? =
        transactions.tx("Failed to find the first remembrance") { loadFirst() }

    override suspend fun findByTagNameIgnoreCase(tagName: String): List<Remembrance> =
        transactions.tx("Failed to find remembrances by tag '$tagName'") {
            load(
                RemembranceTable
                    .innerJoin(RemembranceTagTable)
                    .innerJoin(TagTable)
                    .selectAll()
                    .where { (TagTable.name eq Tag.fold(tagName)) and (TagTable.deletedAt eq null) }
                    .orderBy(RemembranceTable.id to SortOrder.ASC),
            )
        }

    override suspend fun findFavorites(): List<Remembrance> =
        transactions.tx("Failed to find favorite remembrances") {
            load(
                RemembranceTable
                    .selectAll()
                    .where { RemembranceTable.isFavorite eq true }
                    .orderBy(RemembranceTable.id to SortOrder.ASC),
            )
        }

    override suspend fun search(
        locale: Locale,
        query: String,
    ): List<Remembrance> {
        if (query.isBlank()) {
            return emptyList()
        }
        return transactions.tx("Failed to search remembrances") {
            val matchingIds =
                RemembranceTranslationTable
                    .selectAll()
                    .where {
                        (RemembranceTranslationTable.localeCode eq locale.toLanguageTag()) and
                            RemembranceTranslationTable.text.lowerCaseContains(query)
                    }.map { it[RemembranceTranslationTable.ownerId] }
            if (matchingIds.isEmpty()) {
                emptyList()
            } else {
                load(
                    RemembranceTable
                        .selectAll()
                        .where { RemembranceTable.id inList matchingIds }
                        .orderBy(RemembranceTable.id to SortOrder.ASC),
                )
            }
        }
    }

    override suspend fun markFavorite(id: Long) {
        setFavorite(id, favorite = true)
    }

    override suspend fun unmarkFavorite(id: Long) {
        setFavorite(id, favorite = false)
    }

    private suspend fun setFavorite(
        id: Long,
        favorite: Boolean,
    ) {
        val state = if (favorite) "mark" else "unmark"
        transactions.tx("Failed to $state remembrance id=$id as favorite") {
            // A cheap existence probe before the write: backed by `EntityClass.findById`, which is
            // the primary-key lookup rather than a scan, and the point is that this runs before a
            // write rather than as part of one. An update against a row that is not there reports
            // one row changed and reports success.
            if (RemembranceEntity.findById(id) == null) {
                throw RemembranceNotFoundException(id)
            }
            writeFavorite(id, favorite)
        }
    }

    /** The full aggregate for one id, or `null` when no such remembrance exists. */
    private fun loadOne(id: Long): Remembrance? =
        load(RemembranceEntity.findById(id)?.let(::listOf).orEmpty()).firstOrNull()

    /**
     * The full aggregate for whichever row comes first, or `null` when there are none.
     *
     * The read that wants "one of them" used to be written at the call site as
     * `findAll().firstOrNull()`, which is a full scan plus the aggregate fan-out of the whole table
     * to hand back a single row. Pushing the limit down here means the database stops after one
     * parent row and only that row's translations, explanations and tags are ever loaded, so the
     * cost stops depending on how many remembrances the library happens to hold.
     */
    private fun loadFirst(): Remembrance? =
        load(
            RemembranceEntity
                .all()
                .orderBy(RemembranceTable.id to SortOrder.ASC)
                .limit(1)
                .toList(),
        ).firstOrNull()

    /**
     * The full aggregate for a page of parent rows, loading all children in bulk.
     *
     * A caller that has written the predicate in the table DSL — a join, an ordering, a `where` —
     * hands the rows here and the entity class wraps them, so a query nobody thought needed the
     * repository does not have to be rewritten in order to get it.
     */
    private fun load(rows: SizedIterable<ResultRow>): List<Remembrance> =
        load(RemembranceEntity.wrapRows(rows).toList())

    /** The full aggregate for a page of already-loaded parents, loading all children in bulk. */
    private fun load(parents: List<RemembranceEntity>): List<Remembrance> = parents.load()

    /** Inserts the parent row and returns its generated id. */
    private fun insert(
        remembrance: Remembrance,
        now: OffsetDateTime,
    ): Long =
        RemembranceEntity
            .new {
                sourceText = remembrance.source
                grade = remembrance.grade
                createdAt = now
                updatedAt = now
            }.id.value

    private fun update(
        id: Long,
        remembrance: Remembrance,
        now: OffsetDateTime,
    ) {
        RemembranceEntity.findById(id)?.updateAndFlush {
            sourceText = remembrance.source
            grade = remembrance.grade
            // Nothing maintains `updated_at` in the database despite what the DDL comment
            // says, so the application bumps it on every write.
            updatedAt = now
        }
    }

    /**
     * Puts the remembrance into (or takes it out of) the favourited state.
     *
     * A column now rather than a row: the `favorite` table this replaces held nothing but its own
     * `created_at`, nothing ever read that stamp, and the star carries no date anywhere in the
     * domain model or the UI. Modelling it as a row meant an insert, a delete, a second read to work
     * out which of the two to do, and a nullable foreign key whose only two states were "points at a
     * row" and "null".
     *
     * Written unconditionally, and deliberately left out of [update]: a full-row write there would
     * make a rename the moment the star quietly moves. The read-then-decide that the row version
     * needed was there only to avoid an `UPDATE` that changed nothing, and unlike the reminder tick
     * — which rewrites the whole schedule every few seconds — a star is something the user does
     * once, by hand.
     */
    private fun writeFavorite(
        id: Long,
        favorite: Boolean,
    ) {
        RemembranceEntity.findById(id)?.updateAndFlush { isFavorite = favorite }
    }

    /** Stores every translation and only the explanations that contain text. */
    private fun applyTranslations(
        id: Long,
        translations: Map<Locale, Remembrance.Translations>,
        now: OffsetDateTime,
    ) {
        syncLocalizedTexts(
            entityClass = RemembranceTranslationEntity,
            table = RemembranceTranslationTable,
            id = id,
            desired = translations.textsByLocale { it.translationPair.text },
            now = now,
        )
        syncLocalizedTexts(
            entityClass = ExplanationTranslationEntity,
            table = ExplanationTranslationTable,
            id = id,
            desired =
                translations
                    .filterValues { it.explanationPair.text.isNotBlank() }
                    .textsByLocale { it.explanationPair.text },
            now = now,
        )
    }

    /** Replaces the set of tag links, creating any tag row that does not exist yet. */
    private fun applyTags(
        id: Long,
        tags: Set<Tag>,
        now: OffsetDateTime,
    ) {
        val desiredTagIds = tags.map { tagRepository.findOrCreateId(it.name, it.nameLocale) }.toSet()
        val existing =
            RemembranceTagEntity
                .find { RemembranceTagTable.remembranceId eq id }
                .map { it.tagId.value }
                .toSet()
        val stale = existing - desiredTagIds
        if (stale.isNotEmpty()) {
            // One statement for the whole batch of dropped links rather than one per tag. Detaching
            // a link through its entity would issue one `DELETE` each and gain nothing: the rows are
            // identified by the pair, and the pair is already in hand.
            //
            // The inner parentheses are not decoration. `and` and `notInList` are both infix and bind
            // left to right, so unparenthesized this reads as `((a eq id) and tagId) notInList stale`
            // and fails to typecheck.
            RemembranceTagTable.deleteWhere {
                (RemembranceTagTable.remembranceId eq id) and
                    (RemembranceTagTable.tagId notInList stale)
            }
        }
        (desiredTagIds - existing).forEach { tagId ->
            RemembranceTagEntity.new(remembranceTagId(id, tagId)) {
                // Not taken from the domain object: a link that has just been created has not been
                // stamped by anything else, and both audit columns default on the database side
                // anyway. Written rather than left to that default so that an insert read on its own
                // says when the link was made.
                createdAt = now
                updatedAt = now
            }
        }
    }
}
