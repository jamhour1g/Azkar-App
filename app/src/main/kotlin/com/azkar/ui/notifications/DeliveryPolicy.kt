package com.azkar.ui.notifications

import java.time.LocalDateTime
import java.time.LocalTime

/**
 * What the scheduler is allowed to announce, and when.
 *
 * The rule this object holds is one sentence long: **a reminder is announced only if it became due
 * after the scheduler started.** The window, the cadence, the weekday and the grace period are all
 * arithmetic that [QueueSchedule] and [ContinuousSchedule] already own; what none of them asks is
 * *was this already due before anybody was listening?*
 *
 * That question exists because of the bug this object was written for. The scheduler's first tick
 * runs the moment the app opens, and both of its delivery paths are catch-up paths: the queue
 * treats the most recent slot in its window as due, and a prayer stays due for two minutes after its
 * time. So a slot — or a prayer — that came due while the app was closed was announced the instant
 * the app opened, and on a fresh install, where nothing had ever been delivered, that card was the
 * very first thing on screen: a notification for a reminder the reader had not asked for yet, at the
 * moment they opened the app. A notification that arrives before the reader has decided they want
 * notifications is the one kind of notification that reads as a fault rather than as a reminder.
 *
 * So the scheduler remembers when it started, and declines to announce anything older. What came due
 * while the app was closed is missed rather than replayed — the same answer the schedule already
 * gives a slot it slept through.
 *
 * Pure, like [QueueSchedule] and [QueueSources] beside it: it decides, and it does not deliver.
 */
object DeliveryPolicy {
    /**
     * The queue slot to announce at [now], or null when there is nothing to announce.
     *
     * Null covers every reason there is nothing to say: the queue is off, the day is not one the
     * queue speaks on, the window is closed, and — the reason this object exists — the slot came due
     * before [since].
     *
     * [since] and [now] must be in the same zone: they are compared as local times, and a queue's
     * window is a wall-clock window rather than an absolute one.
     */
    fun dueQueueSlot(
        now: LocalDateTime,
        config: ReminderQueueConfig,
        since: LocalDateTime,
    ): LocalDateTime? =
        when {
            !config.enabled -> null
            !QueueSchedule.isDayActive(now, config.activeDays) -> null
            else -> {
                val interval = config.effectiveIntervalMinutes
                val slot =
                    when (config.cadence) {
                        QueueCadence.CONTINUOUS ->
                            ContinuousSchedule.dueSlot(
                                now = now,
                                intervalMinutes = interval,
                            )

                        QueueCadence.INTERVAL ->
                            QueueSchedule.dueSlot(
                                now = now,
                                startMinute = config.windowStartMinute,
                                endMinute = config.windowEndMinute,
                                intervalMinutes = interval,
                            )
                    }
                // `isBefore` rather than `isAfter`: a slot that comes due at the very moment the
                // scheduler started is one the scheduler was there for.
                slot?.takeIf { !it.isBefore(since) }
            }
        }

    /**
     * Whether the prayer at [prayerAt] is due at [now].
     *
     * Two windows have to agree. The first is the grace window: a prayer is due from its own time
     * until [graceMinutes] after it, which is what lets a thirty-second poll still catch it. The
     * second is [since]: the prayer must be one that came due after the scheduler started, for the
     * same reason a queue slot must be.
     *
     * [now], [since] and [prayerAt] are all local times in the prayer location's own zone.
     */
    fun isPrayerDue(
        now: LocalDateTime,
        prayerAt: LocalTime,
        since: LocalDateTime,
        graceMinutes: Long,
    ): Boolean {
        val at = now.toLocalDate().atTime(prayerAt)
        val withinGrace = !now.isBefore(at) && !now.isAfter(at.plusMinutes(graceMinutes))
        return withinGrace && !at.isBefore(since)
    }
}
