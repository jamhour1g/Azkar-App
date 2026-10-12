package com.azkar.domain.model

import java.util.Locale

/**
 * Where one tag actually lands: the shelves carrying it, and the remembrances carrying it.
 *
 * The management screen can already say *how many* things carry a tag — [TagSummary] answers that,
 * and the list draws it without loading anything else. This is the other question: not how many, but
 * which. It is what turns a row of counts into something actionable, because a user who is about to
 * rename or delete a label needs to see the two collections it is about to be taken off before they
 * act, not the total afterwards.
 *
 * [tag] is carried so the panel can be rendered from one answer — the caller opens this and needs the
 * name and the stamp that identify what is being looked at, and re-reading the summary to get them
 * would make the screen hold two overlapping views of one row.
 *
 * Both lists are whole, and neither is paged. The counts on the row are how the user learns how big
 * each side is, and the panel opens *because* they have decided they want to see all of it; a panel
 * that showed the first ten of two hundred with no way to reach the rest would be worse than no
 * panel. In practice the lists are bounded by what a single tag can have attached to it, and they
 * are only ever read for one tag at a time.
 */
data class TagUsage(
    val tag: TagSummary,
    val collections: List<CollectionSummary>,
    val remembrances: List<TagUsageRemembrance>,
) {
    init {
        require(collections.all { it.id > 0L }) { "A usage entry must belong to a real collection" }
        require(remembrances.all { it.id > 0L }) { "A usage entry must belong to a real remembrance" }
    }
}

/**
 * A remembrance as the usage panel needs it: enough to name it in a list and to open it.
 *
 * Not a [Remembrance], and not a summary of one, for the reason [CollectionSummary] gives: a panel
 * row wants the id to navigate by and some text to recognise the passage by, and assembling the full
 * aggregate would mean loading both translation locales and both explanation locales for every
 * passage on the list in order to print one line of it.
 *
 * [texts] is a `Map<Locale, String>` rather than a decided-upon `preview` because which translation
 * reads as a recognisable identifier is the interface's choice and not this model's — a user reading
 * in English recognises the English line, one reading in Arabic recognises the Arabic one, and they
 * are different strings of the same passage. It matches how [Remembrance.translations] is shaped for
 * the same reason. What the text is *for* here is identification, not reading, which is why
 * [displayText] falls back the way it does.
 *
 * @property id the remembrance to open when the row is chosen.
 * @property texts the passage in each language it has been translated into.
 * @property source where the passage is attributed, or `null` when unattributed.
 */
data class TagUsageRemembrance(
    val id: Long,
    val texts: Map<Locale, String>,
    val source: String?,
) {
    init {
        require(id > 0L) { "A usage entry must belong to a real remembrance" }
    }

    /**
     * The line to name this passage by, preferring [preferred]'s language.
     *
     * Falls back three ways, and each step exists for a reason:
     *
     * 1. The exact locale, then any locale of the same *language*. A user reading `ar-EG` should not be
     *    shown a gap where an `ar` translation is sitting right there, and a passage is not translated
     *    per country — the language is the part that has to match.
     * 2. Whichever text there is. The row still has to be identifiable, and an Arabic line under an
     *    English interface beats no line at all.
     * 3. [source]. Last because attribution names a book, not a passage, so it is the weakest thing
     *    that could stand in for one — but a nameless row in a list of named rows is worse.
     *
     * `null` only when there is genuinely nothing: no translation in any language and no attribution.
     */
    fun displayText(preferred: Locale): String? =
        texts[preferred]
            ?: texts.entries.firstOrNull { it.key.language == preferred.language }?.value
            ?: texts.values.firstOrNull { it.isNotBlank() }
            ?: source?.takeIf { it.isNotBlank() }
}
