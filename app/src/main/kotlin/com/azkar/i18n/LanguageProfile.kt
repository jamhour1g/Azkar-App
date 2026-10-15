package com.azkar.i18n

import java.util.Locale

/**
 * Layout and type choices that belong to one language.
 *
 * Kept in a file of its own rather than in [SupportedLanguages] alongside the locale constants: this
 * is the shape of a single language, and the two English and Arabic instances below are the data the
 * rest of the app is written against. Splitting it out is also what lets `SupportedLanguages.kt` be
 * named for the vocabulary it holds instead of for the one class in it.
 */
data class LanguageProfile(
    val locale: Locale,
    val isRightToLeft: Boolean,
    val usesArabicTypeface: Boolean,
)
