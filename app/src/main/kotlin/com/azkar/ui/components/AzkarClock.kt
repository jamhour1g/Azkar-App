package com.azkar.ui.components

import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Every clock the app prints, formatted in one place and in one convention.
 *
 * The app used to hold four unrelated clocks: `DateTimeFormatter.ofPattern("HH:mm")` for the queue's
 * window fields and its summary card, `ofPattern("hh:mm a")` for prayer names and `HH:mm` again
 * for the time beside them, `ofLocalizedTime(SHORT)` for the collection reminder dialogs, and a
 * `"%02d"` pair for that dialog's two dropdowns. They disagreed with each other in the same row —
 * a prayer showed `05:12` on the right and `5:12 AM` underneath it — and the queue's fields were
 * pinned to 24-hour by a KDoc that argued the notices [NotificationRuntime] prints must match them,
 * which is an argument about two labels agreeing, not about which label a reader should see.
 *
 * There is one answer now: a localised short time, which is `8:00 AM` in English and `8:00 ص` in
 * Arabic, resolved from the active UI locale rather than hard-coded. Which is why the whole app
 * reads AM/PM rather than only the new picker: a user who has been taught one clock by four
 * screens has learned none of them.
 *
 * Locale-derived rather than fixed on purpose. The alternative — `hh:mm a` with an English pattern —
 * prints "AM" inside an Arabic sentence beside an Arabic meridiem that nobody asked for, and it is
 * the only thing that would have made the clock read differently depending on which screen asked for
 * it. The meridiem is now localised too, which is not quite the same as being English everywhere: it
 * is the same *answer* everywhere, which is what a schedule needs. The one thing this deliberately
 * does not promise is digit shapes — see [formatMinuteOfHour].
 *
 * Formatters are immutable and expensive enough to build that caching them is worth it:
 * [PrayerTimesModel] republishes its whole table every second, and `ofLocalizedTime` resolves a
 * locale provider on each call. The cache is keyed by [Locale], which — unlike the string it often
 * wraps — has real `equals`/`hashCode`, so `Locale.ENGLISH` and `Locale.forLanguageTag("en")` hit the
 * same entry when they are the same locale and miss when they are not. Nothing is ever evicted:
 * there are two locales in the app and a handful of others at most.
 */
object AzkarClock {
    private const val MINUTES_IN_DAY = 24 * 60
    private const val HOURS_IN_DAY = 24
    private const val MINUTES_IN_HOUR = 60

    private val FORMATTERS = ConcurrentHashMap<Locale, DateTimeFormatter>()

    /** The locale's own short clock — `8:00 AM` in English, `8:00 ص` in Arabic. */
    fun timeFormatter(locale: Locale): DateTimeFormatter =
        FORMATTERS.getOrPut(locale) {
            DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
        }

    /** [timeFormatter] applied to a time of day. */
    fun format(
        time: LocalTime,
        locale: Locale,
    ): String = timeFormatter(locale).format(time)

    /** [format] for an hour and minute, each clamped into its own range rather than trusted. */
    fun format(
        hour: Int,
        minute: Int,
        locale: Locale,
    ): String =
        format(LocalTime.of(hour.coerceIn(0, HOURS_IN_DAY - 1), minute.coerceIn(0, MINUTES_IN_HOUR - 1)), locale)

    /**
     * [format] for a minute-of-day, which is how every schedule in the app is stored.
     *
     * The window boundaries, the queue interval and the collection reminder times are all integers
     * rather than [LocalTime]s, so this is the overload they all need. Out-of-range values clamp
     * instead of throwing: a corrupted preference must not take the settings screen down with it.
     */
    fun format(
        minuteOfDay: Int,
        locale: Locale,
    ): String = format(minuteOfDay / MINUTES_IN_HOUR, minuteOfDay % MINUTES_IN_HOUR, locale)

    /**
     * A bare minute, zero-padded to two digits.
     *
     * Not [format] because the clock's suffix is noise here: this is the trailing half of a
     * two-dropdown picker, where the hour field beside it already says "AM" or "PM", and repeating
     * it sixty times in the open menu would be noise on noise. Zero-padded rather than plain
     * because the value is a column of two-digit numbers and `8` above `10` reads as out of order.
     *
     * Formatted through the locale rather than through [Locale.ROOT] so that the digits follow
     * whatever the platform says the language writes numbers in. On the JVM this app ships against
     * that resolves to the same Latin digits the rest of the app's `%d` arguments produce — the two
     * agreeing is the point, and a hard-coded root locale would have been the one place a reader
     * could see a different numeral set from every other number on the screen.
     */
    fun formatMinuteOfHour(
        minute: Int,
        locale: Locale,
    ): String = String.format(locale, "%02d", minute.coerceIn(0, MINUTES_IN_HOUR - 1))

/** Whether a stored minute-of-day is a legal value, for callers that validate before writing. */
    fun isValidMinuteOfDay(minuteOfDay: Int): Boolean = minuteOfDay in 0 until MINUTES_IN_DAY

    /**
     * Reads a minute-of-day back out of text the user typed, or null when it is not a time.
     *
     * The inverse of [format], and the reason the editable half of [AzkarTimePicker] can exist: a
     * field that displays [format]'s output has to accept [format]'s input, or it is showing the reader
     * a spelling it will refuse. The rules — which bare hour means morning, why noon needs a meridiem,
     * why `13 PM` is refused — live with the parser in [AzkarClockParse]; nothing here has to restate
     * them, which is the reason [parse] takes no [Locale]. It accepts both clocks and both meridiems
     * in every locale, so there is nothing for a locale argument to decide.
     *
     * Null rather than a default, and the caller is expected to keep the text: a field that rewrites
     * itself to `12:00 AM` while somebody is three characters into `9:45 PM` destroys the keystroke
     * they are halfway through, whereas a field that simply has not committed yet is doing the only
     * thing it can be asked to do.
     */
    fun parse(text: String): Int? = AzkarClockParse.minuteOfDay(text)
}
