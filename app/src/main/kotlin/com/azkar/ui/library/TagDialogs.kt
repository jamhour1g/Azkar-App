package com.azkar.ui.library

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.azkar.domain.model.TagSummary
import com.azkar.generated.resources.Res
import com.azkar.generated.resources.collectionsActionCancel
import com.azkar.generated.resources.tagsActionDelete
import com.azkar.generated.resources.tagsActionSave
import com.azkar.generated.resources.tagsDialogDeleteBody
import com.azkar.generated.resources.tagsDialogDeleteNote
import com.azkar.generated.resources.tagsDialogDeleteTitle
import com.azkar.generated.resources.tagsDialogNameLabel
import com.azkar.generated.resources.tagsDialogNamePlaceholder
import com.azkar.generated.resources.tagsDialogNameRequired
import com.azkar.generated.resources.tagsDialogNameTaken
import com.azkar.generated.resources.tagsDialogRenameCurrent
import com.azkar.generated.resources.tagsDialogRenameNote
import com.azkar.generated.resources.tagsDialogRenameTitle
import com.azkar.generated.resources.tagsUsageCollections
import com.azkar.generated.resources.tagsUsageRemembrances
import com.azkar.ui.components.AzkarDialogSurface
import com.azkar.ui.components.AzkarOutlinedField
import com.azkar.ui.components.PrimaryButton
import com.azkar.ui.components.SecondaryButton
import com.azkar.ui.library.data.CollectionPresenter
import com.azkar.ui.library.data.TagSaveResult
import com.azkar.ui.theme.AzkarTheme
import com.azkar.ui.theme.AzkarThemePreview
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

// * `ui/library/TagDialogs.kt` — the three questions this screen asks before it changes anything, and
// * the chrome they share.
// *
// * They live apart from the page because every one of them is a separate window with its own reasons
// * for existing, and none of them is any of the others: one field asked once for a new name and once
// * for a corrected one, and one confirmation that names what hiding a label costs. Keeping them here
// * leaves the page itself reading as a page.
// *
// * They share [TagDialogHeader] and [TagMarkTile], which is the point of the redesign. Each of these
// * windows was a bold word, a label and a field — enough to answer the question and nothing to look
// * at, which made three of them on one screen feel like three unrelated popups rather than three views
// * of the same vocabulary. What they now share is the mark: the tile a tag wears everywhere else in
// * the app, sitting beside the title, so the window is already about *this* tag before a word of it is
// * read. On the name dialog it is drawn from what is being typed, which makes it the one control on
// * screen that answers "what will this look like" while the name is still unfinished.
//

/**
 * A title, what it is a title *of*, and the tile that says which tag.
 *
 * The subtitle is the reminder-dialog's, in the colour that reminder uses for it — the reminder dialog
 * already establishes that a dialog may name its subject under its own title, and a window whose
 * subject is a name rather than a sentence needs somewhere to put it.
 *
 * [subtitle] is null rather than empty for the create window, which has no subject yet. A row of
 * empty space under a title is worse than a tighter window.
 */
@Composable
internal fun TagDialogHeader(
    markName: String,
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
) {
    val colors = AzkarTheme.colors
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TagMarkTile(name = markName)
        Spacer(Modifier.width(MARK_GAP))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = colors.textPrimary,
                fontSize = TITLE_SP,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = colors.primaryStrong,
                    fontSize = SUBTITLE_SP,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = SUBTITLE_GAP),
                )
            }
        }
    }
}

/**
 * The tile a tag wears in the list, at window size.
 *
 * Deliberately the *same* tile and not a lookalike: [tagAvatarBrush] and [iconForCollection] are the
 * list's own, so the mark here is the one the row will carry. Drawn from what is typed rather than
 * from the saved name, which is the only thing on this window that changes as you type, and it
 * changes because the answer is genuinely different — a tag named "evening" is not the tag the user
 * had in mind when they named it "morning".
 *
 * A blank name still gets a tile rather than a hole, because [CollectionPresenter.iconKeyFor] falls
 * back to a glyph for a name it does not recognise and the fallback is the point: it is what an
 * unrecognised name will look like.
 */
@Composable
internal fun TagMarkTile(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = MARK_SIZE,
) {
    val colors = AzkarTheme.colors
    Box(
        modifier =
            modifier
                .size(size)
                .clip(RoundedCornerShape(MARK_SHAPE))
                .background(tagAvatarBrush(name)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = iconForCollection(CollectionPresenter.iconKeyFor(name)),
            contentDescription = null,
            tint = colors.primaryStrong.copy(alpha = MARK_GLYPH_ALPHA),
            modifier = Modifier.size(size * MARK_GLYPH_RATIO),
        )
    }
}

/**
 * One field and one question: what is this tag called?
 *
 * Serves both adding and renaming, because the two differ only in which row they were opened from, in
 * what they do with the answer, and in what their confirm button says. [confirmLabel] exists because
 * it used not to: the rename window's button read "New tag", which named the other action on the same
 * screen and described nothing about the one in front of the user.
 *
 * The refusal is drawn under the field rather than raised as a toast. A toast outlives the dialog, so
 * a message about a field the user is no longer looking at is a message they have to remember, and
 * the one case that most needs reading — the name is taken — is the case a toast would race the next
 * dialog open. Holding the dialog keeps the typed name in it and the cursor where it was.
 *
 * [onSubmit] suspends so the dialog can hold itself open until the answer arrives, which is the only
 * reason it can show that answer at all; see [TagsState.create].
 *
 * @param subtitle what the window is about, under the title; null for a tag that does not exist yet.
 * @param note the rule this field applies, printed under it. See [TagNameNote].
 */
@Composable
internal fun TagNameDialog(
    title: String,
    initialName: String,
    confirmLabel: String,
    onSubmit: suspend (String) -> TagSaveResult?,
    onDismiss: () -> Unit,
    subtitle: String? = null,
    note: String? = null,
) {
    var name by remember { mutableStateOf(initialName) }
    var problem by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // Resolved here rather than where they are used, because the place they are used is inside the
    // coroutine below and a `stringResource` cannot be read from one — a coroutine is not a
    // composition, so it has no locale of its own to resolve against.
    val nameTakenMessage = stringResource(Res.string.tagsDialogNameTaken)
    val nameRequiredMessage = stringResource(Res.string.tagsDialogNameRequired)

    Dialog(onDismissRequest = onDismiss) {
        AzkarDialogSurface(modifier = Modifier.widthIn(min = DIALOG_MIN_WIDTH, max = DIALOG_MAX_WIDTH)) {
            Column {
                TagDialogHeader(markName = name, title = title, subtitle = subtitle)
                Spacer(Modifier.height(BODY_GAP))
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
                }
                Spacer(Modifier.height(BODY_GAP))
                DialogFooter(
                    confirmLabel = confirmLabel,
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
 * the muted ink rather than the theme's validation colour.
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
                fontSize = NOTE_SP,
            )
        }
    }
}

/**
 * Asks before hiding a label, and says what will happen to it.
 *
 * The counts are repeated here rather than left on the row: a row's counts are a fact, and a
 * confirmation's counts are the thing being agreed to. What it costs is a sentence and what it can be
 * undone is a note, rather than one paragraph carrying both — the sentence is what the user is being
 * asked to accept, and the note is the reassurance that makes accepting it safe. Mixing them meant
 * the reassuring half read as another clause of the warning.
 */
@Composable
internal fun TagDeleteDialog(
    tag: TagSummary,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        AzkarDialogSurface(modifier = Modifier.widthIn(min = DIALOG_MIN_WIDTH, max = DIALOG_MAX_WIDTH)) {
            Column {
                TagDialogHeader(
                    markName = tag.name,
                    title = stringResource(Res.string.tagsDialogDeleteTitle),
                    subtitle = null,
                )
                Spacer(Modifier.height(BODY_GAP))
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
                    color = AzkarTheme.colors.textSecondary,
                    fontSize = BODY_SP,
                )
                TagNameNote(stringResource(Res.string.tagsDialogDeleteNote))
                Spacer(Modifier.height(BODY_GAP))
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
 * is private to that file, and these windows are the first in a different one. Duplicating four
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

// ---- previews ------------------------------------------------------------------------------------

@Preview
@Composable
private fun TagDeleteDialogPreview() {
    AzkarThemePreview(width = 520.dp) {
        TagDeleteDialog(
            tag = previewTagSummary("evening", collections = 1, remembrances = 54, added = true),
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
            initialName = "evening",
            confirmLabel = stringResource(Res.string.tagsActionSave),
            subtitle = stringResource(Res.string.tagsDialogRenameCurrent, "evening"),
            note = stringResource(Res.string.tagsDialogRenameNote),
            onSubmit = { TagSaveResult.Saved },
            onDismiss = {},
        )
    }
}

// The window's own measurements. The mark is the list's tile at window size, and the type is the
// reminder dialog's — a dialog that is about a name rather than a sentence wants a smaller one than
// the page title it is imitating.
private val DIALOG_MIN_WIDTH = 340.dp
private val DIALOG_MAX_WIDTH = 460.dp
private val BODY_GAP = 18.dp
private val BODY_SP = 13.sp
private val SMALL_GAP = 8.dp
private val NOTE_GAP = 8.dp
private val NOTE_GLYPH = 13.dp
private val NOTE_SP = 11.sp
private val TITLE_SP = 16.sp
private val SUBTITLE_SP = 12.sp
private val SUBTITLE_GAP = 3.dp
private val MARK_SIZE = 40.dp
private val MARK_SHAPE = 13.dp
private val MARK_GAP = 14.dp
private const val MARK_GLYPH_RATIO = 0.5f
private const val MARK_GLYPH_ALPHA = 0.35f
