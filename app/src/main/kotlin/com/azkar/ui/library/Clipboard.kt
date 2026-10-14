package com.azkar.ui.library

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import java.awt.datatransfer.StringSelection

/** Writes plain text through Compose's suspendable clipboard API on the desktop host. */
@OptIn(ExperimentalComposeUiApi::class)
internal suspend fun Clipboard.setPlainText(text: String) {
    setClipEntry(ClipEntry(StringSelection(text)))
}
