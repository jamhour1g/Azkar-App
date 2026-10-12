package com.azkar.domain.model

import java.time.Instant

/**
 * Represents a tag that can be associated with a [Remembrance].
 *
 * A tag is a simple label used for categorization, grouping or filtering. It carries a name and
 * may optionally include metadata such as a unique identifier and the time it was introduced.
 *
 * The origin of a tag is left abstract: it may come from a local source, an external service, or
 * be dynamically created within the system.
 */
interface Tag {
    /** A unique identifier for this tag, or `null` when the tag has never been persisted. */
    val id: Long?

    /**
     * The name of this tag — the main label describing it, such as "morning" or "gratitude".
     *
     * Always folded to lower case by [TagImpl], which is what makes two tags named the same word
     * the same tag however either was spelled. Presentation capitalises it again; nothing should
     * have to guess which form it is holding.
     */
    val name: String

    /** When this tag was first introduced or recorded in the system, or `null` when unknown. */
    val createdAt: Instant?

    companion object {
        /**
         * Folds [name] into the form a [Tag] stores and the `tag` table holds it in.
         *
         * Call this on any name arriving from outside the domain — a query parameter, a seed row,
         * an imported file — before comparing it to [name]. It is the same operation [TagImpl]
         * applies on construction, which is what lets a plain unique index on `tag.name` stand in
         * for a case-insensitive one: a unique index on text is case-sensitive, so the guarantee
         * only holds while every value that reaches the column has been through here.
         */
        fun fold(name: String): String = name.trim().lowercase()
    }
}
