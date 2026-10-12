package com.azkar.ui.library

import com.azkar.TestDispatchers
import com.azkar.domain.model.Remembrance
import com.azkar.domain.model.RemembranceCollection
import com.azkar.ui.library.data.CollectionExport
import com.azkar.ui.library.data.CollectionTransfer
import com.azkar.ui.library.data.ImportPlanResult
import com.azkar.ui.library.data.TransferCollection
import com.azkar.ui.library.data.TransferFormat
import com.azkar.ui.library.data.TransferMember
import de.infix.testBalloon.framework.core.testSuite
import kotlinx.coroutines.CoroutineScope
import org.assertj.core.api.Assertions.assertThat
import java.util.Locale

/**
 * The import flow as the user meets it: paste, see what it will do, then commit.
 *
 * Every test drives the same three steps in the same order — write a payload, preview it, confirm
 * it — because that is the only order the dialog allows. What is being pinned down is mostly what
 * the flow *refuses* to do: it must not act on a payload the user has since edited, must not treat a
 * document it could not read as an empty one, and must not report success it did not get.
 *
 * Every test builds its own harness rather than sharing one held at suite scope. TestBalloon runs a
 * suite's tests concurrently, so a shared state holder would have each test overwriting the
 * previous one's payload mid-preview — which shows up as a plan that is null for no visible reason,
 * and never as an error the compiler can point at.
 */
val CollectionImportStateSuite by testSuite("Bringing a collection in") {
    test("a dialog starts closed, empty and on the machine format") {
        val harness = importerWith()

        assertThat(harness.state.isOpen).isFalse()
        assertThat(harness.state.format).isEqualTo(TransferFormat.JSON)
        assertThat(harness.state.payload).isEmpty()
        assertThat(harness.state.plan).isNull()
        assertThat(harness.state.report).isNull()
        assertThat(harness.state.canConfirm).isFalse()
    }

    test("opening sets the flag and starting in the list format is honoured") {
        val harness = importerWith()

        harness.state.open(TransferFormat.TEXT)

        assertThat(harness.state.isOpen).isTrue()
        assertThat(harness.state.format).isEqualTo(TransferFormat.TEXT)
    }

    test("a document with nothing in it plans nothing rather than failing") {
        val harness = importerWith()

        harness.state.onPayloadChanged(CollectionTransfer.encodeJson(emptyList()))

        harness.state.preview()

        assertThat(harness.state.isUnreadable).isFalse()
        val plan = harness.state.plan
        assertThat(plan).isNotNull()
        assertThat(plan!!.isEmpty).isTrue()
        assertThat(harness.state.canConfirm).isFalse()
    }

    test("a payload the codec cannot read says so instead of looking empty") {
        val harness = importerWith()

        harness.state.onPayloadChanged("this is not an Azkar file")

        harness.state.preview()

        assertThat(harness.state.isUnreadable).isTrue()
        assertThat(harness.state.plan).isNull()
        assertThat(harness.state.canConfirm).isFalse()
    }

    test("a plain list reads as readily as the machine format") {
        val harness = importerWith()

        harness.state.onFormatChanged(TransferFormat.TEXT)
        harness.state.onPayloadChanged(listNaming("From a notes app"))

        harness.state.preview()

        assertThat(harness.state.isUnreadable).isFalse()
        assertThat(harness.state.plan!!.created).isEqualTo(1)
    }

    test("previewing a fresh collection reports the one it will create") {
        val harness = importerWith()

        harness.state.onPayloadChanged(jsonNaming("Morning copy"))

        harness.state.preview()

        val plan = harness.state.plan!!
        assertThat(plan.created).isEqualTo(1)
        assertThat(plan.merged).isZero()
        assertThat(plan.membersAdded).isEqualTo(1)
        assertThat(plan.unmatched).isEmpty()
        assertThat(harness.state.canConfirm).isTrue()
    }

    test("a passage that is not in the catalogue is reported as unmatched, not silently dropped") {
        val harness = importerWith()

        harness.state.onPayloadChanged(
            jsonNaming("Half known", MORNING_TEXT, "Something never seeded"),
        )

        harness.state.preview()

        val plan = harness.state.plan!!
        assertThat(plan.membersAdded).isEqualTo(1)
        assertThat(plan.unmatched.map { it.text }).containsExactly("Something never seeded")
    }

    test("a citation that was reworded still matches on the passage alone") {
        val harness = importerWith()

        harness.state.onPayloadChanged(
            CollectionTransfer.encodeJson(
                listOf(
                    TransferCollection(
                        name = "Reworded citation",
                        members = listOf(TransferMember(text = MORNING_TEXT, source = "Somebody Else 9/9")),
                    ),
                ),
            ),
        )

        harness.state.preview()

        assertThat(harness.state.plan!!.membersAdded).isEqualTo(1)
    }

    test("a name that is already on the shelf merges rather than creating a second") {
        val harness =
            importerWith(collections = listOf(customCollection(id = 20L, name = "Morning copy")))
        harness.state.onPayloadChanged(jsonNaming("Morning copy"))

        harness.state.preview()

        val plan = harness.state.plan!!
        assertThat(plan.merged).isEqualTo(1)
        assertThat(plan.created).isZero()
    }

    test("a shipped collection of the same name is refused rather than merged into") {
        val harness =
            importerWith(
                collections = listOf(officialCollection(id = 10L, name = "Morning Azkar")),
            )
        harness.state.onPayloadChanged(jsonNaming("Morning Azkar"))

        harness.state.preview()

        val plan = harness.state.plan!!
        assertThat(plan.skippedOfficial).containsExactly("Morning Azkar")
        assertThat(plan.created).isZero()
        assertThat(plan.merged).isZero()
    }

    test("editing the payload after previewing drops the stale plan") {
        val harness = importerWith()

        harness.state.onPayloadChanged(jsonNaming("Morning copy"))
        harness.state.preview()
        assertThat(harness.state.canConfirm).isTrue()

        harness.state.onPayloadChanged(jsonNaming("Something else"))

        assertThat(harness.state.plan).isNull()
        assertThat(harness.state.canConfirm).isFalse()
    }

    test("previewing again after an edit plans what is on screen now") {
        val harness = importerWith()

        harness.state.onPayloadChanged(jsonNaming("Morning copy"))
        harness.state.preview()
        harness.state.onPayloadChanged(jsonNaming("Second copy"))

        harness.state.preview()

        assertThat(harness.state.plan!!.created).isEqualTo(1)
    }

    test("switching format forgets the payload, because the same bytes mean something else now") {
        val harness = importerWith()

        harness.state.onPayloadChanged(jsonNaming("Morning copy"))
        harness.state.preview()

        harness.state.onFormatChanged(TransferFormat.TEXT)

        assertThat(harness.state.payload).isEmpty()
        assertThat(harness.state.plan).isNull()
        assertThat(harness.state.format).isEqualTo(TransferFormat.TEXT)
    }

    test("confirming a fresh collection adds it and reports what changed") {
        val harness = importerWith()
        harness.state.onPayloadChanged(jsonNaming("Morning copy"))
        harness.state.preview()

        harness.state.confirm()

        val report = harness.state.report!!
        assertThat(report.created).isEqualTo(1)
        assertThat(report.membersAdded).isEqualTo(1)
        assertThat(harness.gateway.collections.map { it.name }).contains("Morning copy")
    }

    test("confirming merges into the existing shelf rather than adding a duplicate") {
        val harness =
            importerWith(collections = listOf(customCollection(id = 20L, name = "Morning copy")))
        harness.state.onPayloadChanged(jsonNaming("Morning copy"))
        harness.state.preview()

        harness.state.confirm()

        assertThat(harness.state.report!!.merged).isEqualTo(1)
        assertThat(harness.gateway.collections.map { it.name }).containsExactly("Morning copy")
    }

    test("a refused shipped collection is left alone and counted as skipped") {
        val harness =
            importerWith(
                collections = listOf(officialCollection(id = 10L, name = "Morning Azkar")),
            )
        harness.state.onPayloadChanged(jsonNaming("Morning Azkar"))
        harness.state.preview()

        harness.state.confirm()

        assertThat(harness.state.report!!.skippedOfficial).containsExactly("Morning Azkar")
        assertThat(harness.gateway.collections).hasSize(1)
    }

    test("confirming reads the shelf back so the tile behind the dialog is already right") {
        val harness = importerWith()

        harness.state.onPayloadChanged(jsonNaming("Morning copy"))
        harness.state.preview()
        val readsBefore = harness.gateway.snapshotReads

        harness.state.confirm()

        assertThat(harness.gateway.snapshotReads).isGreaterThan(readsBefore)
    }

    test("confirming with nothing decoded reports nothing imported and writes nothing") {
        val harness = importerWith()

        val readsBefore = harness.gateway.snapshotReads

        harness.state.confirm()

        assertThat(harness.state.report!!.isEmpty).isTrue()
        assertThat(harness.gateway.snapshotReads).isEqualTo(readsBefore)
        assertThat(harness.gateway.collections).isEmpty()
    }

    test("a failed import says so and reports nothing") {
        val harness = importerWith()

        harness.state.onPayloadChanged(jsonNaming("Morning copy"))
        harness.state.preview()
        harness.gateway.failure = IllegalStateException("the database is gone")

        harness.state.confirm()

        assertThat(harness.state.report).isNull()
        assertThat(harness.collections.onNotice)
            .isEqualTo(CollectionNotice.Message(CollectionNoticeKey.FAILED))
    }

    test("an unreadable payload that is then replaced previews normally again") {
        val harness = importerWith()

        harness.state.onPayloadChanged("nonsense")
        harness.state.preview()
        assertThat(harness.state.isUnreadable).isTrue()

        harness.state.onPayloadChanged(jsonNaming("Morning copy"))
        harness.state.preview()

        assertThat(harness.state.isUnreadable).isFalse()
        assertThat(harness.state.canConfirm).isTrue()
    }

    test("resetting clears the payload, the plan and the report but leaves the encoding alone") {
        val harness = importerWith()

        harness.state.onPayloadChanged(jsonNaming("Morning copy"))
        harness.state.preview()
        harness.state.onFormatChanged(TransferFormat.TEXT)

        harness.state.reset()

        assertThat(harness.state.payload).isEmpty()
        assertThat(harness.state.plan).isNull()
        assertThat(harness.state.report).isNull()
        // Still TEXT: which encoding is open is the reader's setting, not part of the document they
        // were working on. Clearing the document must not silently move them to the other one.
        assertThat(harness.state.format).isEqualTo(TransferFormat.TEXT)
    }

    test("the dialog can be reopened after a completed import") {
        val harness = importerWith()

        harness.state.onPayloadChanged(jsonNaming("Morning copy"))
        harness.state.preview()
        harness.state.confirm()
        harness.state.close()

        harness.state.open()

        assertThat(harness.state.isOpen).isTrue()
        assertThat(harness.state.payload).isEmpty()
    }

    test("a document exported from the app reads back in as what it was") {
        val original =
            officialCollection(
                id = 10L,
                name = "Morning Azkar",
                members = listOf(remembrance(1L)),
            )
        // Seeded with the collection being exported, so its name is not free and the planner has to
        // deal with a clash rather than sail past it.
        val harness = importerWith(collections = listOf(original))
        val exported = CollectionExport.exportOf(original, ::englishText)
        harness.state.onPayloadChanged(CollectionTransfer.encodeJson(listOf(exported)))

        harness.state.preview()

        // The name matches a shipped collection, so the honest outcome is a refusal rather than a
        // duplicate shelf full of the same passages.
        assertThat(harness.state.plan!!.skippedOfficial).containsExactly("Morning Azkar")
    }

    test("a shared document can be pasted straight into the list field") {
        val harness = importerWith()

        harness.state.onFormatChanged(TransferFormat.TEXT)
        harness.state.onPayloadChanged(
            CollectionTransfer.encodeText(
                listOf(
                    TransferCollection(
                        name = "Shared",
                        members = listOf(TransferMember(text = MORNING_TEXT, source = SOURCE)),
                    ),
                ),
            ),
        )

        harness.state.preview()

        assertThat(harness.state.plan!!.membersAdded).isEqualTo(1)
    }

    test("an exported collection keeps its description and icon but never its pin") {
        val pinned =
            customCollection(
                id = 20L,
                name = "Pinned",
                description = "A note",
                iconKey = "star",
                pinnedAt = FixtureInstant,
            )

        val exported = CollectionExport.exportOf(pinned, ::englishText)

        assertThat(exported.description).isEqualTo("A note")
        assertThat(exported.iconKey).isEqualTo("star")
        assertThat(exported.tagNames).isEmpty()
    }

    test("a shipped collection exports its labels like any other, because nothing about it is read-only now") {
        val exported =
            CollectionExport.exportOf(
                officialCollection(id = 10L, name = "M", tags = setOf(tag(1L, "morning"))),
                ::englishText,
            )

        assertThat(exported.tagNames).containsExactly("morning")
    }

    test("a cover travels as encoded bytes, so the recipient gets this picture and not a reference to it") {
        val exported =
            CollectionExport.exportOf(
                customCollection(id = 20L, name = "C", coverImagePath = "cover.png"),
                ::englishText,
                coverImageBase64 = "aGVsbG8=",
            )

        assertThat(exported.coverImageBase64).isEqualTo("aGVsbG8=")
    }

    test("a collection with no cover exports without one rather than an empty string") {
        val exported = CollectionExport.exportOf(customCollection(id = 20L, name = "C"), ::englishText)

        assertThat(exported.coverImageBase64).isNull()
    }

    test("an export drops a member that was never persisted, because there is no text to match it by") {
        val collection =
            customCollection(
                id = 20L,
                name = "Draft",
                members = listOf(remembrance(null), remembrance(1L)),
            )

        val exported = CollectionExport.exportOf(collection, ::englishText)

        assertThat(exported.members.map { it.text }).containsExactly("Remembrance 1")
    }

    test("a plan that would do nothing still reads as a plan, not as a failure") {
        val harness = importerWith()

        harness.state.onPayloadChanged(
            jsonNaming("All unknown", "Never seeded one", "Never seeded two"),
        )

        harness.state.preview()

        val plan: ImportPlanResult = harness.state.plan!!
        assertThat(plan.isEmpty).isFalse()
        assertThat(plan.unmatched).hasSize(2)
        assertThat(harness.state.isUnreadable).isFalse()
    }
}

/**
 * The harness: one state over one fake gateway. The gateway is the same [FakeCollectionsGateway] the
 * rest of the library suites use, and its `importCollections` runs the real planner, so an import
 * that works here goes through the same rules as one the hub screen triggers.
 */
private class ImportHarness(
    val state: CollectionImportState,
    val collections: CollectionsCatalogue,
    val gateway: FakeCollectionsGateway,
)

private fun importerWith(
    collections: List<RemembranceCollection> = emptyList(),
    remembrances: List<Remembrance> = listOf(remembrance(1L)),
): ImportHarness {
    val scope = CoroutineScope(TestDispatchers.unconfined)
    val gateway = FakeCollectionsGateway(collections = collections, remembrances = remembrances)
    val collectionsState =
        CollectionsCatalogue(scope = scope, uiLocale = Locale.ENGLISH, gateway = gateway, covers = fakeCovers())
    collectionsState.loadData()
    return ImportHarness(
        state = CollectionImportState(collections = collectionsState, scope = scope),
        collections = collectionsState,
        gateway = gateway,
    )
}

/** The one passage the catalogue knows, named the way the [remembrance] fixture spells it. */
private const val MORNING_TEXT = "Remembrance 1"
private const val SOURCE = "Sunan an-Nasa'i"

/** The English text, which is what the app would export with a Latin-script device. */
private fun englishText(remembrance: Remembrance): String =
    remembrance
        .translationsFor(Locale.ENGLISH)
        ?.translationPair
        ?.text
        .orEmpty()

/**
 * A payload built the way a user would paste one: named, holding the passages it should carry.
 *
 * Named without a passage means [MORNING_TEXT], the one passage this suite's catalogue holds. A
 * transfer carrying no members plans nothing at all — importing an empty list is a no-op rather than
 * a collection — so a bare name would quietly make every plan assertion below vacuous.
 */
private fun jsonNaming(
    name: String,
    vararg texts: String,
): String =
    CollectionTransfer.encodeJson(
        listOf(transferNaming(name, *texts.ifEmpty { arrayOf(MORNING_TEXT) })),
    )

private fun listNaming(name: String): String =
    CollectionTransfer.encodeText(
        listOf(TransferCollection(name = name, members = listOf(TransferMember(text = MORNING_TEXT, source = SOURCE)))),
    )

private fun transferNaming(
    name: String,
    vararg texts: String,
): TransferCollection =
    TransferCollection(
        name = name,
        members = texts.map { TransferMember(text = it, source = SOURCE) },
    )
