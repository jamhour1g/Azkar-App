package com.azkar.ui.notifications.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.azkar.i18n.ARABIC
import com.azkar.i18n.ENGLISH
import com.azkar.ui.notifications.NotificationAction
import com.azkar.ui.theme.AzkarTheme
import de.infix.testBalloon.framework.core.testSuite
import org.assertj.core.api.Assertions.assertThat
import java.util.Locale

/**
 * What the user can actually do with a card, asserted through the semantics tree.
 *
 * The presenter suite covers what the card *is*; this covers what it *does*, which is the half a
 * headless unit test cannot reach because it needs the pointer and the composition: that the whole
 * body is one target that opens Azkar, that the cross is a separate target that dismisses, and that
 * the cross's name is translated — an untranslated accessibility label in the Arabic build is a
 * real defect, and the one thing here that has to be checked against a resource rather than against
 * a callback.
 *
 * Every case goes through [cardTest], which owns the frame and the theme, so what a test states is
 * what it is about and not the scaffolding around it.
 */
private val CARD_SIZE = Size(420f, 200f)

private const val TITLE = "Fajr"
private const val MESSAGE = "Say it once, slowly."

/** The rendered name of the dismiss target in each language under test. */
private const val ENGLISH_DISMISS_LABEL = "Dismiss"
private const val ARABIC_DISMISS_LABEL = "إخفاء"

@OptIn(ExperimentalTestApi::class)
private fun cardTest(
    locale: Locale = ENGLISH,
    darkTheme: Boolean = false,
    content: @Composable () -> Unit = { card() },
    block: SkikoComposeUiTest.() -> Unit,
) = runSkikoComposeUiTest(size = CARD_SIZE, density = Density(1f)) {
    setContent {
        AzkarTheme(darkTheme = darkTheme, uiLocale = locale) {
            Box(Modifier.fillMaxSize().padding(12.dp)) { content() }
        }
    }
    block()
}

@Composable
private fun card(
    onOpen: () -> Unit = {},
    onDismiss: () -> Unit = {},
) {
    NotificationCard(item = notice(), onOpen = onOpen, onDismiss = onDismiss, onPauseChanged = {})
}

@OptIn(ExperimentalTestApi::class)
val NotificationCardSuite by testSuite("The overlay notification card") {
    test("it shows what was published, in the app's own card") {
        cardTest {
            onNodeWithText(TITLE).assertExists()
            onNodeWithText(MESSAGE).assertExists()
        }
    }

    test("the body opens Azkar") {
        var opened = 0
        cardTest(content = { card(onOpen = { opened++ }) }) {
            onNodeWithText(TITLE).performClick()
        }

        assertThat(opened).isEqualTo(1)
    }

    test("the cross dismisses, and is not the same gesture as opening") {
        var opened = 0
        var dismissed = 0
        cardTest(content = { card(onOpen = { opened++ }, onDismiss = { dismissed++ }) }) {
            onNodeWithContentDescription(ENGLISH_DISMISS_LABEL).performClick()
        }

        assertThat(dismissed).isEqualTo(1)
        assertThat(opened).isZero()
    }

    test("the cross is named in the language the app is in") {
        // The resource environment on desktop reads `Locale.getDefault()` at the moment the string is
        // asked for, so this is the only way to make the card compose in Arabic at all — and the
        // reason an untranslated label is a real defect here rather than a theoretical one.
        val previous = Locale.getDefault()
        Locale.setDefault(ARABIC)
        try {
            cardTest(locale = ARABIC) {
                onNodeWithContentDescription(ARABIC_DISMISS_LABEL).assertExists()
            }
        } finally {
            Locale.setDefault(previous)
        }
    }

    test("it draws in both themes, and never asks the window for a background") {
        // What a semantic assertion *can* prove about theming is that the card composes cleanly in
        // either theme and lays out — a missing token or an unresolvable colour would throw here —
        // while "it looks right" is a picture's job, not a test's: `./gradlew :app:runScreenshot
        // -Pazkar.screenshot.screen=notifications`, with `azkar.screenshot.dark` and
        // `azkar.screenshot.locale` flipping the two axes. Colour itself is deliberately not asserted
        // through the semantics tree: Compose 1.12's text semantics carry no span styles, so any such
        // assertion would be reading a null.
        listOf(true, false).forEach { dark ->
            cardTest(darkTheme = dark) {
                onNodeWithText(TITLE).assertIsDisplayed()
                onNodeWithText(MESSAGE).assertIsDisplayed()
                onNodeWithContentDescription(ENGLISH_DISMISS_LABEL).assertIsDisplayed()
            }
        }
    }
}

private fun notice() =
    PresentedNotice(
        id = 1L,
        title = TITLE,
        message = MESSAGE,
        action = NotificationAction.Remembrance(1L),
    )
