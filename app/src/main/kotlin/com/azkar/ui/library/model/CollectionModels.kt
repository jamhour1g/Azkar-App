package com.azkar.ui.library.model

import com.azkar.domain.model.CollectionKind
import com.azkar.domain.model.Remembrance
import com.azkar.domain.model.RemembranceCollection
import java.time.Instant

/**
 * One load of everything the library needs: the shelf, the remembrances behind it, and how far
 * through each collection the user has got.
 *
 * Both halves are read together on purpose. A collection's member list is resolved in the same
 * database round trip that returns the collections, so splitting them would only buy a second
 * query. The remembrances also have to be held because the hub's search reaches across the whole
 * catalogue, and the detail screen's add-picker has to offer what is *not* already in the
 * collection.
 *
 * [recitedCounts] is keyed by collection and holds only the collections that have something counted,
 * for the same reason [RecitationSummary] leaves the rest out: a collection absent from the map has
 * recorded nothing, which is a different answer from one counted zero times. It travels with the
 * shelf rather than being read per tile because every card draws the figure, and one grouped read for
 * the whole shelf beats one query per card.
 *
 * [favoritesCollectionId] names the one collection that mirrors the user's stars. It travels here
 * rather than being looked up by name because the user can rename that collection like any other, and
 * a mirror identified by its name would quietly stop mirroring the day they did. `null` is a real
 * answer — the settings file can outlive the database it names — and a caller that gets it must treat
 * the mirror as absent rather than falling back to a lookup.
 */
data class CollectionsSnapshot(
    val collections: List<RemembranceCollection>,
    val remembrances: List<Remembrance>,
    val recitedCounts: Map<Long, Int>,
    val refreshedAt: Instant,
    val favoritesCollectionId: Long? = null,
)

/**
 * Which shelf the hub is showing.
 *
 * A filter rather than a navigation state, because switching between them is a click on a chip in
 * the same place every time — the alternative would be four shelves the user has to go "back from"
 * to compare.
 */
enum class CollectionScope {
    /** Everything on the shelf. */
    ALL,

    /** Only the collections that ship with the app. */
    OFFICIAL,

    /** Only the collections the user made. */
    CUSTOM,

    /** Only the collections pinned to the top of the shelf. */
    PINNED,

    /**
     * Only the collections the user starred.
     *
     * The counterpart to [PINNED] and deliberately separate from it: a pin says "put this where I can
     * reach it", which is about position, and a star says "this is the one I want", which is about
     * wanting. A collection can be neither, either or both, and folding them into one scope would
     * answer "what have I singled out?" with whichever of the two happened to be asked about.
     */
    FAVORITE,

    /**
     * Only the collections the user has started and not finished.
     *
     * The one scope that answers from something other than the collection's own row: progress lives
     * in the recitation table, not here, so the shelf has to be handed the counts alongside it.
     * Strictly between the two ends — nothing counted has not been started, and everything counted
     * has nothing left to come back to, so neither answers "in progress".
     */
    IN_PROGRESS,
    ;

    /** Whether this scope needs the shelf to be narrowed at all, rather than shown whole. */
    val isNarrowing: Boolean get() = this != ALL

    fun accepts(kind: CollectionKind): Boolean =
        when (this) {
            ALL -> true
            OFFICIAL -> kind == CollectionKind.OFFICIAL
            CUSTOM -> kind == CollectionKind.CUSTOM
            // Pinning, starring and progress are all properties a collection carries whatever kind it
            // is, so there is nothing for a kind predicate to say about any of them. The filter
            // answers them.
            PINNED, FAVORITE, IN_PROGRESS -> true
        }
}

/** How the hub orders the shelf. */
enum class CollectionSort {
    /** Newest activity first: the last edit, or the last time the collection was opened. */
    RECENTLY_UPDATED,

    /** Alphabetical, the way a filing cabinet is ordered. */
    NAME,

    /** The full shelves first, which is the order a library user actually browses in. */
    MOST_ITEMS,

    /**
     * Furthest along first, by the share of a collection's members that have been recited.
     *
     * A fraction rather than a count of passages, so a three-passage collection read twice outranks a
     * fifty-two-passage collection read once — which is the honest answer to "how far through are
     * you", and also the one that puts what is nearly finished at the top.
     */
    PROGRESS,
}

/**
 * A collection, flattened into the strings the shelf tile draws.
 *
 * The tile renders from this rather than from [RemembranceCollection] so that building a shelf of
 * tiles cannot accidentally reach for a resource, and so the same tile can be drawn from a fake in
 * a test with no resource environment at all.
 */
data class CollectionTile(
    val id: Long,
    val name: String,
    val description: String,
    val kind: CollectionKind,
    /**
     * The glyph to draw, never blank.
     *
     * Non-null and never empty because a card with nothing in the middle reads as broken: a collection
     * that names no icon of its own gets the same derived one every time rather than nothing.
     */
    val iconKey: String,
    /**
     * The picture to draw instead of the generated wash, or `null` for the wash.
     *
     * The *name* of a file in the app's own covers directory — not a path, not a URL. Both of those
     * would tie a library that is otherwise portable to a location that moves: a home directory that
     * changes, a machine that is not this one.
     */
    val coverImagePath: String?,
    val isPinned: Boolean,
    val memberCount: Int,
    val updatedAt: Instant?,
    val lastOpenedAt: Instant?,
    /**
     * Whether the user put this collection on the Home screen.
     *
     * Carried here so the detail page's menu can label the action that is on offer — "Show on
     * Home" or "Hide from Home" — without reaching back for the domain object.
     */
    val isOnHome: Boolean,
    /**
     * Whether the user starred this collection.
     *
     * Carried for the same reason [isPinned] and [isOnHome] are: three per-device claims about one
     * collection, and a card or a menu that cannot see them has to either guess or reach back for the
     * domain object to find out which one is set.
     */
    val isFavorite: Boolean = false,
    /** How many of the user's own collections this one belongs to — the "in 2 collections" badge. */
    val inCustomCollections: Int,
    /**
     * How many of this collection's members have been recited at least once.
     *
     * Counted rather than asked for: a member whose count has been undone to zero is not recited,
     * so this is a count of members and not a sum of repetitions.
     */
    val recitedCount: Int = 0,
    /**
     * The ids of this collection's labels, for the shelf's tag filter.
     *
     * Ids rather than names, because the filter compares against tag ids: a tile carrying names would
     * have to fold each one back to an id on every comparison to answer a question the read already
     * settled.
     */
    val tagIds: Set<Long> = emptySet(),
) {
    /**
     * How far through the collection this is, from nothing to all of it.
     *
     * A proportion rather than a count because every surface that draws progress wants a length, and
     * a card's track wants a fraction rather than a number of pixels it would have to guess at. Zero
     * for a collection with no members, so the caller never divides by zero and an empty collection
     * never reads as complete.
     */
    val completion: Float
        get() = if (memberCount <= 0) 0f else (recitedCount.toFloat() / memberCount).coerceIn(0f, 1f)
}

/** A remembrance, flattened into the strings a grid tile or the read dialog draws. */
data class RemembranceTile(
    val id: Long,
    val category: String,
    val primaryPreview: String,
    val secondaryPreview: String,
    val source: String,
    val isFavorite: Boolean,
    /** How many of the user's own collections hold this remembrance, for the same badge. */
    val inCustomCollections: Int,
    /**
     * Where this remembrance sits in the collection that is being shown, counting from one.
     *
     * Carried on the tile rather than asked for by the card because the reference line reads
     * "source • 2 of 29" and one of those two numbers is a fact about the *collection*, not about
     * the remembrance — the same tile is drawn from the shelf, from a search result and from the
     * detail page, and only the last of those knows the position. Defaulting to one keeps the other
     * two callers honest: they simply never print it, because they have nothing true to print.
     *
     * Zero means "no position", which is what a caller that cannot answer passes rather than a lie.
     */
    val position: Int = 0,
    /**
     * Every category this remembrance is filed under, not just the one shown on its face.
     *
     * The grid filters by category, and a filter menu can only offer what it knows exists — so the
     * full set travels with the tile while [category] stays the single label the card prints. Empty
     * rather than defaulted to a list of [category], because the callers outside a collection have
     * no tag information to summarise and a one-entry list would imply a filter that filters nothing.
     */
    val categories: List<String> = emptyList(),
    /**
     * Which of the collection marks stands for this tile's category.
     *
     * Resolved by the presenter rather than by the card, because the card cannot: it is handed a
     * *localized* category name, and mapping a translated label back onto a mark would mean a table
     * of every language in the app inside a composable. The presenter is the one place that still
     * knows the raw tag name, so it makes the decision here and the card just draws it.
     *
     * Always one of the keys the mosaic already owns, so `iconForCollection` cannot fall through to
     * its generic fallback and give a whole page the same mark.
     */
    val iconKey: String = "book",
) {
    /** Whether this tile knows where it sits in a collection, and so can print an ordinal. */
    val hasPosition: Boolean get() = position > 0
}

/**
 * The order a collection's members are listed in.
 *
 * A surface the user chooses rather than a property of the data, because the order the members were
 * *added* in is only one of three reasonable answers and it is not always the one being looked for:
 * a returning user wants their place back, someone reading for a category wants that category
 * grouped, and someone who knows what they want wants to type it and see it first.
 */
enum class CollectionMemberSort {
    /** The order the collection was assembled in — the sequence a recitation follows. */
    SEQUENCE,

    /** Alphabetically by the passage's text. */
    TITLE,

    /** Grouped by category, then by passage within the category. */
    CATEGORY,
}

/**
 * Whether the members are laid out as tiles or as rows.
 *
 * Both are asked for because the grid's five columns is the right answer for browsing a collection
 * and the wrong one for comparing two passages side by side, and the choice is per-visit rather than
 * per-collection: it is a view of the members rather than a claim about them.
 */
enum class CollectionMemberView {
    GRID,
    LIST,
}
