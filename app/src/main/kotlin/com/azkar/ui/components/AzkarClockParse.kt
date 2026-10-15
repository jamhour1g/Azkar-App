package com.azkar.ui.components

import java.util.Locale

/**
 * Reading a time back out of something the user typed.
 *
 * Split out of [AzkarClock] for one reason: everything here is about *untrusted text*, while
 * [AzkarClock] itself only ever formats values the app already trusts. Keeping the forgiving parser
 * away from the formatter means the formatter cannot drift toward accepting half-written input, and
 * the parser cannot drift toward deciding what a stored schedule means.
 *
 * The rule that shapes every decision below is that returning null is always available and always
 * better than guessing. A time field that holds the text somebody is halfway through typing is
 * recoverable; one that rewrites itself mid-keystroke is not.
 */
internal object AzkarClockParse {
    private const val MINUTES_IN_HOUR = 60
    private const val HOURS_IN_DAY = 24
    private const val HOURS_IN_HALF_DAY = 12
    private const val NOON_HOUR = 12

    /** The narrow and no-break spaces CLDR puts before a meridiem; all read as plain space here. */
    private val SPACES = setOf('\u202F', '\u00A0', '\t', ' ')

    /**
     * Every meridiem this app has to recognise, and which half of the day each one means.
     *
     * A table rather than something derived at runtime, and that is a deliberate trade. Deriving the
     * markers from [AzkarClock] would need a way to tell a morning spelling from an evening one
     * without a table in the first place, so it would only move the guessing somewhere less obvious.
     * Spelled out here, it is wrong in exactly one way — a language whose meridiem this app does not
     * list — and that is a mistake a test can see. `AzkarClockSuite` asserts against the markers the
     * real formatters produce, so a locale that starts spelling its meridiem differently fails the
     * build instead of quietly losing its afternoons.
     *
     * Arabic is here because the app ships it, and the two entries are the characters its formatter
     * actually emits: ص for morning and م for evening.
     */
    private val MERIDIEMS =
        mapOf(
            "am" to false,
            "a.m." to false,
            "\u0635" to false,
            "pm" to true,
            "p.m." to true,
            "\u0645" to true,
        )

    /**
     * Arabic-Indic digits folded onto Latin ones, as a lookup rather than an offset.
     *
     * This JVM's formatters print Latin digits for Arabic, so the text [minuteOfDay] normally meets
     * is Latin — but a keyboard layout, a paste, or a different runtime may not agree with that. A
     * `٨` reaching `String.toIntOrNull` as an unknown character would read as a malformed time rather
     * than as eight, and a subtraction from the block's base codepoint would quietly turn anything
     * outside the block into nonsense. A lookup fails on the one character it does not cover instead.
     */
    private val ARABIC_INDIC_DIGITS: Map<Char, Char> =
        mapOf(
            '\u0660' to '0',
            '\u0661' to '1',
            '\u0662' to '2',
            '\u0663' to '3',
            '\u0664' to '4',
            '\u0665' to '5',
            '\u0666' to '6',
            '\u0667' to '7',
            '\u0668' to '8',
            '\u0669' to '9',
        )

    /**
     * A minute-of-day, or null when [text] is not a time this app would agree to store.
     *
     * Accepts every shape a reader is likely to type: `8`, `8:00`, `08:00`, `8:00 AM`, `8am`, `20:00`,
     * `8:00 ص`, and the same in Arabic-Indic digits. Rejecting any of those would make the editable
     * field a trap, because the string the field *displays* has to be one the field *accepts*.
     *
     * A bare hour is read as a twelve-hour hour, because that is the clock the field is showing: a
     * reader typing `8` next to `8:00 AM` means eight in the morning, and reading it as eight at night
     * would move the schedule twelve hours from the one they were looking at. Hours above twelve are
     * the exception that keeps the rule safe — `20` is not a twelve-hour hour, so it is taken at face
     * value. `12` with no meridiem is the single refusal, because it is the one hour where morning
     * and evening are genuinely indistinguishable and noon versus midnight is a twelve-hour mistake.
     */
    fun minuteOfDay(text: String): Int? {
        val (body, isPm) = splitMeridiem(fold(text))
        val parts = body.split(':')
        // A bare "8" is eight o'clock, but a trailing "9:" is a time still being typed. They look the
        // same from here — no minutes have arrived — and they do not want the same answer: one is
        // finished, and committing the other would rewrite the field out from under the colon.
        val minute =
            when {
                parts.size == 1 -> 0
                else ->
                    parts
                        .getOrNull(1)
                        ?.trim()
                        ?.takeIf { it.isNotEmpty() }
                        ?.toIntOrNull()
            }
        val resolved =
            parts
                .first()
                .trim()
                .toIntOrNull()
                ?.takeIf { parts.size <= 2 }
                ?.let { hourOfDay(it, minute, isPm) }
        return if (resolved == null || minute == null) null else resolved * MINUTES_IN_HOUR + minute
    }

    /**
     * The hour a typed fragment means on a twenty-four hour clock, or null when it cannot mean one.
     *
     * Split out of [minuteOfDay] because the rules and the reading are two different jobs: this decides
     * what "5", "17" and "5 PM" each mean, and the caller adds the minutes on top once the hour is
     * settled. A bare hour is a morning hour, an hour above twelve is taken at face value whatever the
     * meridiem says it does not, and `12` on its own is refused because noon and midnight are the one
     * place a twelve-hour reading is genuinely ambiguous.
     */
    private fun hourOfDay(
        hour: Int?,
        minute: Int?,
        isPm: Boolean?,
    ): Int? =
        when {
            hour == null || minute == null -> null
            hour !in 0 until HOURS_IN_DAY || minute !in 0 until MINUTES_IN_HOUR -> null
            // A meridiem only makes sense on an hour a twelve-hour clock could have shown.
            isPm != null && hour > NOON_HOUR -> null
            isPm == null && hour == NOON_HOUR -> null
            isPm == true -> (hour % HOURS_IN_HALF_DAY) + HOURS_IN_HALF_DAY
            isPm == false -> hour % HOURS_IN_HALF_DAY
            else -> hour
        }

    /** The markers themselves, for the test that checks this table against the real formatters. */
    fun meridiemMarkers(): Set<String> = MERIDIEMS.keys

    /** Which half of the day [marker] names, for that same test. */
    fun isEvening(marker: String): Boolean? = MERIDIEMS[marker.lowercase(Locale.ROOT)]

    private fun fold(text: String): String {
        val trimmed = text.trim()
        return buildString(trimmed.length) {
            trimmed.forEach { character ->
                val foldedDigit = ARABIC_INDIC_DIGITS[character]
                if (foldedDigit != null) {
                    append(foldedDigit)
                } else if (character !in SPACES || character == ' ') {
                    append(character)
                }
            }
        }.trim()
    }

    /**
     * Peels a trailing meridiem off a time, returning the rest and which half of the day it named.
     *
     * Null means no meridiem was found, which is **not** the same as "morning" — that difference is
     * the whole of the twelve-hour reading, and collapsing it is how `8` becomes 8pm.
     *
     * Longest marker first, so a marker that is a prefix of another cannot claim the text and leave
     * the remainder inside the hour field.
     */
    private fun splitMeridiem(cleaned: String): Pair<String, Boolean?> {
        val lowered = cleaned.lowercase(Locale.ROOT)
        val hit =
            MERIDIEMS.keys
                .sortedByDescending { it.length }
                .firstOrNull { lowered.endsWith(it) }
        val body = if (hit == null) cleaned else cleaned.dropLast(hit.length)
        return body.trim() to hit?.let(MERIDIEMS::getValue)
    }
}
