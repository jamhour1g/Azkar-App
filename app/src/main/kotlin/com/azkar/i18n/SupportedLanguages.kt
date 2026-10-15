package com.azkar.i18n

import java.util.Locale

// The languages the app ships translations for, and the handful of locale questions the UI asks
// about them. Nothing here holds state, reads storage or knows that Compose exists — it is the
// vocabulary the rest of the app is written against, so it can be exercised from a plain unit test.
// The one type in the vocabulary, [LanguageProfile], lives next door in its own file so that this one
// can be named for what it holds rather than for a single declaration inside it.
//
// The *active* language is not here. It is [AzkarAppState.locale] inside the tree, and
// `Locale.getDefault()` outside it; see [activateLocale] for why the two have to be kept in step.

/** The default language, and the fallback whenever a stored or requested tag is unusable. */
val ENGLISH: Locale = Locale.ENGLISH

/** The RTL language currently translated. */
val ARABIC: Locale = Locale.forLanguageTag("ar")

/** Supported languages and their script-specific presentation choices, in display order. */
val languageProfiles: List<LanguageProfile> =
    listOf(
        LanguageProfile(ENGLISH, isRightToLeft = false, usesArabicTypeface = false),
        LanguageProfile(ARABIC, isRightToLeft = true, usesArabicTypeface = true),
    )

/** Locales offered by the language dialog, in display order. */
val supportedLocales: List<Locale> = languageProfiles.map(LanguageProfile::locale)

/** Whether this tag names the same language as [other], ignoring case, script and region. */
fun Locale.isSameLanguageAs(other: Locale): Boolean = language.equals(other.language, ignoreCase = true)

/** This locale's endonym, always expressed in the [display] language. */
fun Locale.displayNameIn(display: Locale): String = getDisplayName(display).replaceFirstChar { it.uppercase(display) }

/** Resolves presentation choices from this configured language, with an English fallback. */
private fun Locale.profile(): LanguageProfile =
    languageProfiles.firstOrNull { isSameLanguageAs(it.locale) }
        ?: LanguageProfile(ENGLISH, isRightToLeft = false, usesArabicTypeface = false)

/** Whether this locale's interface should use right-to-left layout. */
val Locale.isRightToLeft: Boolean get() = profile().isRightToLeft

/** Whether this locale should use the Arabic display typeface. */
val Locale.usesArabicTypeface: Boolean get() = profile().usesArabicTypeface
