package com.azkar.ui.library

import com.azkar.ui.library.model.TagOption

/**
 * The tag-picking half of a form, so that one panel can serve every form that has tags.
 *
 * An interface rather than a second copy of the panel, because the panel is not the interesting part:
 * the rules it encodes — fold to lowercase, never hide a chosen tag behind the query, let a tag be
 * created from inside the picker — are the same rules whichever thing is being tagged, and two copies
 * of them are two sets of rules that will be amended one at a time. What differs between a collection
 * and a remembrance is everything *around* the tags, and that is what the drafts below each hold.
 *
 * Deliberately narrow: the search box, the counter, the chip row and the new-tag row are the whole
 * surface, so nothing here can drift into being a form's other half.
 */
internal interface TagDraftView {
    /** The tag names carried so far, in the order they were picked. */
    val tags: List<String>

    /** What the picker is filtering by. */
    val tagQuery: String

    /** The half-typed name of a tag that does not exist yet. */
    val newTagName: String

    /** Whether [addTag] would do something, which is what the "Add tag" button's enabled state is. */
    val newTagAcceptable: Boolean

    fun updateTagQuery(value: String)

    fun updateNewTagName(value: String)

    fun isTagSelected(name: String): Boolean

    fun toggleTag(name: String)

    fun clearTags()

    fun addTag(raw: String): Boolean

    /** The options to draw, folded together with anything chosen here that storage has yet to hear of. */
    fun tagOptions(available: List<TagOption>): List<TagOption>
}
