package com.azkar.ui.library

import de.infix.testBalloon.framework.core.testSuite
import org.assertj.core.api.Assertions.assertThat

/**
 * The tags a dialog carries, now that they are chips rather than a comma-separated field.
 *
 * The behaviour that used to live here — splitting on commas and de-duplicating ignoring case — has
 * moved from parse time to press time, and that is the whole point of the picker: storage folds names
 * to lowercase before the unique index sees them, so "Sleep" and "sleep" are one tag wearing two hats,
 * and the only way to honour that is never to hold both at once.
 */
val CollectionEditorDraftSuite by testSuite("The tags a dialog carries are the ones the user pressed") {

    test("a pressed tag is submitted, and an unpressed one is not") {
        val draft =
            CollectionEditorDraft.create().apply {
                toggleTag("morning")
                toggleTag("sleep")
            }

        assertThat(draft.submittedTagNames).containsExactly("morning", "sleep")
    }

    test("pressing a lit tag again takes it back off") {
        val draft =
            CollectionEditorDraft.create().apply {
                toggleTag("morning")
                toggleTag("sleep")
                toggleTag("morning")
            }

        assertThat(draft.submittedTagNames).containsExactly("sleep")
    }

    test("two spellings of one tag never both survive") {
        val draft =
            CollectionEditorDraft.create().apply {
                toggleTag("Sleep")
                toggleTag("sleep")
            }

        assertThat(draft.submittedTagNames).isEmpty()
    }

    test("a blank press changes nothing") {
        val draft =
            CollectionEditorDraft.create().apply {
                toggleTag("morning")
                toggleTag("   ")
            }

        assertThat(draft.submittedTagNames).containsExactly("morning")
    }

    test("a tag typed around its spaces is trimmed on the way in") {
        val draft = CollectionEditorDraft.create().apply { toggleTag("  after prayer  ") }

        assertThat(draft.submittedTagNames).containsExactly("after prayer")
    }

    test("a fresh collection starts with nothing") {
        val draft = CollectionEditorDraft.create()

        assertThat(draft.submittedTagNames).isEmpty()
    }

    test("a rename carries the collection's own tags in, folded and alphabetised") {
        val collection =
            customCollection(
                id = 1L,
                name = "Morning Azkar",
                tags = taggedSet("Travel", "Sleep", "morning"),
            )

        val draft = CollectionEditorDraft.edit(collection)

        assertThat(draft.submittedTagNames).containsExactly("morning", "sleep", "travel")
    }

    test("a duplicate starts with no tags, because it inherits the name and nothing else") {
        val collection = customCollection(id = 1L, name = "Morning Azkar", tags = taggedSet("sleep"))

        val draft = CollectionEditorDraft.duplicate(collection, newName = "Morning Azkar (copy)")

        assertThat(draft.submittedTagNames).isEmpty()
    }

    test("a typed tag joins the submitted ones and clears the field") {
        val draft = CollectionEditorDraft.create().apply { updateNewTagName("commute") }

        assertThat(draft.addTag(draft.newTagName)).isTrue()
        assertThat(draft.submittedTagNames).containsExactly("commute")
        assertThat(draft.newTagName).isEmpty()
    }

    test("a tag the collection already carries is not added twice") {
        val draft =
            CollectionEditorDraft.create().apply {
                toggleTag("Sleep")
                updateNewTagName("sleep")
            }

        assertThat(draft.newTagAcceptable).isFalse()
        assertThat(draft.addTag(draft.newTagName)).isFalse()
        assertThat(draft.submittedTagNames).containsExactly("Sleep")
    }

    test("a blank name is not an acceptable tag") {
        val draft = CollectionEditorDraft.create().apply { updateNewTagName("   ") }

        assertThat(draft.newTagAcceptable).isFalse()
    }

    test("the options are every tag the library has, in the order the catalogue gave them") {
        val draft = CollectionEditorDraft.create()

        val options = draft.tagOptions(listOf("morning", "sleep", "travel"))

        assertThat(options).containsExactly("morning", "sleep", "travel")
    }

    test("a query narrows the options to what matches it") {
        val draft = CollectionEditorDraft.create().apply { updateTagQuery("tra") }

        val options = draft.tagOptions(listOf("morning", "sleep", "travel"))

        assertThat(options).containsExactly("travel")
    }

    test("a chosen tag the library has never heard of is still an option") {
        val draft = CollectionEditorDraft.create().apply { addTag("commute") }

        val options = draft.tagOptions(listOf("morning", "sleep"))

        assertThat(options).containsExactly("morning", "sleep", "commute")
    }

    test("a query never hides a tag the user has already chosen") {
        val draft =
            CollectionEditorDraft.create().apply {
                toggleTag("morning")
                updateTagQuery("travel")
            }

        val options = draft.tagOptions(listOf("morning", "sleep", "travel"))

        assertThat(options).containsExactly("morning", "travel")
    }

    test("an empty query is no filter at all") {
        val draft = CollectionEditorDraft.create().apply { updateTagQuery("  ") }

        assertThat(draft.tagOptions(listOf("morning", "sleep"))).containsExactly("morning", "sleep")
    }

    test("a search that matches nothing offers nothing") {
        val draft = CollectionEditorDraft.create().apply { updateTagQuery("commute") }

        assertThat(draft.tagOptions(listOf("morning", "sleep"))).isEmpty()
    }
}

/**
 * Tags named in the order given, with ids to match.
 *
 * The fixture's own [tag] is used rather than a second fake so that these tests exercise the same
 * folding the storage layer does — if `TagImpl` ever stopped folding, the de-duplication assertions
 * above would start failing here rather than in production.
 */
private fun taggedSet(vararg names: String): Set<com.azkar.domain.model.Tag> =
    names.mapIndexed { index, name -> tag(id = index.toLong() + 1L, name = name) }.toSet()
