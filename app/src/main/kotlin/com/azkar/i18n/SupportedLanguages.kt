package com.azkar.i18n

import java.util.Locale

// The languages the app ships translations for, and the handful of locale questions the UI asks
// about them. Nothing here holds state, reads storage or knows that Compose exists — it is the
// vocabulary the rest of the app is written against, so it can be exercised from a plain unit test.
//
// The *active* language is not here. It is [AzkarAppState.locale] inside the tree, and
// `Locale.getDefault()` outside it; see [activateLocale] for why the two have to be kept in step.

/** The default language, and the fallback whenever a stored or requested tag is unusable. */
val ENGLISH: Locale = Locale.ENGLISH

/** The only RTL language currently translated. */
val ARABIC: Locale = Locale.forLanguageTag("ar")

/** Locales offered by the language dialog, in display order. */
val supportedLocales: List<Locale> = listOf(ENGLISH, ARABIC)

/** Whether this tag names the same language as [other], ignoring case, script and region. */
fun Locale.isSameLanguageAs(other: Locale): Boolean = language.equals(other.language, ignoreCase = true)

/** This locale's endonym, always expressed in the [display] language. */
fun Locale.displayNameIn(display: Locale): String = getDisplayName(display).replaceFirstChar { it.uppercase(display) }

/**
 * Whether this locale is Arabic, which is the only right-to-left language and the only non-Latin
 * script the app ships.
 *
 * Both the layout direction and the typeface follow from this one answer, which is why it is the
 * only question asked of a locale here. A third language is what makes it insufficient: a
 * right-to-left language that is not Arabic — Hebrew — has to lay the tree out mirrored while still
 * drawing in Inter, and a Latin-script language with a right-to-left tradition the other way, so the
 * direction question and the script question separate again and this stops being the answer to both.
 */
val Locale.isArabic: Boolean
    get() = isSameLanguageAs(ARABIC)
