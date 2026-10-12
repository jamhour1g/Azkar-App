package com.azkar.data.mapping

import com.azkar.domain.model.CollectionKind
import com.azkar.domain.model.Remembrance
import com.azkar.domain.model.RemembranceCollection
import com.azkar.domain.model.Tag
import com.azkar.domain.model.impl.RemembranceCollectionImpl
import java.time.Instant

/** The columns of one `collection` row, already unwrapped from Exposed's types. */
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
) {
    fun toCollection(
        members: List<Remembrance>,
        tags: Set<Tag>,
    ): RemembranceCollection =
        RemembranceCollectionImpl(
            id = id,
            name = name,
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
        )
}
