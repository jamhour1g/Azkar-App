package com.azkar.domain.model

import de.infix.testBalloon.framework.core.testSuite
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * When a reminder comes round.
 *
 * The centre of these tests is [ReminderSchedule.isDue] answering the same question twice, because
 * that is the property the whole feature rests on: a tick runs every half minute for as long as the
 * app is open, and a reminder that answered yes twice would notify twice. Everything else here is a
 * way of pinning down which moments a cadence actually lands on.
 *
 * The zone is fixed at UTC throughout so a test run in a different one cannot move an answer. The
 * one test that varies it is about the zone, not about the schedule.
 */
val ReminderScheduleSuite by testSuite("When a reminder comes round") {
    // Wednesday 3 June 2026, 12:00 local. A Wednesday so the weekday rules are not all trivially
    // satisfied by the day under test.
    val noon = LocalDateTime.of(2026, 6, 3, 12, 0)
    val zone = ZoneId.of("UTC")

    testSuite("what the model insists on") {
        test("a reminder belongs to a real collection") {
            assertThatThrownBy { daily(collectionId = 0L) }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("real collection")
        }

        test("a time of day is a number of minutes into the day") {
            assertThatThrownBy { daily(minuteOfDay = -1) }
                .isInstanceOf(IllegalArgumentException::class.java)
            assertThatThrownBy { daily(minuteOfDay = 24 * 60) }
                .isInstanceOf(IllegalArgumentException::class.java)
        }

        test("a daily reminder must not name a day") {
            assertThatThrownBy { daily(dayOfWeek = DayOfWeek.MONDAY) }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("Only a weekly reminder names a day")
        }

        test("a weekly reminder must name a day") {
            assertThatThrownBy { weekly(dayOfWeek = null) }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("Only a weekly reminder names a day")
        }

        test("minutes into the day are read back as a time of day") {
            assertThat(daily(minuteOfDay = 6 * 60 + 5).timeOfDay.hour).isEqualTo(6)
            assertThat(daily(minuteOfDay = 6 * 60 + 5).timeOfDay.minute).isEqualTo(5)
            assertThat(daily(minuteOfDay = 0).timeOfDay.hour).isZero()
        }
    }

    testSuite("a daily reminder") {
        test("comes round again tomorrow when today's has passed") {
            val morning = daily(minuteOfDay = 6 * 60)

            val next = ReminderSchedule.nextTriggerAfter(morning, noon, zone)

            assertThat(next).isEqualTo(Instant.parse("2026-06-04T06:00:00Z"))
        }

        test("comes round later today when today's is still to come") {
            val evening = daily(minuteOfDay = 20 * 60)

            val next = ReminderSchedule.nextTriggerAfter(evening, noon, zone)

            assertThat(next).isEqualTo(Instant.parse("2026-06-03T20:00:00Z"))
        }

        test("an acknowledged reminder is owed from its time, not before") {
            val morning =
                daily(minuteOfDay = 6 * 60)
                    .copy(lastTriggeredAt = Instant.parse("2026-06-03T06:00:30Z"))
            val beforeTomorrow = LocalDateTime.of(2026, 6, 4, 5, 59)

            assertThat(ReminderSchedule.isDue(morning, beforeTomorrow, zone)).isFalse()

            val atTomorrow = LocalDateTime.of(2026, 6, 4, 6, 0)
            assertThat(ReminderSchedule.isDue(morning, atTomorrow, zone)).isTrue()
        }

        test("a reminder that has never fired is owed the moment it exists") {
            // Its last coming-round is in the past — yesterday at six — and nothing has
            // acknowledged it, so it is due. This is the intended reading of "remind me at six" set
            // at nine in the evening: the six has gone, so say so now rather than tomorrow.
            val setInTheEvening = daily(minuteOfDay = 6 * 60)

            assertThat(ReminderSchedule.isDue(setInTheEvening, noon, zone)).isTrue()
        }

        test("stops being due once the firing is acknowledged") {
            val morning = daily(minuteOfDay = 6 * 60)
            val later = LocalDateTime.of(2026, 6, 3, 9, 0)
            val fired = morning.copy(lastTriggeredAt = Instant.parse("2026-06-03T06:00:01Z"))

            assertThat(ReminderSchedule.isDue(morning, later, zone)).isTrue()
            assertThat(ReminderSchedule.isDue(fired, later, zone)).isFalse()
        }

        test("is not due when the acknowledgement is older than the last firing") {
            // A reminder set for a time that has already passed today, then acknowledged yesterday.
            // The acknowledgement predates the moment, so the reminder is owed again.
            val morning =
                daily(minuteOfDay = 6 * 60)
                    .copy(lastTriggeredAt = Instant.parse("2026-06-02T06:00:00Z"))

            assertThat(ReminderSchedule.isDue(morning, noon, zone)).isTrue()
        }

        test("a paused reminder is never due and never has a next moment") {
            val paused = daily(minuteOfDay = 6 * 60).copy(enabled = false)

            assertThat(ReminderSchedule.isDue(paused, noon, zone)).isFalse()
            assertThat(ReminderSchedule.nextTriggerAfter(paused, noon, zone)).isNull()
            assertThat(ReminderSchedule.mostRecentTrigger(paused, noon, zone)).isNull()
        }
    }

    testSuite("a weekday reminder") {
        test("comes round the next day when Wednesday's has passed") {
            val morning = weekdays(minuteOfDay = 6 * 60)

            val next = ReminderSchedule.nextTriggerAfter(morning, noon, zone)

            assertThat(next).isEqualTo(Instant.parse("2026-06-04T06:00:00Z"))
        }

        test("skips the weekend and comes round on Monday") {
            val friday = LocalDateTime.of(2026, 6, 5, 20, 0)

            val next = ReminderSchedule.nextTriggerAfter(weekdays(minuteOfDay = 6 * 60), friday, zone)

            assertThat(next).isEqualTo(Instant.parse("2026-06-08T06:00:00Z"))
        }

        test("a Saturday morning reminder waits for Monday rather than reporting a Sunday") {
            val saturday = LocalDateTime.of(2026, 6, 6, 7, 0)

            val next = ReminderSchedule.nextTriggerAfter(weekdays(minuteOfDay = 6 * 60), saturday, zone)

            assertThat(next).isEqualTo(Instant.parse("2026-06-08T06:00:00Z"))
        }

        test("has no last firing over a weekend, because it did not come round") {
            val saturday = LocalDateTime.of(2026, 6, 6, 7, 0)
            val reminder = weekdays(minuteOfDay = 6 * 60)

            val mostRecent = ReminderSchedule.mostRecentTrigger(reminder, saturday, zone)

            assertThat(mostRecent).isEqualTo(Instant.parse("2026-06-05T06:00:00Z"))
            assertThat(ReminderSchedule.isDue(reminder, saturday, zone)).isTrue()
        }
    }

    testSuite("a weekly reminder") {
        test("comes round on its own day, not seven days from now") {
            // Set on a Wednesday for a Thursday.
            val reminder = weekly(DayOfWeek.THURSDAY, minuteOfDay = 6 * 60)

            val next = ReminderSchedule.nextTriggerAfter(reminder, noon, zone)

            assertThat(next).isEqualTo(Instant.parse("2026-06-04T06:00:00Z"))
        }

        test("waits six days when the day has just gone past") {
            // Wednesday noon, for a Wednesday 06:00 reminder.
            val reminder = weekly(DayOfWeek.WEDNESDAY, minuteOfDay = 6 * 60)

            val next = ReminderSchedule.nextTriggerAfter(reminder, noon, zone)

            assertThat(next).isEqualTo(Instant.parse("2026-06-10T06:00:00Z"))
        }

        test("is not due on a day that is not its day") {
            val reminder = weekly(DayOfWeek.SATURDAY, minuteOfDay = 6 * 60)
            val saturdayNoon = LocalDateTime.of(2026, 6, 6, 12, 0)

            assertThat(ReminderSchedule.isDue(reminder, saturdayNoon, zone)).isTrue()

            val sundayNoon = LocalDateTime.of(2026, 6, 7, 12, 0)
            assertThat(ReminderSchedule.isDue(reminder, sundayNoon, zone)).isTrue()
        }

        test("fires once a week and not once a day") {
            val reminder = weekly(DayOfWeek.WEDNESDAY, minuteOfDay = 6 * 60)
            val thursday = LocalDateTime.of(2026, 6, 4, 7, 0)
            val fired = reminder.copy(lastTriggeredAt = Instant.parse("2026-06-03T06:00:00Z"))

            assertThat(ReminderSchedule.isDue(fired, thursday, zone)).isFalse()

            val nextWednesday = LocalDateTime.of(2026, 6, 10, 7, 0)
            assertThat(ReminderSchedule.isDue(fired, nextWednesday, zone)).isTrue()
        }
    }

    testSuite("the zone the machine is in") {
        test("a fixed zone is honoured rather than the JVM default") {
            // 06:00 in Tokyo on the 4th is 21:00 UTC on the 3rd — the previous evening.
            val tokyo = ZoneId.of("Asia/Tokyo")
            val reminder = daily(minuteOfDay = 6 * 60)
            val nowInTokyo = LocalDateTime.of(2026, 6, 4, 7, 0)

            val next = ReminderSchedule.nextTriggerAfter(reminder, nowInTokyo, tokyo)

            assertThat(next).isEqualTo(Instant.parse("2026-06-04T21:00:00Z"))
        }

        test("the same wall-clock time in two zones is two different moments") {
            val reminder = daily(minuteOfDay = 6 * 60)
            val sameWallClock = LocalDateTime.of(2026, 6, 3, 7, 0)

            val inTokyo = ReminderSchedule.nextTriggerAfter(reminder, sameWallClock, ZoneId.of("Asia/Tokyo"))
            val inUtc = ReminderSchedule.nextTriggerAfter(reminder, sameWallClock, ZoneId.of("UTC"))

            assertThat(inTokyo).isEqualTo(Instant.parse("2026-06-03T21:00:00Z"))
            assertThat(inUtc).isEqualTo(Instant.parse("2026-06-04T06:00:00Z"))
        }
    }

    testSuite("sweeping a whole list") {
        test("reports only the ones that are owed, in the order they were given") {
            val dueMorning = daily(collectionId = 1L, minuteOfDay = 6 * 60)
            val dueEvening = daily(collectionId = 2L, minuteOfDay = 11 * 60)
            val paused = daily(collectionId = 3L, minuteOfDay = 6 * 60).copy(enabled = false)
            val alreadyFired =
                daily(collectionId = 4L, minuteOfDay = 6 * 60)
                    .copy(lastTriggeredAt = Instant.parse("2026-06-03T06:30:00Z"))
            val laterTodayNotYet =
                daily(collectionId = 5L, minuteOfDay = 23 * 60)
                    .copy(lastTriggeredAt = Instant.parse("2026-06-03T06:00:00Z"))

            val due =
                ReminderSchedule.dueReminders(
                    listOf(dueEvening, paused, laterTodayNotYet, alreadyFired, dueMorning),
                    noon,
                    zone,
                )

            assertThat(due).containsExactly(dueEvening, dueMorning)
        }

        test("an empty list has nothing due") {
            assertThat(ReminderSchedule.dueReminders(emptyList(), noon, zone)).isEmpty()
        }
    }
}

private fun daily(
    collectionId: Long = 1L,
    minuteOfDay: Int = 6 * 60,
    dayOfWeek: DayOfWeek? = null,
) = CollectionReminder(
    collectionId = collectionId,
    cadence = ReminderCadence.DAILY,
    minuteOfDay = minuteOfDay,
    dayOfWeek = dayOfWeek,
)

private fun weekdays(
    collectionId: Long = 1L,
    minuteOfDay: Int = 6 * 60,
) = CollectionReminder(
    collectionId = collectionId,
    cadence = ReminderCadence.WEEKDAYS,
    minuteOfDay = minuteOfDay,
)

private fun weekly(
    dayOfWeek: DayOfWeek?,
    collectionId: Long = 1L,
    minuteOfDay: Int = 6 * 60,
) = CollectionReminder(
    collectionId = collectionId,
    cadence = ReminderCadence.WEEKLY,
    minuteOfDay = minuteOfDay,
    dayOfWeek = dayOfWeek,
)
