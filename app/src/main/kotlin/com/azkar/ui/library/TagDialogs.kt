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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.azkar.domain.model.TagSummary
import com.azkar.generated.resources.Res
import com.azkar.generated.resources.collectionsActionCancel
import com.azkar.generated.resources.contentLanguageNote
import com.azkar.generated.resources.contentPrimaryLanguage
import com.azkar.generated.resources.contentTranslationFor
import com.azkar.generated.resources.contentTranslationRemove
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
import com.azkar.i18n.displayNameIn
import com.azkar.i18n.textDirectionOf
import com.azkar.ui.components.AzkarDialogSurface
import com.azkar.ui.components.AzkarFilterChip
import com.azkar.ui.components.AzkarOutlinedField
import com.azkar.ui.components.IconActionButton
import com.azkar.ui.components.PrimaryButton
import com.azkar.ui.components.SecondaryButton
import com.azkar.ui.library.data.CollectionPresenter
import com.azkar.ui.library.data.TagSaveResult
import com.azkar.ui.library.model.TranslationEntry
import com.azkar.ui.library.model.missingLanguages
import com.azkar.ui.library.model.survivingRename
import com.azkar.ui.theme.AzkarTheme
import com.azkar.ui.theme.AzkarThemePreview
import com.azkar.ui.theme.isRtlLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import java.util.Locale

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
 * The two answers a save can be refused with, resolved once before the coroutine that needs them.
 *
 * They cannot be read inside the write, because a coroutine is not a composition and has no locale of
 * its own to resolve against. Held as one value rather than two strings so the call site reads as a
 * single decision — which messages this dialog can say — instead of two loose arguments.
 */
@Immutable
private data class TagSaveMessages(
    val nameTaken: String,
    val nameRequired: String,
)

/**
 * The edits a translation section can make to the list it was handed.
 *
 * An interface rather than three lambdas because the section never invents any of them — it forwards all
 * three to whoever owns the list. Letting the section also *be* the holder is what keeps its own
 * parameter list short enough that the forwarding stays visible when someone reads it.
 */
private interface TagTranslationEdits {
    val translations: List<TranslationEntry>

    fun addTranslation(locale: Locale)

    fun changeTranslation(
        entry: TranslationEntry,
        value: String,
    )

    fun removeTranslation(entry: TranslationEntry)
}

/**
 * The half-typed name and its translations, and the one write that turns them into a tag.
 *
 * A class rather than four remembered values because those four cannot be judged apart: a save has to
 * refuse a name that is blank and a name that is taken, and both answers land on the same note under the
 * same box. Held apart they would mean the footer and the field each had to know about the other's state,
 * which is how a note ends up describing a save that never happened.
 *
 * The properties are snapshot state rather than plain fields, and that is load-bearing rather than
 * stylistic: [TagNameDialog] reads [busy] and [problem] while composing, so a plain `var` would never
 * invalidate the composition and the footer would stay enabled for the whole of a slow write.
 */
@Stable
private class TagNameDraft(
    private val scope: CoroutineScope,
    /**
     * The name as it was when the dialog opened, kept for the life of the draft because
     * [submit] needs it to tell a translation the user rewrote from one they merely left sitting there.
     * See [survivingRename].
     */
    private val openedWith: String,
    /** The other-language names as they were when the dialog opened. The same reason as [openedWith]. */
    private val openedWithTranslations: List<TranslationEntry>,
    private val onSubmit: suspend (String, Map<Locale, String>) -> TagSaveResult?,
    private val onDismiss: () -> Unit,
    private val messages: TagSaveMessages,
) : TagTranslationEdits {
    /** The name as it stands, which the header's monogram is drawn from as the user types. */
    var name by mutableStateOf(openedWith)
        private set

    /** What is wrong with the name, or `null`. Cleared by the next keystroke, never on its own. */
    var problem by mutableStateOf<String?>(null)
        private set

    /** Whether a write is in flight. Blocks a second press, which for a create means a second row. */
    var busy by mutableStateOf(false)
        private set

    override var translations by mutableStateOf(openedWithTranslations)
        private set

    fun rename(value: String) {
        name = value
        problem = null
    }

    override fun addTranslation(locale: Locale) {
        translations = translations + TranslationEntry(locale)
    }

    override fun changeTranslation(
        entry: TranslationEntry,
        value: String,
    ) {
        translations = translations.replacing(entry.locale) { it.copy(name = value) }
    }

    override fun removeTranslation(entry: TranslationEntry) {
        translations = translations.filterNot { it.locale == entry.locale }
    }

    /**
     * Write the tag, then close on success or explain the refusal.
     *
     * A translation the user opened but did not fill in is dropped rather than refused on the way in:
     * an empty language is the normal state of one someone has just added and has not typed yet, and
     * treating that as an error would put a red note under a box nobody has finished writing.
     */
    fun submit() {
        if (busy) return
        busy = true
        scope.launch {
            val result =
                onSubmit(
                    name,
                    translations
                        .survivingRename(
                            renamedFrom = openedWith,
                            renamedTo = name,
                            opened = openedWithTranslations,
                        ).toNameTranslations(),
                )
            busy = false
            problem =
                when (result) {
                    TagSaveResult.Saved, null -> null
                    TagSaveResult.NameTaken -> messages.nameTaken
                    TagSaveResult.Missing, TagSaveResult.Blank -> messages.nameRequired
                }
            if (result == TagSaveResult.Saved) {
                onDismiss()
            }
        }
    }
}

/**
 * The canonical name, its field, and whatever note the last refusal left under it.
 *
 * Split out of [TagNameDialog] because the language line above the box is not part of the field: it
 * exists to say *which* language this box writes in, and it is the only place in the app that knows a
 * box's language before anything has been typed into it.
 */
@Composable
private fun TagPrimaryNameBlock(
    draft: TagNameDraft,
    nameLocale: Locale,
    note: String?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        FieldLabel(text = stringResource(Res.string.tagsDialogNameLabel))
        Text(
            text =
                stringResource(
                    Res.string.contentPrimaryLanguage,
                    nameLocale.displayNameIn(AzkarTheme.locale),
                ),
            color = AzkarTheme.colors.textSecondary,
            fontSize = NOTE_SP,
        )
        Spacer(Modifier.height(SMALL_GAP))
        TagNameField(
            name = draft.name,
            problem = draft.problem,
            onNameChanged = draft::rename,
        )
        if (note != null) {
            TagNameNote(note)
        }
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
 * A tag may be given a name in more than one language, which is the difference between a tag that
 * reads "المساء" to the person who wrote it and "evening" to everybody else, and one that reads the
 * same in both. [initialTranslations] seeds the other-language fields when renaming; a create has
 * none, because the tag does not exist yet.
 *
 * @param subtitle what the window is about, under the title; null for a tag that does not exist yet.
 * @param note the rule this field applies, printed under it. See [TagNameNote].
 */
@Composable
internal fun TagNameDialog(
    title: String,
    initialName: String,
    confirmLabel: String,
    onSubmit: suspend (String, Map<Locale, String>) -> TagSaveResult?,
    onDismiss: () -> Unit,
    subtitle: String? = null,
    note: String? = null,
    initialTranslations: List<TranslationEntry> = emptyList(),
    initialNameLocale: Locale = AzkarTheme.locale,
) {
    val scope = rememberCoroutineScope()
    // Resolved here rather than inside `remember`, because a `stringResource` is a composable call and
    // this block is not a composition — the same reason the messages cannot be read from the coroutine
    // in [TagNameDraft.submit], which is not one either.
    val nameTakenMessage = stringResource(Res.string.tagsDialogNameTaken)
    val nameRequiredMessage = stringResource(Res.string.tagsDialogNameRequired)
    // Keyed on what the dialog was opened with, not on the scope: the scope survives every recomposition
    // and keying on it would rebuild the draft — and throw away what the user has typed — on each pass.
    val draft =
        remember(initialName, initialTranslations) {
            TagNameDraft(
                scope = scope,
                openedWith = initialName,
                openedWithTranslations = initialTranslations,
                onSubmit = onSubmit,
                onDismiss = onDismiss,
                messages =
                    TagSaveMessages(
                        nameTaken = nameTakenMessage,
                        nameRequired = nameRequiredMessage,
                    ),
            )
        }

    Dialog(onDismissRequest = onDismiss) {
        AzkarDialogSurface(modifier = Modifier.widthIn(min = DIALOG_MIN_WIDTH, max = DIALOG_MAX_WIDTH)) {
            Column {
                TagDialogHeader(markName = draft.name, title = title, subtitle = subtitle)
                Spacer(Modifier.height(BODY_GAP))
                TagPrimaryNameBlock(draft = draft, nameLocale = initialNameLocale, note = note)
                Spacer(Modifier.height(BODY_GAP))
                TagTranslationSection(
                    name = draft.name,
                    nameLocale = initialNameLocale,
                    edits = draft,
                )
                Spacer(Modifier.height(BODY_GAP))
                DialogFooter(
                    confirmLabel = confirmLabel,
                    confirmEnabled = !draft.busy,
                    onDismiss = onDismiss,
                    onConfirm = draft::submit,
                )
            }
        }
    }
}

/**
 * The other-language names for this tag, and the chips that start one.
 *
 * Built here rather than beside the collection editor's because a tag is not a pair: it has a name and
 * nothing else, so this is one field per language where that is two.
 *
 * The canonical name's recorded language is never offered. The field above is already that language,
 * and offering a second box for it would ask for the same word twice and leave the reader to work out
 * which one wins.
 */
@Composable
private fun TagTranslationSection(
    name: String,
    nameLocale: Locale,
    edits: TagTranslationEdits,
) {
    val locale = AzkarTheme.locale
    val available =
        remember(edits.translations, name, nameLocale, locale) {
            missingLanguages(
                name = name,
                existing = edits.translations,
                uiLocale = locale,
                nameLocale = nameLocale,
            )
        }
    if (available.isEmpty() && edits.translations.isEmpty()) return

    Column {
        Text(
            text = stringResource(Res.string.contentLanguageNote),
            color = AzkarTheme.colors.textSecondary,
            fontSize = NOTE_SP,
        )
        Spacer(Modifier.height(SMALL_GAP))
        edits.translations.forEach { entry ->
            TagTranslationBlock(
                entry = entry,
                onNameChanged = edits::changeTranslation,
                onRemove = edits::removeTranslation,
            )
            Spacer(Modifier.height(SMALL_GAP))
        }
        if (available.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(TRANSLATION_CHIP_GAP)) {
                available.forEach { candidate ->
                    AzkarFilterChip(
                        text = candidate.displayNameIn(locale),
                        selected = false,
                        leadingIcon = Icons.Filled.Add,
                        onClick = { edits.addTranslation(candidate) },
                    )
                }
            }
        }
    }
}

/** One language's field, under a heading naming it, with the button that throws it away. */
@Composable
private fun TagTranslationBlock(
    entry: TranslationEntry,
    onNameChanged: (TranslationEntry, String) -> Unit,
    onRemove: (TranslationEntry) -> Unit,
) {
    val ambient = if (isRtlLayout()) LayoutDirection.Rtl else LayoutDirection.Ltr
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(Res.string.contentTranslationFor, entry.locale.displayNameIn(AzkarTheme.locale)),
                color = AzkarTheme.colors.textPrimary,
                fontSize = BODY_SP,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(SMALL_GAP))
            IconActionButton(
                icon = Icons.Filled.Close,
                contentDescription = stringResource(Res.string.contentTranslationRemove),
                onClick = { onRemove(entry) },
                iconSize = TRANSLATION_REMOVE_GLYPH,
            )
        }
        AzkarOutlinedField(
            value = entry.name,
            onValueChange = { onNameChanged(entry, it) },
            placeholder = entry.locale.displayNameIn(AzkarTheme.locale),
            textDirection = textDirectionOf(entry.name, ambient),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Keeps the list pure: no entry is edited in place, so the row below it cannot mutate under the one above. */
private fun List<TranslationEntry>.replacing(
    locale: Locale,
    change: (TranslationEntry) -> TranslationEntry,
): List<TranslationEntry> = map { if (it.locale == locale) change(it) else it }

/**
 * The storage shape of the fields above: one name per language, blanks dropped.
 *
 * A language the user opened and then left empty is an entry they chose to abandon, not a request to
 * save an empty name — which the column's not-blank check would refuse, turning a shrug into an error.
 */
private fun List<TranslationEntry>.toNameTranslations(): Map<Locale, String> =
    mapNotNull { entry ->
        entry.name
            .trim()
            .takeIf { it.isNotEmpty() }
            ?.let { entry.locale to it }
    }.toMap()

/**
 * The tag's other-language names, in a shape the dialog can hold.
 *
 * Skipped when the name is blank and ordered by language, so the boxes arrive in the same order every
 * time the same tag is opened — a row that reshuffled itself between visits would move a field out
 * from under the pointer the way a rename used to.
 */
internal fun TagSummary.translationEntries(): List<TranslationEntry> =
    translations.entries
        .mapNotNull { (locale, name) ->
            name.trim().takeIf { it.isNotEmpty() }?.let { TranslationEntry(locale = locale, name = it) }
        }.sortedBy { it.locale.language }

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
 *
 * The field takes its direction from what has been typed rather than from the app's language, because
 * the caret for an Arabic tag name belongs on the right even when everything around it is English.
 */
@Composable
private fun TagNameField(
    name: String,
    problem: String?,
    onNameChanged: (String) -> Unit,
) {
    val ambient = if (isRtlLayout()) LayoutDirection.Rtl else LayoutDirection.Ltr
    AzkarOutlinedField(
        value = name,
        onValueChange = onNameChanged,
        placeholder = stringResource(Res.string.tagsDialogNamePlaceholder),
        active = problem != null,
        textDirection = textDirectionOf(name, ambient),
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
 *
 * The name in the sentence is the one this reader can read, while the mark beside the title is still
 * drawn from the stored one — see `TagsScreen`'s `TagAvatar` for why those two are allowed to differ.
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
                            tag.displayName(AzkarTheme.locale),
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
            onSubmit = { _, _ -> TagSaveResult.Saved },
            onDismiss = {},
        )
    }
}

// The window's own measurements. The mark is the list's tile at window size, and the type is the
// reminder dialog's — a dialog that is about a name rather than a sentence wants a smaller one than
// the page title it is imitating.
private val DIALOG_MIN_WIDTH = 340.dp
private val DIALOG_MAX_WIDTH = 460.dp

// The two measurements the translation chips and their remove buttons need, matching the ones the
// collection editor uses for the same two controls so a name in a second language looks the same
// wherever it was typed.
private val TRANSLATION_CHIP_GAP = 10.dp
private val TRANSLATION_REMOVE_GLYPH = 14.dp
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
