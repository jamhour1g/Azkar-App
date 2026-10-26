package com.azkar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import com.azkar.i18n.activateLocale
import com.azkar.ui.library.CollectionSessionScreen
import com.azkar.ui.library.rememberCollectionSessionState
import com.azkar.ui.preview.PREVIEW_COLLECTION_ID
import com.azkar.ui.preview.previewReminders
import de.infix.testBalloon.framework.core.testSuite
import io.github.takahirom.roborazzi.captureRoboImage
import java.util.Locale

@OptIn(ExperimentalTestApi::class)
val CollectionSessionInteractionScreenshotSuite by testSuite("Compose Desktop interaction screenshots") {
    test("advance to the next remembrance") {
        val originalLocale = Locale.getDefault()
        val originalName = System.getProperty("user.name")
        try {
            Locale.ENGLISH.activateLocale()
            System.setProperty("user.name", "Azkar")
            runDesktopComposeUiTest(width = 1440, height = 900) {
                setContent {
                    ScreenshotFrame(dark = false) { SessionInteractionShot() }
                }
                waitUntil(timeoutMillis = 30_000) {
                    onAllNodesWithText(FIRST_REMEMBRANCE, substring = true).fetchSemanticsNodes().isNotEmpty()
                }
                onAllNodesWithText(FIRST_REMEMBRANCE, substring = true).onFirst().assertIsDisplayed()
                waitForIdle()
                onRoot().captureRoboImage("src/test/roborazzi/interactions/session-next-before.png")

                onNodeWithText("Next").performClick()

                waitUntil(timeoutMillis = 30_000) {
                    onAllNodesWithText(SECOND_REMEMBRANCE, substring = true).fetchSemanticsNodes().isNotEmpty()
                }
                onAllNodesWithText(SECOND_REMEMBRANCE, substring = true).onFirst().assertIsDisplayed()
                onAllNodesWithText(FIRST_REMEMBRANCE, substring = true).assertCountEquals(0)
                waitForIdle()
                onRoot().captureRoboImage("src/test/roborazzi/interactions/session-next-after.png")
            }
        } finally {
            originalLocale.activateLocale()
            originalName?.let { System.setProperty("user.name", it) } ?: System.clearProperty("user.name")
        }
    }
}

@Composable
private fun SessionInteractionShot() {
    LibraryShot { catalogue ->
        val reminders = remember { previewReminders(PREVIEW_COLLECTION_ID) }
        CollectionSessionScreen(
            session = rememberCollectionSessionState(catalogue, PREVIEW_COLLECTION_ID, reminders),
            onBack = {},
            onOpenRemembrance = {},
            showToast = { _, _, _ -> },
        )
    }
}

private const val FIRST_REMEMBRANCE = "O Allah, I ask You for wellbeing."
private const val SECOND_REMEMBRANCE = "Glory to Allah, and praise be to Him."
