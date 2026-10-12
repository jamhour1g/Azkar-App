package com.azkar.domain.model

/**
 * How often a collection reminder comes round.
 *
 * Deliberately three plain cases rather than something expressible. The old reminder planner let a
 * user build an arbitrary cadence — "every 30 minutes", "twice a day" — which for a recitation
 * reminder produced schedules nobody kept: a nudge every half hour is a nag, and a reminder the
 * user has to re-arm by hand stops being one.
 *
 * A weekly reminder also has to name a day, which is why this is an enum and not a whole pattern.
 * [CollectionReminder] carries that day, and its constructor insists the two agree.
 */
enum class ReminderCadence {
    /**
     * Every day, at the reminder's time.
     *
     * The default a user means by "remind me": morning azkar is a daily thing, and asking which
     * weekday would be a question about something that does not vary.
     */
    DAILY,

    /**
     * Monday to Friday.
     *
     * Workdays without naming them, which is the group that actually wants a nudge and the one a
     * five-item list of switches would be too clumsy to express.
     */
    WEEKDAYS,

    /** Once a week, on the day [CollectionReminder.dayOfWeek] names. */
    WEEKLY,
}
