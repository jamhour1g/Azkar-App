package com.azkar.data.mapping

import com.azkar.data.table.entity.CollectionReminderEntity
import com.azkar.data.table.entity.RecitationProgressEntity
import com.azkar.data.table.entity.TagEntity
import com.azkar.domain.model.CollectionReminder
import com.azkar.domain.model.RecitationProgress
import com.azkar.domain.model.ReminderCadence
import com.azkar.domain.model.Tag
import com.azkar.domain.model.TagSummary
import com.azkar.domain.model.impl.TagImpl
import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.DayOfWeek
import java.util.Locale

// Entity -> domain. Three extensions, one per entity whose fields are the whole of a domain value.
//
// They used to be `ResultRow` extensions reading `row[Table.column]`; the entity hands over the
// column as a named property, so each of these is shorter than the one it replaces and cannot be
// handed a row that belongs to a different table.
//
// The composite-key entities need no extension of their own for `id`: `CollectionTagEntity` and
// `CollectionMemberEntity` exist only to be written and deleted, and a repository that wants to know
// which row it just touched reads `.id.value` inline.

// `tag` — one label. Stored folded, and it comes back out folded, which is the invariant the unique
// index on `tag.name` rests on (see `TagTable.name`).
//
// The translations ride in as an argument for the same reason the counts do below: they are a second
// table, and a mapper that went back for them itself would be issuing a statement per row of whatever
// list it is mapping.
private val logger = KotlinLogging.logger {}

internal fun TagEntity.toTag(translations: Map<Locale, String> = emptyMap()): Tag =
    TagImpl(
        id = id.value,
        name = name,
        nameLocale = Locale.forLanguageTag(nameLocaleCode),
        createdAt = createdAt.toInstant(),
        translations = translations,
    )

/**
 * `tag` — one label, with what is hanging off it.
 *
 * A second extension over the same entity rather than an overload of [toTag], because the two answer
 * different questions and neither is the other with a field missing: a [Tag] is something a collection
 * *carries*, and carries nothing about how many things carry it, while a [TagSummary] is something the
 * management screen *lists*, and the two counts are the reason it exists.
 *
 * The counts are arguments rather than another query here, because they come from the join tables and
 * a mapper that went back for them would be issuing a statement per row of the list it is mapping.
 * The translations are arguments for the same reason, one table over.
 */
internal fun TagEntity.toSummary(
    collectionCount: Int,
    remembranceCount: Int,
    translations: Map<Locale, String> = emptyMap(),
): TagSummary =
    TagSummary(
        id = id.value,
        name = name,
        nameLocale = Locale.forLanguageTag(nameLocaleCode),
        createdAt = createdAt.toInstant(),
        updatedAt = updatedAt.toInstant(),
        collectionCount = collectionCount,
        remembranceCount = remembranceCount,
        deletedAt = deletedAt?.toInstant(),
        translations = translations,
    )

/**
 * `recitation_progress` — how far through one collection one member has got.
 *
 * The row is keyed on the collection as well as the remembrance, so a row found by remembrance alone
 * carries both ids while [RecitationProgress] names only the passage. That is deliberate: the domain
 * question "how often was this recited?" is about the passage, and the collection is what the caller
 * already knows.
 */
internal fun RecitationProgressEntity.toProgress(): RecitationProgress =
    RecitationProgress(
        remembranceId = remembranceId.value,
        repeatCount = repeatCount,
        lastRecitedAt = lastRecitedAt?.toInstant(),
    )

/**
 * `collection_reminder` — the standing nudge to come back to one collection.
 *
 * Returns `null` rather than throwing for a weekly schedule whose `day_of_week` does not name a day.
 * The column is free text and the schema cannot constrain it to `DayOfWeek` names, so a row written
 * by an import or an older release can hold something unparseable; a reminder that cannot say which
 * day it fires is not a reminder, and the caller's answer to that is "no such reminder", not a crash
 * in the middle of the tick that reads every one of them.
 */
internal fun CollectionReminderEntity.toReminder(): CollectionReminder? {
    val day =
        dayOfWeek?.let { stored ->
            runCatching { DayOfWeek.valueOf(stored) }
                .onFailure { failure -> logger.warn(failure) { "Unable to parse the stored day of week [$stored]" } }
                .getOrNull()
        }
    if (cadence == ReminderCadence.WEEKLY && day == null) return null
    return CollectionReminder(
        collectionId = collectionId,
        cadence = cadence,
        minuteOfDay = minuteOfDay,
        dayOfWeek = day,
        enabled = enabled,
        lastTriggeredAt = lastTriggeredAt?.toInstant(),
    )
}
