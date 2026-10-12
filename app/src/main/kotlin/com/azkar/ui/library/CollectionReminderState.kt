@file:OptIn(ExperimentalAtomicApi::class)

package com.azkar.ui.library

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.azkar.domain.model.CollectionReminder
import com.azkar.domain.model.ReminderCadence
import com.azkar.domain.model.ReminderSchedule
import com.azkar.ui.library.data.CollectionReminderGateway
import com.azkar.ui.library.data.CollectionReminderRow
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch

/**
 * A reminder that came round, in the terms an announcement needs.
 *
 * Carries the name and the size rather than the schedule, because the announcement is about the
 * collection — "time for Morning Azkar, 52 passages" — and only the row knows what it is called.
 */
data class FiredReminder(
    val collectionId: Long,
    val collectionName: String,
    val memberCount: Int,
)

/**
 * The schedules attached to collections, and the tick that fires them.
 *
 * Lives in the library rather than in the shell because it is the same kind of thing as
 * [CollectionsState]: something the user changes, read through a gateway and held in snapshot state
 * so the screens behind it recompose. What is different is that this one is asked *when* anything is
 * due, because a reminder has to come round wherever the user happens to be.
 *
 * The tick **returns** what fired rather than announcing it. The words a reminder is announced with
 * come from the user's language, which only something that can read resources knows, so the decision
 * of *what* is due stays here and the decision of *how to say it* stays with the caller. That is also
 * what makes the tick testable: a test asserts on the returned list.
 *
 * @param zone the machine's own zone. A time of day is not a moment until a zone is attached, and
 * the zone belongs to the machine rather than to the reminder, so it is never stored and always read
 * from here.
 */
@Stable
class CollectionReminderState(
    private val scope: CoroutineScope,
    private val gateway: CollectionReminderGateway,
    val zone: ZoneId = ZoneId.systemDefault(),
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Whether two reads may both be in flight without the older one landing last.
     *
     * The same device as [CollectionsState]'s: a screen that loads, is rebuilt and loads again would
     * otherwise be shown the schedules from before the rebuild.
     */
    private val loadVersion = AtomicLong(0L)

    /**
     * Every schedule, keyed by the collection it belongs to.
     *
     * Exposed as the rows the gateway assembles rather than as bare schedules so the reminder row on
     * a collection page can name the collection and count its passages without a second read.
     */
    var rows: Map<Long, CollectionReminderRow> by mutableStateOf(emptyMap())
        private set

    /**
     * The collections whose reminder has come round and has not been dealt with yet.
     *
     * In memory, and deliberately so. It is the badge on the Home rail, and the badge is a claim
     * about this session: once the reminder has been announced and the user has opened the
     * collection, there is nothing left for it to say. Writing it down instead would mean a badge
     * that outlived the reason for it, and a column duplicating what `last_triggered_at` already
     * records.
     */
    var dueCollections: Set<Long> by mutableStateOf(emptySet())
        private set

    /** The schedule for one collection, or `null` when it has none. */
    fun forCollection(collectionId: Long): CollectionReminderRow? = rows[collectionId]

    /**
     * When this collection's reminder next comes round, or `null` when it has none or is paused.
     *
     * A [LocalDateTime] rather than an [Instant] because the caller is a screen that wants to print the
     * time, and handing it a moment would only make it convert back to something it started with.
     * The paused case comes back as `null` rather than as "never" because a paused reminder has no
     * next time at all, and a screen asking what is next deserves to be told there is nothing.
     */
    fun nextTriggerAt(
        collectionId: Long,
        now: LocalDateTime,
    ): LocalDateTime? =
        rows[collectionId]
            ?.reminder
            ?.let { ReminderSchedule.nextTriggerAfter(it, now, zone) }
            ?.let { LocalDateTime.ofInstant(it, zone) }

    /** Reads every schedule, discarding the result of any read a newer one has overtaken. */
    fun load() {
        val version = loadVersion.incrementAndFetch()
        scope.launch {
            runCatching { gateway.fetchReminders() }
                .onSuccess { found ->
                    if (version != loadVersion.load()) return@onSuccess
                    rows = found.associateBy { it.reminder.collectionId }
                    dueCollections = dueCollections.intersect(rows.keys)
                }.onFailure { failure ->
                    logger.error(failure) { "Unable to read the reminder schedules" }
                }
        }
    }

    /**
     * Records that a reminder came round at [now], and reports which ones did.
     *
     * Due-ness is judged against the last time each schedule came round rather than against the
     * clock, which is what makes a tick that lands twice in the same second harmless: the first one
     * advances the stored time and the second finds nothing owed.
     *
     * The stored time is advanced *before* the write is attempted, and is not put back when the write
     * fails. That is the one place in this state that does not roll back, and it is deliberate. The
     * announcement is the part that matters; the acknowledgement is bookkeeping, and a bookkeeping
     * failure is not worth re-announcing the same reminder every thirty seconds until the database
     * comes back. What it costs is that the reminder may come round twice on one day.
     */
    suspend fun tick(now: Instant): List<FiredReminder> {
        val moment = LocalDateTime.ofInstant(now, zone)
        val owed = rows.values.filter { ReminderSchedule.isDue(it.reminder, moment, zone) }
        if (owed.isEmpty()) return emptyList()

        val fired = mutableListOf<FiredReminder>()
        for (row in owed) {
            val id = row.reminder.collectionId
            rows = rows + (id to row.copy(reminder = row.reminder.copy(lastTriggeredAt = now)))
            dueCollections = dueCollections + id
            runCatching { gateway.markReminderTriggered(id, now) }
                .onFailure { failure ->
                    logger.warn(failure) { "Reminder for collection $id fired but could not be acknowledged" }
                }
            fired +=
                FiredReminder(
                    collectionId = id,
                    collectionName = row.collectionName,
                    memberCount = row.memberCount,
                )
        }
        return fired
    }

    /**
     * Sets the time and the cadence [collectionId] comes round at.
     *
     * The row appears the moment the user confirms rather than when the write lands, because a
     * reminder they have just set up should not need a re-read to show up. What shows up meanwhile
     * is the one they chose, with the paused flag and the last firing carried over from whatever was
     * there — the same two things the service preserves, so the row on screen and the row on disk
     * never disagree about whether it is running.
     */
    fun save(
        collectionId: Long,
        cadence: ReminderCadence,
        minuteOfDay: Int,
        dayOfWeek: DayOfWeek?,
        collectionName: String = "",
        memberCount: Int = 0,
        onSuccess: () -> Unit = {},
        onFailure: () -> Unit = {},
    ) {
        val existing = rows[collectionId]?.reminder
        val provisional =
            CollectionReminderRow(
                reminder =
                    CollectionReminder(
                        collectionId = collectionId,
                        cadence = cadence,
                        minuteOfDay = minuteOfDay,
                        dayOfWeek = dayOfWeek,
                        enabled = existing?.enabled ?: true,
                        lastTriggeredAt = existing?.lastTriggeredAt,
                    ),
                collectionName = collectionName,
                memberCount = memberCount,
            )
        rows = rows + (collectionId to provisional)
        scope.launch {
            runCatching {
                gateway.saveReminder(
                    collectionId = collectionId,
                    cadence = cadence,
                    minuteOfDay = minuteOfDay,
                    dayOfWeek = dayOfWeek,
                )
            }.onSuccess { stored ->
                rows = rows + (collectionId to stored)
                onSuccess()
            }.onFailure { failure ->
                logger.error(failure) { "Unable to set the reminder for collection $collectionId" }
                if (rows[collectionId]?.reminder == provisional.reminder) {
                    rows = rows - collectionId
                }
                onFailure()
            }
        }
    }

    /**
     * Pauses or resumes a schedule.
     *
     * Applied locally first so the switch moves under the user's finger, and put back when the write
     * fails — the opposite of the tick's rule, because here nothing has been announced yet and a
     * switch that snapped back would read as the app having ignored the click.
     */
    fun setEnabled(
        collectionId: Long,
        enabled: Boolean,
        onFailure: () -> Unit = {},
    ) {
        val before = rows[collectionId] ?: return
        if (before.reminder.enabled == enabled) return
        rows = rows + (collectionId to before.copy(reminder = before.reminder.copy(enabled = enabled)))
        scope.launch {
            runCatching { gateway.setReminderEnabled(collectionId, enabled) }
                .onSuccess { stored ->
                    rows = rows + (collectionId to stored)
                }.onFailure { failure ->
                    logger.error(failure) { "Unable to pause or resume the reminder for collection $collectionId" }
                    rows = rows + (collectionId to before)
                    onFailure()
                }
        }
    }

    /**
     * Forgets the schedule for [collectionId].
     *
     * Drops the row first rather than waiting for the write: the user asked for the reminder to stop,
     * and leaving it on screen for as long as a write takes would be the app contradicting them in
     * the one place they can see the answer. A failed removal puts it back by re-reading, because
     * guessing what the database still holds is worse than asking.
     */
    fun remove(
        collectionId: Long,
        onSuccess: () -> Unit = {},
        onFailure: () -> Unit = {},
    ) {
        if (!rows.containsKey(collectionId)) return
        rows = rows - collectionId
        dueCollections = dueCollections - collectionId
        scope.launch {
            runCatching { gateway.deleteReminder(collectionId) }
                .onSuccess { onSuccess() }
                .onFailure { failure ->
                    logger.error(failure) { "Unable to remove the reminder for collection $collectionId" }
                    load()
                    onFailure()
                }
        }
    }

    /**
     * Marks a collection's due badge as dealt with.
     *
     * Called when the collection is opened, which is what the badge was asking for. Reading the
     * collection is the answer to "your reminder came round", whether or not anything is recited
     * while the user is there.
     */
    fun acknowledge(collectionId: Long) {
        if (!dueCollections.contains(collectionId)) return
        dueCollections = dueCollections - collectionId
    }
}
