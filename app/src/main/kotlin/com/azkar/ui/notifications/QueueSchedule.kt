package com.azkar.ui.notifications

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/** Pure queue ordering and time-window calculations, kept independent of Compose and storage. */
object QueueSchedule {
    /**
     * How far [nextSlotOnActiveDays] will look for the next allowed weekday.
     *
     * Seven rather than "until found": any non-empty day set contains at least one of the next seven
     * days, so the bound can only be reached by an empty set, which is rejected at the top.
     */
    private const val DAYS_IN_WEEK = 7

    /**
     * Whether [at] falls on a day the queue is allowed to open its window on.
     *
     * Separate from [dueSlot] rather than a parameter on it, because the two answer different
     * questions. [dueSlot] asks "which slot in the window is now?", and a day the queue does not run
     * on is not a moment inside any window — it is a day the question does not apply to. Folding the
     * check in would mean every caller repeating the guard, and the one caller that forgot would
     * deliver a Monday schedule on a Saturday.
     *
     * An empty [activeDays] answers `false` rather than "every day". The config rejects an empty set,
     * so the only way to reach it is a value written before the field existed, and silencing such a
     * queue is the safe reading of "I could not tell which days this meant".
     */
    fun isDayActive(
        at: LocalDateTime,
        activeDays: Set<DayOfWeek>,
    ): Boolean = at.dayOfWeek in activeDays

    /**
     * The most recent interval slot in the active window, or null outside it.
     * A window may cross midnight; a missed slot coalesces to the latest slot rather than replaying
     * a burst of notifications when the computer wakes.
     */
    fun dueSlot(
        now: LocalDateTime,
        startMinute: Int,
        endMinute: Int,
        intervalMinutes: Int,
    ): LocalDateTime? {
        requireWindow(startMinute, endMinute, intervalMinutes)

        val start = windowStart(now, startMinute, endMinute)
        val end = windowEnd(start, startMinute, endMinute)
        if (now.isBefore(start) || !now.isBefore(end)) return null

        val elapsedMinutes = ChronoUnit.MINUTES.between(start, now)
        return start.plusMinutes((elapsedMinutes / intervalMinutes) * intervalMinutes)
    }

    /**
     * The first interval slot strictly after [now], or null when the window is closed for the day.
     *
     * The mirror of [dueSlot], and kept beside it for one reason: the reminders screen opens on
     * "when is the next one?", and the only answer that cannot drift from what
     * [NotificationRuntime] fires is one derived from the same window arithmetic. Re-deriving it on
     * the settings screen would be a second implementation of the same rule, free to disagree.
     *
     * Null rather than "tomorrow morning" when the window has run out. The runtime never opens a
     * window that has closed, so a preview claiming a delivery a day away would be reporting a
     * schedule that does not exist.
     */
    fun nextSlot(
        now: LocalDateTime,
        startMinute: Int,
        endMinute: Int,
        intervalMinutes: Int,
    ): LocalDateTime? {
        requireWindow(startMinute, endMinute, intervalMinutes)

        val start = windowStart(now, startMinute, endMinute)
        val end = windowEnd(start, startMinute, endMinute)
        val due = dueSlot(now, startMinute, endMinute, intervalMinutes)
        return when {
            due != null -> due.plusMinutes(intervalMinutes.toLong()).takeIf { it.isBefore(end) }
            now.isBefore(start) -> start
            else -> null
        }
    }

    /**
     * The first slot the queue would reach on a day it is allowed to run, or null when it never runs.
     *
     * [nextSlot] answers the question for a day that is open. This one answers it for a queue that
     * only opens on some days, which is the difference between "the next reminder is at 4:00 PM" and
     * "the next reminder is on Monday at 4:00 PM" — and the reminders screen's summary has to be
     * able to say the second one, because [NotificationRuntime] gates every delivery on
     * [isDayActive] and a preview that ignored the gate would promise a Saturday delivery for a
     * Monday-only queue.
     *
     * Today is probed with [nextSlot], so a queue whose window has already closed for the day falls
     * through to the next active day rather than reporting nothing. Later days are answered directly
     * as their window's opening time: a window that crosses midnight belongs to the day it *opens*
     * on, so asking [nextSlot] about tomorrow would have it measure from tonight's window — a slot
     * already in the past, which is worse than the bug this replaces.
     *
     * Null only when [activeDays] is empty, which [ReminderQueueConfig] forbids and
     * [NotificationPreferencesRepository.validated] repairs; the bound search is the seven days that
     * contain one occurrence of every weekday.
     */
    fun nextSlotOnActiveDays(
        now: LocalDateTime,
        startMinute: Int,
        endMinute: Int,
        intervalMinutes: Int,
        activeDays: Set<DayOfWeek>,
    ): LocalDateTime? {
        val today = now.toLocalDate()
        // firstNotNullOfOrNull rather than firstOrNull, because today's window being spent is not the
        // end of the search — it is one answer among the days still to come, and taking only the first
        // allowed day would report nothing at all on the evening a queue has finished for the day.
        return (0 until DAYS_IN_WEEK)
            .map { offset -> today.plusDays(offset.toLong()) }
            .firstNotNullOfOrNull { day ->
                when {
                    day.dayOfWeek !in activeDays -> null
                    day == today -> nextSlot(now, startMinute, endMinute, intervalMinutes)
                    else -> day.atTime(atTime(startMinute))
                }
            }
    }

    /**
     * The arguments every entry point shares, checked once so a bad window fails the same way
     * whichever function it arrives through. The runtime is the only caller that can crash here in
     * anger, and a [java.time.DateTimeException] from deep inside the arithmetic would not say which of
     * the three numbers was wrong.
     */
    private fun requireWindow(
        startMinute: Int,
        endMinute: Int,
        intervalMinutes: Int,
    ) {
        require(startMinute in 0..1439) { "startMinute must be within a day, was $startMinute" }
        require(endMinute in 0..1439 && startMinute != endMinute) {
            "endMinute must be within a day and differ from $startMinute, was $endMinute"
        }
        require(intervalMinutes > 0) { "intervalMinutes must be positive, was $intervalMinutes" }
    }

    /**
     * The instant the window containing [now] opened.
     *
     * A window that crosses midnight opened on the previous day, so the date the start belongs to
     * is not [now]'s own date — and [now] can legitimately sit *before* the result, in the gap
     * between last night's close and this evening's open. That gap is the case [nextSlot] needs to
     * answer, which is why the start is computed rather than assumed.
     */
    private fun windowStart(
        now: LocalDateTime,
        startMinute: Int,
        endMinute: Int,
    ): LocalDateTime {
        val crossesMidnight = endMinute < startMinute
        val startDate =
            when {
                !crossesMidnight -> now.toLocalDate()
                minuteOfDay(now) < endMinute -> now.toLocalDate().minusDays(1)
                else -> now.toLocalDate()
            }
        return startDate.atTime(atTime(startMinute))
    }

    private fun windowEnd(
        start: LocalDateTime,
        startMinute: Int,
        endMinute: Int,
    ): LocalDateTime {
        val days = if (endMinute < startMinute) 1 else 0
        val endDate: LocalDate = start.toLocalDate().plusDays(days.toLong())
        return endDate.atTime(atTime(endMinute))
    }

    private fun minuteOfDay(at: LocalDateTime): Int = at.toLocalTime().hour * 60 + at.toLocalTime().minute

    private fun atTime(minuteOfDay: Int): LocalTime = LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)

    /**
     * Prepares one cycle.
     *
     * [QueueMode.MANUAL] keeps the order it was given, which is the library's own order: the
     * collections the user arranged, and within each of them the passages the collection holds.
     * Weighted mode uses the lowest configured source drop chance for an item present in
     * overlapping collections, which prevents overlap from multiplying an item's influence — a
     * higher drop chance only makes it more likely to be skipped.
     */
    fun cycleOrder(
        itemIds: List<Long>,
        mode: QueueMode,
        dropChance: (Long) -> Int,
        random: kotlin.random.Random = kotlin.random.Random.Default,
    ): List<Long> {
        val unique = itemIds.distinct()
        if (unique.isEmpty()) return emptyList()
        return when (mode) {
            QueueMode.MANUAL -> unique

            QueueMode.RANDOM -> unique.shuffled(random)

            QueueMode.WEIGHTED -> {
                val included = unique.filter { random.nextInt(100) >= dropChance(it).coerceIn(0, 100) }
                included
            }
        }
    }
}
