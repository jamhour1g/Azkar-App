package com.azkar.ui.library

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import com.azkar.i18n.ENGLISH
import com.azkar.ui.theme.AzkarTheme
import de.infix.testBalloon.framework.core.testSuite
import org.assertj.core.api.Assertions.assertThat

/**
 * What the edit dialog lets through, and what it refuses.
 *
 * The rules pinned down here are the ones a user would call a bug. An editor that opens on an empty
 * box loses words silently. A save that accepts an empty Arabic replaces a passage with nothing. And
 * a save that accepts an empty English deletes a translation the user never asked about — which is the
 * hardest of the three to notice, because the edit they *did* ask for lands perfectly and the loss
 * happens somewhere else, later, where nothing looks broken.
 *
 * Nothing here asserts a colour or a size. How the dialog looks is the design system's business; what
 * it does with what you typed is this file's.
 */
private const val EXISTING_ENGLISH = "Whoever says good morning"
private const val EXISTING_ARABIC = "ذكر 1"
private const val EDITED_ARABIC = "أذكار المساء"
private const val TITLE = "Edit this remembrance"
private const val ARABIC_LABEL = "Arabic text"
private const val ENGLISH_LABEL = "English translation"
private const val REQUIRED_NOTE = "A remembrance cannot be empty."
private const val SAVE = "Save"
private const val CANCEL = "Cancel"

/** What one opening of the dialog reported back, so a test can assert on how it closed. */
private class Reported {
    val confirmations = mutableListOf<Pair<String, String?>>()
    var dismissed = false
}

/** Opens the dialog on [EXISTING_ARABIC] / [EXISTING_ENGLISH], runs [body], and reports what happened. */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
private fun converse(body: SkikoComposeUiTest.() -> Unit): Reported {
    val reported = Reported()
    runSkikoComposeUiTest {
        setContent {
            AzkarTheme(darkTheme = false, uiLocale = ENGLISH) {
                RemembranceEditorDialog(
                    remembrance = remembrance(id = 1L, text = EXISTING_ENGLISH),
                    onDismiss = { reported.dismissed = true },
                    onConfirm = { arabic, english -> reported.confirmations += arabic to english },
                )
            }
        }
        body()
    }
    return reported
}

@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
val RemembranceEditorSuite by testSuite("The remembrance editor dialog") {
    test("the dialog opens on the words the remembrance already carries") {
        converse {
            // An editor that opens empty is the one failure that loses data rather than refusing it.
            onNodeWithText(EXISTING_ARABIC).assertExists()
            onNodeWithText(EXISTING_ENGLISH).assertExists()
            onNodeWithText(TITLE).assertExists()
            onNodeWithText(ARABIC_LABEL).assertExists()
            onNodeWithText(ENGLISH_LABEL).assertExists()
        }
    }

    test("saving hands back the Arabic that was typed and the translation that was typed") {
        val reported =
            converse {
                onNodeWithText(EXISTING_ARABIC).performTextReplacement(EDITED_ARABIC)
                onNodeWithText(EXISTING_ENGLISH).performTextReplacement("Whoever says good evening")
                onNodeWithText(SAVE).performClick()
            }

        assertThat(reported.confirmations).containsExactly(EDITED_ARABIC to "Whoever says good evening")
    }

    test("saving with the Arabic blank refuses to close, and says why") {
        val reported =
            converse {
                onNodeWithText(EXISTING_ARABIC).performTextReplacement("")
                onNodeWithText(SAVE).performClick()
            }

        assertThat(reported.confirmations).isEmpty()
        assertThat(reported.dismissed).isFalse()
    }

    test("the note about the empty Arabic waits until the user has tried to save") {
        converse {
            // Nobody is scolded on the way in: a red line under a field somebody has not finished
            // typing in is the dialog answering a first keystroke.
            onNodeWithText(REQUIRED_NOTE).assertDoesNotExist()
            onNodeWithText(EXISTING_ARABIC).performTextReplacement("")
            onNodeWithText(SAVE).performClick()
            onNodeWithText(REQUIRED_NOTE).assertExists()
            // And the dialog is still open to fix it in.
            onNodeWithText(SAVE).assertExists()
        }
    }

    test("typing after being told off takes the complaint away again") {
        converse {
            onNodeWithText(EXISTING_ARABIC).performTextReplacement("")
            onNodeWithText(SAVE).performClick()
            onNodeWithText(REQUIRED_NOTE).assertExists()
            // Looked up by what the box does rather than by what it says, because the text it would
            // be found by is the text this test has just deleted.
            onAllNodes(
                SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText),
            )[0].performTextInput(EDITED_ARABIC)
            onNodeWithText(REQUIRED_NOTE).assertDoesNotExist()
        }
    }

    test("a blank translation asks to keep it, not to empty it") {
        // The assertion the whole null-versus-empty-string distinction exists for. A blank box is
        // silence about the translation, and silence must never be read as "delete it".
        val reported =
            converse {
                onNodeWithText(EXISTING_ARABIC).performTextReplacement(EDITED_ARABIC)
                onNodeWithText(EXISTING_ENGLISH).performTextReplacement("")
                onNodeWithText(SAVE).performClick()
            }

        assertThat(reported.confirmations).containsExactly(EDITED_ARABIC to null)
    }

    test("cancelling closes the dialog without confirming anything") {
        val reported =
            converse {
                onNodeWithText(EXISTING_ARABIC).performTextReplacement(EDITED_ARABIC)
                onNodeWithText(CANCEL).performClick()
            }

        assertThat(reported.confirmations).isEmpty()
        assertThat(reported.dismissed).isTrue()
    }
}
