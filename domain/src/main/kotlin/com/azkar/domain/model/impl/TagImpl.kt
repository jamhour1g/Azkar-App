package com.azkar.domain.model.impl

import com.azkar.domain.model.Remembrance
import com.azkar.domain.model.Tag
import java.time.Instant

/**
 * The default immutable implementation of [Tag].
 *
 * A tag is a simple label used to categorize or group [Remembrance] entries. It provides a name
 * and may optionally include an identifier and a creation timestamp.
 *
 * As with [RemembranceImpl], the canonical constructor is private so that the name is folded
 * before it reaches the generated `equals`/`hashCode`. That makes two tags named "Morning" and
 * "morning" the same tag by construction, which is the property `tag.name`'s unique index
 * depends on — see [Tag.fold].
 *
 * @throws IllegalArgumentException if [name] is blank
 */
data class TagImpl private constructor(
    override val id: Long?,
    override val name: String,
    override val createdAt: Instant?,
) : Tag {
    init {
        require(name.isNotBlank()) { "Tag name must not be blank" }
    }

    companion object {
        /**
         * Builds a [TagImpl], folding [name] into its stored form: whitespace trimmed, lower case.
         *
         * @param id a unique identifier, or `null` when the tag has never been persisted
         * @param name the name of the tag; must not be blank once trimmed
         * @param createdAt when the tag was introduced, or `null` when unknown
         */
        operator fun invoke(
            id: Long? = null,
            name: String,
            createdAt: Instant? = null,
        ): TagImpl = TagImpl(id = id, name = Tag.fold(name), createdAt = createdAt)
    }
}
