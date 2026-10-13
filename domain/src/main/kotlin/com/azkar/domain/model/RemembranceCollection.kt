package com.azkar.domain.model

import java.time.Instant
import java.util.Locale

/**
 * A named, ordered group of [Remembrance] entries that the user browses as a unit.
 *
 * The model is deliberately named after the thing it holds rather than simply `Collection`, which
 * would shadow `kotlin.collections.Collection` on import in every file that used both.
 *
 * A collection is an aggregate: [members] arrive loaded, because every screen that shows a
 * collection also shows what is in it, and paying a second round trip per card on the shelf would
 * make the hub a sequence of queries rather than one.
 *
 * Every collection is the user's. There is no field here naming a catalogue a shelf has to agree
 * with, and none deciding who may change it: a collection that shipped and one the user built are
 * the same shape, hold the same kinds of edits, and store membership the same way. [kind] survives
 * only as provenance for the badge and the shelf-scope chips.
 */
interface RemembranceCollection {
    /** A unique identifier, or `null` when the collection has never been persisted. */
    val id: Long?

    /**
     * The collection's display name.
     *
     * Always present so that search and sorting work in the domain layer, and always editable: a
     * shipped name is a starting suggestion, not a value the user is locked out of.
     *
     * This is the *canonical* name, not the one to draw. It is what the row is addressed by and what
     * the shipped catalogue is matched against, so it has to stay the language it was written in
     * rather than following the interface — otherwise a name would change underneath the edit dialog
     * that is changing it. To draw one, ask [displayName].
     */
    val name: String

    /** Language the canonical [name] was entered in; independent of its script and display direction. */
    val nameLocale: Locale

    /** The user's own prose about the collection, or `null` when they wrote none. */
    val description: String?

    /**
     * The name and description as written in each language they have been given in.
     *
     * Empty for anything the user wrote themselves, which is the ordinary state rather than a gap:
     * one person typing one collection has one language, and inventing a second one would mean
     * inventing text they never wrote. Non-empty for the collections that shipped, so that a library
     * read in Arabic reads as Arabic rather than as English with the interface translated around it.
     *
     * Keyed by the language the text is written in rather than the language it is for, which is the
     * same convention [Remembrance.translations] uses and for the same reason — a value carries its
     * own language, so nothing has to agree about it separately.
     */
    val translations: Map<Locale, LocalizedPair>

    /** Whether the collection shipped with the application or was built by the user. */
    val kind: CollectionKind

    /**
     * A glyph key the user chose for this collection, or `null` to derive one.
     *
     * Null is the normal state, not the absence of one: most collections should never need a picked
     * glyph, so deriving from the tags and the cover has to be what a fresh collection gets. Storing
     * an explicit override rather than the derived answer is what lets the two be told apart — the
     * derivation moves when the tags move, and a stored value could not say whether it was chosen
     * or merely stale.
     */
    val iconKey: String?

    /**
     * The file name of this collection's cover picture, inside the application's own covers
     * directory, or `null` for the generated wash.
     *
     * A stored name rather than a URL because the picture was copied into storage this application
     * owns when the user picked it. That is what makes a deleted image an event it can see: it can
     * fall back to the wash, sweep the orphan, and stop naming something that is gone.
     */
    val coverImagePath: String?

    /**
     * The labels the user has put on this collection, drawn from the same tag rows a
     * [Remembrance] carries so one vocabulary covers both.
     *
     * Never `null` and free of duplicates, because membership is stored under a composite key. A
     * tag is a label and not a membership rule: tagging a collection does not put anything in it,
     * and untagging does not empty it.
     */
    val tags: Set<Tag>

    /**
     * When the collection was pinned to the top of the shelf, or `null` when it is not pinned.
     *
     * A timestamp rather than a flag so the shelf can order pinned cards by recency without a second
     * sort key, the same way [lastOpenedAt] earns the recently-opened ordering.
     *
     * Deliberately separate from [homePinnedAt]. Pinning means "top of the shelf", which is where
     * the user finds it among everything else; being on Home means "one of the few I reach for
     * daily". One flag for both would force the hub's Pinned chip to disagree with what Home shows.
     */
    val pinnedAt: Instant?

    /** When the collection was created, or `null` when unknown. */
    val createdAt: Instant?

    /** When the collection last changed, or `null` when unknown. */
    val updatedAt: Instant?

    /** When the collection was last opened, or `null` when it never has been. */
    val lastOpenedAt: Instant?

    /**
     * When the collection was put on the Home screen, or `null` when it is not there.
     *
     * A timestamp rather than a flag so the Home rail can order its cards by recency without a
     * second sort key. See [pinnedAt] for why this is not the same claim.
     */
    val homePinnedAt: Instant?

    /**
     * Whether the user has marked this collection as one they come back to.
     *
     * Separate from every other per-device claim on this row — [pinnedAt], [homePinnedAt] and
     * [coverImagePath] — because each answers a different question. Pinning says where the shelf puts
     * it, Home says it is one of the few reached daily, the cover says how it looks, and this says
     * the user starred it and expects to find that star still there. Folding it into any of the
     * others would make one toggle quietly change another.
     *
     * Not a timestamp, unlike [pinnedAt], because nothing orders favourites yet; a favourite that
     * sorted by when it was starred would be a second, silent ordering to explain and no way to
     * change.
     */
    val isFavorite: Boolean

    /**
     * The members, in the order the user arranged them.
     *
     * Never `null` — an empty collection is an ordinary state, not an unknown one — and free of
     * duplicates, because membership is stored under a composite key.
     */
    val members: List<Remembrance>

    /** Whether the user pinned this collection to the top of the shelf. */
    val isPinned: Boolean
        get() = pinnedAt != null

    /** Whether the user put this collection on the Home screen. */
    val isOnHome: Boolean
        get() = homePinnedAt != null

    /**
     * The name to draw when the interface is in [locale].
     *
     * The language asked for exactly if it was given one, then any other region carrying that same
     * language, and finally [name]. Never another language than the one asked for: showing a French
     * name under an Arabic interface is not a worse answer than the English one, it is a wrong one.
     */
    fun displayName(locale: Locale): String = translations.displayName(locale, name)

    /**
     * The prose to draw when the interface is in [locale], or `null` when this language has none.
     *
     * `null` rather than the canonical prose when only the name was translated, because a translated
     * name above an untranslated sentence reads as one unedited mistake rather than as two languages
     * that were not finished together. An absent description is silent; a borrowed one is not.
     */
    fun displayDescription(locale: Locale): String? = translations.displayDescription(locale, description)
}
