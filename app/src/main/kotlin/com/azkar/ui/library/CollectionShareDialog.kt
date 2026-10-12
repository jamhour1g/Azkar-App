package com.azkar.ui.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.azkar.domain.model.Remembrance
import com.azkar.domain.model.RemembranceCollection
import com.azkar.generated.resources.Res
import com.azkar.generated.resources.collectionShareCopy
import com.azkar.generated.resources.collectionShareHint
import com.azkar.generated.resources.collectionShareTitle
import com.azkar.generated.resources.collectionsActionClose
import com.azkar.ui.library.data.CollectionExport
import com.azkar.ui.library.data.TransferFormat
import com.azkar.ui.theme.AzkarTheme
import org.jetbrains.compose.resources.stringResource

/**
 * Shows a collection as a document and offers to put it on the clipboard.
 *
 * There is no file dialog on purpose. The project carries no file-picker dependency, and the
 * clipboard is what a desktop user reaches for when moving a short list between two installs anyway:
 * they paste it into whatever they use to carry it, and the recipient pastes it back.
 *
 * The document is rendered rather than copied blind, because a transfer the user cannot read is a
 * transfer they cannot check before sending. Recitation progress is not in it either — a count is a
 * fact about one machine's habit, and importing it elsewhere would be a lie.
 */
@Composable
internal fun ShareCollectionDialog(
    collection: RemembranceCollection,
    format: TransferFormat,
    textOf: (Remembrance) -> String,
    coverImageBase64: String?,
    callbacks: ShareCallbacks,
) {
    // Keyed on the picture as well as the collection: the format can be switched after the dialog
    // opens, and the JSON document has to be rebuilt when it is, or switching to JSON would keep
    // showing the text document's contents.
    val document =
        remember(collection, format, textOf, coverImageBase64) {
            CollectionExport.exportAs(collection, format, textOf, coverImageBase64)
        }
    TransferDialogFrame(
        title = stringResource(Res.string.collectionShareTitle),
        onDismiss = callbacks.onDismiss,
        footer = {
            DialogFooter(
                cancelLabel = stringResource(Res.string.collectionsActionClose),
                confirmLabel = stringResource(Res.string.collectionShareCopy),
                onCancel = callbacks.onDismiss,
                onConfirm = { callbacks.onCopy(document) },
            )
        },
    ) {
        Column {
            Text(
                text = stringResource(Res.string.collectionShareHint),
                color = AzkarTheme.colors.textSecondary,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(10.dp))
            TransferFormatChips(format = format, onFormatChanged = callbacks.onFormatChanged)
            Spacer(Modifier.height(10.dp))
            DocumentPreview(text = document)
        }
    }
}

/**
 * What the share dialog needs from whoever raised it.
 *
 * Bundled rather than passed as three more parameters, following the shape the rest of the library
 * already uses for its callbacks (`HeaderCallbacks`, `DetailActions`): a dialog that takes a bag of
 * lambdas is easier to read at the call site than one whose arguments must be matched by position.
 */
@Immutable
internal data class ShareCallbacks(
    val onFormatChanged: (TransferFormat) -> Unit,
    val onCopy: (String) -> Unit,
    val onDismiss: () -> Unit,
)
