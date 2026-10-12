package com.azkar.i18n

import de.infix.testBalloon.framework.core.testSuite
import org.assertj.core.api.Assertions.assertThat
import java.util.Locale

/**
 * The locale vocabulary the rest of the app is written against.
 *
 * Two things here are load-bearing enough to pin. [Locale.isSameLanguageAs] is what stops a user
 * re-picking the language they are already on from rebuilding the tree, so its "language only, not
 * the region" rule is a deliberate decision rather than a shortcut. And [Locale.isArabic] decides
 * both the layout direction and the typeface at once, which is only sound while those two questions
 * have the same answer — the last test says what would break them apart.
 */
val SupportedLanguagesSuite by testSuite("The supported languages") {
    test("Arabic runs right to left and English does not") {
        assertThat(ARABIC.isArabic).isTrue()
        assertThat(ENGLISH.isArabic).isFalse()
        assertThat(Locale.forLanguageTag("ar").isArabic).isTrue()
    }

    test("a tag for a language the app does not ship is treated as English") {
        // Anything unrecognized lands in the English arm of every question asked of a locale, so the
        // layout is left-to-right and the face is Inter. A desktop OS set to a language the app has
        // no strings for gets a working window rather than a mirror it has nothing to mirror.
        assertThat(Locale.forLanguageTag("fr").isArabic).isFalse()
        assertThat(Locale.forLanguageTag("he").isArabic).isFalse()
        assertThat(Locale.forLanguageTag("ja").isArabic).isFalse()
    }

    test("languages compare by language alone, ignoring case and region") {
        assertThat(Locale.forLanguageTag("ar").isSameLanguageAs(Locale.forLanguageTag("AR"))).isTrue()
        // Different region, same language: still the same language to a user choosing between them,
        // and still Arabic, so still right to left.
        assertThat(Locale.forLanguageTag("ar").isSameLanguageAs(Locale.forLanguageTag("ar-EG"))).isTrue()
        assertThat(Locale.forLanguageTag("ar-EG").isArabic).isTrue()
        assertThat(Locale.forLanguageTag("en").isSameLanguageAs(Locale.forLanguageTag("en-GB"))).isTrue()
        assertThat(Locale.forLanguageTag("en").isSameLanguageAs(Locale.forLanguageTag("ar"))).isFalse()
    }

    test("each language is shown in its own script, not the reader's") {
        val arabicNameInArabic = ARABIC.displayNameIn(ARABIC)
        val arabicNameInEnglish = ARABIC.displayNameIn(ENGLISH)

        assertThat(arabicNameInArabic).isEqualTo("العربية")
        assertThat(arabicNameInEnglish).isEqualTo("Arabic")
    }

    test("the suggested locales are the two the language dialog offers") {
        assertThat(supportedLocales).containsExactly(Locale.ENGLISH, Locale.forLanguageTag("ar"))
    }

    test("one language answering both questions is what will stop being true") {
        // `isArabic` decides the layout direction and the typeface from a single comparison, which
        // holds only because Arabic is both the one right-to-left language and the one non-Latin
        // script in the app. Hebrew is the case that breaks it: right to left, so mirrored layout,
        // but not Arabic script, so it must still draw in Inter — and this predicate cannot say so.
        // A language that needs a face we do not bundle is the other half of the same limit.
        assertThat(Locale.forLanguageTag("he").isArabic).isFalse()
    }
}
