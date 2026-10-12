package com.azkar.domain.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * When a reminder comes round, worked out with no clock and no database.
 *
 * The app ticks a few times a minute and asks every enabled reminder whether it is due. All the
 * arithmetic that decides the answer lives here, as plain functions over values, because this is
 * the part of a scheduler that is worth being able to test: a reminder that fires twice, or not at
 * all, is a bug a user notices at six in the morning and nothing else would catch.
 *
 * Two questions are asked of the same reminder, and the difference between them is the whole design:
 *
 * - [mostRecentTrigger] is the last moment the schedule came round at or before now. Due-ness is
 *   "that moment has happened and I have not acknowledged it since", which is what makes firing
 *   idempotent. A thirty-second tick asks the question thirty times a minute and gets the same
 *   answer until something records the firing.
 * - [nextTriggerAfter] is the first moment still to come, which is what the settings screen shows
 *   and what makes "tomorrow at 06:00" honest rather than a restatement of the stored fields.
 *
 * Both are computed by scanning candidate days rather than by date arithmetic. A scan of a week is
 * a few comparisons, it is obviously right at the boundaries — a month end, a leap day, the Monday
 * after a schedule set on Sunday — and it cannot be subtly wrong in the way an offset calculation
 * can.
 */
object ReminderSchedule {
    /**
     * How far either scan looks.
     *
     * A weekly reminder is at most seven days from its next firing, so eight candidate days always
     * contains one: the current day plus the seven that follow it. Generous by a day on purpose —
     * being one day short would turn "no upcoming reminder" into a claim that is merely usually
     * right.
     */
    private const val SEARCH_DAYS = 8

    /**
     * The first moment [reminder] comes round strictly after [now], or `null` when it is paused.
     *
     * "Strictly after" rather than "at or before" so that asking twice at the same instant cannot
     * return the same moment twice; [mostRecentTrigger] is the one that owns "at or before".
     */
    fun nextTriggerAfter(
        reminder: CollectionReminder,
        now: LocalDateTime,
        zone: ZoneId,
    ): Instant? {
        if (!reminder.enabled) return null
        return daysFrom(now.toLocalDate(), step = 1)
            .map { LocalDateTime.of(it, reminder.timeOfDay) }
            .firstOrNull { it.isAfter(now) && fallsOn(reminder, it.toLocalDate()) }
            ?.atZone(zone)
            ?.toInstant()
    }

    /**
     * The last moment [reminder] came round at or before [now], or `null` when it is paused or has
     * not come round yet.
     *
     * A schedule that has never fired in the window the scan covers — a weekly reminder set on a
     * Sunday, asked on the Sunday eight days later — reports nothing, which reads as "not due" and
     * is the honest answer: that moment is in the past but outside the schedule.
     */
    fun mostRecentTrigger(
        reminder: CollectionReminder,
        now: LocalDateTime,
        zone: ZoneId,
    ): Instant? {
        if (!reminder.enabled) return null
        return daysFrom(now.toLocalDate(), step = -1)
            .map { LocalDateTime.of(it, reminder.timeOfDay) }
            .firstOrNull { !it.isAfter(now) && fallsOn(reminder, it.toLocalDate()) }
            ?.atZone(zone)
            ?.toInstant()
    }

    /**
     * Whether [reminder] is due now — its last firing has passed and has not been acknowledged.
     *
     * Acknowledging it means storing a [CollectionReminder.lastTriggeredAt] at or after that
     * moment, which is what [CollectionReminderService.markTriggered] does. Comparing against the
     * last firing rather than against now is what keeps a reminder from firing again on the next
     * tick, and it also means a reminder set for a time that has already passed today is owed
     * straight away: the last firing it can see is in the past, and nothing has acknowledged it, so
     * the first tick after creation does fire it. That is the intended reading — "you asked to be
     * reminded at six and it is now seven" — and it is what makes a schedule feel responsive the
     * moment it is set rather than silent until tomorrow.
     */
    fun isDue(
        reminder: CollectionReminder,
        now: LocalDateTime,
        zone: ZoneId,
    ): Boolean {
        // No separate check for a paused reminder: [mostRecentTrigger] reports nothing for one, and
        // a schedule that is paused is one that has not come round.
        val due = mostRecentTrigger(reminder, now, zone) ?: return false
        val acknowledged = reminder.lastTriggeredAt
        return acknowledged == null || acknowledged.isBefore(due)
    }

    /**
     * Every reminder in [reminders] that is due at [now], in the order they were given.
     *
     * The order is the caller's rather than a sort by name or by collection, because the caller
     * already holds the list it wants them shown in and re-ordering it here would be one more place
     * for two screens to disagree.
     */
    fun dueReminders(
        reminders: List<CollectionReminder>,
        now: LocalDateTime,
        zone: ZoneId,
    ): List<CollectionReminder> = reminders.filter { isDue(it, now, zone) }

    /**
     * [start] and the days either side of it, at most [SEARCH_DAYS] of them.
     *
     * A lazy sequence rather than a loop, so both scans above read as the one thing they are: a walk
     * over candidate days that stops at the first the cadence accepts. The bound is what stops a
     * schedule whose day can never arrive — the only such case is a weekly reminder naming a day,
     * and [CollectionReminder] requires that day to be a real one — from walking forever.
     */
    private fun daysFrom(
        start: LocalDate,
        step: Long,
    ): Sequence<LocalDate> = generateSequence(start) { it.plusDays(step) }.take(SEARCH_DAYS)

    /**
     * Whether the schedule comes round on [day] at all, ignoring the time.
     *
     * The one place a cadence is interpreted, so "what does WEEKDAYS mean" has a single answer
     * rather than one per scan direction.
     */
    private fun fallsOn(
        reminder: CollectionReminder,
        day: LocalDate,
    ): Boolean =
        when (reminder.cadence) {
            ReminderCadence.DAILY -> true

            // DayOfWeek numbers Monday as 1, so the comparison reads as the thing it means: the
            // first five days of the week.
            ReminderCadence.WEEKDAYS -> day.dayOfWeek.value <= DayOfWeek.FRIDAY.value

            ReminderCadence.WEEKLY -> day.dayOfWeek == reminder.dayOfWeek
        }
}
