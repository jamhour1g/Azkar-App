package com.azkar.ui.library

import de.infix.testBalloon.framework.core.testSuite
import org.assertj.core.api.Assertions.assertThat

/**
 * The labels a dialog submits, parsed from the one comma-separated field the user types.
 *
 * De-duplicating ignoring case is what stops the writer from being asked to insert the same folded
 * tag twice: storage folds names to lowercase before the unique index sees them, so "Sleep" and
 * "sleep" are one tag wearing two hats.
 */
val CollectionEditorDraftSuite by testSuite("The submitted labels are split and de-duplicated") {

    test("a typed list splits on commas and trims the pieces") {
        val draft = CollectionEditorDraft.create().apply { updateTagInput("morning, sleep") }

        assertThat(draft.submittedTagNames).containsExactly("morning", "sleep")
    }

    test("two spellings of one label survive only as the first") {
        val draft = CollectionEditorDraft.create().apply { updateTagInput("Sleep, sleep, Travel") }

        assertThat(draft.submittedTagNames).containsExactly("Sleep", "Travel")
    }

    test("empty pieces do not become empty labels") {
        val draft = CollectionEditorDraft.create().apply { updateTagInput(" , morning, ,") }

        assertThat(draft.submittedTagNames).containsExactly("morning")
    }

    test("a field with nothing but separators submits no labels") {
        val draft = CollectionEditorDraft.create().apply { updateTagInput(" , ,") }

        assertThat(draft.submittedTagNames).isEmpty()
    }
}
