package com.azkar.ui.library

import androidx.compose.runtime.Composable
import com.azkar.ui.theme.AzkarVariantsPreview

@AzkarVariantsPreview
@Composable
internal fun ConfirmCollectionDeleteDialogPreview() {
    ConfirmCollectionDeleteDialog(name = "Morning Azkar", onDismiss = {}, onConfirm = {})
}
