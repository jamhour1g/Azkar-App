package com.azkar.domain.service

import com.azkar.domain.exception.CollectionNotFoundException
import com.azkar.domain.model.CollectionReminder
import com.azkar.domain.model.ReminderCadence
import com.azkar.domain.repo.CollectionReminderRepository
import com.azkar.domain.repo.CollectionRepository
import java.time.DayOfWeek
import java.time.Instant

/**
 * The application's entry point for collection reminders.
 *
 * Mirrors the other services — reads delegate to [reminders], writes are wrapped in [transactions] —
 * with one rule that is about the domain rather than about storage: a reminder requires its
 * collection to exist.
 *
 * The foreign key already says that, but a check here fails with a
 * [CollectionNotFoundException] naming the collection instead of a constraint violation naming a
 * constraint. It matters more here than it does for progress, because a reminder outlives the
 * screen that set it: a stale id would otherwise leave a schedule ticking away for a collection
 * that is not there, and the user would be nudged about a shelf they cannot open.
 */
class CollectionReminderService(
    private val reminders: CollectionReminderRepository,
    private val collections: CollectionRepository,
    private val transactions: TransactionRunner,
) {
    /** Returns every reminder, paused ones included. */
    suspend fun findAll(): List<CollectionReminder> = reminders.findAll()

    /** Returns the reminder for [id], or `null` when the user has not asked for one. */
    suspend fun findByCollection(id: Long): CollectionReminder? = reminders.findByCollection(id)

    /**
     * Sets the schedule for [id], creating it or replacing what was there.
     *
     * Two things are carried over from any existing row rather than taken from the arguments,
     * because both would be wrong to reset here:
     *
     * - [CollectionReminder.enabled], so saving a schedule is not how a paused reminder is
     *   accidentally resumed. [setEnabled] is the only thing that moves it.
     * - [CollectionReminder.lastTriggeredAt], so nudging the time does not re-arm a reminder that
     *   has already fired today. Moving a 20:00 reminder to 07:00 at 21:00 leaves the 20:00
     *   acknowledgement in place, and the shortened schedule waits for tomorrow.
     */
    suspend fun save(
        id: Long,
        cadence: ReminderCadence,
        minuteOfDay: Int,
        dayOfWeek: DayOfWeek? = null,
    ): CollectionReminder =
        transactions.run {
            require(id)
            val existing = reminders.findByCollection(id)
            reminders.save(
                CollectionReminder(
                    collectionId = id,
                    cadence = cadence,
                    minuteOfDay = minuteOfDay,
                    dayOfWeek = dayOfWeek,
                    enabled = existing?.enabled ?: true,
                    lastTriggeredAt = existing?.lastTriggeredAt,
                ),
            )
        }

    /**
     * Pauses or resumes [id]'s reminder, leaving the schedule alone.
     *
     * A no-op write is skipped, so a switch that is already where it was asked to be does not
     * rewrite the row and lose the moment the schedule was last edited.
     */
    suspend fun setEnabled(
        id: Long,
        enabled: Boolean,
    ) {
        transactions.run {
            require(id)
            val existing = reminders.findByCollection(id) ?: return@run
            if (existing.enabled != enabled) {
                reminders.save(existing.copy(enabled = enabled))
            }
        }
    }

    /** Removes [id]'s reminder entirely, which is what "no reminder" means on disk. */
    suspend fun delete(id: Long) {
        transactions.run {
            require(id)
            reminders.deleteByCollection(id)
        }
    }

    /**
     * Records that [id]'s reminder fired at [at], so the next tick stops reporting it as due.
     *
     * The service does not decide whether a firing was warranted: that is
     * [com.azkar.domain.model.ReminderSchedule]'s job, and the caller has already asked it. What is
     * checked here is only that the collection is still there, because a firing for a collection
     * that has been deleted is a schedule that would never stop repeating.
     */
    suspend fun markTriggered(
        id: Long,
        at: Instant,
    ) {
        transactions.run {
            require(id)
            reminders.markTriggered(id, at)
        }
    }

    /** Loads [id] or fails loudly, for the same reason as the other services' own check. */
    private suspend fun require(id: Long) {
        if (collections.findById(id) == null) throw CollectionNotFoundException(id)
    }
}
