package com.azkar.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.azkar.ui.theme.AzkarTheme

/**
 * One row of a popup menu.
 *
 * [icon] is required rather than optional. It used to default to `null`, which meant a menu could
 * quietly come to be half-illustrated and half-bare depending on who wrote it — and a menu whose items
 * are inconsistent with each other is read as two different kinds of command rather than as one list.
 * Making it required puts the choice at every call site, where [ActionIcons] is the only place that
 * choice belongs.
 */
data class AzkarMenuItem(
    val label: String,
    val onClick: () -> Unit,
    val icon: ImageVector,
)

/**
 * One row of a popup menu, drawn the same way in every popup.
 *
 * Its own function because there is more than one popup now: the "⋯" menu and the menu a
 * right-click opens are the same list of commands and must look the same, and a copy of the row
 * would be free to drift from the original the first time one of them was restyled.
 */
@Composable
internal fun AzkarMenuItemRow(
    item: AzkarMenuItem,
    onDismiss: () -> Unit,
) {
    DropdownMenuItem(
        text = {
            Text(
                text = item.label,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        },
        leadingIcon = {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                modifier = Modifier.size(13.dp),
            )
        },
        colors =
            MenuDefaults.itemColors(
                textColor = AzkarTheme.colors.textPrimary,
                leadingIconColor = AzkarTheme.colors.textSecondary,
            ),
        onClick = {
            onDismiss()
            item.onClick()
        },
    )
}
