package com.azkar.data.repo.exposed

import com.azkar.data.mapping.LocalizedTextRow
import com.azkar.data.mapping.RemembranceMapper.toRemembrance
import com.azkar.data.mapping.toTag
import com.azkar.data.table.ExplanationTranslationTable
import com.azkar.data.table.LocalizedTextTable
import com.azkar.data.table.RemembranceTagTable
import com.azkar.data.table.RemembranceTranslationTable
import com.azkar.data.table.TagTable
import com.azkar.data.table.entity.ExplanationTranslationEntity
import com.azkar.data.table.entity.LocalizedText
import com.azkar.data.table.entity.RemembranceEntity
import com.azkar.data.table.entity.RemembranceTranslationEntity
import com.azkar.data.table.entity.TagEntity
import com.azkar.domain.model.Remembrance
import com.azkar.domain.model.Tag
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.util.Locale

/**
 * Assembles whole [Remembrance] aggregates out of the tables they are spread across.
 *
 * A remembrance has one parent row, up to one row per locale in each of the two translation tables,
 * and a set of tags through the join table. Every read here loads the parents first and then
 * bulk-loads the children for the whole result set, which keeps the query count constant instead of
 * producing the N+1 a per-row walk would. The JPQL version this replaced needed explicit
 * `JOIN FETCH` hints for exactly the same reason.
 *
 * This is an object rather than a collaborator on either repository because two of them need it and
 * neither owns it: [ExposedRemembranceRepository] assembles the aggregates it returns directly, and
 * [ExposedCollectionRepository] assembles the members of a collection, which are whole remembrances
 * by the time a shelf shows them. Everything either of them would otherwise need is here, and
 * neither can assemble an aggregate alone.
 *
 * Every method must be called from inside the caller's transaction — the repositories own
 * transaction boundaries, so this opens none of its own. Reads go through the entity classes; the
 * raw statements left here are the two joins a single table cannot express, which is why they are
 * marked where they appear.
 */
internal object RemembranceAggregates {
    /** The full aggregate for a page of already-loaded parents, loading all children in bulk. */
    fun List<RemembranceEntity>.load(): List<Remembrance> {
        if (isEmpty()) {
            return emptyList()
        }
        val ids = map { it.id.value }
        val translations =
            localizedTexts(
                RemembranceTranslationEntity.find { RemembranceTranslationTable.remembranceId inList ids },
            )
        val explanations =
            localizedTexts(
                ExplanationTranslationEntity.find { ExplanationTranslationTable.remembranceId inList ids },
            )
        val tags = tagsByRemembrance(ids)
        return map { entity ->
            val id = entity.id.value
            entity.toRemembrance(
                translations = translations[id].orEmpty(),
                explanations = explanations[id].orEmpty(),
                tags = tags[id].orEmpty(),
            )
        }
    }

    /**
     * The locale codes one remembrance already has a row for in the given translation table.
     *
     * Takes the table rather than an entity class because both translation tables answer this
     * identically and the writer reconciling one of them does not care which it is.
     */
    fun LocalizedTextTable.existingLocales(id: Long): Set<String> =
        this
            .selectAll()
            .where { remembranceId eq id }
            .map { it[localeCode] }
            .toSet()

    /**
     * The text rows of one translation table, grouped by remembrance and then by locale.
     *
     * One query per table for the whole page, not one per parent: this is the statement that would
     * become the N+1 if it were asked per row, and it is asked per row.
     */
    private fun localizedTexts(rows: Iterable<LocalizedText>): Map<Long, Map<Locale, LocalizedTextRow>> =
        rows
            .groupBy { it.remembranceId }
            .mapValues { (_, texts) ->
                texts.associate { text ->
                    Locale.forLanguageTag(text.localeCode) to
                        LocalizedTextRow(
                            id = text.rowId,
                            text = text.text,
                        )
                }
            }

    /**
     * The tags of every remembrance among [ids], keyed by remembrance.
     *
     * Asked as one question over the whole page rather than one question per parent. The join table
     * is the only place the answer lives — `remembrance_tag` carries no tag of its own — and asking
     * it once for the page is what keeps this off the N+1 path the rest of this class avoids. The
     * joined rows are wrapped into [TagEntity]s so the name and the id come off the entity rather
     * than off column indices.
     */
    private fun tagsByRemembrance(ids: List<Long>): Map<Long, Set<Tag>> {
        if (ids.isEmpty()) {
            return emptyMap()
        }
        return (RemembranceTagTable innerJoin TagTable)
            .selectAll()
            .where { RemembranceTagTable.remembranceId inList ids }
            .groupBy(
                { it[RemembranceTagTable.remembranceId].value },
                { TagEntity.wrapRow(it).toTag() },
            ).mapValues { (_, tags) -> tags.toSet() }
    }
}
