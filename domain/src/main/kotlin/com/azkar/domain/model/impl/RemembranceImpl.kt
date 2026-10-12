package com.azkar.domain.model.impl

import com.azkar.domain.model.HadithGrade
import com.azkar.domain.model.Remembrance
import com.azkar.domain.model.Tag
import java.time.Instant
import java.util.Locale

/**
 * The default immutable implementation of [Remembrance].
 *
 * A remembrance carries its grade, source reference, translations, tags and timestamps, and says
 * whether it has been marked as a favorite.
 *
 * The canonical constructor is private and the [companion][Companion.invoke] operator is public
 * instead, because Kotlin's `data class` cannot normalise its own properties: `equals`,
 * `hashCode`, `toString` and `copy` are all generated from the raw constructor parameters. Routing
 * every call through [invoke] is what keeps `"  Book of Dua  "` and `"Book of Dua"` equal, the way
 * the Java record's compact constructor did.
 *
 * @property id a unique identifier, or `null` when the remembrance has never been persisted
 * @property grade the hadith grade (authenticity/reliability level)
 * @property isFavorite whether this remembrance has been marked as a favorite
 * @property source the source reference (e.g. book, scholar), or `null` when blank
 * @property createdAt when the remembrance was created, or `null` when unknown
 * @property updatedAt when the remembrance was last updated, or `null` when unknown
 * @property translations the translations keyed by locale; never `null`
 * @property tags the tags associated with this remembrance; never `null`
 * @throws IllegalArgumentException if [updatedAt] is earlier than [createdAt]
 */
data class RemembranceImpl private constructor(
    override val id: Long?,
    override val grade: HadithGrade,
    override val isFavorite: Boolean,
    override val source: String?,
    override val createdAt: Instant?,
    override val updatedAt: Instant?,
    override val translations: Map<Locale, Remembrance.Translations>,
    override val tags: Set<Tag>,
) : Remembrance {
    init {
        require(createdAt == null || updatedAt == null || !updatedAt.isBefore(createdAt)) {
            "updatedAt cannot be before createdAt"
        }
    }

    companion object {
        /**
         * Builds a [RemembranceImpl], normalising `source` to `null` when it is blank and
         * defensively copying [tags] and [translations] so the result cannot be mutated through a
         * collection the caller still holds.
         */
        operator fun invoke(
            id: Long? = null,
            grade: HadithGrade = HadithGrade.UNSPECIFIED,
            isFavorite: Boolean = false,
            source: String? = null,
            createdAt: Instant? = null,
            updatedAt: Instant? = null,
            translations: Map<Locale, Remembrance.Translations> = emptyMap(),
            tags: Set<Tag> = emptySet(),
        ): RemembranceImpl =
            RemembranceImpl(
                id = id,
                grade = grade,
                isFavorite = isFavorite,
                source = source?.trim()?.takeIf { it.isNotEmpty() },
                createdAt = createdAt,
                updatedAt = updatedAt,
                translations = translations.toMap(),
                tags = tags.toSet(),
            )
    }
}
