package com.azkar.data.mapping

import com.azkar.domain.model.CollectionKind
import com.azkar.domain.model.LocalizedPair
import com.azkar.domain.model.Remembrance
import com.azkar.domain.model.RemembranceCollection
import com.azkar.domain.model.Tag
import com.azkar.domain.model.impl.RemembranceCollectionImpl
import java.time.Instant
import java.util.Locale

/**
 * The columns of one `collection` row, already unwrapped from Exposed's types.
 *
 * `translations` is not a column and is not here because [com.azkar.data.repo.exposed.CollectionAggregates]
 * loads it in bulk alongside the parents rather than per row — which is why it has a default here and
 * why every construction site still names the other twelve fields.
 */
internal data class CollectionRow(
    val id: Long,
    val name: String,
    val description: String?,
    val kind: CollectionKind,
    val iconKey: String?,
    val coverImagePath: String?,
    val pinnedAt: Instant?,
    val createdAt: Instant?,
    val updatedAt: Instant?,
    val lastOpenedAt: Instant?,
    val homePinnedAt: Instant?,
    val isFavorite: Boolean,
    val position: Int?,
    val translations: Map<Locale, LocalizedPair> = emptyMap(),
    val nameLocale: Locale = Locale.ENGLISH,
) {
    fun toCollection(
        members: List<Remembrance>,
        tags: Set<Tag>,
    ): RemembranceCollection =
        RemembranceCollectionImpl(
            id = id,
            name = name,
            nameLocale = nameLocale,
            description = description,
            kind = kind,
            iconKey = iconKey,
            coverImagePath = coverImagePath,
            tags = tags,
            pinnedAt = pinnedAt,
            createdAt = createdAt,
            updatedAt = updatedAt,
            lastOpenedAt = lastOpenedAt,
            homePinnedAt = homePinnedAt,
            isFavorite = isFavorite,
            members = members,
            translations = translations,
            position = position,
        )
}
