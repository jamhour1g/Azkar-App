package com.azkar.ui.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.azkar.generated.resources.Res
import com.azkar.generated.resources.settingsClose
import com.azkar.generated.resources.settingsUpdateCopyCommands
import com.azkar.generated.resources.settingsUpdateLinuxInfoBody
import com.azkar.generated.resources.settingsUpdateLinuxInfoLink
import com.azkar.generated.resources.settingsUpdateLinuxInfoTitle
import com.azkar.ui.components.AzkarDialogSurface
import com.azkar.ui.components.AzkarTooltip
import com.azkar.ui.components.SecondaryButton
import com.azkar.ui.theme.AzkarComponentShowcasePreview
import com.azkar.ui.theme.AzkarTheme
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun LinuxRepositoryInfoContent(
    onCopyCommands: () -> Unit,
    onOpenGuide: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(Res.string.settingsUpdateLinuxInfoTitle),
            color = AzkarTheme.colors.textPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            stringResource(Res.string.settingsUpdateLinuxInfoBody),
            color = AzkarTheme.colors.textSecondary,
            fontSize = 13.sp,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onOpenGuide) {
                Text(stringResource(Res.string.settingsUpdateLinuxInfoLink))
            }
            Spacer(Modifier.weight(1f))
            val copyLabel = stringResource(Res.string.settingsUpdateCopyCommands)
            AzkarTooltip(tooltip = copyLabel) {
                IconButton(
                    onClick = onCopyCommands,
                    modifier =
                        Modifier.size(40.dp).semantics(mergeDescendants = true) {
                            contentDescription = copyLabel
                        },
                ) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(20.dp))
                }
            }
            SecondaryButton(text = stringResource(Res.string.settingsClose), onClick = onDismiss)
        }
    }
}

@AzkarComponentShowcasePreview
@Composable
internal fun UpdateLinuxRepositoryInfoPreview() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AzkarDialogSurface(modifier = Modifier.widthIn(min = 380.dp, max = 520.dp)) {
            LinuxRepositoryInfoContent(onCopyCommands = {}, onOpenGuide = {}, onDismiss = {})
        }
    }
}
