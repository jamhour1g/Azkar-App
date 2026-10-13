/**
 * Writing and clearing per-locale text, for every table that has one.
 *
 * The two translation tables that a remembrance owns were joined here first, because they are the
 * same shape and writing one twice would be a second copy of a reconciliation to keep in step with
 * the first. A third table with that same shape — a label's own wording, `tag_translation` —
 * turned the second copy into a liability, so the reconciliation moved out to be shared rather than
 * copied again.
 *
 * What is deliberately *not* here is the pair of names a collection carries. `collection_translation`
 * holds a name and a description rather than one text, so it reconciles by its own function and
 * cannot be expressed as a [LocalizedTextTable] however much the two shapes rhyme. Sharing the loop
 * across a third shape would mean passing column accessors in and giving up the statically-checked
 * field names, which is a worse trade than a second short function.
 */

package com.azkar.data.repo.exposed

import com.azkar.data.table.LocalizedTextTable
import com.azkar.data.table.entity.LocalizedText
import com.azkar.data.table.entity.updateAndFlush
import com.azkar.domain.model.LocalizedPair
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.dao.Entity
import org.jetbrains.exposed.v1.dao.EntityClass
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.time.OffsetDateTime
import java.util.Locale

/**
 * Reconciles one [LocalizedTextTable] for one parent row: rows for locales that are no longer
 * present are deleted, known locales are updated in place, the rest are inserted.
 *
 * Identity is keyed on `(owner_id, locale_code)` — the schema's own unique key — so the id carried
 * by the incoming domain object is only honoured when it still points at a live row. A row is found
 * by that pair rather than by the id the domain object remembered, for exactly that reason: a row
 * re-created after a delete comes back with an id nothing is holding, and the pair is the only
 * handle that means anything.
 *
 * Written as one function over every table that fits because [RemembranceTranslationEntity],
 * [ExplanationTranslationEntity] and [TagTranslationEntity] implement [LocalizedText] and have
 * identical shapes; the entity class and the table are passed in because that is the only thing that
 * tells them apart. The type carries two bounds rather than one because it has to be both halves of
 * that sentence at once: `EntityClass<ID, T>` requires `T : Entity<ID>`, and this function needs `T`
 * to be a [LocalizedText] as well. A single bound can only be one of the two.
 */
internal fun <T> syncLocalizedTexts(
    entityClass: EntityClass<Long, T>,
    table: LocalizedTextTable,
    id: Long,
    desired: Map<String, String>,
    now: OffsetDateTime,
) where T : Entity<Long>, T : LocalizedText {
    val existing = table.existingLocales(id)

    // One statement for the whole batch of removed locales, rather than one per locale.
    (existing - desired.keys).forEach { locale ->
        table.deleteWhere { (table.ownerId eq id) and (table.localeCode eq locale) }
    }
    desired.forEach { (locale, text) ->
        val row =
            entityClass
                .find {
                    (table.ownerId eq id) and (table.localeCode eq locale)
                }.firstOrNull()
        if (row != null) {
            row.updateAndFlush {
                this.text = text
                updatedAt = now
            }
        } else {
            entityClass.new {
                ownerId = id
                localeCode = locale
                this.text = text
                createdAt = now
                updatedAt = now
            }
        }
    }
}

/**
 * Deletes every row this table holds for [id].
 *
 * What a rename does. A row whose canonical name has changed is no longer the thing the translations
 * were written for, and leaving them would show a name in another language that the editor no longer
 * holds anywhere — a shelf card whose Arabic title and English title disagree about what it is.
 *
 * Takes the table rather than an entity class for the same reason [syncLocalizedTexts] does: the
 * caller knows which table it is clearing and does not care what shape its rows have.
 */
internal fun LocalizedTextTable.clearTexts(id: Long) {
    deleteWhere { ownerId eq id }
}

/**
 * The locale codes one parent row already has a row for in this table.
 *
 * Takes the table rather than an entity class because every table of this shape answers the question
 * identically, and the writer reconciling one of them does not care which it is.
 */
internal fun LocalizedTextTable.existingLocales(id: Long): Set<String> =
    this
        .selectAll()
        .where { ownerId eq id }
        .map { it[localeCode] }
        .toSet()

/**
 * Re-keys a per-locale map by BCP-47 language tag, which is what `locale_code` stores.
 *
 * Generic over the value rather than bound to one domain type, because the three callers each hold a
 * different one — a remembrance's `Translations`, a collection's name and description, a label's
 * name — and none of them should have to restate the same `entries.associate` to get there. [extract]
 * exists because only some of them want the value whole.
 */
internal fun <T> Map<Locale, T>.textsByLocale(extract: (T) -> String): Map<String, String> =
    entries.associate { (locale, value) -> locale.toLanguageTag() to extract(value) }

/**
 * Re-keys a per-locale map of plain strings by BCP-47 language tag, which is what `locale_code`
 * stores.
 *
 * The no-extractor counterpart to the overload above, for the case where the value *is* the text.
 */
internal fun Map<Locale, String>.textsByLocale(): Map<String, String> =
    entries.associate { (locale, text) -> locale.toLanguageTag() to text }

/**
 * Re-keys a per-locale map of name-and-description pairs, keeping both fields.
 *
 * Separate from the two above rather than an overload because the value is a two-field row and this
 * one is not lossy: a blanket `textsByLocale { it.name }` would drop every description on the way to
 * storage, which is precisely the field a shelf card draws second and the reason the table has two
 * columns rather than being another [LocalizedTextTable].
 */
internal fun Map<Locale, LocalizedPair>.pairsByLocale(): Map<String, LocalizedPair> =
    entries.associate { (locale, pair) -> locale.toLanguageTag() to pair }
