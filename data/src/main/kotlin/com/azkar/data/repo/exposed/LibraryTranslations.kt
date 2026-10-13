package com.azkar.data.repo.exposed

import com.azkar.data.table.CollectionTranslationTable
import com.azkar.data.table.TagTranslationTable
import com.azkar.data.table.entity.CollectionTranslationEntity
import com.azkar.data.table.entity.TagTranslationEntity
import com.azkar.domain.model.LocalizedPair
import org.jetbrains.exposed.v1.core.inList
import java.util.Locale

/**
 * Bulk reads for the shipped wording of the things the library is *filed under* rather than made of.
 *
 * A remembrance carries its own text in [RemembranceAggregates]; these two carry the prose a shelf or
 * a label is named with, which is a different question asked of a different pair of tables and — for
 * the collection side — a different number of columns per locale.
 *
 * Kept apart from the aggregates that consume it because those are about assembling a whole value and
 * this is about one lookup. Every aggregate here ends up asking the same question in the same shape
 * for the same reason, and the answer was otherwise going to be written three times.
 *
 * Every method must be called from inside the caller's transaction — the repositories own
 * transaction boundaries, so this opens none of its own.
 */
internal object LibraryTranslations {
    /**
     * Every stored wording of the collections among [ids], keyed by collection and then by locale.
     *
     * One statement for the whole page, which is the point: asked per collection this is exactly the
     * N+1 the rest of the read path is built to avoid, and a shelf is the shape of query where it
     * hurts — every card wants a name in the current language.
     *
     * A blank description is normalised away rather than carried. The column is nullable so a
     * translation can be a name alone, and "" would import as a description that is somehow empty
     * instead of as none at all — a distinction no reader can act on.
     */
    fun collectionTranslations(ids: List<Long>): Map<Long, Map<Locale, LocalizedPair>> {
        if (ids.isEmpty()) {
            return emptyMap()
        }
        return CollectionTranslationEntity
            .find { CollectionTranslationTable.collectionId inList ids }
            .groupBy { it.collectionId }
            .mapValues { (_, rows) ->
                rows.associate { row ->
                    Locale.forLanguageTag(row.localeCode) to
                        LocalizedPair(
                            name = row.name,
                            description = row.description?.takeIf { it.isNotBlank() },
                        )
                }
            }
    }

    /**
     * Every stored label of the tags among [ids], keyed by tag and then by locale.
     *
     * Same shape as [collectionTranslations] for the same reason, and against a single-text table, so
     * this one could have gone through the generic [LocalizedText] path the remembrance translations
     * take. It is spelled out rather than shared because the answer here is a bare `Map<Locale,
     * String>` with no row ids to carry — there is nothing a caller could do with the id of a label
     * translation, and threading [com.azkar.data.mapping.LocalizedTextRow] through to discard it at
     * three call sites would be the shape that reads worse.
     */
    fun tagTranslations(ids: List<Long>): Map<Long, Map<Locale, String>> {
        if (ids.isEmpty()) {
            return emptyMap()
        }
        return TagTranslationEntity
            .find { TagTranslationTable.ownerId inList ids }
            .groupBy { it.ownerId }
            .mapValues { (_, rows) ->
                rows.associate { row -> Locale.forLanguageTag(row.localeCode) to row.text }
            }
    }
}
