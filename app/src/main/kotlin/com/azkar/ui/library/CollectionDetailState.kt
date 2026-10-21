package com.azkar.ui.library

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.azkar.domain.model.RecitationProgress
import com.azkar.domain.model.Remembrance
import com.azkar.ui.library.data.CollectionFiltering
import com.azkar.ui.library.data.CollectionStats
import com.azkar.ui.library.data.findById
import com.azkar.ui.library.data.nextMember
import com.azkar.ui.library.data.statsOf
import com.azkar.ui.library.data.upcomingMembers
import com.azkar.ui.library.model.CollectionMemberSort
import com.azkar.ui.library.model.CollectionMemberView
import com.azkar.ui.library.model.RemembranceTile
import java.time.Duration
import java.util.Locale

/**
 * The collection detail screen's view state: which picker is open, what is ticked in it, and what is
 * about to be confirmed.
 *
 * Deliberately *not* a second copy of the collection. Reads go through [CollectionsCatalogue] so the
 * shelf and the detail page can never disagree about what a collection holds — the alternative is
 * two loaders that drift apart the moment a membership is changed on one side, and the user sees a
 * tile whose count disagrees with the page behind it.
 *
 * Mutations are forwarded to [CollectionsCatalogue] for the same reason, and because the notice the
 * screen raises has to come from one place.
 *
 * Whether a collection feeds the reminder queue is read through the app-wide
 * [CollectionReminderState] rather than kept here, for the same reason: membership is a fact about
 * the collection rather than a thing this screen owns, and the runtime that draws from it lives above
 * the screens.
 */
@Stable
class CollectionDetailState(
    private val collections: CollectionsCatalogue,
    val collectionId: Long,
    private val reminders: CollectionReminderState,
) {
    /** The collection being viewed, or `null` when its id is no longer on the shelf. */
    val collection get() = collections.allCollections.findById(collectionId)

    val isUncategorized: Boolean get() = collections.uncategorizedCollectionId == collectionId

    val isFavoritesCollection: Boolean get() = collections.favoritesCollectionId == collectionId

    /**
     * The shared shelf this page reads, handed back to whoever composes the page.
     *
     * Exposed so the page can be given a state and a handful of callbacks rather than the state
     * *and* everything it was built from. Without it every caller would have to pass the catalogue
     * alongside the holder that already holds it, which is one more way for the two to disagree.
     */
    internal val catalogue: CollectionsCatalogue get() = collections

    // ---- add picker ---------------------------------------------------------------------------

    /**
     * The dialog that adds remembrances to this collection.
     *
     * Held rather than inlined because it is a self-contained screen-in-a-dialog — its own query,
     * ticking and open flag, all reset when it closes — and the page has nothing to say about any of
     * them outside [CollectionDetailDialogs].
     */
    val addPicker: CollectionDetailAddPicker = CollectionDetailAddPicker(collections, collectionId)

    /** The in-progress remembrance form, or `null` while it is closed. */
    var remembranceDraft: RemembranceCreateDraft? by mutableStateOf(null)

    // ---- removal -------------------------------------------------------------------------------

    /** The members ticked for removal on the page itself, as ids. */
    var selectedToRemove: Set<Long> by mutableStateOf(emptySet())
        private set

    fun toggleRemoveSelection(id: Long) {
        selectedToRemove = if (id in selectedToRemove) selectedToRemove - id else selectedToRemove + id
    }

    fun clearRemoveSelection() {
        selectedToRemove = emptySet()
    }

    fun confirmRemove() {
        val ids = selectedToRemove.toList()
        selectedToRemove = emptySet()
        if (ids.isEmpty()) return
        collections.removeMembers(collectionId, ids)
    }

    // ---- recitation ----------------------------------------------------------------------------

    /** How far through the collection the user has got. Reads the shelf's progress, never a copy. */
    val progress: List<RecitationProgress>
        get() = collections.progress.of(collectionId)

    /** The progress summary shown in the page's header and in the session. */
    val stats: CollectionStats
        get() = collection?.let { statsOf(it, progress) } ?: EMPTY_STATS

    /** The member to recite next, or `null` when the collection is finished or missing. */
    val nextMember: Remembrance? get() = collection?.let { nextMember(it, progress) }

    /**
     * The members still to come, for the session's "ahead of this one" list.
     *
     * The current one is excluded by the caller asking for one more than it shows and dropping the first,
     * because "what is next" is a property of the collection while "what is after this one" depends on
     * where the session has got to — and the session owns that, not this page.
     */
    fun upcomingMembers(limit: Int): List<Remembrance> =
        collection?.let { upcomingMembers(it, progress, limit) }.orEmpty()

    /**
     * Counts a member once. Optimistic, and rolled back by [CollectionProgressState] if the write
     * fails — which is also what raises the notice, so a session that could not record a repeat says
     * so rather than silently losing it.
     */
    fun recordRecitation(
        remembranceId: Long,
        times: Int = 1,
    ) = collections.progress.record(collectionId, remembranceId, times) {
        collections.onNotice = CollectionNotice.Message(CollectionNoticeKey.FAILED)
    }

    /**
     * Forgets the whole collection's progress, from the detail page's reset button.
     *
     * The optimistic clear and its rollback both live in [CollectionProgressState]; this only decides
     * what the user is told afterwards, because which notice to raise is a question about this page.
     */
    fun resetProgress() =
        collections.progress.reset(
            collectionId = collectionId,
            onSuccess = { collections.onNotice = CollectionNotice.Message(CollectionNoticeKey.PROGRESS_RESET) },
            onFailure = { collections.onNotice = CollectionNotice.Message(CollectionNoticeKey.FAILED) },
        )

    /**
     * Starts reading this collection's progress.
     *
     * Called from a `LaunchedEffect` on both the detail page and the session, so it is idempotent —
     * whichever is composed first pays for the read and the other reuses the answer.
     */
    fun loadProgress() = collections.progress.load(collectionId)

    // ---- reminders -----------------------------------------------------------------------------

    /**
     * Whether the queue draws from this collection.
     *
     * One question with one answer, because the queue has exactly one question to ask about a
     * collection. The page's own bell and its menu both read this one fact, so they cannot disagree
     * about whether a collection is in the reminders.
     */
    val inReminders: Boolean get() = reminders.contains(collectionId)

    /**
     * The reminder holder, for the page's controls.
     *
     * Exposed rather than passed alongside this state so a control raised from the header or the menu
     * can reach the one reminder store without the page threading a second object through every
     * function between them.
     */
    val reminderState: CollectionReminderState get() = reminders

    /** Adds this collection to the queue's sources, or takes it out again. */
    fun setInReminders(enabled: Boolean) = reminders.setEnabled(collectionId, enabled)

    // ---- member tiles --------------------------------------------------------------------------

    /** How the members are ordered. See [CollectionMemberSort] for why this is a choice and not a fact. */
    var memberSort: CollectionMemberSort by mutableStateOf(CollectionMemberSort.SEQUENCE)
        internal set

    /** Whether the members are tiles or rows. See [CollectionMemberView]. */
    var memberView: CollectionMemberView by mutableStateOf(CollectionMemberView.GRID)
        internal set

    /** Filters only this collection's members; the collection itself remains unchanged. */
    var memberQuery: String by mutableStateOf("")
        private set

    fun onMemberQueryChanged(query: String) {
        memberQuery = query
    }

    fun setMemberSort(sort: CollectionMemberSort) {
        memberSort = sort
    }

    fun setMemberView(view: CollectionMemberView) {
        memberView = view
    }

    /**
     * Whether a drag on a tile should move it, which is true only of the collection's own order.
     *
     * "In order" is the one sort that means "the order you put them in", so it is the only one where
     * a drop survives the next re-read. Dragging while sorted by title or category would let the user
     * put a tile somewhere and then watch it walk back on the following composition. A search filter
     * also disables dragging: its visible indices are not the source collection's indices.
     */
    val canReorderMembers: Boolean get() = memberSort == CollectionMemberSort.SEQUENCE && memberQuery.isBlank()

    /** Puts the member at [fromIndex] where [toIndex] was. Indices are positions in [memberTiles]. */
    fun moveMember(
        fromIndex: Int,
        toIndex: Int,
    ) {
        if (!canReorderMembers) return
        collections.moveMember(collectionId, fromIndex, toIndex)
    }

    /** How many remembrances the collection holds. */
    val memberCount: Int get() = collection?.members?.size ?: 0

    /**
     * The page's tiles, in the collection's own order.
     *
     * Not re-sorted here: the order in [com.azkar.domain.model.RemembranceCollection.members] is the
     * order the user put them in, and re-sorting it would discard the one piece of information this
     * page can carry that the shelf cannot.
     *
     * Each tile is stamped with its position in *this* list as it is built, rather than the caller
     * numbering it afterwards — which is the only way the position can stay honest once a sort is in
     * play, because a number assigned after sorting would count the subset on screen rather than the
     * collection.
     */
    fun memberTiles(): List<RemembranceTile> {
        val current = collection ?: return emptyList()
        return current.members.mapIndexed { index, member ->
            collections.presenter.toTile(
                remembrance = member,
                inCustomCollections = collections.membershipCounts.collectionsIn(member.id ?: -1L),
                position = index + 1,
            )
        }
    }

    /**
     * The tiles the grid draws: [memberTiles], in the order asked for.
     *
     * Split from [memberTiles] rather than merged into it because the band above reads the *whole*
     * collection while the grid reads this, and because sorting is a choice about the reading rather
     * than a property of what the collection holds.
     *
     * A "By category" order is still offered even though the chip row that could *filter* by category
     * is gone: it groups the passages under the heading they already carry, which is an ordering, and
     * reading them in groups is not the same act as being shown only one group.
     */
    fun visibleMemberTiles(): List<RemembranceTile> {
        val current = collection ?: return emptyList()
        val query = memberQuery.trim().lowercase(Locale.ROOT)
        val tiles =
            current.members.mapIndexedNotNull { index, member ->
                if (query.isNotEmpty() && !CollectionFiltering.matchesRemembrance(member, query)) {
                    return@mapIndexedNotNull null
                }
                collections.presenter.toTile(
                    remembrance = member,
                    inCustomCollections = collections.membershipCounts.collectionsIn(member.id ?: -1L),
                    position = index + 1,
                )
            }
        return when (memberSort) {
            CollectionMemberSort.SEQUENCE -> tiles
            CollectionMemberSort.TITLE -> tiles.sortedBy { it.primaryPreview.lowercase(Locale.ROOT) }
            CollectionMemberSort.CATEGORY ->
                tiles.sortedWith(
                    compareBy(String.CASE_INSENSITIVE_ORDER, RemembranceTile::category)
                        .thenBy(String.CASE_INSENSITIVE_ORDER, RemembranceTile::primaryPreview),
                )
        }
    }
}

/** The stats of a collection that is not there, so the header has something honest to show. */
private val EMPTY_STATS = CollectionStats(0, 0, 0, 0, Duration.ZERO)
