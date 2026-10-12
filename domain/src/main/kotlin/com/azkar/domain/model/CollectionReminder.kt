package com.azkar.domain.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime

/**
 * The standing nudge to come back to one collection.
 *
 * The source of a reminder is a collection rather than a loose set of remembrances. That is the
 * whole point of the rework: a notification has to be able to say "0 of 52", and only a collection
 * knows what 52 is. It also means one schedule covers everything the user does to the collection
 * afterwards — they can empty it, fill it, or recite it twice, and tomorrow's nudge is still right.
 *
 * [minuteOfDay] is a number of minutes rather than a `LocalTime` because a time of day is not a
 * moment until a zone is attached, and the zone is a property of the machine reminding the user, not
 * of the reminder. [ReminderSchedule] is where the two are joined.
 *
 * A row exists only for a collection the user asked to be reminded about. [enabled] is separate from
 * that so a schedule can be paused over a holiday without being retyped, and [lastTriggeredAt] is
 * what stops a reminder that fires every thirty seconds from firing every thirty seconds.
 */
data class CollectionReminder(
    val collectionId: Long,
    val cadence: ReminderCadence,
    val minuteOfDay: Int,
    val dayOfWeek: DayOfWeek? = null,
    val enabled: Boolean = true,
    val lastTriggeredAt: Instant? = null,
) {
    init {
        require(collectionId > 0L) { "A reminder must belong to a real collection" }
        require(minuteOfDay in 0 until MINUTES_IN_DAY) {
            "A reminder's time of day has to be a number of minutes into the day, was $minuteOfDay"
        }
        // A weekly reminder without a day is every week, and a daily one carrying a day is a day it
        // will skip. Either would be a schedule that quietly does not mean what the screen says.
        require(if (cadence == ReminderCadence.WEEKLY) dayOfWeek != null else dayOfWeek == null) {
            "Only a weekly reminder names a day of the week, and a weekly one must"
        }
    }

    /** The time of day this reminder comes round, without a zone. */
    val timeOfDay: LocalTime
        get() = LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)

    companion object {
        /** One day's worth of minutes; the upper bound is exclusive. */
        const val MINUTES_IN_DAY: Int = 24 * 60
    }
}
