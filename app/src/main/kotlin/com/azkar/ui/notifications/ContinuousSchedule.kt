package com.azkar.ui.notifications

import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * The slots of a queue that never closes.
 *
 * A separate object from [QueueSchedule] rather than three more functions on it, and the split is the
 * shape of the difference rather than a workaround for a file getting long. [QueueSchedule] answers
 * questions about a *window* — which slot is now, which is next, which day may open — and every one
 * of those can be `null`, because there is an hour of the day the queue is shut. None of the questions
 * here can be: a queue on this cadence is always running, so "is there a slot?" is not a question that
 * gets asked, and adding `startMinute`/`endMinute` arguments that the implementation would have to
 * ignore is how a schedule that never stops ends up stopping at half past ten.
 *
 * Which is the bug this object exists to make impossible. The continuous cadence used to be computed by
 * the same windowed arithmetic with a smaller interval, so a queue the user had set to keep going went
 * quiet the moment its window closed and the summary card reported the next passage nineteen hours
 * away — which is tomorrow's window, not the slot a minute from now.
 */
object ContinuousSchedule {
    /**
     * How far [nextSlotOnActiveDays] will look for the next allowed weekday.
     *
     * Seven rather than "until found": any non-empty day set contains at least one of the next seven
     * days, so the bound can only be reached by an empty set, which the config rejects.
     */
    private const val DAYS_IN_WEEK = 7

    /**
     * The instant the current day's slots began.
     *
     * Anchored to midnight rather than to a rolling day, so a queue running every five minutes keeps
     * the same slot minutes at 3am as it does at noon. A rolling anchor would move the whole schedule
     * back to zero every midnight, and every notice would arrive on a different minute of the hour from
     * the one the summary card had just promised.
     */
    private fun dayStart(now: LocalDateTime): LocalDateTime = now.toLocalDate().atStartOfDay()

    /**
     * The most recent slot at [now]. Never null — there is no window to be outside of.
     */
    fun dueSlot(
        now: LocalDateTime,
        intervalMinutes: Int,
    ): LocalDateTime {
        require(intervalMinutes > 0) { "intervalMinutes must be positive, was $intervalMinutes" }
        val anchor = dayStart(now)
        val elapsed = ChronoUnit.MINUTES.between(anchor, now)
        return anchor.plusMinutes((elapsed / intervalMinutes) * intervalMinutes)
    }

    /** The first slot strictly after [now]. Never null — a continuous queue is never done. */
    fun nextSlot(
        now: LocalDateTime,
        intervalMinutes: Int,
    ): LocalDateTime = dueSlot(now, intervalMinutes).plusMinutes(intervalMinutes.toLong())

    /**
     * The first slot on a day the queue is allowed to speak on.
     *
     * The counterpart to [QueueSchedule.nextSlotOnActiveDays], and the reason the two disagree about
     * what a spent day means. For the windowed queue, today's window closing ends today's answer and
     * the search moves on. Here it does not — the queue is still going, so the search simply asks about
     * tomorrow and carries on.
     */
    fun nextSlotOnActiveDays(
        now: LocalDateTime,
        intervalMinutes: Int,
        activeDays: Set<DayOfWeek>,
    ): LocalDateTime {
        val today = now.toLocalDate()
        return (0 until DAYS_IN_WEEK)
            .map { offset -> today.plusDays(offset.toLong()) }
            .first { day -> day.dayOfWeek in activeDays }
            .let { day ->
                if (day == today) nextSlot(now, intervalMinutes) else day.atStartOfDay()
            }
    }
}
