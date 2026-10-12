package com.azkar.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.azkar.domain.model.RemembranceCollection
import com.azkar.generated.resources.Res
import com.azkar.generated.resources.collectionsActionCancel
import com.azkar.generated.resources.collectionsActionChooseCover
import com.azkar.generated.resources.collectionsActionCreate
import com.azkar.generated.resources.collectionsActionDelete
import com.azkar.generated.resources.collectionsActionRemoveCover
import com.azkar.generated.resources.collectionsActionSave
import com.azkar.generated.resources.collectionsDialogCoverBusy
import com.azkar.generated.resources.collectionsDialogCoverChosen
import com.azkar.generated.resources.collectionsDialogCoverLabel
import com.azkar.generated.resources.collectionsDialogCoverNone
import com.azkar.generated.resources.collectionsDialogCreateTitle
import com.azkar.generated.resources.collectionsDialogDeleteBody
import com.azkar.generated.resources.collectionsDialogDeleteTitle
import com.azkar.generated.resources.collectionsDialogDescriptionLabel
import com.azkar.generated.resources.collectionsDialogDescriptionPlaceholder
import com.azkar.generated.resources.collectionsDialogDuplicateTitle
import com.azkar.generated.resources.collectionsDialogNameLabel
import com.azkar.generated.resources.collectionsDialogNamePlaceholder
import com.azkar.generated.resources.collectionsDialogNameRequired
import com.azkar.generated.resources.collectionsDialogRenameTitle
import com.azkar.generated.resources.collectionsDialogTagsHint
import com.azkar.generated.resources.collectionsDialogTagsLabel
import com.azkar.generated.resources.collectionsDialogTagsPlaceholder
import com.azkar.ui.components.AzkarDialogSurface
import com.azkar.ui.components.AzkarOutlinedField
import com.azkar.ui.components.PrimaryButton
import com.azkar.ui.components.SecondaryButton
import com.azkar.ui.library.data.CollectionPresenter
import com.azkar.ui.theme.AzkarColors
import com.azkar.ui.theme.AzkarTheme
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Which of the three name-writing dialogs is open.
 *
 * They are one dialog with three headings rather than three dialogs because they edit the same two
 * fields and submit the same pair — the mode only decides the heading, the confirm label, and whether
 * a description field is shown. Splitting them would mean three copies of the same validation.
 */
enum class CollectionEditorMode {
    CREATE,
    RENAME,

    /**
     * A duplicate only takes a name.
     *
     * The copy inherits the description and the tags it was made from, so offering editors for them
     * would suggest the source's own text travels somewhere it does not. It does not inherit the cover
     * either — two collections sharing one image file would make deleting it ambiguous — so there is
     * nothing to edit there but a choice that has not been made yet, and a duplicate is not the moment
     * to make it.
     */
    DUPLICATE,
    ;

    /** Whether this mode edits anything beyond the name. See [DUPLICATE] for why it might not. */
    val editsDetails: Boolean
        get() = this != DUPLICATE

    val titleResource: StringResource
        get() =
            when (this) {
                CREATE -> Res.string.collectionsDialogCreateTitle
                RENAME -> Res.string.collectionsDialogRenameTitle
                DUPLICATE -> Res.string.collectionsDialogDuplicateTitle
            }

    val confirmResource: StringResource
        get() =
            when (this) {
                CREATE -> Res.string.collectionsActionCreate
                RENAME, DUPLICATE -> Res.string.collectionsActionSave
            }
}

/**
 * The half-typed collection behind [CollectionEditorDialog].
 *
 * Held by the screen rather than by the dialog so dismissing the dialog and reopening it for the same
 * collection starts from what was already typed. A class rather than a data class on purpose:
 * equality would compare two `MutableState` delegates instead of the text the user can see, and the
 * shelf's own `equals` checks would then disagree with what is on screen.
 *
 * The cover is carried as the *name* of a file the app has already copied, for the same reason the
 * column holds a name rather than the path the user picked: the picture belongs to the library now, so
 * moving the home directory cannot break it and deleting the original cannot take it away.
 */
@Stable
class CollectionEditorDraft private constructor(
    val mode: CollectionEditorMode,
    val collectionId: Long,
    initialName: String,
    initialDescription: String,
    initialTagInput: String,
    initialCoverImagePath: String?,
) {
    var name: String by mutableStateOf(initialName)
        private set

    var description: String by mutableStateOf(initialDescription)
        private set

    /**
     * The label field, verbatim as typed — commas, doubled spaces and all.
     *
     * Parsing happens on submit rather than on every keystroke, so that a comma typed mid-word does not
     * spring two chips apart under the user's cursor while they are still writing the word.
     */
    var tagInput: String by mutableStateOf(initialTagInput)
        private set

    /**
     * The name of the cover file, or `null` for the generated wash.
     *
     * Set to `null` to go back to the wash, which is a real choice rather than an absent one — hence a
     * nullable field and not a blank string the storage layer would have to interpret.
     */
    var coverImagePath: String? by mutableStateOf(initialCoverImagePath)
        private set

    /**
     * Whether a save attempt should be rejected.
     *
     * Set only after a save was attempted, so an untouched dialog does not open by scolding the user
     * for a name they have not had a chance to type.
     */
    var nameRejected: Boolean by mutableStateOf(false)
        private set

    /** The name as storage should receive it: trimmed, and required. */
    val submittedName: String get() = name.trim()

    /** The description as storage should receive it: absent rather than a run of spaces. */
    val submittedDescription: String? get() = description.trim().ifEmpty { null }

    /**
     * The labels as storage should receive them: one comma-separated field, split, trimmed, and
     * de-duplicated *ignoring case*.
     *
     * Case matters here in a way it does not for the name. Tags fold to lowercase before they are
     * stored, and the folded name is what the unique index is built on, so "Sleep" and "sleep" are one
     * tag wearing two hats — and a list that offered both would ask storage to insert the same row
     * twice and fail the save. De-duplicating here means the shelf never offers an answer the writer
     * cannot accept. The first spelling the user typed is the one that survives, so their capitalisation
     * is preserved for the chips.
     */
    val submittedTagNames: List<String>
        get() =
            tagInput
                .split(TAG_SEPARATOR)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinctBy { it.lowercase() }

    fun updateName(value: String) {
        name = value
    }

    fun updateDescription(value: String) {
        description = value
    }

    fun updateTagInput(value: String) {
        tagInput = value
    }

    /**
     * Records a cover the user has just picked, or clears the existing one when [path] is `null`.
     *
     * The file itself has been copied into the library by the time this is called, so the name here is
     * already durable. See [CollectionEditorDialog]'s `onPickCover` for why the dialog asks rather than
     * reaching for the file itself.
     */
    fun updateCoverImage(path: String?) {
        coverImagePath = path
    }

    /** The confirm button's click handler. Reports whether the dialog may close. */
    fun submit(): Boolean {
        if (submittedName.isEmpty()) {
            nameRejected = true
            return false
        }
        return true
    }

    companion object {
        /** Only read for [CollectionEditorMode.RENAME] and [CollectionEditorMode.DUPLICATE]. */
        const val NO_COLLECTION_ID = -1L

        fun create(): CollectionEditorDraft =
            CollectionEditorDraft(
                mode = CollectionEditorMode.CREATE,
                collectionId = NO_COLLECTION_ID,
                initialName = "",
                initialDescription = "",
                initialTagInput = "",
                initialCoverImagePath = null,
            )

        fun rename(collection: RemembranceCollection): CollectionEditorDraft =
            CollectionEditorDraft(
                mode = CollectionEditorMode.RENAME,
                collectionId = requireId(collection),
                initialName = collection.name,
                initialDescription = collection.description.orEmpty(),
                // Sorted rather than in the set's own order for the same reason the chips are: a
                // hash-ordered set would reorder this field every time the user opened the dialog.
                initialTagInput =
                    collection.tags
                        .map { it.name }
                        .sortedWith(String.CASE_INSENSITIVE_ORDER)
                        .joinToString(TAG_SEPARATOR_TEXT),
                initialCoverImagePath = collection.coverImagePath,
            )

        /**
         * @param newName the name already carrying its "(copy)" suffix. Resolved by the caller so the
         * suffix is translated in the language on screen right now, not the one the draft remembers.
         */
        fun duplicate(
            collection: RemembranceCollection,
            newName: String,
        ): CollectionEditorDraft =
            CollectionEditorDraft(
                mode = CollectionEditorMode.DUPLICATE,
                collectionId = requireId(collection),
                initialName = newName,
                initialDescription = "",
                initialTagInput = "",
                initialCoverImagePath = null,
            )

        private fun requireId(collection: RemembranceCollection): Long =
            requireNotNull(collection.id) { "A persisted collection came back without an identifier" }
    }
}

/**
 * The editor: a name, and — outside a duplicate — a description, labels and a cover.
 *
 * One dialog for creating, renaming and duplicating, because the three differ only in what they are
 * called, which fields they offer, and what happens on confirm. The description is a two-line field
 * rather than the shared single-line default: it is the one thing the user writes *about* a collection,
 * and it is read on the detail page, where one line is not enough to say what belongs in there.
 *
 * @param onPickCover opens the file chooser and returns the name of the copy the app made, or `null` if
 * the user backed out. It is a parameter rather than a file dialog this composable opens itself for two
 * reasons: a chooser is a blocking desktop call that has no business being reached for from inside a
 * composition, and the copy is a write that belongs to the gateway beside every other write. A `null`
 * return means "cancelled", which is why it is not folded into the same value as an error.
 */
@Composable
internal fun CollectionEditorDialog(
    draft: CollectionEditorDraft,
    covers: CollectionCoverCache?,
    onPickCover: suspend () -> String?,
    onDismiss: () -> Unit,
    onConfirm: (name: String, description: String?, tagNames: List<String>, coverImagePath: String?) -> Unit,
) {
    val colors = AzkarTheme.colors
    val scope = rememberCoroutineScope()
    var picking by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss) {
        AzkarDialogSurface(modifier = Modifier.widthIn(min = 380.dp, max = 560.dp)) {
            Column {
                Text(
                    text = stringResource(draft.mode.titleResource),
                    color = colors.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(14.dp))
                CollectionNameField(draft = draft, colors = colors)
                if (draft.mode.editsDetails) {
                    CollectionDetailsFields(
                        draft = draft,
                        covers = covers,
                        picking = picking,
                        onPickCover = {
                            if (picking) {
                                return@CollectionDetailsFields
                            }
                            scope.launch {
                                picking = true
                                // Cleared even if the copy throws, so a failed pick cannot leave the
                                // button permanently disabled for the rest of the dialog's life.
                                try {
                                    onPickCover()?.let(draft::updateCoverImage)
                                } finally {
                                    picking = false
                                }
                            }
                        },
                    )
                }
                Spacer(Modifier.height(18.dp))
                DialogActions(
                    confirmLabel = stringResource(draft.mode.confirmResource),
                    onDismiss = onDismiss,
                    onConfirm = {
                        if (draft.submit()) {
                            onConfirm(
                                draft.submittedName,
                                draft.submittedDescription,
                                draft.submittedTagNames,
                                draft.coverImagePath,
                            )
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun CollectionNameField(
    draft: CollectionEditorDraft,
    colors: AzkarColors,
) {
    FieldLabel(stringResource(Res.string.collectionsDialogNameLabel))
    AzkarOutlinedField(
        value = draft.name,
        onValueChange = draft::updateName,
        placeholder = stringResource(Res.string.collectionsDialogNamePlaceholder),
        modifier = Modifier.fillMaxWidth(),
    )
    if (draft.nameRejected) {
        Text(
            text = stringResource(Res.string.collectionsDialogNameRequired),
            color = colors.dialogValidation,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 5.dp),
        )
    }
}

@Composable
private fun CollectionDetailsFields(
    draft: CollectionEditorDraft,
    covers: CollectionCoverCache?,
    picking: Boolean,
    onPickCover: () -> Unit,
) {
    Spacer(Modifier.height(12.dp))
    FieldLabel(stringResource(Res.string.collectionsDialogDescriptionLabel))
    AzkarOutlinedField(
        value = draft.description,
        onValueChange = draft::updateDescription,
        placeholder = stringResource(Res.string.collectionsDialogDescriptionPlaceholder),
        modifier = Modifier.fillMaxWidth(),
        singleLine = false,
        minHeight = 72.dp,
    )
    Spacer(Modifier.height(12.dp))
    FieldLabel(stringResource(Res.string.collectionsDialogTagsLabel))
    AzkarOutlinedField(
        value = draft.tagInput,
        onValueChange = draft::updateTagInput,
        placeholder = stringResource(Res.string.collectionsDialogTagsPlaceholder),
        supportingText = stringResource(Res.string.collectionsDialogTagsHint),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(12.dp))
    FieldLabel(stringResource(Res.string.collectionsDialogCoverLabel))
    CoverEditorRow(
        draft = draft,
        covers = covers,
        picking = picking,
        onPick = onPickCover,
    )
}

/**
 * The cover as it currently stands, with the two things the user can do about it.
 *
 * The preview is [CollectionCoverArt] rather than a plain image so that "no cover" and "a cover whose
 * file has since been deleted" render as the same thing the card will render as. Showing a broken
 * thumbnail here and a generated wash on the shelf would make a working collection look broken and a
 * broken one look fixed, which are both the opposite of what the user needs to see.
 *
 * Remove is offered only once there is something to remove: a button that appears and does nothing is
 * worse than no button, and the row already says plainly when the cover is the generated wash.
 */
@Composable
private fun CoverEditorRow(
    draft: CollectionEditorDraft,
    covers: CollectionCoverCache?,
    picking: Boolean,
    onPick: () -> Unit,
) {
    val colors = AzkarTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        CollectionCoverArt(
            iconKey = draft.coverIconKey(),
            coverImagePath = draft.coverImagePath,
            covers = covers,
            modifier = Modifier.size(COVER_PREVIEW_SIZE).clip(RoundedCornerShape(8.dp)),
            iconSize = 28.dp,
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text =
                    if (draft.coverImagePath == null) {
                        stringResource(Res.string.collectionsDialogCoverNone)
                    } else {
                        stringResource(Res.string.collectionsDialogCoverChosen)
                    },
                color = colors.textSecondary,
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            SecondaryButton(
                text =
                    if (picking) {
                        stringResource(Res.string.collectionsDialogCoverBusy)
                    } else {
                        stringResource(Res.string.collectionsActionChooseCover)
                    },
                onClick = onPick,
                enabled = !picking,
                modifier = Modifier.padding(top = 6.dp),
            )
            if (draft.coverImagePath != null) {
                SecondaryButton(
                    text = stringResource(Res.string.collectionsActionRemoveCover),
                    onClick = { draft.updateCoverImage(null) },
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

/**
 * The glyph the preview shows when there is no cover, taken from the same derivation the shelf uses.
 *
 * Reads raw label names rather than tags because the shelf's [CollectionPresenter.iconKeyForTags]
 * takes a loaded [com.azkar.domain.model.RemembranceCollection]'s tags and this dialog has only what
 * it was told — a name and a list of labels for a collection that does not exist yet. Both paths
 * sort the labels, take the first and fall back to the same key, so they agree by construction.
 */
private fun CollectionEditorDraft.coverIconKey(): String = CollectionPresenter.iconKeyForTagNames(submittedTagNames)

/**
 * The delete confirmation.
 *
 * Names the collection rather than asking about "this collection": on a shelf of a dozen tiles a
 * generic question is ambiguous, and by the time the answer turns out to be wrong the collection is
 * already gone. It also says what is *not* being deleted, because losing remembrances to a cleared
 * shelf is the failure this sentence exists to prevent.
 */
@Composable
internal fun ConfirmCollectionDeleteDialog(
    name: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val colors = AzkarTheme.colors
    Dialog(onDismissRequest = onDismiss) {
        AzkarDialogSurface(modifier = Modifier.widthIn(min = 340.dp, max = 460.dp)) {
            Column {
                Text(
                    text = stringResource(Res.string.collectionsDialogDeleteTitle),
                    color = colors.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(Res.string.collectionsDialogDeleteBody, name),
                    color = colors.textSecondary,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(18.dp))
                DialogActions(
                    confirmLabel = stringResource(Res.string.collectionsActionDelete),
                    onDismiss = onDismiss,
                    onConfirm = onConfirm,
                )
            }
        }
    }
}

/**
 * A caption above a field.
 *
 * The field itself takes a placeholder, which vanishes the moment there is text in it — so the label
 * has to be the thing that stays, or a filled-in field has no name at all.
 */
@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        color = AzkarTheme.colors.textSecondary,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(bottom = 5.dp),
    )
}

/** Cancel on the left, confirm on the right — the order every other dialog in the app uses. */
@Composable
private fun DialogActions(
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SecondaryButton(text = stringResource(Res.string.collectionsActionCancel), onClick = onDismiss)
        Spacer(Modifier.width(8.dp))
        PrimaryButton(text = confirmLabel, onClick = onConfirm)
    }
}

// ---- plumbing -------------------------------------------------------------------------------------

/**
 * What splits one label from the next in the field.
 *
 * A comma and not a semicolon, because a label is a word and a semicolon looks like part of one — and
 * because a comma is what the user will already try, since every other multi-value field they have used
 * this week takes one.
 */
private const val TAG_SEPARATOR: Char = ','

/** [TAG_SEPARATOR] as text, for joining a collection's labels back into the field it was edited in. */
private const val TAG_SEPARATOR_TEXT: String = ", "

/** Big enough to judge a picture by, small enough not to argue with the name field above it. */
private val COVER_PREVIEW_SIZE = 72.dp
