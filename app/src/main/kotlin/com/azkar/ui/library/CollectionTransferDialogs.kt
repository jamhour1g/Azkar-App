package com.azkar.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.azkar.generated.resources.Res
import com.azkar.generated.resources.collectionImportFormatJson
import com.azkar.generated.resources.collectionImportFormatText
import com.azkar.ui.components.AzkarCard
import com.azkar.ui.components.AzkarDialogSurface
import com.azkar.ui.components.AzkarFilterChip
import com.azkar.ui.components.PrimaryButton
import com.azkar.ui.components.SecondaryButton
import com.azkar.ui.library.data.TransferFormat
import com.azkar.ui.theme.AzkarTheme
import com.azkar.ui.theme.AzkarThemePreview
import org.jetbrains.compose.resources.stringResource

/*
 * The frame the import and share dialogs share.
 *
 * They are two halves of one gesture — what you copy out is what you paste in — so the frame, the
 * encoding switch and the footer live here and each dialog is only what is between the heading and
 * the buttons. Sharing shows a finished document; importing shows a decision the user has to agree
 * to. That difference is the whole reason they are two dialogs and not one with a mode.
 */

private val DIALOG_WIDTH = 560.dp
private val LABEL_SP = 11.sp
private val BODY_SP = 12.sp

/**
 * A heading, a scrolling body and a footer of buttons.
 *
 * The body scrolls rather than the whole surface because the footer holds the two buttons the user
 * is reaching for, and a dialog whose actions scroll out of sight mid-decision is a dialog that
 * gets dismissed instead.
 */
@Composable
internal fun TransferDialogFrame(
    title: String,
    onDismiss: () -> Unit,
    footer: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        AzkarDialogSurface(modifier = Modifier.widthIn(max = DIALOG_WIDTH)) {
            Column {
                Text(
                    text = title,
                    color = AzkarTheme.colors.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(12.dp))
                Column(
                    modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                ) { content() }
                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = AzkarTheme.colors.border)
                Spacer(Modifier.height(12.dp))
                footer()
            }
        }
    }
}

/** The two encoding choices, as a chip row. */
@Composable
internal fun TransferFormatChips(
    format: TransferFormat,
    onFormatChanged: (TransferFormat) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AzkarFilterChip(
            text = stringResource(Res.string.collectionImportFormatJson),
            selected = format == TransferFormat.JSON,
            onClick = { onFormatChanged(TransferFormat.JSON) },
        )
        AzkarFilterChip(
            text = stringResource(Res.string.collectionImportFormatText),
            selected = format == TransferFormat.TEXT,
            onClick = { onFormatChanged(TransferFormat.TEXT) },
        )
    }
}

@Preview
@Composable
private fun TransferFormatChipsPreview() {
    AzkarThemePreview {
        Column(
            modifier = Modifier.width(360.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TransferFormatChips(format = TransferFormat.JSON, onFormatChanged = {})
            TransferFormatChips(format = TransferFormat.TEXT, onFormatChanged = {})
        }
    }
}

/** Cancel on the left, the confirming action on the right — the order every other dialog uses. */
@Composable
internal fun DialogFooter(
    cancelLabel: String,
    confirmLabel: String,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SecondaryButton(text = cancelLabel, onClick = onCancel)
        PrimaryButton(text = confirmLabel, onClick = onConfirm)
    }
}

@Preview
@Composable
private fun DialogFooterPreview() {
    AzkarThemePreview {
        DialogFooter(
            cancelLabel = "Cancel",
            confirmLabel = "Import",
            onCancel = {},
            onConfirm = {},
        )
        Spacer(Modifier.height(16.dp))
        DialogFooter(
            cancelLabel = "Cancel",
            confirmLabel = "Import",
            onCancel = {},
            onConfirm = {},
        )
    }
}

/** A small heading above a block inside a dialog. */
@Composable
internal fun TransferLabel(text: String) {
    Text(
        text = text,
        color = AzkarTheme.colors.textSecondary,
        fontSize = LABEL_SP,
        fontWeight = FontWeight.SemiBold,
    )
}

@Preview
@Composable
private fun TransferLabelPreview() {
    AzkarThemePreview {
        TransferLabel(text = "What will be transferred")
    }
}

/** A one-line summary figure, used by the plan and the report alike. */
@Composable
internal fun TransferStatLine(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, color = AzkarTheme.colors.textSecondary, fontSize = BODY_SP)
        Spacer(Modifier.width(10.dp))
        Text(
            text = value,
            color = AzkarTheme.colors.textPrimary,
            fontSize = BODY_SP,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Preview
@Composable
private fun TransferStatLinePreview() {
    AzkarThemePreview {
        Column(modifier = Modifier.width(320.dp)) {
            TransferStatLine(label = "Collections", value = "12")
            TransferStatLine(label = "Remembrances", value = "96")
            TransferStatLine(label = "Skipped", value = "0")
        }
    }
}

/**
 * A finished document, shown rather than hidden.
 *
 * A card with a `Column` inside it rather than a read-only text field: a field invites editing, and
 * the whole point of the preview is that the user can check the text without being tempted to change
 * it. `AzkarCard` is a `Box`, so the column here is not optional.
 */
@Composable
internal fun DocumentPreview(
    text: String,
    modifier: Modifier = Modifier,
) {
    AzkarCard(
        modifier = modifier.fillMaxWidth().heightIn(max = 190.dp),
        contentPadding = 12.dp,
    ) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Text(
                text = text,
                color = AzkarTheme.colors.textSecondary,
                fontSize = BODY_SP,
            )
        }
    }
}

@Preview
@Composable
private fun DocumentPreviewPreview() {
    AzkarThemePreview {
        DocumentPreview(
            text = """{"name":"Morning Azkar","members":[{"text":"أَسْتَغْفِرُ اللَّهَ","source":"Abu Dawud 1516"}]}""",
            modifier = Modifier.width(420.dp),
        )
    }
}

/** A titled, unboxed note: either an explanation or something that went wrong. */
@Composable
internal fun TransferNotice(
    title: String,
    hint: String,
    isProblem: Boolean,
) {
    Column {
        Text(
            text = title,
            color = if (isProblem) AzkarTheme.colors.dialogValidation else AzkarTheme.colors.textPrimary,
            fontSize = BODY_SP,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        Text(text = hint, color = AzkarTheme.colors.textSecondary, fontSize = LABEL_SP)
    }
}

@Preview
@Composable
private fun TransferNoticePreview() {
    AzkarThemePreview {
        Column(
            modifier = Modifier.width(420.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TransferNotice(
                title = "Recitation progress is not included",
                hint = "A count is a fact about one machine's habit.",
                isProblem = false,
            )
            TransferNotice(
                title = "2 remembrances could not be matched",
                hint = "They will be skipped rather than guessed at.",
                isProblem = true,
            )
        }
    }
}

/** A bullet list of names, used for the collections we refuse and the passages we do not know. */
@Composable
internal fun TransferNameList(names: List<String>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        names.forEach { name ->
            Text(
                text = "• $name",
                color = AzkarTheme.colors.textPrimary,
                fontSize = LABEL_SP,
            )
        }
    }
}

@Preview
@Composable
private fun TransferNameListPreview() {
    AzkarThemePreview {
        Column(
            modifier = Modifier.width(360.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TransferNameList(names = listOf("Morning Azkar", "Evening Azkar", "After Prayer", "For the car"))
            TransferNameList(names = emptyList())
        }
    }
}
