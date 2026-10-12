package com.azkar.domain.model

import java.time.Instant
import java.util.Locale

/**
 * Represents a remembrance (dua, or hadith) in the domain model.
 *
 * A remembrance encapsulates a specific piece of text along with its grade, source reference,
 * translations and [associated tags][Tag], plus the metadata describing when it was introduced
 * and last updated.
 *
 * The storage or retrieval mechanism is intentionally abstracted away — a remembrance may come
 * from a local repository, an online source, or another provider. Every descriptive field is
 * nullable because no value is guaranteed to be known or provided.
 */
interface Remembrance {
    /**
     * A unique identifier for this remembrance, or `null` when it has never been persisted.
     *
     * The identifier distinguishes this remembrance from others, but its presence is not
     * guaranteed: an object handed to the UI before it has been saved carries none.
     */
    val id: Long?

    /** The hadith grade of this remembrance, or [HadithGrade.UNSPECIFIED] when unknown. */
    val grade: HadithGrade

    /** Whether this remembrance has been marked as a favorite by a user. */
    val isFavorite: Boolean

    /**
     * The source reference of the remembrance — a book title, a scholar's name, a collection, or
     * any other textual reference. `null` when not specified.
     */
    val source: String?

    /** When this remembrance was first introduced or recorded, or `null` when unknown. */
    val createdAt: Instant?

    /** When this remembrance was last updated or modified, or `null` when unknown. */
    val updatedAt: Instant?

    /**
     * The translations of this remembrance, keyed by [Locale].
     *
     * Each entry provides both an explanation and a textual rendering of the remembrance in the
     * target language.
     */
    val translations: Map<Locale, Translations>

    /** The tags associated with this remembrance, used for categorization, filtering or grouping. */
    val tags: Set<Tag>

    /** The translations for [locale], or `null` when this remembrance carries none for it. */
    fun translationsFor(locale: Locale): Translations? = translations[locale]

    /**
     * The translations of a remembrance: both an explanation of it and a rendering of its text.
     *
     * @property explanationPair the explanation of the remembrance
     * @property translationPair the translation of the remembrance text
     */
    data class Translations(
        val explanationPair: Pair,
        val translationPair: Pair,
    ) {
        /**
         * A single text element — either an explanation or a translation.
         *
         * @property id an identifier for referencing this element, or `null` when not provided
         * @property text the actual text content
         */
        data class Pair(
            val id: Long?,
            val text: String,
        )
    }
}
