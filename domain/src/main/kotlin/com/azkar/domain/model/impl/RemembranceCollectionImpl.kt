package com.azkar.domain.model.impl

import com.azkar.domain.model.CollectionKind
import com.azkar.domain.model.LocalizedPair
import com.azkar.domain.model.Remembrance
import com.azkar.domain.model.RemembranceCollection
import com.azkar.domain.model.Tag
import java.time.Instant
import java.util.Locale

/**
 * The default immutable implementation of [RemembranceCollection].
 *
 * As with [RemembranceImpl] and [TagImpl], the canonical constructor is private so that trimming
 * and validation happen before the generated `equals`/`hashCode` see the values.
 *
 * @throws IllegalArgumentException if [name] is blank
 */
data class RemembranceCollectionImpl private constructor(
    override val id: Long?,
    override val name: String,
    override val nameLocale: Locale,
    override val description: String?,
    override val kind: CollectionKind,
    override val iconKey: String?,
    override val coverImagePath: String?,
    override val tags: Set<Tag>,
    override val pinnedAt: Instant?,
    override val createdAt: Instant?,
    override val updatedAt: Instant?,
    override val lastOpenedAt: Instant?,
    override val homePinnedAt: Instant?,
    override val isFavorite: Boolean,
    override val members: List<Remembrance>,
    override val translations: Map<Locale, LocalizedPair>,
    override val position: Int?,
) : RemembranceCollection {
    init {
        require(name.isNotBlank()) { "Collection name must not be blank" }
    }

    companion object {
        /**
         * Builds a [RemembranceCollectionImpl], trimming [name] and [description] and normalizing
         * blank text to `null`.
         *
         * Every optional text field is trimmed and blank-normalized through the same helper, so a
         * cover that points at nothing and a cover that points at an empty name cannot be told apart
         * by whichever caller remembered to check.
         *
         * [translations] is copied rather than aliased for the same reason [tags] and [members] are:
         * a caller building a collection out of a draft holds a map it may still be editing, and a
         * collection that reflected a later edit to it would have changed without being saved.
         */
        operator fun invoke(
            id: Long? = null,
            name: String,
            description: String? = null,
            kind: CollectionKind = CollectionKind.CUSTOM,
            iconKey: String? = null,
            coverImagePath: String? = null,
            tags: Set<Tag> = emptySet(),
            pinnedAt: Instant? = null,
            createdAt: Instant? = null,
            updatedAt: Instant? = null,
            lastOpenedAt: Instant? = null,
            homePinnedAt: Instant? = null,
            isFavorite: Boolean = false,
            members: List<Remembrance> = emptyList(),
            translations: Map<Locale, LocalizedPair> = emptyMap(),
            nameLocale: Locale = Locale.ENGLISH,
            position: Int? = null,
        ): RemembranceCollectionImpl =
            RemembranceCollectionImpl(
                id = id,
                name = name.trim(),
                nameLocale = nameLocale,
                description = description.trimmedOrNull(),
                kind = kind,
                iconKey = iconKey.trimmedOrNull(),
                coverImagePath = coverImagePath.trimmedOrNull(),
                tags = tags.toSet(),
                pinnedAt = pinnedAt,
                createdAt = createdAt,
                updatedAt = updatedAt,
                lastOpenedAt = lastOpenedAt,
                homePinnedAt = homePinnedAt,
                isFavorite = isFavorite,
                members = members.toList(),
                translations = translations.toMap(),
                position = position,
            )
    }
}

private fun String?.trimmedOrNull(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
