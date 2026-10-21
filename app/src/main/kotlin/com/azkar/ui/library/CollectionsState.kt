package com.azkar.ui.library

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.azkar.domain.model.LocalizedPair
import com.azkar.domain.model.Remembrance
import com.azkar.domain.model.RemembranceCollection
import com.azkar.domain.model.Tag
import com.azkar.ui.library.data.CollectionFiltering
import com.azkar.ui.library.data.MembershipCounts
import com.azkar.ui.library.data.SearchGroup
import com.azkar.ui.library.model.CollectionScope
import com.azkar.ui.library.model.CollectionSort
import com.azkar.ui.library.model.CollectionTile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * The shelf's own view of the library: what the user is narrowing it by, and nothing else.
 *
 * Everything that is a fact about the *library* rather than about the *shelf* lives in
 * [CollectionsCatalogue] — the collections, the remembrances, the counts, and every write — and is
 * shared, so this class is what each screen holds to get at it. Only one screen ever asks the
 * questions kept here, which is what makes that split possible: a search box, a sort order and a
 * category selection are answers about what the user is looking at, and the detail page and the
 * session have no opinion about any of them.
 *
 * That distinction is also the reason the two are not merged. While this was all one holder, every
 * library screen built its own and re-read the entire catalogue on the way in, so opening a
 * collection and starting a session paid for the same whole-library read three times — and the
 * filters would have had to be shared too, which is the part that would have been a bug rather than
 * a saving.
 *
 * Deliberately not a ViewModel, matching the rest of the app: there is no `ViewModel` dependency in
 * this project, and this one is remembered per screen by the shelf, which is exactly the lifetime
 * its contents deserve.
 *
 * @param catalogue the shared library data. Public because the import flow is handed the catalogue
 * rather than this view — it plans against the whole library, not against what the shelf is showing.
 */
@Stable
class CollectionsState(
    private val scope: CoroutineScope,
    val catalogue: CollectionsCatalogue,
    /**
     * Which collections the reminder queue draws from.
     *
     * Read here rather than kept here: the queue's source list belongs to the queue, and a second
     * copy in the shelf's own state would be a second answer to "is this collection reminded".
     */
    val reminders: CollectionReminderState = CollectionReminderState.empty(),
) {
    // ---- the shared data, read straight through --------------------------------------------------

    val uiLocale: Locale get() = catalogue.uiLocale

    /** Turns domain objects into the strings the tiles draw. */
    val presenter get() = catalogue.presenter

    val allCollections: List<RemembranceCollection> get() = catalogue.allCollections

    val allRemembrances: List<Remembrance> get() = catalogue.allRemembrances

    /** How many of the user's own collections hold each remembrance. */
    val membershipCounts: MembershipCounts get() = catalogue.membershipCounts

    /** Whether a whole-library read is under way. Written by the catalogue, never by this view. */
    val loading: Boolean get() = catalogue.loading

    /** How far through each collection the user has got, which the cards draw as a bar. */
    val recitedCounts: Map<Long, Int> get() = catalogue.recitedCounts

    /** The "Updated …" / "Loading …" / "Load failed" strip at the bottom. */
    val infoLine: String get() = catalogue.infoLine

    /** The one-shot message the screen should raise, consumed by reading it once and clearing. */
    var onNotice: CollectionNotice?
        get() = catalogue.onNotice
        set(value) {
            catalogue.onNotice = value
        }

    suspend fun loadLabels() = catalogue.loadLabels()

    fun loadData() = catalogue.loadData()

    fun findCollection(id: Long) = catalogue.findCollection(id)

    fun toggleFavorite(id: Long) = catalogue.toggleFavorite(id)

    fun setDisplay(
        id: Long,
        preference: CollectionDisplayPreference,
        on: Boolean,
    ) = catalogue.setDisplay(id, preference, on)

    fun createCollection(
        name: String,
        description: String?,
        tagNames: Collection<String>,
        coverImagePath: String?,
        translations: Map<Locale, LocalizedPair> = emptyMap(),
        onCreated: (Long) -> Unit,
        nameLocale: Locale = Locale.ENGLISH,
    ) = catalogue.createCollection(
        name = name,
        description = description,
        tagNames = tagNames,
        coverImagePath = coverImagePath,
        translations = translations,
        onCreated = onCreated,
        nameLocale = nameLocale,
    )

    fun editCollection(
        id: Long,
        name: String,
        description: String?,
        tagNames: Collection<String>,
        coverImagePath: String?,
        translations: Map<Locale, LocalizedPair>? = null,
        nameLocale: Locale? = null,
        tagLocale: Locale? = null,
    ) = catalogue.editCollection(id, name, description, tagNames, coverImagePath, translations, nameLocale, tagLocale)

    fun deleteCollection(id: Long) = catalogue.deleteCollection(id)

    fun duplicateCollection(
        id: Long,
        newName: String,
        nameLocale: Locale = Locale.ENGLISH,
    ) = catalogue.duplicateCollection(id, newName, nameLocale)

    /** Decodes and caches collection covers for the cards and the editor preview to draw. */
    val covers: CollectionCoverCache get() = catalogue.covers

    /**
     * Asks the user for a picture and copies it into the app's own covers directory.
     *
     * Returns the stored name, or `null` if the dialog was cancelled or the file could not be read.
     * The two are deliberately not distinguished: to the caller — a dialog button that either gets
     * a name to hold or gets nothing to change — they mean the same thing, and reporting which had
     * happened would only invite it to treat a failed copy differently from a deliberate skip.
     */
    suspend fun pickCoverImage(title: String): String? = catalogue.pickCoverImage(title)

    // ---- reminder picks -------------------------------------------------------------------------

    /**
     * Whether a passage is one the user asked to be reminded about.
     *
     * A pick rather than a membership in a collection the user can see and edit, because the point of
     * picking one passage is that it is one passage: it should not turn up on the shelf as a shelf of
     * its own, and it should not be something the user has to keep tidy.
     */
    fun isPicked(remembranceId: Long): Boolean = catalogue.isPicked(remembranceId)

    /** Asks for a passage to be reminded about, or to stop being reminded about. */
    fun setReminderPick(
        remembranceId: Long,
        picked: Boolean,
    ) = catalogue.setReminderPick(remembranceId, picked)

    // ---- the shelf's own order -------------------------------------------------------------------

    /**
     * Puts a collection where the user dropped it.
     *
     * The drop is expressed as two places in the shelf the user was looking at, and the shelf the
     * user was looking at is the answer: the order they dragged in is the order they meant, whether
     * the shelf was sorted by name or by anything else. So the order that was on screen is what gets
     * written, with one card moved — and the shelf then shows that order, because an order the user
     * arranged and an order the shelf refuses to show are the same thing to them.
     *
     * Switching the sort to [CollectionSort.CUSTOM] is therefore part of the drop rather than a
     * separate decision: the user has just told the library which order they want, and leaving the
     * shelf sorted by name would throw that away the moment the page recomposed.
     */
    fun reorderVisible(
        from: Int,
        to: Int,
    ) {
        val visible = visibleCollections
        if (from !in visible.indices || to !in visible.indices || from == to) return
        val ids = visible.mapNotNull { it.id }
        // A collection with no id cannot be written down, and one that has it cannot be half-moved:
        // the order written has to be every card's, or the cards that were left out would keep
        // positions that now belong to somebody else.
        if (ids.size != visible.size) return
        val reordered = ids.toMutableList()
        reordered.add(to, reordered.removeAt(from))
        sort = CollectionSort.CUSTOM
        catalogue.reorderCollections(reordered)
    }

    // ---- filters -------------------------------------------------------------------------------

    var scopeFilter: CollectionScope by mutableStateOf(CollectionScope.ALL)

    var sort: CollectionSort by mutableStateOf(CollectionSort.RECENTLY_UPDATED)

    /** The shelf's own search field. Applied to names and descriptions only. */
    var collectionQuery: String by mutableStateOf("")

    /**
     * The label the shelf is being narrowed by, or `null` for every label.
     *
     * One label at a time, matching every other narrowing on this screen. The underlying filter takes
     * a set because a label filter is an intersection rather than an equality — "show me the morning
     * shelves" is a question about any of a collection's labels — and because that is the shape the
     * pure function should have regardless of how many chips this screen offers.
     *
     * The shelf used to carry a second narrowing beside this one, singling out a single collection by
     * id. It went with the row of name chips that wrote it: one control for one question, and the
     * cards below already answer "which one".
     */
    var labelFilter: Long? by mutableStateOf(null)

    /**
     * The cross-collection search field, split into what the user has typed and what has settled.
     *
     * The field is bound to [remembranceQuery] so it stays responsive on every keystroke, while
     * [settledRemembranceQuery] is what the results are computed from. Grouping a query means
     * scanning every member of every collection, so running it per keystroke would make typing into
     * an empty shelf progressively slower as the answer grows.
     */
    var remembranceQuery: String by mutableStateOf("")

    private var settledRemembranceQuery: String by mutableStateOf("")
        private set

    private var searchDebounce: Job? = null

    // ---- derived, over the shared data -----------------------------------------------------------

    /**
     * The collections the queue draws from.
     *
     * The queue's own source list, plus the reserved "Reminder picks" shelf — which is a source by
     * virtue of holding the passages the user picked one at a time, and is not in the list because
     * nobody puts it there. The shelf appears and disappears with its contents, so asking the
     * catalogue for its id is the same as asking whether anything is picked.
     */
    val reminderCollectionIds: Set<Long>
        get() {
            val enabled = reminders.enabledCollectionIds
            val picks = catalogue.reminderPicksCollectionId
            return if (picks == null) enabled else enabled + picks
        }

    val visibleCollections: List<RemembranceCollection>
        get() =
            CollectionFiltering.shelf(
                collections = allCollections,
                scope = scopeFilter,
                query = collectionQuery,
                sort = sort,
                recitedCounts = catalogue.recitedCounts,
                tagIds = labelFilter?.let { setOf(it) }.orEmpty(),
                reminderCollectionIds = reminderCollectionIds,
                // The name the sort breaks its ties on has to be the one on the cards, or a shelf
                // sorted by anything else orders rows by a name nobody is reading.
                uiLocale = uiLocale,
            )

    val shelfTiles: List<CollectionTile>
        get() =
            visibleCollections.map {
                presenter.shelfTile(
                    collection = it,
                    recitedCounts = catalogue.recitedCounts,
                    inReminders = it.id in reminderCollectionIds,
                )
            }

    /**
     * Every label in use on the shelf, for the label chips, alphabetically and with no duplicates.
     *
     * Read straight through from [CollectionsCatalogue.shelfTags] rather than collected here, because
     * the editor's tag picker needs the same list and it is opened from the detail page too, where
     * there is no [CollectionsState] to ask. Two derivations of "every tag in use" would eventually
     * disagree about ordering, and a chip row that reorders itself is a bug nobody can reproduce.
     */
    val shelfLabels: List<Tag> get() = catalogue.shelfTags

    /**
     * How many remembrances the whole shelf holds, for the sidebar's "All Collections" row.
     *
     * Summed over the members rather than counted from [allRemembrances], because a user collection
     * may hold the same passage as a shipped one and the sidebar is counting what there is to read,
     * not how many distinct rows the database has.
     */
    val totalRemembrances: Int
        get() = allCollections.sumOf { it.members.size }

    /** Cross-collection hits, grouped by the collection each was found in. Empty unless searching. */
    val searchGroups: List<SearchGroup>
        get() =
            CollectionFiltering.groupSearchResults(
                collections = allCollections,
                remembrances = allRemembrances,
                query = settledRemembranceQuery,
            )

    val isSearchingRemembrances: Boolean get() = settledRemembranceQuery.isNotBlank()

    val isEmpty: Boolean get() = !catalogue.loading && visibleCollections.isEmpty() && !isSearchingRemembrances

    // ---- filter input --------------------------------------------------------------------------

    fun onRemembranceQueryChanged(value: String) {
        remembranceQuery = value
        searchDebounce?.cancel()
        if (value.isBlank()) {
            // Clearing the field shows the shelf again at once; waiting out a debounce to get there
            // would make the clearest possible query the slowest to answer.
            settledRemembranceQuery = ""
            return
        }
        searchDebounce =
            scope.launch {
                delay(SEARCH_DEBOUNCE_MILLIS)
                settledRemembranceQuery = value
            }
    }

    // ---- lifecycle -----------------------------------------------------------------------------

    /** Cancels the pending search debounce. Called when the screen leaves the composition. */
    fun shutdown() {
        searchDebounce?.cancel()
        searchDebounce = null
    }

    private companion object {
        /** How long the cross-collection search waits for the user to stop typing. */
        const val SEARCH_DEBOUNCE_MILLIS = 180L
    }
}
