package com.azkar.ui.library

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.azkar.domain.model.TagSummary
import com.azkar.domain.model.TagUsage
import com.azkar.ui.library.data.TagGateway
import com.azkar.ui.library.data.TagSaveResult
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.Locale

private val logger = KotlinLogging.logger {}

/**
 * How many rows the footer pages at.
 *
 * A page size rather than an endless scroll because the footer already has to say how many tags there
 * are — a count and an endless list contradict each other, and twelve is roughly a screenful at the
 * row height this screen draws, so paging here is paging the reader's attention rather than the
 * database's work.
 */
internal const val TAGS_PAGE_SIZE = 12

/**
 * Which tags the list is showing, chosen from the toolbar's scope dropdown.
 *
 * [DELETED] overlaps with the "show deleted" toggle next to it rather than replacing it, and the two
 * are kept because they answer different questions. The dropdown says *what kind of tag I am looking
 * at*; the toggle says *also let me see the ones I removed without losing my place in the list I was
 * reading*. Picking [DELETED] implies the toggle, which is why the toggle is hidden in that case
 * instead of being drawn inert.
 */
enum class TagScope {
    ALL,
    USED,
    UNUSED,
    DELETED,
}

/**
 * The order the rows are drawn in.
 *
 * Every case falls back to the name when the figures it sorts on tie, so two tags with no usage
 * between them never swap places between a sort press and the next one — the same rule the shelf's
 * own tag chips use.
 */
enum class TagSort {
    NAME_ASC,
    NAME_DESC,
    MOST_USED,
    LEAST_USED,
    NEWEST,
}

/**
 * The three figures the hero band reports, folded from the rows that are already in memory.
 *
 * Not three counts issued by the database: the screen has just read every tag with its usage totals,
 * so adding them up costs nothing, and a statistic that came from a second statement is one that can
 * disagree with the rows above it.
 *
 * @property total how many tags exist right now.
 * @property usedInCollections how many of them are on at least one collection.
 * @property taggedRemembrances how many passages carry a tag, counting a passage once per tag it
 * carries — the figure is "taggings", not distinct passages, and the screen's wording says so.
 */
@Stable
data class TagStats(
    val total: Int,
    val usedInCollections: Int,
    val taggedRemembrances: Int,
)

/**
 * The Manage tags screen's view of the tag vocabulary, and nothing else.
 *
 * Its own holder rather than a part of [CollectionsCatalogue] for a reason that is worth stating
 * because it looks like duplication: the catalogue answers "what is in the library", and a tag is
 * not in the library until something is attached to it. A label the user has typed but not yet used
 * is a real row in `tag` and appears in no collection at all, so a screen listing the vocabulary
 * would be showing rows the catalogue cannot know about. The two do have to be kept in step after a
 * write — [onTagsChanged] is how — but they answer different questions and are read at different
 * times.
 *
 * Every write reports through [onNotice] rather than raising a toast itself, for the same reason
 * the shelf's mutations do: the host owns the toast, and a notice key survives a language switch
 * where a formatted string would not.
 *
* @param onTagsChanged called after every successful write, so the shelf can drop its cached copy of
 *  the tag names it draws its label chips from. Without it a tag deleted here would keep appearing as
 *  a chip on the shelf until the whole library was read again.
 * @param labelOf the name a tag goes by in the language the reader is using, which is what the search
 *  and the sort work on. Taken as a function rather than a [Locale] so this class needs no resource
 *  environment to build, which is what lets it be tested against two tags and no database. Defaults to
 *  the stored name, which is right for a tag with no translations and is the honest answer for a
 *  caller that has not said which language it is sorting in.
 */
@Stable
@Suppress("TooManyFunctions")
class TagsState(
    private val scope: CoroutineScope,
    private val gateway: TagGateway,
    private val onTagsChanged: () -> Unit,
    private val labelOf: (TagSummary) -> String = { it.name },
) {
    /** Whether the first read is still under way. Distinguishes "loading" from "the user has none". */
    var loading: Boolean by mutableStateOf(true)
        private set

    /** Every tag in use, with the counts the rows are drawn from. */
    var tags: List<TagSummary> by mutableStateOf(emptyList())
        private set

    /**
     * The tags the user has deleted, most recently deleted first.
     *
     * Held separately from [tags] rather than behind a flag on the same list because the two are
     * answered by two different questions and change for two different reasons: a live tag changes
     * when it is renamed or attached to something, and a deleted one only when it is deleted or
     * restored. Keeping them apart means the "show deleted" toggle is a filter over what was
     * already read rather than a third read on a toggle press.
     */
    var deleted: List<TagSummary> by mutableStateOf(emptyList())
        private set

    /** The screen's own search field. Applied to the name only, on both lists. */
    var query: String by mutableStateOf("")

    /**
     * Whether the soft-deleted tags are the ones on show, in place of the live ones.
     *
     * Private to [toggleDeleted] so that the page reset cannot be skipped by writing the flag
     * directly, the same reason [onQueryChanged] and [selectScope] exist rather than a bare field.
     */
    private var showDeleted: Boolean by mutableStateOf(false)

    /** Which tags the scope dropdown has narrowed the list to. */
    var scopeFilter: TagScope by mutableStateOf(TagScope.ALL)
        private set

    /** The order the toolbar's sort dropdown asks for. */
    var sort: TagSort by mutableStateOf(TagSort.NAME_ASC)
        private set

    /** The zero-based page the footer is on. Clamped for reading, so a shrinking list needs no write. */
    var page: Int by mutableStateOf(0)
        private set

    /** The usage panel's contents, or null while it is closed. */
    var usage: TagUsage? by mutableStateOf(null)
        private set

    /** Whether the usage panel is waiting on its own read, which is a second round trip. */
    var usageLoading: Boolean by mutableStateOf(false)
        private set

    /** The one-shot message the screen should raise, consumed by reading it once and clearing. */
    var onNotice: CollectionNotice? by mutableStateOf(null)

    // ---- derived views --------------------------------------------------------------------------

    /** The live tags left by the search, the scope and the sort, before the page is cut. */
    val visibleTags: List<TagSummary>
        get() =
            if (lookingAtDeleted) {
                emptyList()
            } else {
                sortedTags(scopedTags(filterTags(tags, query, labelOf), scopeFilter), sort, labelOf)
            }

    /** The deleted tags the search leaves standing — empty unless they are what the list is showing. */
    val visibleDeleted: List<TagSummary>
        get() =
            if (lookingAtDeleted) sortedTags(filterTags(deleted, query, labelOf), sort, labelOf) else emptyList()

    /** Everything the list is drawing, live rows first, as one sequence for the footer to count. */
    val rows: List<TagSummary> get() = visibleTags + visibleDeleted

    /** How many pages the rows occupy, never zero — an empty list is page one of one. */
    val pageCount: Int get() = lastPage + 1

    /** The rows on this page. Slices [rows], so paging counts live and deleted tags together. */
    val pagedRows: List<TagSummary> get() = rows.drop(currentPage * TAGS_PAGE_SIZE).take(TAGS_PAGE_SIZE)

    /** [page] after clamping, which is what every paging control reads. */
    val currentPage: Int get() = page.coerceIn(0, lastPage)

    /** The three figures the hero band reports. */
    val stats: TagStats
        get() = TagStats(tags.size, tags.count { it.collectionCount > 0 }, tags.sumOf { it.remembranceCount })

    /** Whether choosing [TagScope.DELETED] already implies the toggle, so the toggle is not drawn. */
    val deletedToggleRelevant: Boolean get() = scopeFilter != TagScope.DELETED

    /** Whether the toolbar's deleted toggle is pressed, which is what gives the chip its selected look. */
    val deletedFilterOn: Boolean get() = showDeleted

    /** Whether the search matched nothing, which is a different answer from having no tags at all. */
    val searchMissedEverything: Boolean get() = query.isNotBlank() && rows.isEmpty()

    /**
     * Whether the deleted half is on show and has nothing in it.
     *
     * Its own answer rather than either neighbour: the library may be full of tags and the reader
     * still gets nothing here, which is neither "you have no tags" nor a search that missed.
     */
    val noDeletedTags: Boolean get() = lookingAtDeleted && deleted.isEmpty()

    /** Whether there is nothing at all to draw once the search has had its say. */
    val isEmpty: Boolean get() = !loading && !lookingAtDeleted && tags.isEmpty()

    private val lastPage: Int get() = (rows.size - 1).coerceAtLeast(0) / TAGS_PAGE_SIZE

    /**
     * Whether the list is looking at the removed tags rather than the live ones.
     *
     * A filter, not an extra. Pressing the toggle takes the live tags *out* of the list instead of
     * appending the deleted ones underneath them: someone who reaches for it is asking what they
     * just removed, and making them scroll past everything they chose to keep to reach it is a worse
     * answer than the question deserved. [TagScope.DELETED] asks the same thing from the dropdown, so
     * both land here rather than one of them leaving the toggle's own meaning ambiguous.
     */
    private val lookingAtDeleted: Boolean get() = showDeleted || scopeFilter == TagScope.DELETED

    // ---- reads -----------------------------------------------------------------------------------

    /** Reads both halves of the vocabulary. Called when the screen opens and after every write. */
    fun load() {
        scope.launch {
            loading = true
            refresh(announce = null)
        }
    }

    /**
     * Rereads both halves and reports the outcome.
     *
     * One read for both lists rather than one per branch of the screen: they are the same table, and
     * asking twice would let them disagree about a tag deleted between the two statements — the
     * deleted list would offer to restore something the live list has already re-adopted.
     */
    private suspend fun refresh(announce: CollectionNoticeKey?) {
        runCatching {
            gateway.fetchSummaries() to gateway.fetchDeleted()
        }.onSuccess { (live, gone) ->
            tags = live
            deleted = gone
            loading = false
            if (announce != null) {
                onNotice = CollectionNotice.Message(announce)
            }
        }.onFailure { failure ->
            logger.error(failure) { "Unable to read the tag vocabulary" }
            loading = false
            onNotice = CollectionNotice.Message(CollectionNoticeKey.FAILED)
        }
    }

    /**
     * Opens the panel that lists what a tag is on.
     *
     * A second read rather than an answer to the one already made, and deliberately so: the summaries
     * carry counts but not *which* collections, and a tag on two hundred passages cannot bring two
     * hundred rows along on every list refresh. The panel is the only place either is needed.
     */
    fun openUsage(id: Long) {
        scope.launch {
            usageLoading = true
            usage = attempt("read what tag id=$id is used by") { gateway.usage(id) }
            usageLoading = false
        }
    }

    /**
     * Closes the usage panel.
     *
     * Called when a collection or passage is opened from it as well as by its own close control: the
     * destination is pushed on top of this screen rather than replacing it, so a panel left open would
     * still be waiting there on the way back, pointing at a row the reader has already read.
     */
    fun closeUsage() {
        usage = null
        usageLoading = false
    }

    // ---- filters ---------------------------------------------------------------------------------

    /**
     * Replaces the search text and returns to the first page.
     *
     * The reset is not a convenience. Narrowing a list while the reader is on page four of five
     * leaves them looking at an empty page with no way to guess why, and the empty page is
     * indistinguishable from "no tags match".
     */
    fun onQueryChanged(value: String) {
        query = value
        page = 0
    }

    /** Applies a scope and returns to the first page, for the reason [onQueryChanged] gives. */
    fun selectScope(value: TagScope) {
        scopeFilter = value
        page = 0
    }

    /** Applies a sort. Re-sorts in place, so the page is left alone — the row count has not moved. */
    fun selectSort(value: TagSort) {
        sort = value
    }

    /**
     * Switches between the live tags and the removed ones, and returns to the first page.
     *
     * The reset is [onQueryChanged]'s reason: the two lists are rarely the same length, so a reader
     * on page three of the tags they kept would land past the end of the tags they threw away.
     */
    fun toggleDeleted() {
        showDeleted = !showDeleted
        page = 0
    }

    /** Moves forward a page, or does nothing on the last one. */
    fun nextPage() {
        page = (currentPage + 1).coerceAtMost(lastPage)
    }

    /** Moves back a page, or does nothing on the first one. */
    fun previousPage() {
        page = (currentPage - 1).coerceAtLeast(0)
    }

    // ---- writes ----------------------------------------------------------------------------------

    /**
     * Names a new tag and reports what the storage layer made of the name.
     *
     * Suspend and returning the answer rather than firing and announcing it, because the caller is a
     * dialog with a field and a button, and "that name is taken" belongs under that field rather than
     * in a toast that arrives after the dialog has closed. `null` means the write failed outright,
     * which has already been announced and needs no answer of its own.
     *
     * [translations] are the other-language names typed alongside [name]. They are optional in every
     * sense: a tag with none is a complete tag that reads the same in every language, which is a
     * legitimate thing to have.
     */
    suspend fun create(
        name: String,
        translations: Map<Locale, String> = emptyMap(),
        nameLocale: Locale = Locale.ENGLISH,
    ): TagSaveResult? {
        val result = attempt("create the tag '$name'") { gateway.create(name, translations, nameLocale) } ?: return null
        if (result == TagSaveResult.Saved) {
            refresh(announce = null)
            onTagsChanged()
        }
        return result
    }

    /** Renames [id] to [newName], reporting the outcome the way [create] does. */
    suspend fun rename(
        id: Long,
        newName: String,
        translations: Map<Locale, String>? = null,
        nameLocale: Locale? = null,
    ): TagSaveResult? {
        val result =
            attempt("rename tag id=$id to '$newName'") { gateway.rename(id, newName, translations, nameLocale) } ?: return null
        if (result == TagSaveResult.Saved) {
            refresh(announce = CollectionNoticeKey.TAG_RENAMED)
            onTagsChanged()
        }
        return result
    }

    /**
     * Hides a tag from the library without forgetting it.
     *
     * Fired and announced rather than returned, because nothing is typed into it and there is no
     * answer for the user to read: the row leaves the list and the shelf's chips lose it, which is
     * the whole of what a successful delete looks like.
     */
    fun softDelete(id: Long) {
        scope.launch {
            attempt("delete tag id=$id") { gateway.softDelete(id) } ?: return@launch
            refresh(announce = CollectionNoticeKey.TAG_DELETED)
            onTagsChanged()
        }
    }

    /** Puts a soft-deleted tag back, with every shelf and passage it was on. */
    fun restore(id: Long) {
        scope.launch {
            attempt("restore tag id=$id") { gateway.restore(id) } ?: return@launch
            refresh(announce = CollectionNoticeKey.TAG_RESTORED)
            onTagsChanged()
        }
    }

    /**
     * Runs a storage call, turning anything it throws into the one failure notice.
     *
     * The gateway already answers the questions that have a better answer than "it failed" — a name
     * that is taken, a blank field — as values rather than exceptions. What reaches here is a driver
     * failure, which is the one thing there is no more specific sentence for, and the row is logged
     * because the exception alone says that something failed rather than which change did not happen.
     *
     * [Exception] is caught rather than something narrower on purpose, and the suppression is the
     * record of that. Naming the types here would be naming the driver's: H2 and whichever engine
     * ships in front of it raise different ones for the same deadlock, and the day that list is
     * written down the screen starts dropping failures on the floor the first time an exception
     * arrives that was not on it. Cancellation is the one exclusion, since swallowing that would
     * leave a cancelled coroutine running on to report a failure nobody is waiting for.
     */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun <T> attempt(
        description: String,
        operation: suspend () -> T,
    ): T? =
        try {
            operation()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            logger.error(failure) { "Unable to $description" }
            onNotice = CollectionNotice.Message(CollectionNoticeKey.FAILED)
            null
        }
}

/**
 * The tags whose name contains [query], compared without regard to case, or all of them when
 * [query] is blank.
 *
 * A plain case-insensitive containment test rather than the database's own search, for the same
 * reason the tag picker searches in memory: both lists are bounded by how many labels one person has
 * typed, and the row the user is narrowing by is a row the screen is already holding.
 *
 * Matched against [labelOf] rather than the stored name, because the reader is searching for a word
 * they can see on the row and not for the word the row was filed under. The stored name is still
 * searched — a reader who knows the tag by the name it is stored as is just as likely — and the
 * distinction only exists for a tag that has both, which is a tag that has been translated.
 *
 * Free of the gateway on purpose. Everything here is a filter over two lists, so it can be tested
 * against lists of two tags without a database, a fake, or a coroutine — which is also why it is a
 * top-level function and not a method on [TagsState].
 */
internal fun filterTags(
    tags: List<TagSummary>,
    query: String,
    labelOf: (TagSummary) -> String = { it.name },
): List<TagSummary> {
    val needle = query.trim()
    if (needle.isEmpty()) return tags
    return tags.filter { searchableText(it, labelOf).contains(needle, ignoreCase = true) }
}

/**
 * Both of a tag's names, joined, so one containment test covers either.
 *
 * The separator is a space rather than nothing so a query cannot match by straddling the two names —
 * "rn gEve" is not how anyone searches, and a false positive there is a row that looks like a match
 * and is not. Duplicated names cost one redundant scan of a string and nothing else.
 */
private fun searchableText(
    tag: TagSummary,
    labelOf: (TagSummary) -> String,
): String {
    val label = labelOf(tag)
    val stored = tag.name
    return if (label.equals(stored, ignoreCase = true)) label else "$label $stored"
}

/**
 * The live tags a scope leaves standing.
 *
 * "Used" counts a tag as used when anything at all carries it, so a tag on only passages and one on
 * only collections are both in — the scope answers "did I get round to using this", not "is this a
 * collection label". [TagScope.DELETED] leaves the live list empty rather than filtering it, because
 * the deleted rows are a different list held for a different reason.
 */
internal fun scopedTags(
    tags: List<TagSummary>,
    scope: TagScope,
): List<TagSummary> =
    when (scope) {
        TagScope.ALL -> tags
        TagScope.USED -> tags.filter { it.usageCount > 0 }
        TagScope.UNUSED -> tags.filter { it.usageCount == 0 }
        TagScope.DELETED -> emptyList()
    }

/**
 * The rows in the order a sort asks for, breaking every tie on the name.
 *
 * A tag with no creation date sorts to the bottom of [TagSort.NEWEST] rather than to 1970 in the
 * middle of the list, because "added recently" is a claim about rows that were added and a tag that
 * was never stamped is not one of them.
 *
 * Broken on the *shown* name rather than the stored one, which is the whole point: an alphabetically
 * sorted list a reader cannot read alphabetically is not sorted, and the tiebreaker is what decides
 * the order of every list sorted by anything else as well. [labelOf] defaults to the stored name so
 * that a caller which has not said which language it is sorting in gets the old behaviour rather than
 * a silent default nobody chose.
 */
internal fun sortedTags(
    tags: List<TagSummary>,
    sort: TagSort,
    labelOf: (TagSummary) -> String = { it.name },
): List<TagSummary> {
    val byName = compareBy(String.CASE_INSENSITIVE_ORDER, labelOf)
    return when (sort) {
        TagSort.NAME_ASC -> tags.sortedWith(byName)
        TagSort.NAME_DESC -> tags.sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER, labelOf))
        TagSort.MOST_USED -> tags.sortedWith(byUsageDescending().thenBy(String.CASE_INSENSITIVE_ORDER, labelOf))
        TagSort.LEAST_USED -> tags.sortedWith(byUsageAscending().thenBy(String.CASE_INSENSITIVE_ORDER, labelOf))
        TagSort.NEWEST -> tags.sortedWith(byNewestFirst().thenBy(String.CASE_INSENSITIVE_ORDER, labelOf))
    }
}

private fun byUsageDescending(): Comparator<TagSummary> = compareByDescending { it.usageCount }

private fun byUsageAscending(): Comparator<TagSummary> = compareBy { it.usageCount }

private fun byNewestFirst(): Comparator<TagSummary> =
    compareByDescending { it.createdAt?.toEpochMilli() ?: Long.MIN_VALUE }

/**
 * The palette slot a tag's avatar tile is filled from.
 *
 * Hashed rather than handed out in order so that a tag keeps its colour between reads, between sorts
 * and between sessions — a colour that came from its position would repaint the whole list the
 * moment one tag was deleted. [Math.floorMod] rather than `%` because a negative hash must still land
 * inside the palette, and Kotlin's remainder keeps the sign.
 */
internal fun tagAvatarSlot(
    name: String,
    paletteSize: Int,
): Int = Math.floorMod(name.hashCode(), paletteSize)
