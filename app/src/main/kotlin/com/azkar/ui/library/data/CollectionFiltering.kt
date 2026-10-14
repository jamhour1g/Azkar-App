package com.azkar.ui.library.data

import com.azkar.domain.model.CollectionKind
import com.azkar.domain.model.Remembrance
import com.azkar.domain.model.RemembranceCollection
import com.azkar.ui.library.model.CollectionScope
import com.azkar.ui.library.model.CollectionSort
import java.util.Locale

/**
 * How many of the user's own collections hold each thing.
 *
 * The "in 2 collections" badge on a tile needs a count per remembrance, and the shelf needs the same
 * count per collection. Both come from one pass over the membership the snapshot already carries,
 * which is why this is a value the state layer computes once and hands to the tile builders rather
 * than something each tile recomputes.
 *
 * Only custom collections are counted. An official collection's membership is derived from a tag,
 * so every remembrance carrying that tag is in it — counting those would put a badge of 14 on
 * almost everything and say nothing the user did.
 */
data class MembershipCounts(
    /** Custom-collection id to how many custom collections it belongs to. Always 1 or 0. */
    val customCollectionIds: Set<Long>,
    /** Remembrance id to how many custom collections hold it. */
    val byRemembrance: Map<Long, Int>,
) {
    fun collectionsIn(remembranceId: Long): Int = byRemembrance[remembranceId] ?: 0

    companion object {
        val EMPTY = MembershipCounts(emptySet(), emptyMap())
    }
}

/** Counts custom-collection membership across a whole shelf in one pass. */
fun membershipCountsOf(
    collections: List<RemembranceCollection>,
    excludedCollectionId: Long? = null,
): MembershipCounts {
    val custom = collections.filter { it.kind == CollectionKind.CUSTOM && it.id != excludedCollectionId }
    val ids = custom.mapNotNull { it.id }.toSet()
    val counts = HashMap<Long, Int>()
    for (collection in custom) {
        for (member in collection.members) {
            val id = member.id ?: continue
            counts[id] = (counts[id] ?: 0) + 1
        }
    }
    return MembershipCounts(ids, counts)
}

/**
 * One group of cross-collection search results.
 *
 * The hub's search box reaches across every collection, which means a hit has to say *where* it was
 * found — otherwise the same 300-word remembrance appearing under Morning, Evening and After Prayer
 * looks like three different results. Grouping is the fix, and the group label is the collection
 * name rather than a tag, because the collection is what the user can go and look at.
 */
data class SearchGroup(
    val collectionId: Long,
    val collectionName: String,
    val results: List<Remembrance>,
)

/**
 * Pure filtering and ordering rules for the shelf and the search box.
 *
 * No Compose types, no resources, no I/O. Everything here is a function of its arguments, which is
 * what makes the behaviour that actually matters — which collection outranks which, and what a
 * query matches — testable without a screen.
 */
object CollectionFiltering {
    /**
     * Narrows and orders the shelf.
     *
     * Pinned collections are lifted above the rest whatever sort is chosen, and each of the two
     * halves is then ordered by that sort. The alternative — letting the sort speak for the whole
     * shelf — would mean a pinned collection drifts back down as soon as the user sorts by name,
     * which defeats the point of pinning it.
     *
     * The pinned and in-progress scopes answer from something other than a collection's kind, which
     * is the narrowing a kind predicate cannot express: pinning is a column on the collection, and
     * progress lives in the recitation table.
     */
    fun shelf(
        collections: List<RemembranceCollection>,
        scope: CollectionScope,
        query: String,
        sort: CollectionSort,
        categoryId: Long? = null,
        recitedCounts: Map<Long, Int> = emptyMap(),
        tagIds: Set<Long> = emptySet(),
        uiLocale: Locale = Locale.getDefault(),
    ): List<RemembranceCollection> {
        val normalized = query.trim().lowercase(Locale.ROOT)
        val inScope =
            when (scope) {
                // Pinning is answered from the collection's own timestamp rather than from its kind,
                // so the filter has to read it here — `accepts` deliberately cannot, because a
                // pinned collection is pinned whatever it is.
                CollectionScope.PINNED -> collections.filter { it.isPinned }
                // Starring is the same kind of answer as pinning — a column on the collection rather
                // than anything about its kind — and it is read here for the same reason.
                CollectionScope.FAVORITE -> collections.filter { it.isFavorite }
                CollectionScope.IN_PROGRESS -> collections.filter { it.isPartlyRecited(recitedCounts) }
                else -> collections.filter { scope.accepts(it.kind) }
            }
        // The category narrows to a single collection, so it is applied before the query rather
        // than after: a query that does not match the chosen category should empty the grid
        // outright, which is what filtering one out of the other order would also do but with a
        // scan of every collection to get there.
        val categorised =
            if (categoryId == null) {
                inScope
            } else {
                inScope.filter { it.id == categoryId }
            }
        // A label is a label, not a second identity, so this is an intersection rather than an
        // equality: a collection carrying any of the chosen labels is a collection about that, and
        // requiring all of them would answer a question nobody asked. Applied before the query for
        // the same reason the category is — a mismatched query should empty the grid outright.
        val labelled =
            if (tagIds.isEmpty()) {
                categorised
            } else {
                categorised.filter { collection -> collection.tags.any { it.id in tagIds } }
            }
        val narrowed = labelled.filter { matchesQuery(it, normalized) }
        if (scope == CollectionScope.PINNED) {
            // Everything left is pinned, so the "pinned first" half of the comparator has nothing to
            // do and the chosen sort alone decides the order.
            return narrowed.sortedWith(comparatorFor(sort, recitedCounts = recitedCounts, uiLocale = uiLocale))
        }
        // Starred collections deliberately do not get that shortcut. One can be starred and unpinned,
        // and the shelf's standing promise is that pinned collections come first whatever the scope
        // says — so a starred shelf still sorts its pinned rows ahead of the rest, and only the
        // "which of the two halves of a tie does the sort decide" half collapses into one pass.
        // The two halves are sorted separately and then concatenated. Sorting the concatenation
        // would be a single pass over a mixed list, and any comparator that does not also order by
        // `isPinned` would interleave the two halves again.
        val comparator = comparatorFor(sort, pinnedFirst = false, recitedCounts = recitedCounts, uiLocale = uiLocale)
        return narrowed.filter { it.isPinned }.sortedWith(comparator) +
            narrowed.filterNot { it.isPinned }.sortedWith(comparator)
    }

    /**
     * Whether the user has started this collection and not finished it.
     *
     * An absent entry in [recitedCounts] means nothing has been counted, which is the same answer as
     * a zero and is treated as it — the map leaves untouched collections out to keep it small, not
     * to distinguish them.
     *
     * The count is of *members*, never of repetitions, so a single passage recited forty times does
     * not make a fifty-two-passage collection look nearly finished.
     */
    private fun RemembranceCollection.isPartlyRecited(recitedCounts: Map<Long, Int>): Boolean {
        val recited = id?.let { recitedCounts[it] } ?: 0
        return recited > 0 && recited < members.size
    }

    /**
     * Matches a collection by name or description, in every language it is written in.
     *
     * Matching the description too is deliberate: a user who cannot remember whether they wrote
     * "for the car" in a name or a description should not have to know which.
     *
     * Searching every locale rather than the UI's is the same rule [matchesRemembrance] follows and
     * for the same reason: a collection whose Arabic name is on screen has an English name too, and
     * a reader who knows it by either should be able to type it. The stored name is always searched
     * as well, so a collection with no translations behaves exactly as it did before.
     */
    fun matchesQuery(
        collection: RemembranceCollection,
        normalizedQuery: String,
    ): Boolean {
        if (normalizedQuery.isEmpty()) return true
        return searchableCollectionText(collection).contains(normalizedQuery)
    }

    /**
     * Everything a collection can be found by, folded to one string.
     *
     * The stored name and description come first and every translation follows, so a collection with
     * no translations produces exactly the two strings it always did. Joins are single spaces so a
     * query cannot match by straddling a boundary between two fields.
     */
    private fun searchableCollectionText(collection: RemembranceCollection): String {
        val parts = ArrayList<String>(2 + collection.translations.size * 2)
        parts += collection.name
        collection.description?.takeIf { it.isNotBlank() }?.let(parts::add)
        for (pair in collection.translations.values) {
            parts += pair.name
            pair.description?.takeIf { it.isNotBlank() }?.let(parts::add)
        }
        return parts.joinToString(" ").lowercase(Locale.ROOT)
    }

    /**
     * Orders a shelf by the chosen sort.
     *
     * Every comparator falls back to the collection name so that two collections with the same
     * count, or edited in the same millisecond, do not swap places between two loads of the same
     * data.
     *
     * That fallback name is the one the reader sees. Sorting by the stored name would put an Arabic
     * shelf in an order that reads as arbitrary to somebody reading Arabic, and the name is the
     * tiebreaker for every sort rather than only [CollectionSort.NAME] — so an order decided by the
     * wrong one is an order decided by the wrong one whichever sort was chosen. [uiLocale] defaults
     * to the JVM default, which `LocaleStore.activateLocale` has already pointed at the active
     * language by the time this runs.
     *
     * [recitedCounts] is only read by [CollectionSort.PROGRESS], and defaults to empty so that the
     * three orders that have no use for it do not have to be handed a figure they ignore. An empty
     * map is not a mistake to guard against: it means nothing has been counted, and a shelf where
     * nothing has been counted is genuinely un-ordered by progress — every collection ties at zero
     * and the name decides, which is the honest answer.
     */
    fun comparatorFor(
        sort: CollectionSort,
        pinnedFirst: Boolean = true,
        recitedCounts: Map<Long, Int> = emptyMap(),
        uiLocale: Locale = Locale.getDefault(),
    ): Comparator<RemembranceCollection> {
        val byName =
            compareBy<RemembranceCollection, String>(String.CASE_INSENSITIVE_ORDER) {
                it.displayName(uiLocale)
            }.thenBy { it.id ?: Long.MIN_VALUE }
        return when (sort) {
            CollectionSort.NAME -> byName
            CollectionSort.MOST_ITEMS -> compareByDescending<RemembranceCollection> { it.members.size }.then(byName)
            CollectionSort.RECENTLY_UPDATED ->
                compareByDescending<RemembranceCollection> { recencyOf(it) }
                    .then(byName)
            // Cross-multiplied rather than divided: comparing two fractions needs both numerators and
            // both denominators, and dividing here would divide by zero for an empty collection. The
            // order it produces is the order the progress track on each card already draws, which is
            // the point — a shelf sorted by progress must not disagree with the progress it shows.
            CollectionSort.PROGRESS ->
                Comparator<RemembranceCollection> { first, second ->
                    val firstShare =
                        completed(first, recitedCounts).toLong() * second.members.size
                    val secondShare =
                        completed(second, recitedCounts).toLong() * first.members.size
                    secondShare.compareTo(firstShare)
                }.then(byName)
        }.let { base ->
            if (pinnedFirst) {
                compareByDescending<RemembranceCollection> { it.isPinned }.then(base)
            } else {
                base
            }
        }
    }

    /**
     * How many of a collection's members have been recited, clamped to the member count.
     *
     * Clamped because the share it feeds would otherwise exceed one for a collection whose members
     * changed after the count was taken — a passage deleted from a collection the user had finished
     * would otherwise report more progress than it has members.
     */
    private fun completed(
        collection: RemembranceCollection,
        recitedCounts: Map<Long, Int>,
    ): Int = (collection.id?.let { recitedCounts[it] } ?: 0).coerceIn(0, collection.members.size)

    /**
     * When a collection was last touched, for the recently-updated order.
     *
     * Opening a collection counts as touching it, and falls back to the last edit for a collection
     * that has never been opened. All three inputs are nullable, so the floor is [Long.MIN_VALUE] —
     * a never-touched collection has to sort last rather than first, which is the only thing the
     * sentinel has to guarantee. `lastOpenedAt` beats `updatedAt` because reading a shelf is a
     * stronger signal of what the user cares about than the day its seed row happened to be written.
     */
    fun recencyOf(collection: RemembranceCollection): Long =
        collection.lastOpenedAt?.toEpochMilli()
            ?: collection.updatedAt?.toEpochMilli()
            ?: Long.MIN_VALUE

    /**
     * Groups every cross-collection search hit under the collection it was found in.
     *
     * A remembrance in five collections is five hits, one in each — which is the honest answer, since
     * the result opens that collection's page. Duplicates within a single collection are impossible
     * because membership is a primary key.
     *
     * Collections are visited in the order given so the hub's own ordering survives into the results
     * instead of the search quietly re-sorting the shelf.
     */
    fun groupSearchResults(
        collections: List<RemembranceCollection>,
        remembrances: List<Remembrance>,
        query: String,
        uiLocale: Locale = Locale.getDefault(),
    ): List<SearchGroup> {
        val normalized = query.trim().lowercase(Locale.ROOT)
        if (normalized.isEmpty()) return emptyList()
        val byId = remembrances.associateBy { it.id }
        val groups =
            collections.mapNotNull { collection ->
                val id = collection.id ?: return@mapNotNull null
                val hits = collection.members.mapNotNull { byId[it.id] }.filter { matchesRemembrance(it, normalized) }
                // The heading names the collection the reader will land in, so it is the name that
                // collection will be known by when they get there.
                if (hits.isEmpty()) null else SearchGroup(id, collection.displayName(uiLocale), hits)
            }
        return groups
    }

    /**
     * Matches a remembrance by its source or by any of its translations.
     *
     * Every locale is searched, not just the UI's: a user typing "forgiveness" in an Arabic window
     * still expects to find the remembrance they saw labelled in English last week, and the
     * translation text is the only thing the tile would have shown them. A remembrance's labels are
     * searched the same way, and for the same reason — the tag it is filed under is on screen in the
     * reader's language even though it is filed under the one that was seeded.
     */
    fun matchesRemembrance(
        remembrance: Remembrance,
        normalizedQuery: String,
    ): Boolean =
        normalizedQuery.isEmpty() ||
            remembrance.source?.lowercase(Locale.ROOT)?.contains(normalizedQuery) == true ||
            remembrance.tags.any { tag ->
                tag.translations.values.any { it.lowercase(Locale.ROOT).contains(normalizedQuery) } ||
                    tag.name.lowercase(Locale.ROOT).contains(normalizedQuery)
            } ||
            remembrance.translations.values.any { translations ->
                translations.translationPair.text
                    .lowercase(Locale.ROOT)
                    .contains(normalizedQuery) ||
                    translations.explanationPair.text
                        .lowercase(Locale.ROOT)
                        .contains(normalizedQuery)
            }

    /**
     * The untagged remembrances with no membership outside the Uncategorized holding shelf.
     *
     * Favorites is deliberately an ordinary outside membership here: a starred remembrance stays in
     * Favorites rather than being filed in Uncategorized, even if its mirror is temporarily missing.
     * A remembrance in any other collection is already categorized by where it lives even if it has
     * no tags.
     */
    fun uncategorizedRemembranceIds(
        remembrances: List<Remembrance>,
        collections: List<RemembranceCollection>,
        uncategorizedCollectionId: Long,
    ): Set<Long> {
        val otherMemberships =
            collections
                .asSequence()
                .filter { it.id != uncategorizedCollectionId }
                .flatMap { it.members.asSequence() }
                .mapNotNull { it.id }
                .toSet()
        return remembrances
            .asSequence()
            .filter { it.tags.isEmpty() && !it.isFavorite }
            .mapNotNull { it.id }
            .filterNot { it in otherMemberships }
            .toSet()
    }

    /**
     * The remembrances that may be added to [collection] — the catalogue minus its current members.
     *
     * Answering it for a shipped collection too is deliberate even though nothing is added to one:
     * a shipped collection's membership is derived from a tag rather than from rows, so an add
     * would be rejected by the service on save. Returning a list the screen never shows would be
     * harmless, but returning it only for the collections the picker is reachable on is what makes
     * the question answerable — the caller checks [CollectionKind.isEditable] and does not show the
     * affordance at all.
     */
    fun addableRemembrances(
        collection: RemembranceCollection,
        catalogue: List<Remembrance>,
    ): List<Remembrance> {
        val memberIds = collection.members.mapNotNull { it.id }.toSet()
        return catalogue.filterNot { it.id in memberIds }
    }
}
