package com.azkar.ui.library.data

import com.azkar.domain.model.Remembrance
import com.azkar.domain.model.RemembranceCollection
import com.azkar.domain.model.Tag
import com.azkar.generated.resources.Res
import com.azkar.generated.resources.libraryCategoryUncategorized
import com.azkar.generated.resources.libraryMissingText
import com.azkar.generated.resources.librarySourceUnknown
import com.azkar.i18n.RemembranceI18n
import com.azkar.ui.library.model.CollectionTile
import com.azkar.ui.library.model.RemembranceTile
import org.jetbrains.compose.resources.getString
import java.util.Locale

/**
 * Turns domain objects into the short, localized, display-ready strings the shelf and the grids
 * draw.
 *
 * Ported from `LibraryRemembrancePresenter`. The four static labels are resolved once by [load] and
 * the rest of the class stays synchronous, so a row can still be built from a plain lambda while a
 * grid is being composed — which matters, because a shelf of twenty collections is twenty row
 * builds per recomposition and none of them may suspend.
 *
 * The class holds no Compose state on purpose. It is created once per screen and re-read after a
 * language switch, and a `@Stable` composable-local would be recreated by the locale keying that
 * `AzkarApp` does anyway.
 */
class CollectionPresenter(
    private val uiLocale: Locale,
) {
    private var sourceUnknown: String = ""
    private var missingText: String = ""
    private var categoryUncategorized: String = ""

    /**
     * Resolves the labels that do not vary per row. Idempotent, so a language switch can call it
     * again on the same instance and get fresh strings — the guard is on emptiness, not on a flag,
     * precisely so a second call is allowed to overwrite.
     */
    suspend fun load() {
        sourceUnknown = getString(Res.string.librarySourceUnknown)
        missingText = getString(Res.string.libraryMissingText)
        categoryUncategorized = getString(Res.string.libraryCategoryUncategorized)
    }

    /**
     * Flattens a collection into its shelf tile.
     *
     * [inCustomCollections] is passed in rather than derived here: counting membership means
     * holding every collection at once, which the state layer already does, and doing it per tile
     * would make the shelf quadratic in the number of collections. [recitedCount] is passed in for
     * the same reason — it comes from one grouped read of the whole shelf.
     *
     * The name and description are resolved to the interface language here, once, rather than left
     * to whichever row happens to draw them. A tile is a snapshot that every consumer — the card, the
     * chip, the search result — already expects to hold finished strings, so resolving at the edge
     * keeps the resolution rule in one place instead of one per surface.
     */
    fun toTile(
        collection: RemembranceCollection,
        inCustomCollections: Int = 0,
        recitedCount: Int = 0,
        inReminders: Boolean = false,
    ): CollectionTile =
        CollectionTile(
            id = collection.id ?: MISSING_ID,
            name = collection.displayName(uiLocale),
            description = collection.displayDescription(uiLocale).orEmpty(),
            kind = collection.kind,
            iconKey = collection.iconKey ?: iconKeyForTags(collection.tags),
            coverImagePath = collection.coverImagePath,
            isPinned = collection.isPinned,
            memberCount = collection.members.size,
            updatedAt = collection.updatedAt,
            lastOpenedAt = collection.lastOpenedAt,
            isOnHome = collection.isOnHome,
            isFavorite = collection.isFavorite,
            inCustomCollections = inCustomCollections,
            recitedCount = recitedCount.coerceAtLeast(0),
            tagIds = collection.tags.mapNotNullTo(mutableSetOf()) { it.id },
            inReminders = inReminders,
        )

    /**
     * The tile the shelf draws: [toTile] plus the figures that live on the state layer.
     *
     * A separate method rather than a third argument threaded to every caller, because the counts
     * live in maps on the shelf's state and the callers — the cards, the chips, the search
     * results — are three views of one shelf that must not each go looking them up. A collection
     * absent from `recitedCounts` has recorded nothing, which is why the fallback is zero.
     */
    fun shelfTile(
        collection: RemembranceCollection,
        recitedCounts: Map<Long, Int>,
        inReminders: Boolean = false,
    ): CollectionTile =
        toTile(
            collection = collection,
            inCustomCollections = 0,
            recitedCount = recitedCounts[collection.id ?: MISSING_ID] ?: 0,
            inReminders = inReminders,
        )

    /** Flattens a remembrance into its grid tile. */
    fun toTile(
        remembrance: Remembrance,
        inCustomCollections: Int = 0,
        position: Int = 0,
    ): RemembranceTile =
        RemembranceTile(
            id = remembrance.id ?: MISSING_ID,
            category = primaryCategory(remembrance),
            primaryPreview = preview(localizedPrimaryText(remembrance), PREVIEW_MAX_LENGTH),
            secondaryPreview = preview(localizedSecondaryText(remembrance), PREVIEW_MAX_LENGTH),
            source = remembrance.source ?: sourceUnknown,
            isFavorite = remembrance.isFavorite,
            inCustomCollections = inCustomCollections,
            position = position.coerceAtLeast(0),
            categories = categories(remembrance),
            iconKey = iconKeyForCategory(rawCategory(remembrance)?.name ?: CATEGORY_RAW_UNCATEGORIZED),
        )

    /**
     * Every category this remembrance is filed under, localized and ordered by name.
     *
     * The grid's filter chips are built from this rather than from a hand-written list of the tags
     * that exist, because the honest answer to "which categories can I filter by" is "the ones the
     * members actually carry" — a chip for a category no member has would filter the grid to nothing
     * while looking exactly as useful as the rest.
     *
     * Ordered case-insensitively for the same reason [primaryCategory] is: a stable order is what
     * stops a chip row from reshuffling as the collection is edited, because the chips are derived
     * from a set and a set has no order of its own.
     */
    fun categories(remembrance: Remembrance): List<String> =
        remembrance.tags
            .map { it.displayName(uiLocale) }
            .distinct()
            .sortedWith(String.CASE_INSENSITIVE_ORDER)

    /**
     * The categories this remembrance is *about*, as opposed to [categories]' full tag list.
     *
     * The publisher is dropped here for the same reason it is dropped from [primaryCategory]: it is
     * on every row of the shipped catalogue, so a chip labelled with it selects exactly what "All"
     * selects. A filter chip is a claim that pressing it narrows something, and this one would not.
     *
     * Unlike [primaryCategory] this keeps every remaining tag rather than choosing one, because a
     * chip row wants the whole set a remembrance could be filed under, not the one it leads with.
     */
    fun subjectCategories(remembrance: Remembrance): List<String> =
        remembrance.tags
            .filterNot { it.name.isPublisherTag() }
            .map { it.displayName(uiLocale) }
            .distinct()
            .sortedWith(String.CASE_INSENSITIVE_ORDER)

    /**
     * The tag a remembrance leads with: the first by name, case-insensitively, that is not the
     * publisher.
     *
     * A remembrance carries several tags and the grid has room for one line of them, so the choice
     * is arbitrary by design — alphabetical is the one ordering that is the same every time, which
     * stops the eyebrow from changing as unrelated remembrances are added.
     *
     * The publisher tag is skipped, though, and that is not an ordering preference: it is on all 270
     * rows of the shipped catalogue, so alphabetically first is *always*. A "primary category" that
     * said "Muslim Data" on every card said nothing at all, and worse, it said the same thing as the
     * source badge printed right above it — two labels on one card, both of them the publisher. A
     * remembrance with no tag but the publisher still leads with the publisher, because that is the
     * only thing true of it.
     */
    fun primaryCategory(remembrance: Remembrance): String =
        rawCategory(remembrance)?.displayName(uiLocale) ?: categoryUncategorized

    /**
     * The category as the data stores it, before any label is applied.
     *
     * Kept alongside [primaryCategory] because there are two questions to ask of a remembrance's tags
     * and they cannot both be answered from the localized string: "what does this say on screen" needs
     * a label, and "which mark should stand for it" needs the raw name, since a table keyed on
     * translated text would have to be rebuilt for every locale the app ships.
     */
    private fun rawCategory(remembrance: Remembrance): Tag? {
        val byName = remembrance.tags.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        return byName.firstOrNull { !it.name.isPublisherTag() } ?: byName.firstOrNull()
    }

    /**
     * The collection mark that stands for [raw]'s category.
     *
     * Every tag in the shipped seed data is mapped, and anything new falls back to the book rather
     * than to nothing: a tile whose category has no mark still has to be drawn, and the book is the
     * one mark in this app that means "a remembrance" without also meaning "morning" or "travel".
     *
     * `lowercase` on a root locale because the keys are English and the tag names are compared against
     * English — folding with a Turkish locale would dot the i in "Islamic"-shaped keys and miss.
     */
    fun iconKeyForCategory(raw: String): String = iconKeyFor(raw)

    /**
     * The collection mark that stands for this collection's labels.
     *
     * The same table [iconKeyForCategory] uses, reached by the collection's first label rather than a
     * remembrance's category: a collection is *about* its labels, so which label leads is what decides
     * its mark, and this is what makes a collection's icon track its tag without anyone choosing it.
     *
     * Ordered so the answer does not move underneath the user. Tag rows carry no order of their own,
     * and a hash-ordered set would let an unrelated insert reshuffle which label leads and silently
     * change a card's icon; sorting by name makes the leader the same on every read. The order is not
     * "best" — nothing knows which of a user's labels is the most characteristic — only stable, which
     * is the property a card actually needs.
     *
     * Falls back to the mosque on no labels, which is also what a collection whose labels are all
     * unmapped gets. A collection with no labels is a perfectly ordinary one, and drawing it as nothing
     * would be the one outcome worse than drawing it generically.
     */
    fun iconKeyForTags(tags: Set<Tag>): String = iconKeyForTagNames(tags.map { it.name })

    fun localizedPrimaryText(remembrance: Remembrance): String =
        RemembranceI18n
            .resolveTexts(
                remembrance,
                uiLocale,
                textFallback(remembrance),
                missingText,
            ).translation

    fun localizedSecondaryText(remembrance: Remembrance): String =
        RemembranceI18n
            .resolveTexts(
                remembrance,
                uiLocale,
                textFallback(remembrance),
                missingText,
            ).explanation

    fun sourceLabel(remembrance: Remembrance): String = "${remembrance.source ?: sourceUnknown}"

    /**
     * The remembrance in Arabic, whatever the interface is written in.
     *
     * Pinned to the Arabic entry rather than resolved through [RemembranceI18n] on purpose, and the
     * reason is what this is for. The recitation card is the one place on the app that shows the dhikr
     * itself rather than a description of it, and the dhikr is Arabic whatever the reader's locale is —
     * a card that swapped the Arabic out for the English meaning because the window happens to be in
     * English would be showing the reader a translation of the thing they are about to recite, with
     * nothing marking it as the substitute it is.
     *
     * Empty rather than a fallback when there is no Arabic text, because the alternative is a card
     * whose headline is a publisher name. The card treats empty as "this one has no Arabic" and drops
     * the block rather than inventing something to fill it.
     *
     * Asked for by language and not by an exact locale, because the seed stores its Arabic under a
     * specific tag and matching on the language alone is what survives a reader whose Arabic locale
     * carries a region the data does not.
     */
    fun arabicText(remembrance: Remembrance): String =
        remembrance.translations[ARABIC_LOCALE]
            ?.translationPair
            ?.text
            ?.takeIf(::hasText)
            .orEmpty()

    /**
     * The English meaning of the remembrance, whatever the interface is written in.
     *
     * The mirror of [arabicText], and for the mirror's reason: the panel this fills is captioned as a
     * translation, and a translation panel showing the reader's own language is not a translation. In
     * an Arabic window this is the only English on the card, and in an English window it is the whole
     * point of having one beside the Arabic.
     */
    fun englishText(remembrance: Remembrance): String =
        remembrance.translations[ENGLISH_LOCALE]
            ?.translationPair
            ?.text
            ?.takeIf(::hasText)
            .orEmpty()

    private fun hasText(value: String?): Boolean = !value.isNullOrBlank()

    /**
     * Shortens [text] to [max] characters on a character count, with an ellipsis for the cut.
     *
     * The count is of `Char`s, not grapheme clusters, so a tile holding a Quranic passage with
     * diacritics can end mid-combining-sequence. That is a cosmetic edge at 96 characters and
     * measuring graphemes here would cost an allocation on every row of every grid.
     */
    fun preview(
        text: String,
        max: Int,
    ): String {
        val flattened = text.replace('\n', ' ').trim()
        return if (flattened.length <= max) flattened else flattened.take(max - 3) + "..."
    }

    private fun textFallback(remembrance: Remembrance): String = sourceLabel(remembrance)

    /**
     * Category tag name to collection mark.
     *
     * A table of English seed-data names rather than of localized labels, for the reason
     * [iconKeyForCategory] gives. Kept beside [categoryLabels] on purpose: the two tables cover the
     * same list of categories and a tag added to one and not the other would show up as a
     * properly-labeled category wearing the fallback mark, which is the kind of thing nobody notices
     * until two tiles in a row look identical.
     */
    companion object {
        /**
         * Stands in for a collection or remembrance that somehow has no identifier.
         *
         * Only reachable for an object that was never persisted. Rendering a tile under this id is
         * better than dropping it, because every action on it fails loudly and visibly instead of
         * silently doing nothing against id `0`.
         */
        const val MISSING_ID = -1L

        const val PREVIEW_MAX_LENGTH = 96

        /** What a custom collection with no icon of its own falls back to. */
        const val DEFAULT_ICON_KEY = "mosque"

        /**
         * The mark a category with no entry in the table above is drawn with.
         *
         * Public because [iconKeyFor] is, and a caller falling back without knowing the answer has
         * no business hard-coding a second value that could drift from this one.
         */
        const val CATEGORY_ICON_FALLBACK = "book"

        /**
         * Category tag name to collection mark.
         *
         * A table of English seed-data names rather than of localized labels, for the reason
         * [iconKeyFor] gives, and kept in step with the shipped wording in
         * `OfficialTagVocabulary` for the same reason it used to be kept beside [categoryLabels]: a
         * category added to one and not the other shows up as a properly-named category wearing the
         * fallback mark, which is the kind of thing nobody notices until two tiles in a row look
         * identical.
         */
        private val categoryIconKeys: Map<String, String> =
            mapOf(
                // The open book rather than the closed one the `library` key names: at the size a
                // tile's header draws it — 46dp on a wash of colour — the closed book's outline is a
                // hollow rounded rectangle with nothing in it, and a card whose only picture reads as
                // a broken image says so about every card that shares its category.
                "muslim data" to "book",
                "prayer" to "khatim",
                "morning" to "sunrise",
                "morning & evening" to "sunset",
                "evening" to "moon",
                "praising allah" to "star",
                "joy & distress" to "heart",
                "good etiquette" to "book",
                "sickness & death" to "nature",
                "home & family" to "home",
                "nature" to "nature",
                "travel" to "compass",
                "food & drink" to "sparkle",
                "hajj & umrah" to "mosque",
            )

        /**
         * The mark that stands for [raw]'s category, without needing a presenter.
         *
         * Static on purpose and not only because the table has no instance state: the collection
         * editor has to draw a preview of a collection that does not exist yet, so there is no
         * presenter holding its labels, only a list of names typed into a field.
         *
         * Every tag in the shipped seed data is mapped, and anything new falls back to the book
         * rather than to nothing: a tile whose category has no mark still has to be drawn, and the
         * book is the one mark in this app that means "a remembrance" without also meaning "morning"
         * or "travel".
         *
         * `lowercase` on a root locale because the keys are English and the tag names are compared
         * against English — folding with a Turkish locale would dot the i in "Islamic"-shaped keys and
         * miss.
         */
        fun iconKeyFor(raw: String): String =
            categoryIconKeys[raw.trim().lowercase(Locale.ROOT)] ?: CATEGORY_ICON_FALLBACK

        /**
         * The mark that stands for these label names, without needing a presenter.
         *
         * The same ordering and fallback [iconKeyForTags] applies, reachable from raw names so that a
         * caller which has only typed something — the editor's preview — gets the same answer as the
         * shelf will later. Ordering is for stability and not quality: nothing knows which of a
         * user's labels is most characteristic, only that the leader must not move underneath them.
         */
        fun iconKeyForTagNames(tagNames: Collection<String>): String =
            tagNames
                .sortedWith(String.CASE_INSENSITIVE_ORDER)
                .firstOrNull()
                ?.let { iconKeyFor(it) }
                ?: DEFAULT_ICON_KEY

        /** The raw tag name a remembrance with no tags at all is filed under, before it is labeled. */
        const val CATEGORY_RAW_UNCATEGORIZED = "uncategorized"

        /**
         * The two locales the seed data is written in.
         *
         * Bare language tags rather than full locales, and that is not a simplification: the loader
         * stores exactly `"ar"` and `"en"` and the reader turns each back into exactly these keys, so
         * the lookup below is an equality test rather than a language test. Asking by the reader's own
         * locale would then be the thing that breaks, which is why nothing here reads [uiLocale].
         *
         * Not the app's current locale, and that is the point: [arabicText] and [englishText] ask for
         * one language each rather than for whatever the reader happens to be reading in, because the
         * recitation card needs both at once and the interface's language is not one of them.
         */
        val ARABIC_LOCALE: Locale = Locale.forLanguageTag("ar")

        val ENGLISH_LOCALE: Locale = Locale.forLanguageTag("en")
    }
}

/**
 * The one tag in the seed data that names who compiled the row rather than what it is about.
 *
 * It is a tag like any other and it is on every shipped row, which is exactly why it is named here as
 * a file constant rather than left as one more entry in the label table: [primaryCategory] has to
 * recognise it to step over it, and a localized label is not something it can recognise.
 */
private const val PUBLISHER_TAG = "muslim data"

/**
 * Whether this tag names the compiler of the row rather than its subject.
 *
 * Compared against the folded form because a stored tag name always is one — see
 * [com.azkar.domain.model.Tag.fold] — while the constant is compared literally, so there is one
 * spelling to keep in step here rather than two.
 */
private fun String.isPublisherTag(): Boolean = equals(PUBLISHER_TAG, ignoreCase = true)

/** `LibraryRemembranceLookup.findById`. */
fun List<Remembrance>.findById(id: Long): Remembrance? = firstOrNull { it.id == id }

/** `List<RemembranceCollection>.findById`. */
fun List<RemembranceCollection>.findById(id: Long): RemembranceCollection? = firstOrNull { it.id == id }
