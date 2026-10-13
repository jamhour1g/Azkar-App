package com.azkar.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.azkar.domain.model.Remembrance
import com.azkar.generated.resources.Res
import com.azkar.generated.resources.collectionsActionCancel
import com.azkar.generated.resources.remembranceEditorArabicLabel
import com.azkar.generated.resources.remembranceEditorArabicPlaceholder
import com.azkar.generated.resources.remembranceEditorArabicRequired
import com.azkar.generated.resources.remembranceEditorEnglishLabel
import com.azkar.generated.resources.remembranceEditorEnglishPlaceholder
import com.azkar.generated.resources.remembranceEditorSave
import com.azkar.generated.resources.remembranceEditorTitle
import com.azkar.generated.resources.remembranceEditorTranslationHelp
import com.azkar.ui.components.AzkarDialogSurface
import com.azkar.ui.components.AzkarOutlinedField
import com.azkar.ui.components.PrimaryButton
import com.azkar.ui.components.SecondaryButton
import com.azkar.ui.library.data.CollectionPresenter
import com.azkar.ui.theme.AzkarTheme
import org.jetbrains.compose.resources.stringResource

/**
 * Lets the user correct what a remembrance says.
 *
 * Two boxes and no more: the Arabic, and the English beside it. That is deliberately the whole scope
 * — attribution, the narrator's chain and the grading are not things this dialog should let anyone
 * rewrite, because correcting a typo in a passage and rewriting where it came from are different
 * acts with different consequences for the reader, and a user fixing a transcription error has no
 * business being offered the second one.
 *
 * The English box is optional, and blank means *leave it alone* rather than *empty it*. A user who
 * fixes the Arabic has not said the English was wrong, and an editor that silently deleted a
 * translation they never touched would be worse than one that refused to open.
 *
 * Seeded from the live remembrance rather than from a draft object the caller maintains, because the
 * dialog has exactly one thing to edit and a second copy of it would only be a second thing to fall
 * out of date.
 */
@Composable
internal fun RemembranceEditorDialog(
    remembrance: Remembrance,
    onDismiss: () -> Unit,
    onConfirm: (arabic: String, english: String?) -> Unit,
) {
    val colors = AzkarTheme.colors
    val draft =
        remember(remembrance.id) {
            Draft(
                arabic = remembrance.textIn(CollectionPresenter.ARABIC_LOCALE),
                english = remembrance.textIn(CollectionPresenter.ENGLISH_LOCALE),
            )
        }
    Dialog(onDismissRequest = onDismiss) {
        // The Box inside AzkarDialogSurface has no arrangement of its own, so the children need a
        // Column of their own to stack. The reset dialog learned this the hard way.
        AzkarDialogSurface(modifier = Modifier.widthIn(min = 380.dp, max = 560.dp)) {
            Column {
                Text(
                    text = stringResource(Res.string.remembranceEditorTitle),
                    color = colors.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(14.dp))
                EditorField(
                    value = draft.arabic,
                    onValueChange = draft::typeArabic,
                    label = stringResource(Res.string.remembranceEditorArabicLabel),
                    placeholder = stringResource(Res.string.remembranceEditorArabicPlaceholder),
                    minHeight = EDITOR_ARABIC_HEIGHT,
                    direction = LayoutDirection.Rtl,
                )
                if (draft.arabicRejected) {
                    ValidationNote(stringResource(Res.string.remembranceEditorArabicRequired))
                }
                Spacer(Modifier.height(12.dp))
                EditorField(
                    value = draft.english,
                    onValueChange = draft::typeEnglish,
                    label = stringResource(Res.string.remembranceEditorEnglishLabel),
                    placeholder = stringResource(Res.string.remembranceEditorEnglishPlaceholder),
                    minHeight = EDITOR_ENGLISH_HEIGHT,
                    direction = LayoutDirection.Ltr,
                )
                Text(
                    text = stringResource(Res.string.remembranceEditorTranslationHelp),
                    color = colors.textSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Spacer(Modifier.height(18.dp))
                EditorButtons(draft = draft, onDismiss = onDismiss, onConfirm = onConfirm)
            }
        }
    }
}

/**
 * Cancel and Save, right-aligned, with Save refusing to close the dialog on an empty Arabic box.
 *
 * The refusal is the whole reason the buttons live beside the draft rather than beside the dialog:
 * whether the dialog may close and what it closes with are one decision, and a Save wired straight
 * to [onConfirm] is a Save that hands an empty string to the caller and lets it decide.
 */
@Composable
private fun EditorButtons(
    draft: Draft,
    onDismiss: () -> Unit,
    onConfirm: (arabic: String, english: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SecondaryButton(
            text = stringResource(Res.string.collectionsActionCancel),
            onClick = onDismiss,
        )
        Spacer(Modifier.width(8.dp))
        PrimaryButton(
            text = stringResource(Res.string.remembranceEditorSave),
            onClick = {
                if (draft.submit()) {
                    onConfirm(draft.arabic.trim(), draft.submittedEnglish)
                }
            },
        )
    }
}

/**
 * One labelled box, and the label above it.
 *
* A pair because the two boxes differ only in their draft slot and their height: splitting them into
 * 2 near-identical composables would mean the placeholder, the multiline setting and the width had
 *  to be kept in step by hand, and there is nothing in either that the other should not have.
 *
 * [direction] is a parameter rather than something inferred from [value] because these two boxes are
 * the one pair in the app whose language is known before anything is typed: one holds Arabic and one
 * holds English. Inferring would get an empty box wrong, and a box nobody has typed into is exactly
 * when the caret has to be in the right place — which is the whole point of the setting.
 */
@Composable
private fun EditorField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    minHeight: Dp,
    direction: LayoutDirection,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        FieldLabel(label)
        AzkarOutlinedField(
            value = value,
            onValueChange = onValueChange,
            placeholder = placeholder,
            modifier = Modifier.fillMaxWidth(),
            singleLine = false,
            minHeight = minHeight,
            textDirection = direction,
        )
    }
}

/**
 * The two boxes, held for the life of one dialog.
 *
 * A class rather than two remembered strings so that [submit] and the validation rule cannot drift
 * apart: there is one place that decides whether the dialog may close, and it is the same place that
 * decides what it closes with.
 *
 * The properties are snapshot state rather than plain fields, and that is load-bearing rather than
 * stylistic: [arabicRejected] is read while the dialog composes, so a plain `var` holding it would
 * never invalidate the composition and the note underneath the box would silently never appear. The
 * same would be true of the boxes themselves — a keystroke would rewrite the draft without the field
 * being told, and the two would drift apart.
 */
private class Draft(
    arabic: String,
    english: String,
) {
    var arabic: String by mutableStateOf(arabic)
        private set

    var english: String by mutableStateOf(english)
        private set

    private var submitted by mutableStateOf(false)

    fun typeArabic(text: String) {
        arabic = text
    }

    fun typeEnglish(text: String) {
        english = text
    }

    /**
     * Whether to scold the user about an empty Arabic box.
     *
     * Only after they have tried to save. Before that the box is simply empty, and a red line under a
     * field nobody has finished typing in is the dialog telling the user off for a first keystroke.
     */
    val arabicRejected: Boolean
        get() = submitted && arabic.isBlank()

    /**
     * The English to write, or `null` to write nothing at all.
     *
     * `null` is the instruction *keep what is there*, which is why [Remembrance.withTexts] treats it
     * as different from an empty string: a blank box is silence about the translation, not a request
     * to delete one the user may have spent time on.
     */
    val submittedEnglish: String?
        get() = english.ifBlank { null }

    /** The Arabic may not be blank: an empty passage is not a shorter one. */
    fun submit(): Boolean {
        submitted = true
        return arabic.isNotBlank()
    }
}

/** What this remembrance renders in [locale], or an empty string when it carries nothing for it. */
private fun Remembrance.textIn(locale: java.util.Locale): String = translations[locale]?.translationPair?.text.orEmpty()

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

@Composable
private fun ValidationNote(text: String) {
    Text(
        text = text,
        color = AzkarTheme.colors.dialogValidation,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 5.dp),
    )
}

// ---- metrics --------------------------------------------------------------------------------------

/** Tall enough to judge a paragraph of Arabic against, without pushing the dialog off a short window. */
private val EDITOR_ARABIC_HEIGHT = 96.dp

/** The translation is a sentence rather than a passage, and gets a box to match. */
private val EDITOR_ENGLISH_HEIGHT = 72.dp
