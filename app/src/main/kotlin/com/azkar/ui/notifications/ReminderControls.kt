package com.azkar.ui.notifications

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.azkar.ui.components.AzkarToggleSwitch
import com.azkar.ui.theme.AzkarSpacing
import com.azkar.ui.theme.AzkarTheme

/**
 * The switch row that is specific to this screen.
 *
 * Everything else it uses — the card, the labelled row, the dropdown, the stepper, the text field —
 * moved to [com.azkar.ui.components.SettingsControls] when the prayer and appearance sections left
 * for the settings page, and the clock moved to [com.azkar.ui.components.AzkarTimePicker]. Only the
 * one control that says "this member is in the set" stayed, because it is the only one whose meaning
 * is about the queue's membership rather than about a setting's value.
 */
@Composable
internal fun SourceRow(
    label: String,
    selected: Boolean,
    onSelectedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit,
) {
    val colors = AzkarTheme.colors
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AzkarSpacing.md),
    ) {
        AzkarToggleSwitch(checked = selected, onCheckedChange = onSelectedChange)
        Text(
            text = label,
            color = colors.textPrimary,
            fontSize = 13.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        trailing()
    }
}
