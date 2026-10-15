package com.azkar.ui.notifications

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.azkar.domain.model.Remembrance
import com.azkar.domain.model.RemembranceCollection
import com.azkar.i18n.RemembranceI18n
import com.azkar.ui.library.model.CollectionsSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

/**
 * The one catalogue read the reminders screen makes, narrowed to the call it actually makes.
 *
 * [ReminderManagerState] needs a shelf of collections and a catalogue of passages and nothing else
 * from the library — no writes, no favourites, no progress. Depending on [CollectionsGateway] for
 * `fetchSnapshot()` alone put a twenty-method interface in this file's constructor for a single
 * call, and made the one thing a preview of this screen needs — a shelf of sample collections —
 * require implementing all twenty.
 */
interface ReminderCatalogueSource {
    suspend fun snapshot(): CollectionsSnapshot
}

/**
 * The queue's next delivery, resolved for the summary at the top of the screen.
 *
 * [passage] is the passage the *next* slot would show, not the one after it: the runtime advances
 * [ReminderQueueProgress.nextIndex] only after publishing, so the progress it wrote is still
 * pointing at the passage that has not been shown yet.
 *
 * [slot] is null when the queue is off, has no sources, or the window has closed for the day. All
 * three read as "nothing scheduled" in the hero rather than as an error, because none of them is
 * one — see [QueueSchedule.nextSlot] for why the closed-window case deliberately does not roll
 * forward.
 */
@Immutable
data class QueuePreview(
    val enabled: Boolean,
    val hasSources: Boolean,
    val slot: LocalDateTime?,
    val passage: Remembrance?,
)

/**
 * Everything the reminders screen knows that the repository does not already own.
 *
 * The screen this replaces was one 688-line composable holding fifteen `remember`ed locals next to
 * the six cards that read them, which made the two halves of the page — "what is scheduled" and
 * "change the schedule" — impossible to read separately.
 *
 * The class holds the queue and nothing else. It used to hold prayer location and overlay
 * presentation too, back when one screen owned all three; they now belong to
 * [NotificationSettingsState], because toggling a prayer is the home screen's job and the rest is a
 * preference with no connection to what is in the queue. The separation is worth the extra type:
 * nothing about building a queue needs to know what a time zone is.
 *
 * The split follows the one the rest of the app already uses: persisted values stay on
 * [NotificationPreferencesRepository]'s `StateFlow`s and are read for drawing with `collectAsState`,
 * while transient form state — the search text, the auto-selection latch — lives here. This class is
 * the only writer of the queue, so every mutation happens through one `scope.launch` and one
 * repository call rather than being scattered across the body of a composable where a `LaunchedEffect`
 * could re-run it.
 *
 * Persisted state is read back through `preferences.<flow>.value` inside the mutators instead of
 * being passed in from the composition. That keeps `copy` chains — which need the whole record, not
 * the one field being changed) out of the call sites entirely, and it is safe because
 * [NotificationPreferencesRepository] is a process-wide singleton whose flow values are the single
 * source of truth for what is stored.
 */
@Stable
class ReminderManagerState(
    private val scope: CoroutineScope,
    private val preferences: NotificationPreferencesRepository,
    private val catalogueSource: ReminderCatalogueSource,
    /**
     * The machine's own zone, which is what the queue's window is written in.
     *
     * The only clock this screen knows about. It used to sit alongside the prayer location's zone so
     * the summary card could resolve "next prayer", which meant a user who was travelling had two
     * zone ids in play and no way to tell which one the interval they were reading belonged to.
     */
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    /**
     * The catalogue behind the source pickers.
     *
     * Replaced wholesale by [bind] rather than merged, and [uncategorizedId] carried separately:
     * "Uncategorised" is a real collection with real members, but it is not something a user picks
     * *from* — its passages are reached through the individual list — so it is filtered out of
     * [collectionItems] rather than shown and then mysteriously ignored.
     */
    var collections: List<RemembranceCollection> by mutableStateOf(emptyList())
        private set

    /** Passages the individual picker offers, including those inside the uncategorised bucket. */
    var remembrances: List<Remembrance> by mutableStateOf(emptyList())
        private set

    var uncategorizedId: Long? by mutableStateOf(null)
        private set

    /** Free text over the passage list. Empty means "no filter", not "match nothing". */
    var searchQuery: String by mutableStateOf("")
        private set

    /**
     * Whether the collection passed to the screen from a collection page has been queued yet.
     *
     * A flag rather than a "remove when done" key precisely because the auto-selection has to
     * survive the catalogue arriving *after* the screen does: the effect that does it re-runs when
     * the collections change, and an effect that deleted itself on its first run would never see
     * the collection it was waiting for.
     */
    var autoSelectionApplied: Boolean by mutableStateOf(false)
        private set

    /** Everything the screen draws, gathered once when it first appears. */
    suspend fun bind() {
        preferences.refresh()
        preferences.observe(scope)
        runCatching { catalogueSource.snapshot() }
            .onSuccess { snapshot ->
                collections = snapshot.collections
                remembrances = snapshot.remembrances
                uncategorizedId = snapshot.uncategorizedCollectionId
            }
    }

    /**
     * Sets the search text.
     *
     * Named `update…` rather than `setSearchQuery` because a `fun setX(x: T)` beside a `var x: T`
     * collides with the JVM setter the property delegate generates — the clash surfaces as
     * "Platform declaration clash" against a line of Kotlin that reads perfectly correctly.
     */
    fun updateSearchQuery(value: String) {
        searchQuery = value
    }

    /**
     * Queues the collection the screen was opened from, once the catalogue has arrived.
     *
     * Re-runs harmlessly: [autoSelectionApplied] latches on success, and a collection that is
     * already queued is left alone so arriving here twice cannot add a duplicate.
     */
    fun autoSelectCollection(collectionId: Long?) {
        val id = collectionId ?: return
        if (autoSelectionApplied || collections.none { it.id == id }) return
        autoSelectionApplied = true
        schedule.setCollectionEnabled(id, enabled = true)
    }

    // ---------------------------------------------------------------- writes

    /**
     * Writes for the queue's schedule.
     *
     * Shared with the settings page rather than owned here: turning the queue on, choosing its order,
     * its cadence, its weekdays and its window are edits to the config and nothing else, and the card
     * that makes them moved there. What could not move — [moveInManualOrder], which cannot rank a
     * sequence without the catalogue — stayed on this class, which is why the split is by data rather
     * than by screen.
     */
    val schedule = ReminderScheduleEditor(scope = scope, preferences = preferences)

    /**
     * Moves a passage one place up or down the manual order.
     *
     * Here rather than on [ReminderScheduleEditor] because the order written back is the *complete*
     * list, and completing it means knowing which passages the selected collections actually contain.
     *
     * The list is rebuilt from the stored order plus anything missing rather than by swapping a pair:
     * [ReminderQueueConfig.manualOrder] is a preference that has to describe the whole sequence,
     * because the runtime reads it as a ranking and appends anything it does not mention. Writing only
     * the two swapped ids would silently drop every passage after them from the stored order on the
     * next reload.
     */
    fun moveInManualOrder(
        id: Long,
        offset: Int,
    ) {
        scope.launch {
            preferences.updateQueue { config ->
                val ids = availableIds(config)
                val order =
                    (
                        config.manualOrder.filter { manual -> manual in ids } +
                            ids.filterNot { manual -> manual in config.manualOrder }
                    ).distinct().toMutableList()
                val from = order.indexOf(id)
                val to = (from + offset).coerceIn(0, order.lastIndex)
                if (from >= 0) {
                    order.add(to, order.removeAt(from))
                }
                config.copy(manualOrder = order)
            }
        }
    }

    // ---------------------------------------------------------------- derived views

    /**
     * The collections offered as queue sources.
     *
     * Null ids are dropped because every picker here is keyed by id, and the uncategorised bucket
     * is dropped because it is a container rather than something a user chooses.
     */
    val collectionItems: List<RemembranceCollection>
        get() = collections.filter { it.id != null && it.id != uncategorizedId }

    /**
     * Every passage the queue could draw from: the members of every selected collection, plus the
     * individually picked ones.
     *
     * Filtered against [remembrances] rather than trusted from the collection members alone, because
     * a collection can name a member the catalogue has not loaded and the old code silently queued
     * an id that would resolve to nothing at delivery time.
     */
    fun availableIds(config: ReminderQueueConfig): List<Long> {
        val members =
            collectionItems
                .filter { it.id in config.collectionIds }
                .flatMap { collection -> collection.members.mapNotNull { it.id } }
        return (members + config.individualRemembranceIds)
            .distinct()
            .filter { id -> remembrances.any { it.id == id } }
    }

    /** [availableIds] resolved to passages and put in manual-order sequence. */
    fun manualItems(
        config: ReminderQueueConfig,
        ids: List<Long> = availableIds(config),
    ): List<Remembrance> =
        (config.manualOrder.filter { it in ids } + ids.filterNot { it in config.manualOrder })
            .distinct()
            .mapNotNull { id -> remembrances.firstOrNull { it.id == id } }

    /**
     * The passages the individual picker shows, narrowed by [searchQuery] and capped.
     *
     * Capped rather than virtualised because the old list was a `verticalScroll` *inside* the
     * page's own `verticalScroll` — two scroll axes on one card, which on a trackpad means the
     * inner list swallows the gesture until it reaches its end. A flat list with a stated cap and a
     * line saying so is honest about the limit and scrolls as one page.
     */
    fun filteredRemembrances(
        locale: Locale,
        limit: Int = SEARCH_RESULT_LIMIT,
    ): List<Remembrance> =
        remembrances
            .filter { it.id != null }
            .filter { searchQuery.isBlank() || notificationLabel(it, locale).contains(searchQuery, ignoreCase = true) }
            .take(limit)

    /**
     * The drop chance a passage inherits from the collections it belongs to.
     *
     * The *lowest* of them, matching [QueueSchedule.cycleOrder]: a passage in two collections should
     * not be more likely to be skipped because it was selected twice.
     */
    fun sourceDropChance(
        id: Long,
        config: ReminderQueueConfig,
    ): Int {
        val chances =
            collectionItems
                .filter { collection ->
                    collection.id in config.collectionIds && collection.members.any { it.id == id }
                }.mapNotNull { collection ->
                    collection.id?.let { config.collectionDropChances[it.toString()] ?: config.defaultDropChance }
                }
        return chances.minOrNull() ?: config.defaultDropChance
    }

    /**
     * Builds the "up next" summary from one clock reading.
     *
     * An [Instant] rather than a [LocalDateTime] so the caller reads the system clock once and hands
     * in the same reading, rather than each part of the summary deciding for itself. It is on the
     * desktop's zone and nothing else now that prayer has left this screen, so there is one clock to
     * agree about instead of two — and a traveller no longer has to work out which of two zone ids
* the interval they are reading belongs to.
     *
     * The schedule is asked about through the same two questions the runtime asks, in the same order,
     * so the preview cannot disagree with what fires. [QueueCadence.CONTINUOUS] in particular must be
     * routed to [QueueSchedule.nextContinuousSlotOnActiveDays] rather than to the windowed question:
     * routing both through the windowed one is what made a queue that never stops report its next
     * passage nineteen hours away, because the answer it was getting was tomorrow's window rather than
     * the slot a minute or two from now.
     */
    fun preview(
        instant: Instant,
        config: ReminderQueueConfig,
        progress: ReminderQueueProgress,
    ): QueuePreview {
        val ids = availableIds(config)
        val now = LocalDateTime.ofInstant(instant, zone)
        return QueuePreview(
            enabled = config.enabled,
            hasSources = ids.isNotEmpty(),
            slot =
                if (config.enabled && ids.isNotEmpty()) {
                    if (config.cadence == QueueCadence.CONTINUOUS) {
                        ContinuousSchedule.nextSlotOnActiveDays(
                            now = now,
                            intervalMinutes = config.effectiveIntervalMinutes,
                            activeDays = config.activeDays,
                        )
                    } else {
                        QueueSchedule.nextSlotOnActiveDays(
                            now = now,
                            startMinute = config.windowStartMinute,
                            endMinute = config.windowEndMinute,
                            intervalMinutes = config.effectiveIntervalMinutes,
                            activeDays = config.activeDays,
                        )
                    }
                } else {
                    null
                },
            passage = nextPassage(ids, config, progress),
        )
    }

    /**
     * The passage the next slot would show.
     *
     * Read from [ReminderQueueProgress] first, because that is what the runtime will use and the
     * preview claiming a different passage than the one about to arrive would be worse than no
     * preview. The fallback — the head of the manual sequence — is deliberately *not*
     * [QueueSchedule.cycleOrder]: that shuffles on every call for the two non-manual modes, so the
     * hero would change its mind on every recomposition until the first real delivery wrote a
     * progress record. A stable placeholder that is occasionally wrong beats a correct one that
     * flickers.
     */
    private fun nextPassage(
        ids: List<Long>,
        config: ReminderQueueConfig,
        progress: ReminderQueueProgress,
    ): Remembrance? {
        if (ids.isEmpty()) return null
        val scheduled = progress.cycleOrder.getOrNull(progress.nextIndex)?.takeIf { it in ids }
        val id = scheduled ?: manualItems(config, ids).firstOrNull()?.id
        return id?.let { candidate -> remembrances.firstOrNull { it.id == candidate } }
    }

    private companion object {
        /**
         * Bounds the passage list at composition time.
         *
         * Top-level rather than a constant on the class because [REMINDER_SEARCH_RESULT_LIMIT] is
         * also the number the card prints when it truncates, and a private companion would force
         * that card to hard-code a second copy that could drift.
         */
        const val SEARCH_RESULT_LIMIT = REMINDER_SEARCH_RESULT_LIMIT
    }
}

/**
 * How many passages the search results will ever list.
 *
 * A number rather than a scroll, and shared rather than private so the card that renders the list
 * can compare against it and say when it has hit the cap — see [ReminderManagerState.filteredRemembrances]
 * for why the list is flat.
 */
internal const val REMINDER_SEARCH_RESULT_LIMIT = 150

/**
 * The passage text as a single line, falling back through the translations and then to nothing.
 *
 * The old version ended in `"Remembrance ${remembrance.id}"` — an English sentence built by string
 * concatenation, which is the same mistake the prayer names made, and it is also why a passage with
 * no translation at all used to be listed under a name nobody had asked for. Returning blank hands
 * that case to the caller, which substitutes `reminderManagerPassageUnnamed` and can therefore say
 * it in the reader's language.
 */
internal fun notificationLabel(
    remembrance: Remembrance,
    locale: Locale,
): String {
    val texts = RemembranceI18n.resolveTexts(remembrance, locale, "", "")
    return texts.translation
        .ifBlank {
            remembrance.translations.values
                .firstOrNull()
                ?.translationPair
                ?.text
                .orEmpty()
        }.replace('\n', ' ')
        .trim()
}
