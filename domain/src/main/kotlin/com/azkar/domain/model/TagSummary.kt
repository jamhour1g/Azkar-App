package com.azkar.domain.model

import java.time.Instant
import java.util.Locale

/**
 * A tag as the management screen needs to see it: the row, plus how many things are hanging off it.
 *
 * A projection rather than a richer [Tag] because the counts are the whole point of asking. The
 * alternative is to answer "which shelves use this label" by loading every collection and every
 * remembrance and folding the counts out of them in the UI — which is what the shelf already does for
 * the filter chips, and is fine there because it has already paid for the read. The management screen
 * has not: it opens on its own and wants a tag, a number next to it and nothing else. Making it ask
 * for the entire library to render a list of words would make it the most expensive screen in the app
 * to draw the least.
 *
 * So the two counts come from the join tables by aggregate and land here beside the name. The row
 * itself is carried whole, including [deletedAt], because the screen has to show what it took out of
 * circulation as well as what is currently in use — a soft delete the user cannot see the result of is
 * a tag they will believe they lost.
 *
 * The name is folded, as everywhere else. See [Tag.fold].
 *
 * @property id the tag's identifier, never `null` — a summary is only ever produced for a stored row.
 * @property collectionCount how many collections carry this tag.
 * @property remembranceCount how many remembrances carry this tag.
 * @property deletedAt when the tag was soft deleted, or `null` while it is in use.
 */
data class TagSummary(
    val id: Long,
    val name: String,
    val createdAt: Instant?,
    val updatedAt: Instant?,
    val collectionCount: Int,
    val remembranceCount: Int,
    val deletedAt: Instant?,
    val translations: Map<Locale, String> = emptyMap(),
    val nameLocale: Locale = Locale.ENGLISH,
) {
    init {
        require(name.isNotBlank()) { "A tag summary needs a name" }
        require(collectionCount >= 0) { "A tag cannot be carried by $collectionCount collections" }
        require(remembranceCount >= 0) { "A tag cannot be carried by $remembranceCount remembrances" }
    }

    /**
     * The name to draw when the interface is in [locale].
     *
     * The same resolver a [Tag] uses, for the same reason: this screen and the shelf's chip row draw
     * the same tags, and one of them answering "morning" while the other answered "أذكار الصباح"
     * because only this one read the table would be worse than either answer.
     */
    fun displayName(locale: Locale): String = translations.displayTagName(locale, name)

    /**
     * Whether this tag is out of circulation and can be brought back.
     *
     * A property rather than a nullable comparison the screen repeats, because the two lists this
     * model feeds are meant to be disjoint and a caller that filtered on `deletedAt == null` somewhere
     * else would produce a third answer.
     */
    val isDeleted: Boolean get() = deletedAt != null

    /**
     * Whether anything still refers to this tag.
     *
     * What decides the wording of the delete confirmation. A tag on nothing costs nothing to remove,
     * and one on nine collections does — but because the removal is a tombstone, the honest question
     * is not "will this disappear" either way; it is "will it leave nine shelves missing a chip", and
     * that is the count worth putting in front of the person confirming.
     */
    val usageCount: Int get() = collectionCount + remembranceCount
}
