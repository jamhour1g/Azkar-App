package com.azkar.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.azkar.domain.model.TagSummary
import com.azkar.generated.resources.Res
import com.azkar.generated.resources.collectionsActionCancel
import com.azkar.generated.resources.tagsActionDelete
import com.azkar.generated.resources.tagsActionNew
import com.azkar.generated.resources.tagsDialogDeleteBody
import com.azkar.generated.resources.tagsDialogDeleteTitle
import com.azkar.generated.resources.tagsDialogNameLabel
import com.azkar.generated.resources.tagsDialogNamePlaceholder
import com.azkar.generated.resources.tagsDialogNameRequired
import com.azkar.generated.resources.tagsDialogNameTaken
import com.azkar.generated.resources.tagsDialogRenameTitle
import com.azkar.generated.resources.tagsUsageCollections
import com.azkar.generated.resources.tagsUsageRemembrances
import com.azkar.ui.components.AzkarDialogSurface
import com.azkar.ui.components.AzkarOutlinedField
import com.azkar.ui.components.PrimaryButton
import com.azkar.ui.components.SecondaryButton
import com.azkar.ui.library.data.TagSaveResult
import com.azkar.ui.theme.AzkarTheme
import com.azkar.ui.theme.AzkarThemePreview
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * `ui/library/TagDialogs.kt` — the three questions this screen asks before it changes anything.
 *
 * They live apart from the page because every one of them is a separate window with its own reasons
 * for existing, and none of them is any of the others: one field asked once for a new name and once
 * for a corrected one, and one confirmation that names what hiding a label costs. Keeping them here
 * leaves the page itself reading as a page.
 */

private val SMALL_GAP = 8.dp
private val DIALOG_MIN_WIDTH = 340.dp
private val DIALOG_MAX_WIDTH = 460.dp
private val DIALOG_GAP = 18.dp
private val NOTE_GAP = 8.dp
private val NOTE_GLYPH = 13.dp
private val NOTE_SP = 11.sp

/**
 * One field and one question: what is this tag called?
 *
 * Serves both adding and renaming, because the two differ only in which row they were opened from and
 * in what they do with the answer. The field starts empty for a new tag and holds the current name
 * for a rename, so the name being corrected is on screen to be corrected rather than retyped.
 *
 * The refusal is drawn under the field rather than raised as a toast. A toast outlives the dialog, so
 * a message about a field the user is no longer looking at is a message they have to remember, and
 * the one case that most needs reading — the name is taken — is the case a toast would race the next
 * dialog open. Holding the dialog keeps the typed name in it and the cursor where it was.
 *
 * [onSubmit] suspends so the dialog can hold itself open until the answer arrives, which is the only
 * reason it can show that answer at all; see [TagsState.create].
 */
@Composable
internal fun TagNameDialog(
    title: String,
    initialName: String,
    onSubmit: suspend (String) -> TagSaveResult?,
    onDismiss: () -> Unit,
    note: String? = null,
) {
    var name by remember { mutableStateOf(initialName) }
    var problem by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val colors = AzkarTheme.colors
    // Resolved here rather than where they are used, because the place they are used is inside the
    // coroutine below and a `stringResource` cannot be read from one — a coroutine is not a
    // composition, so it has no locale of its own to resolve against.
    val nameTakenMessage = stringResource(Res.string.tagsDialogNameTaken)
    val nameRequiredMessage = stringResource(Res.string.tagsDialogNameRequired)

    Dialog(onDismissRequest = onDismiss) {
        AzkarDialogSurface(modifier = Modifier.widthIn(min = DIALOG_MIN_WIDTH, max = DIALOG_MAX_WIDTH)) {
            Column {
                Text(
                    text = title,
                    color = colors.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(DIALOG_GAP))
                FieldLabel(text = stringResource(Res.string.tagsDialogNameLabel))
                Spacer(Modifier.height(SMALL_GAP))
                TagNameField(
                    name = name,
                    problem = problem,
                    onNameChanged = {
                        name = it
                        problem = null
                    },
                )
                if (note != null) {
                    TagNameNote(note)
                    Spacer(Modifier.height(DIALOG_GAP))
                }
                Spacer(Modifier.height(DIALOG_GAP))
                DialogFooter(
                    confirmLabel = stringResource(Res.string.tagsActionNew),
                    confirmEnabled = !busy,
                    onDismiss = onDismiss,
                    onConfirm = {
                        // Re-entry is refused rather than ignored, so a slow write cannot be sent
                        // twice by a second press — which for a create means two rows, because by the
                        // time the second press lands the first is no longer a duplicate.
                        if (busy) return@DialogFooter
                        busy = true
                        scope.launch {
                            val result = onSubmit(name)
                            busy = false
                            problem =
                                when (result) {
                                    TagSaveResult.Saved, null -> null
                                    TagSaveResult.NameTaken -> nameTakenMessage
                                    TagSaveResult.Missing, TagSaveResult.Blank -> nameRequiredMessage
                                }
                            if (result == TagSaveResult.Saved) {
                                onDismiss()
                            }
                        }
                    },
                )
            }
        }
    }
}

/**
 * The rule the field is about to apply, printed under it rather than kept for later.
 *
 * It used to sit in the footer of the tags page, which put a rule about *every* name on the page
 * where it read as a caption and got skimmed. The only user who needs it is the one about to type a
 * name they are unsure of, so it is printed while they are typing. Nothing here is an error, so it is
 * the muted ink rather than [com.azkar.ui.theme.AzkarTheme.colors]'s validation colour.
 */
@Composable
private fun TagNameNote(
    note: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = NOTE_GAP),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = Icons.Filled.Info,
            contentDescription = null,
            tint = AzkarTheme.colors.textSecondary,
            modifier = Modifier.size(NOTE_GLYPH),
        )
        Spacer(Modifier.width(SMALL_GAP))
        Text(
            text = note,
            color = AzkarTheme.colors.textSecondary,
            fontSize = NOTE_SP,
        )
    }
}

/**
 * The name field and the line under it, together.
 *
 * The refusal row is reserved rather than conditional so the dialog does not change height between
 * a clean one and one showing a refusal — a row appearing under the field is enough to make the
 * buttons jump, which reads as the dialog reacting to the typing.
 *
 * Typing clears the refusal rather than leaving it up, because the one thing it can say — that the
 * name is taken — stops being true the moment the name is different.
 */
@Composable
private fun TagNameField(
    name: String,
    problem: String?,
    onNameChanged: (String) -> Unit,
) {
    AzkarOutlinedField(
        value = name,
        onValueChange = onNameChanged,
        placeholder = stringResource(Res.string.tagsDialogNamePlaceholder),
        active = problem != null,
    )
    Box(modifier = Modifier.height(SMALL_GAP)) {
        problem?.let {
            Text(
                text = it,
                color = AzkarTheme.colors.dialogValidation,
                fontSize = 11.sp,
            )
        }
    }
}

/**
 * Asks before hiding a label, and says what will happen to it.
 *
 * The counts are repeated here rather than left on the row: a row's counts are a fact, and a
 * confirmation's counts are the thing being agreed to. They are stated as reversible on purpose — a
 * delete that could not be undone would deserve an explicit warning and one that can does not, and
 * saying so is what lets a user retire a label they are unsure about instead of living with it
 * forever.
 */
@Composable
internal fun TagDeleteDialog(
    tag: TagSummary,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val colors = AzkarTheme.colors
    Dialog(onDismissRequest = onDismiss) {
        AzkarDialogSurface(modifier = Modifier.widthIn(min = DIALOG_MIN_WIDTH, max = DIALOG_MAX_WIDTH)) {
            Column {
                Text(
                    text = stringResource(Res.string.tagsDialogDeleteTitle),
                    color = colors.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(DIALOG_GAP))
                Text(
                    text =
                        stringResource(
                            Res.string.tagsDialogDeleteBody,
                            tag.name,
                            pluralStringResource(
                                Res.plurals.tagsUsageCollections,
                                tag.collectionCount,
                                tag.collectionCount,
                            ),
                            pluralStringResource(
                                Res.plurals.tagsUsageRemembrances,
                                tag.remembranceCount,
                                tag.remembranceCount,
                            ),
                        ),
                    color = colors.textSecondary,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(DIALOG_GAP))
                DialogFooter(
                    confirmLabel = stringResource(Res.string.tagsActionDelete),
                    onDismiss = onDismiss,
                    onConfirm = onConfirm,
                )
            }
        }
    }
}

/**
 * Cancel beside confirm, in the arrangement the library's other dialogs use.
 *
 * Written out here rather than reused because the shared `DialogActions` in `CollectionDialogs.kt`
 * is private to that file, and these two dialogs are the first in a different one. Duplicating four
 * lines is the cheaper answer than loosening a visibility to cover three call sites.
 */
@Composable
private fun DialogFooter(
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    confirmEnabled: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SecondaryButton(
            text = stringResource(Res.string.collectionsActionCancel),
            onClick = onDismiss,
        )
        PrimaryButton(
            text = confirmLabel,
            onClick = onConfirm,
            enabled = confirmEnabled,
        )
    }
}

@Preview
@Composable
private fun TagDeleteDialogPreview() {
    AzkarThemePreview(width = 520.dp) {
        TagDeleteDialog(
            tag = previewTagSummary("morning", collections = 3, remembrances = 12, added = true),
            onDismiss = {},
            onConfirm = {},
        )
    }
}

@Preview
@Composable
private fun TagNameDialogPreview() {
    AzkarThemePreview(width = 520.dp) {
        TagNameDialog(
            title = stringResource(Res.string.tagsDialogRenameTitle),
            initialName = "morning",
            onSubmit = { TagSaveResult.Saved },
            onDismiss = {},
        )
    }
}
