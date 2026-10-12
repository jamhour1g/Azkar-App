package com.azkar.ui.library.data

import java.time.Instant

/**
 * One collection as the Home screen needs it: enough to draw a card, and nothing more.
 *
 * Deliberately not a domain `RemembranceCollection`. That type carries every member, and a rail of
 * a few cards does not draw members — it draws a name, a count and a position. Reading all of them
 * to answer those three questions would mean loading most of the database for the first screen the
 * app paints, so this is a read model over just the columns behind them.
 */
data class HomeCollection(
    val id: Long,
    val name: String,
    val description: String?,
    val iconKey: String,
    val memberCount: Int,
    val recitedCount: Int,
    /** When the user put it on Home, or `null` when it was suggested rather than asked for. */
    val pinnedAt: Instant?,
    /** When a member was last counted, or `null` when none has been. */
    val lastRecitedAt: Instant?,
) {
    /** How far through the collection the user has got, between zero and one. */
    val completion: Float
        get() = if (memberCount <= 0) 0f else (recitedCount.toFloat() / memberCount).coerceIn(0f, 1f)

    /** Whether the user put this one on the rail themselves. */
    val isPinned: Boolean
        get() = pinnedAt != null

    /**
     * Whether the collection is worth offering to pick back up.
     *
     * A finished collection is not, and neither is an empty one: starting over is a decision the
     * user should make from the collection's own page rather than be handed by a suggestion.
     */
    val isResumable: Boolean
        get() = memberCount > 0 && recitedCount > 0 && recitedCount < memberCount
}

/**
 * Choosing which collections the Home rail offers, and in what order.
 *
 * Pure on purpose: the rule governing what appears on the first screen the user sees is worth
 * testing without a database, and its only inputs are rows that have already been read.
 */
object HomeCollections {
    /**
     * Picks up to [limit] cards, best first.
     *
     * Pinned cards come first, most recently added first, because pinning is a deliberate
     * instruction. Resumable ones follow, most recently recited first, because being part-way
     * through is an observation — the rail should be able to offer something the user merely
     * forgot about, but only after the ones they asked for have had their turn.
     *
     * Anything finished or never started is left out, as is anything with no members: there is
     * nothing to resume in either case, and a card with an empty count is noise on a rail short of
     * space.
     */
    fun pick(
        candidates: List<HomeCollection>,
        limit: Int,
    ): List<HomeCollection> {
        if (limit <= 0) return emptyList()
        val worthShowing = candidates.filter { it.memberCount > 0 }
        val pinned = worthShowing.filter { it.isPinned }.sortedWith(PINNED_FIRST)
        val resumable = worthShowing.filterNot { it.isPinned }.filter { it.isResumable }.sortedWith(RESUMED_FIRST)
        return (pinned + resumable).take(limit)
    }

    private val PINNED_FIRST =
        compareByDescending<HomeCollection> { it.pinnedAt }
            // A pinned card with no stamp cannot happen, but a stable order for the rest of the
            // list keeps the rail from reshuffling when two rows share a timestamp.
            .thenBy { it.name }

    private val RESUMED_FIRST =
        compareByDescending<HomeCollection> { it.lastRecitedAt }
            .thenBy { it.name }
}
