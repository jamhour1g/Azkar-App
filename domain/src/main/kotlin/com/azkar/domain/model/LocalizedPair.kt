package com.azkar.domain.model

import java.util.Locale

/**
 * A piece of prose that exists in more than one language, and the rule for reading it.
 *
 * Everything a collection is called and described by is one of these, and so is everything a label
 * is called by — a collection has two pieces of prose, a label has one, so the pair rather than a
 * bare string is what both carry. Grouping them matters for a reason beyond tidiness: a shelf card
 * draws its name and its description from the same row, and resolving them independently would let
 * a card put an Arabic name above an English sentence with nothing wrong with either row.
 *
 * The canonical text is *not* held here. It stays in the column it was typed into — `collection.name`,
 * `tag.name` — because that is what the unique index, the search, the sort and the rename check are
 * all built on. A translation table is a display layer over it, not a replacement, and the resolvers
 * below take the canonical value as their floor rather than owning a second copy of it.
 *
 * @property name the name in this language. Never blank: a blank name is a card with nothing on it.
 * @property description the prose in this language, or `null` when the translator stopped at the name.
 */
data class LocalizedPair(
    val name: String,
    val description: String? = null,
) {
    init {
        require(name.isNotBlank()) { "A translated name cannot be blank" }
    }
}

/**
 * The name to draw this collection by, in [locale]'s language.
 *
 * The same chain [RemembranceI18n] walks, and for the same reason — a user reading `ar-EG` should not
 * be shown a gap where an `ar` translation is sitting right there, and a name is not written per
 * country. Past the language check it stops rather than reaching for an arbitrary other language,
 * because [fallback] is the row's own name: a shelf the user named themselves shows that name in
 * every language rather than borrowing a neighbouring one, which would put words in their mouth that
 * they never typed and cannot edit from this screen.
 *
 * @param fallback the canonical name, for the rows that carry no translation at all.
 */
fun Map<Locale, LocalizedPair>.displayName(
    locale: Locale,
    fallback: String,
): String =
    this[locale]?.name
        ?: entries.firstOrNull { it.key.language.equals(locale.language, ignoreCase = true) }?.value?.name
        ?: fallback

/**
 * The prose to draw under this collection's name, in [locale]'s language.
 *
 * Two situations that look alike and are not, which is the whole reason this does not read as a
 * chain of `?:`:
 *
 *  - **The row has been translated into this language, but the translation stops at the name.** There
 *    is nothing to print, and printing the canonical prose here would put English sentences under an
 *    Arabic name — which reads as a mistranslation rather than as the gap it is. So it returns
 *    `null` and the caller leaves the space out.
 *  - **The row has not been translated into this language at all.** Then the canonical prose is the
 *    only prose there is, and it is what this returns — exactly as [displayName] returns the
 *    canonical name. A shelf the user wrote themselves has to read the same in every language, and a
 *    name translated while its prose was not is a far rarer and far less confusing thing than that.
 *
 * Which of the two applies is decided by whether a translation *row* was found for this language,
 * not by whether that row happens to carry prose — hence finding the pair first and only then asking
 * it for a description.
 *
 * @param fallback the canonical description, for rows that carry no translation at all.
 */
fun Map<Locale, LocalizedPair>.displayDescription(
    locale: Locale,
    fallback: String?,
): String? {
    val here: LocalizedPair? =
        this[locale] ?: entries.firstOrNull { it.key.language.equals(locale.language, ignoreCase = true) }?.value
    return here?.description?.takeIf { it.isNotBlank() }
        ?: if (here == null) fallback?.takeIf { it.isNotBlank() } else null
}

/**
 * The label to draw this tag by, in [locale]'s language.
 *
 * Same chain as [displayName] and the same reasoning, with one extra step worth naming: a tag's
 * canonical name is stored *folded* — lower-cased, because `uq_tag__name` is a plain unique index
 * and folding at the boundary is what lets it be one — so falling all the way through to it and
 * printing it raw would put "food & drink" where "Food & Drink" belongs. [capitalized] is applied at
 * the floor for that reason alone, and is a no-op on a script without case.
 *
 * This is also what makes translations double as duplicate prevention. Two tags are the same tag
 * when they fold to the same string, so a tag written in Arabic and a shipped one written in English
 * are two different rows that mean the same thing — which is why the clash check that stands between
 * them is widened to compare against translations as well.
 *
 * @param fallback the canonical (folded) name, capitalised.
 *
 * The `isNotBlank` guard here is load-bearing where the one in [displayName] is not, and the
 * difference is worth knowing about: a [LocalizedPair] cannot be constructed with a blank name, but a
 * tag translation is plain text in a column whose only check is `charLength() > 0`, which a run of
 * spaces satisfies. A whitespace-only tag translation is therefore a row that can really be there, and
 * falling through to the floor is the right answer for it.
 */
fun Map<Locale, String>.displayTagName(
    locale: Locale,
    fallback: String,
): String =
    this[locale]?.takeIf { it.isNotBlank() }
        ?: entries
            .firstOrNull {
                it.key.language.equals(
                    locale.language,
                    ignoreCase = true,
                )
            }?.value
            ?.takeIf { it.isNotBlank() }
        ?: fallback.replaceFirstChar { it.uppercase(locale) }
