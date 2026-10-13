/**
 * Which way a piece of user-typed text should be laid out.
 *
 * The app's own direction is decided once, from the interface language: an Arabic interface is
 * right-to-left, an English one left-to-right, and everything on screen follows. That answer is right
 * for chrome, because chrome is written by the app and is therefore in the interface language. It is
 * wrong for the things the user types, because those are in whatever language the user wrote them in,
 * and an English interface is just as likely to hold an Arabic collection name as an Arabic one is to
 * hold an English one.
 *
 * The rule is the Unicode bidirectional algorithm's own: a run of text takes the direction of its
 * first strong character. What counts as strong is narrower here than Unicode makes it. Letters
 * decide; the paired punctuation and currency symbols Unicode also classes as strong are skipped,
 * so a name written `(أذكار)` in Latin brackets is laid out by its letters rather than by its
 * brackets. Digits, punctuation and spaces are skipped for the same reason and for a more visible
 * one — "2024 planner" opens with digits and is plainly left-to-right.
 *
 * Both functions are plain, with no composable receiver, so a caller working out where the caret
 * should sit can call them directly and a test can check them without a composition.
 */
package com.azkar.i18n

import androidx.compose.ui.unit.LayoutDirection

/** Whether [text] says which direction it is written in, and if so which. */
fun directionalHint(text: String?): LayoutDirection? =
    text
        ?.firstOrNull { it.isLetter() }
        ?.let { if (it.isRightToLeftLetter()) LayoutDirection.Rtl else LayoutDirection.Ltr }

/**
 * The direction to lay [text] out in, answering [fallback] when the text does not say.
 *
 * [fallback] is what an empty field, or one holding nothing but digits, is laid out in — and for a
 * field the user has not typed into yet that is the interface's direction, which is what the field
 * was already using before it asked.
 */
fun textDirectionOf(
    text: String?,
    fallback: LayoutDirection,
): LayoutDirection = directionalHint(text) ?: fallback

/**
 * Whether this letter is written right to left.
 *
 * `Character.getDirectionality` is the platform's own answer, and it has *two* right-to-left answers
 * rather than one — `DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC` for a letter, which is what every Arabic
 * letter reports, and `DIRECTIONALITY_RIGHT_TO_LEFT` for the remaining right-to-left marks. Asking only
 * the second of those is the kind of check that looks right and passes a green test written in English:
 * it is false for every Arabic letter on the platform, which is to say it never fires at all.
 *
 * Between them they cover every script Unicode marks right-to-left — Arabic, Hebrew, Syriac, Thaana
 * and the rest. This is a layout fact only; the language of stored content is carried separately as
 * locale metadata.
 */
private fun Char.isRightToLeftLetter(): Boolean =
    when (Character.getDirectionality(this)) {
        Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC,
        Character.DIRECTIONALITY_RIGHT_TO_LEFT,
        -> true

        else -> false
    }
