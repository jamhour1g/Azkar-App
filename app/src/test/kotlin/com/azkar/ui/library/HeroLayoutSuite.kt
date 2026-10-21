package com.azkar.ui.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.azkar.i18n.ARABIC
import com.azkar.i18n.ENGLISH
import com.azkar.ui.preview.previewCollection
import com.azkar.ui.preview.previewCollectionHeroActions
import com.azkar.ui.preview.previewCollectionStats
import com.azkar.ui.theme.AzkarTheme
import com.azkar.ui.theme.LocalWindowSizeClass
import com.azkar.ui.theme.windowSizeClassFor
import de.infix.testBalloon.framework.core.testSuite
import org.assertj.core.api.Assertions.assertThat
import java.util.Locale

/**
 * That the hero band is tall enough for the copy printed on it, in the script it is printed in.
 *
 * Every other assertion in this codebase passed while the band was visibly cut off at the bottom, and
 * the reason is worth stating before the test that would have caught it. Nothing was *missing* — the
 * last line of copy was composed, enabled, carrying the right strings, and reachable. It was measured
 * at no height at all: a `Column` hands its last child whatever is left after everything above it, the
 * copy above it was spending 40dp on an inset applied on both sides of the same call, and the budget
 * above that had been worked out from font sizes rather than from the line boxes those labels actually
 * occupy. The band's `clip` then drew a hard line through the remainder. A test can only fail on a
 * difference it is able to see, and "present and correct" was not one; "has a height" is.
 *
 * So nothing here asks what the band says. It asks how tall the band is and whether the last line in
 * it occupies the whole of its line box, and it asks in both scripts because the answer differs: the
 * fact row names no `lineHeight` and inherits `bodyLarge`'s, which is 24sp of leading in Inter and 32sp
 * in Amiri, so the band is genuinely taller in Arabic and no single constant can be right for both.
 *
 * The one line that *is* named here is the back link, and it is checked because it used to be the
 * second such line. It was bare ink with no surface, drawn straight onto the geometric artwork, which
 * made it unreadable exactly where it mattered most. It is now the band's own glass pill, whose height
 * is fixed — so the check is that it stayed fixed, in both scripts. A bare link would measure 30dp in
 * English and 36dp in Arabic, and the first of those is what fails.
 *
 * The last line is found by its own text rather than by its position. It used to be found by position
 * — the lowest text node — which is how a test written to catch a starved line went on passing while
 * the line was starved: the collapsed node reported a `bottom` *above* its neighbour's, so "the lowest"
 * named the neighbour and the assertion was measuring the wrong thing in a way that read as success.
 *
 * The line is named by a fixture constant rather than by the sentence around it, so a reworded caption
 * in either language leaves this suite alone.
 */
@OptIn(ExperimentalTestApi::class)
val HeroLayoutSuite by testSuite("the collection band holds its own copy") {
    /** What one script's arithmetic comes to: its locale, the band, and the line box it must fit. */
    data class Script(
        val locale: Locale,
        val height: Float,
        val lastLineHeight: Float,
    )

    val scripts =
        listOf(
            Script(locale = ENGLISH, height = 205f, lastLineHeight = 26f),
            Script(locale = ARABIC, height = 211f, lastLineHeight = 32f),
        )

    /**
     * A description written to want both of the lines the band reserves for it.
     *
     * The worst case is a collection that says as much as the band allows, and a shorter one would
     * leave the band with slack to spare and prove nothing.
     */
    val twoLineDescription =
        "A long description written on purpose so that it has to wrap onto the second of the two lines " +
            "the band allows it, which is the case the band has to survive."

    /** Lays the band out on its own, at the width and density a real window gives it. */
    fun SkikoComposeUiTest.showBand(script: Script) {
        setContent {
            AzkarTheme(darkTheme = true, uiLocale = script.locale) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                ) {
                    CollectionDetailHero(
                        collection = previewCollection(description = twoLineDescription),
                        memberCount = 54,
                        stats = previewCollectionStats(),
                        inReminders = false,
                        actions = previewCollectionHeroActions(),
                        modifier = Modifier.testTag(BAND),
                    )
                }
            }
        }
    }

    /**
     * The band's fact row — the last line of copy in it, and the one a starved `Column` collapses.
     *
     * English even in the Arabic case, and that is not an oversight. `stringResource` resolves
     * against the JVM's default locale and `AzkarTheme`'s `uiLocale` does not touch it — the theme
     * chooses the *type scale*, which is the half of the locale that decides this band's height,
     * while the strings stay in whatever language the JVM is running. Producing Arabic text here would
     * mean writing to global state that every other suite in this run is reading, to check something
     * about geometry that the type scale alone already decides.
     */
    fun SkikoComposeUiTest.factRow(): Rect =
        onNodeWithText(FACT_ROW, substring = true).fetchSemanticsNode().boundsInRoot

    /** The band's back link, which is a pill with a height of its own rather than a line of text. */
    fun SkikoComposeUiTest.backLink(): Rect =
        onNodeWithText(BACK_LINK, substring = true).fetchSemanticsNode().boundsInRoot

    scripts.forEach { script ->
        val language = script.locale.displayLanguage

        test("the band is ${script.height.toInt()}dp tall in $language") {
            runSkikoComposeUiTest(size = Size(1584f, 900f), density = Density(1f)) {
                showBand(script)
                val band = onNodeWithTag(BAND).fetchSemanticsNode().boundsInRoot
                assertThat(band.height).describedAs("the band's height in $language").isEqualTo(script.height)
            }
        }

        test("the band in $language is not cut through at the bottom") {
            runSkikoComposeUiTest(size = Size(1584f, 900f), density = Density(1f)) {
                showBand(script)
                val band = onNodeWithTag(BAND).fetchSemanticsNode().boundsInRoot
                val facts = factRow()

                // The symptom, and deliberately the *exact* height rather than "more than nothing".
                //
                // A `Column` starved of height hands its last child whatever is left, and a `Text`
                // handed less than its line box reports a shorter one rather than reporting nothing:
                // at three dp short it still measured 23dp and still sat inside the band, so an
                // assertion of `> 0` passed on a band that had already begun to eat the line. The
                // line box is what the label occupies whatever else happens, so that is the number.
                assertThat(facts.height)
                    .describedAs("the height of the band's last line in $language")
                    .isEqualTo(script.lastLineHeight)

                // And the consequence. A line measured at nothing would also end above the band's
                // edge, which is the edge the clip would have to be cutting along for it to vanish.
                assertThat(facts.bottom)
                    .describedAs(
                        "where the bottom of the band's last line falls, against a band ending at ${band.bottom}",
                    ).isLessThanOrEqualTo(band.bottom)
            }
        }

        test("the back link in $language is a pill of a fixed height") {
            runSkikoComposeUiTest(size = Size(1584f, 900f), density = Density(1f)) {
                showBand(script)
                // Not a colour test — this suite never reads pixels. What it pins is the *height*,
                // because that is the thing the band's budget was rebuilt around: a control of a fixed
                // height cannot vary with the reading direction, and so the band no longer has to.
                assertThat(backLink().height)
                    .describedAs("the height of the back link in $language")
                    .isEqualTo(HERO_PILL_HEIGHT.value)
            }
        }
    }

    test("compact windows keep the add and create actions named") {
        runSkikoComposeUiTest(size = Size(800f, 900f), density = Density(1f)) {
            setContent {
                AzkarTheme(darkTheme = true, uiLocale = ENGLISH) {
                    CompositionLocalProvider(
                        LocalWindowSizeClass provides windowSizeClassFor(width = 800.dp, height = 900.dp),
                    ) {
                        CollectionDetailHero(
                            collection = previewCollection(),
                            memberCount = 54,
                            stats = previewCollectionStats(),
                            inReminders = false,
                            actions = previewCollectionHeroActions(),
                        )
                    }
                }
            }

            onNodeWithContentDescription("Add to Collection").assertExists()
            onNodeWithContentDescription("Create Remembrance").assertExists()
        }
    }
}

private const val BAND = "hero"

/**
 * The destination the back link names, as the string resource names it.
 *
 * The English words, for the reason [factRow] gives: the locale under test is the *type scale*, not
 * the language the strings come out in.
 */
private const val BACK_LINK = "All Collections"

/**
 * The member count as [previewCollectionStats] and `memberCount` between them print it.
 *
 * A fixture fact rather than a sentence, so the suite is about geometry and not about wording.
 */
private const val FACT_ROW = "54 remembrances"
