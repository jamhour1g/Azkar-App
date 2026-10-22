package com.azkar.ui.notifications

import de.infix.testBalloon.framework.core.testSuite
import org.assertj.core.api.Assertions.assertThat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

/**
 * That the scheduler announces what came due while it was running, and nothing else.
 *
 * The rule under test is one sentence: **a reminder is announced only if it became due after the
 * scheduler started.** The arithmetic of windows, intervals and weekdays is [QueueSchedule]'s and
 * [ContinuousSchedule]'s business, and it is already tested there; what was untested — and what the
 * bug was — is the question none of that arithmetic asked, which is whether the thing was already due
 * before anybody was listening.
 *
 * Without the baseline, both delivery paths were catch-up paths. The queue treats the most recent slot
 * in its window as due, so a slot that came due while the app was closed was announced the instant the
 * app opened; and a prayer stays due for two minutes after its time, so opening the app just after a
 * prayer announced that prayer. On a fresh install, where nothing had ever been delivered, that card
 * was the very first thing on screen — a notification for a reminder the reader had not asked for
 * yet, at the moment they opened the app.
 *
 * The moments in these tests are wall-clock values with no zone and no calendar behind them, and the
 * queues they use are written out in full rather than left to the defaults, so a change to the
 * shipping defaults cannot quietly change what is being asked.
 */
val DeliveryPolicySuite by testSuite("What the scheduler is allowed to announce") {
    test("a slot that came due before the scheduler started is not announced") {
        val now = LocalDateTime.parse("2026-10-14T10:37:00")
        val config = queueConfig()
        val since = LocalDateTime.parse("2026-10-14T10:30:00")

        // The slot at 10:00 is the one the schedule would have answered, and it is older than the
        // scheduler: a queue that slept through it misses it rather than replaying it.
        assertThat(DeliveryPolicy.dueQueueSlot(now = now, config = config, since = since)).isNull()
    }

    test("a slot that came due after the scheduler started is announced") {
        val now = LocalDateTime.parse("2026-10-14T10:37:00")
        val config = queueConfig()
        val since = LocalDateTime.parse("2026-10-14T09:55:00")

        assertThat(DeliveryPolicy.dueQueueSlot(now = now, config = config, since = since))
            .isEqualTo(LocalDateTime.parse("2026-10-14T10:00:00"))
    }

    test("a slot that comes due at the very moment the scheduler starts is announced") {
        val now = LocalDateTime.parse("2026-10-14T10:37:00")
        val config = queueConfig()
        val since = LocalDateTime.parse("2026-10-14T10:00:00")

        // Not "strictly after": a slot that lands exactly on the baseline is one the scheduler was
        // there for, and treating it as already-due would swallow a delivery that happened to
        // coincide with the app opening.
        assertThat(DeliveryPolicy.dueQueueSlot(now = now, config = config, since = since))
            .isEqualTo(LocalDateTime.parse("2026-10-14T10:00:00"))
    }

    test("a queue that is switched off has nothing to announce") {
        val now = LocalDateTime.parse("2026-10-14T10:37:00")
        val config = queueConfig(enabled = false)
        val since = LocalDateTime.parse("2026-10-14T08:00:00")

        assertThat(DeliveryPolicy.dueQueueSlot(now = now, config = config, since = since)).isNull()
    }

    test("a queue whose window is closed has nothing to announce") {
        val now = LocalDateTime.parse("2026-10-14T23:30:00")
        val config = queueConfig()
        val since = LocalDateTime.parse("2026-10-14T08:00:00")

        assertThat(DeliveryPolicy.dueQueueSlot(now = now, config = config, since = since)).isNull()
    }

    test("a continuous queue's slot from before the start is not announced either") {
        // The continuous cadence has no window to be outside of, so the baseline is the only thing
        // standing between it and a card at every launch.
        val now = LocalDateTime.parse("2026-10-14T10:37:00")
        val config =
            queueConfig(
                cadence = QueueCadence.CONTINUOUS,
                intervalMinutes = 60,
            )
        val since = LocalDateTime.parse("2026-10-14T10:30:00")

        assertThat(DeliveryPolicy.dueQueueSlot(now = now, config = config, since = since)).isNull()
        assertThat(
            DeliveryPolicy.dueQueueSlot(
                now = now,
                config = config,
                since = LocalDateTime.parse("2026-10-14T10:00:00"),
            ),
        ).isEqualTo(LocalDateTime.parse("2026-10-14T10:00:00"))
    }

    test("a queue that does not speak on this day has nothing to announce") {
        // The day is worked out from the weekday rather than written down, so the test cannot fall
        // over on a calendar it does not check: the Monday it lands on is not a Saturday.
        val monday = LocalDate.of(2026, 10, 14).with(TemporalAdjusters.next(DayOfWeek.MONDAY))
        val now = LocalDateTime.of(monday, LocalTime.of(10, 37))
        val config = queueConfig(activeDays = setOf(DayOfWeek.SATURDAY))
        val since = LocalDateTime.of(monday, LocalTime.of(8, 0))

        assertThat(DeliveryPolicy.dueQueueSlot(now = now, config = config, since = since)).isNull()
    }

    test("a prayer inside its grace window that came due before the start is not announced") {
        val now = LocalDateTime.parse("2026-10-14T12:26:00")
        val prayerAt = LocalTime.of(12, 25)
        val since = LocalDateTime.parse("2026-10-14T12:25:30")

        // Still inside the two-minute grace, but the prayer came due before the scheduler started.
        assertThat(
            DeliveryPolicy.isPrayerDue(
                now = now,
                prayerAt = prayerAt,
                since = since,
                graceMinutes = 2,
            ),
        ).isFalse()
    }

    test("a prayer inside its grace window that came due after the start is announced") {
        val now = LocalDateTime.parse("2026-10-14T12:26:00")
        val prayerAt = LocalTime.of(12, 25)
        val since = LocalDateTime.parse("2026-10-14T12:24:00")

        assertThat(
            DeliveryPolicy.isPrayerDue(
                now = now,
                prayerAt = prayerAt,
                since = since,
                graceMinutes = 2,
            ),
        ).isTrue()
    }

    test("a prayer is announced exactly at its time, and not a moment before") {
        val since = LocalDateTime.parse("2026-10-14T00:00:00")

        assertThat(
            DeliveryPolicy.isPrayerDue(
                now = LocalDateTime.parse("2026-10-14T12:25:00"),
                prayerAt = LocalTime.of(12, 25),
                since = since,
                graceMinutes = 2,
            ),
        ).isTrue()
        assertThat(
            DeliveryPolicy.isPrayerDue(
                now = LocalDateTime.parse("2026-10-14T12:24:59"),
                prayerAt = LocalTime.of(12, 25),
                since = since,
                graceMinutes = 2,
            ),
        ).isFalse()
    }

    test("a prayer is announced up to the end of its grace window, and not after it") {
        val since = LocalDateTime.parse("2026-10-14T00:00:00")

        // The end of the window is inside it. A prayer is due from its own time until two minutes
        // after it, and a poll that lands exactly on the boundary is a poll that was there for it.
        assertThat(
            DeliveryPolicy.isPrayerDue(
                now = LocalDateTime.parse("2026-10-14T12:27:00"),
                prayerAt = LocalTime.of(12, 25),
                since = since,
                graceMinutes = 2,
            ),
        ).isTrue()
        assertThat(
            DeliveryPolicy.isPrayerDue(
                now = LocalDateTime.parse("2026-10-14T12:28:00"),
                prayerAt = LocalTime.of(12, 25),
                since = since,
                graceMinutes = 2,
            ),
        ).isFalse()
    }

    test("a prayer that has not happened yet is not announced") {
        val since = LocalDateTime.parse("2026-10-14T00:00:00")

        assertThat(
            DeliveryPolicy.isPrayerDue(
                now = LocalDateTime.parse("2026-10-14T12:00:00"),
                prayerAt = LocalTime.of(12, 25),
                since = since,
                graceMinutes = 2,
            ),
        ).isFalse()
    }
}

/**
 * The queue these tests ask about: a window from eight in the morning to ten at night, and a
 * reminder every hour.
 *
 * The window and the interval are written out rather than left to [ReminderQueueConfig]'s defaults,
 * so that the slot these tests talk about is the slot they mean — a change to the shipping defaults
 * is a change to the app, not to this question.
 */
private fun queueConfig(
    enabled: Boolean = true,
    cadence: QueueCadence = QueueCadence.INTERVAL,
    intervalMinutes: Int = 60,
    activeDays: Set<DayOfWeek> = ReminderQueueConfig.ALL_DAYS,
): ReminderQueueConfig =
    ReminderQueueConfig(
        enabled = enabled,
        cadence = cadence,
        intervalMinutes = intervalMinutes,
        activeDays = activeDays,
        windowStartMinute = 8 * 60,
        windowEndMinute = 22 * 60,
    )
