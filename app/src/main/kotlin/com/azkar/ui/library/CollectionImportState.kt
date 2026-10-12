package com.azkar.ui.library

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.azkar.domain.model.RemembranceCollection
import com.azkar.ui.library.data.CollectionExport
import com.azkar.ui.library.data.CollectionImport
import com.azkar.ui.library.data.CollectionTransfer
import com.azkar.ui.library.data.ImportPlanResult
import com.azkar.ui.library.data.ImportReport
import com.azkar.ui.library.data.TransferCollection
import com.azkar.ui.library.data.TransferFormat
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The paste-a-document flow, held apart from [CollectionsCatalogue] for the same reason the recitation
 * counters are.
 *
 * The flow is three steps — write, read, confirm — and the middle one is the interesting part. An
 * import can create a shelf, fold into one that already exists, refuse one we ship, and fail to
 * recognise a passage; all four are decided by [CollectionImport.plan] *before* anything is
 * written, so this class holds that decision as state and the dialog can render it. Confirming is
 * then one call that applies the plan.
 *
 * The decoded payload is kept rather than the raw text, so confirming cannot act on a document the
 * user has since edited: [onPayloadChanged] drops the plan, and a confirm with no plan does nothing.
 */
@Stable
class CollectionImportState(
    private val collections: CollectionsCatalogue,
    private val scope: CoroutineScope,
) {
    private val logger = KotlinLogging.logger {}

    /** Whether the import dialog is on screen at all. */
    var isOpen by mutableStateOf(false)
        private set

    /** Which encoding the payload field is holding. */
    var format by mutableStateOf(TransferFormat.JSON)
        private set

    /** The raw text in the payload field, exactly as pasted. */
    var payload by mutableStateOf("")
        private set

    /** What the import *will* do, once a payload has been read. `null` before that. */
    var plan by mutableStateOf<ImportPlanResult?>(null)
        private set

    /** What the import actually did. `null` until the plan has been applied. */
    var report by mutableStateOf<ImportReport?>(null)
        private set

    /**
     * Whether the last read failed to produce a plan.
     *
     * A flag rather than a message, because the state holds no strings for any other reason either:
     * the tree is rebuilt on every locale switch, and a string resolved while the old locale was up
     * would be left behind in the old language.
     */
    var isUnreadable by mutableStateOf(false)
        private set

    /**
     * The payload as it was when the plan was made.
     *
     * Empty means "no plan is showing", which is the same answer as a payload with nothing in it, so
     * one field covers both and there is no state in which a confirm could act on stale input.
     */
    private var decoded: List<TransferCollection> = emptyList()

    /** Opens the dialog, optionally in one encoding, with a clean slate. */
    fun open(startingFormat: TransferFormat = TransferFormat.JSON) {
        isOpen = true
        format = startingFormat
        reset()
    }

    /** Closes the dialog and forgets everything it held. */
    fun close() {
        isOpen = false
        reset()
    }

    /** Forgets the payload, the plan and the report without closing the dialog. */
    fun reset() {
        payload = ""
        decoded = emptyList()
        plan = null
        report = null
        isUnreadable = false
    }

    /**
     * Decodes the payload and works out what it would do, without touching the database.
     *
     * A document that decodes to nothing is not an error — an empty document is a document — so it
     * produces an empty plan, which the dialog renders as "there is nothing here". Only text this
     * build cannot read at all sets [isUnreadable].
     */
    fun preview() {
        val transfers = decodePayload()
        if (transfers == null) {
            isUnreadable = true
            decoded = emptyList()
            plan = null
            return
        }
        isUnreadable = false
        decoded = transfers
        plan = CollectionImport.plan(transfers, collections.allCollections, collections.allRemembrances)
    }

    /**
     * Applies the plan that was previewed.
     *
     * The gateway decides again from the live shelf rather than replaying the plan, which is the
     * safer order: a merge that has become a create in the meantime lands in the right place. An
     * empty or missing plan writes nothing, so a click that arrives while the button is disabled
     * cannot half-apply anything.
     *
     * Returns immediately and applies in the background: a document with fifty members is fifty
     * statements, and a modal dialog that froze while they ran would look like a hang.
     */
    fun confirm() {
        if (decoded.isEmpty()) {
            report = ImportReport.EMPTY
            return
        }
        val pending = decoded
        plan = null
        scope.launch {
            runCatching { collections.importCollections(pending) }
                .onSuccess { outcome ->
                    if (outcome.isFailure) {
                        collections.onNotice = CollectionNotice.Message(CollectionNoticeKey.FAILED)
                    }
                    report = outcome.getOrNull()
                }.onFailure { failure ->
                    logger.error(failure) { "Unable to apply an import of ${pending.size} collections" }
                    collections.onNotice = CollectionNotice.Message(CollectionNoticeKey.FAILED)
                    report = null
                }
        }
    }

    fun onFormatChanged(value: TransferFormat) {
        if (format == value) {
            return
        }
        format = value
        // The same bytes in the other encoding are not the same document, so the plan goes with it.
        reset()
    }

    fun onPayloadChanged(value: String) {
        payload = value
        decoded = emptyList()
        plan = null
        report = null
        isUnreadable = false
    }

    /** Whether a plan is showing and has something in it, which is what gates the confirm button. */
    val canConfirm: Boolean
        get() = plan?.isEmpty == false

    /** Decodes [payload] in the current encoding, or `null` when it is not a document we read. */
    fun decodePayload(): List<TransferCollection>? =
        when (format) {
            TransferFormat.JSON -> CollectionTransfer.decodeJson(payload).getOrNull()
            TransferFormat.TEXT -> CollectionTransfer.decodeText(payload).getOrNull()
        }

    /**
     * The text that would be shared for [collection], in the current encoding.
     *
     * The passage text is resolved through the shelf's presenter, so a document reads in the same
     * language the collection does.
     */
    fun exportText(
        collection: RemembranceCollection,
        coverImageBase64: String? = null,
    ): String =
        CollectionExport.exportAs(
            collection,
            format,
            collections.presenter::localizedPrimaryText,
            coverImageBase64,
        )
}
