package com.azkar.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.azkar.ui.theme.AzkarPreview
import com.azkar.ui.theme.AzkarTheme
import com.azkar.ui.theme.CardSize
import com.azkar.ui.theme.isRtlLayout

@AzkarPreview
@Composable
internal fun AzkarCardPreview() {
    Column(
        modifier = Modifier.width(340.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CardSize.entries.forEach { size ->
            AzkarCard(modifier = Modifier.fillMaxWidth(), size = size) {
                Column {
                    Text(text = size.name, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = "Padding and elevation both follow the size.",
                        color = AzkarTheme.colors.textSecondary,
                        fontSize = 13.sp,
                    )
                }
            }
        }
    }
}

@AzkarPreview
@Composable
internal fun AzkarSurfaceCardPreview() {
    Column(
        modifier = Modifier.width(340.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AzkarSurfaceCard(
            modifier = Modifier.fillMaxWidth(),
            background =
                mirrorHorizontalGradient(
                    colors = listOf(AzkarTheme.colors.primary, AzkarTheme.colors.secondary),
                    isRtl = isRtlLayout(),
                ),
        ) {
            Column {
                Text(text = "Painted card", color = AzkarTheme.colors.onPrimary)
                Text(
                    text = "A wash instead of a flat fill.",
                    color = AzkarTheme.colors.onPrimary.copy(alpha = 0.8f),
                    fontSize = 13.sp,
                )
            }
        }
    }
}

@AzkarPreview
@Composable
internal fun PrimaryButtonPreview() {
    Column(
        modifier = Modifier.width(340.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton(text = "Start session", onClick = {}, leadingIcon = ActionIcons.read)
            PrimaryButton(text = "With icon", onClick = {}, leadingIcon = ActionIcons.duplicate)
        }
        PrimaryButton(text = "Disabled", onClick = {}, enabled = false)
    }
}

@AzkarPreview
@Composable
internal fun SecondaryButtonPreview() {
    Column(
        modifier = Modifier.width(340.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton(text = "Cancel", onClick = {})
            SecondaryButton(text = "Disabled", onClick = {}, enabled = false)
        }
        SecondaryButton(text = "Choose image…", onClick = {}, leadingIcon = Icons.Outlined.Image)
    }
}

@AzkarPreview
@Composable
internal fun NavButtonPreview() {
    Row(
        modifier = Modifier.width(420.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        NavButton(text = "Home", selected = true, onClick = {}, leadingIcon = AzkarIcons.Home)
        NavButton(text = "Library", selected = false, onClick = {}, leadingIcon = AzkarIcons.Library)
        NavButton(text = "Settings", selected = false, onClick = {}, leadingIcon = AzkarIcons.Settings)
    }
}

@AzkarPreview
@Composable
internal fun IconActionButtonPreview() {
    Row(
        modifier = Modifier.width(340.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconActionButton(
            icon = ActionIcons.pin(on = false),
            contentDescription = "Pin to top",
            onClick = {},
        )
        IconActionButton(
            icon = ActionIcons.favorite(on = true),
            contentDescription = "Favourite",
            onClick = {},
            selected = true,
        )
        IconActionButton(
            icon = ActionIcons.delete,
            contentDescription = "Delete",
            onClick = {},
            tint = AzkarTheme.colors.dialogValidation,
        )
    }
}

@AzkarPreview
@Composable
internal fun CircleIconButtonPreview() {
    Row(
        modifier = Modifier.width(340.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CircleIconButton(icon = ActionIcons.copy, contentDescription = "Duplicate", onClick = {})
        CircleIconButton(
            icon = ActionIcons.favorite(on = true),
            contentDescription = "Favourite",
            onClick = {},
            favorite = true,
        )
        CircleIconButton(
            icon = ActionIcons.delete,
            contentDescription = "Delete",
            onClick = {},
            enabled = false,
        )
    }
}

@AzkarPreview
@Composable
internal fun TooltipIconButtonPreview() {
    Row(
        modifier = Modifier.width(340.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TooltipIconButton(
            icon = ActionIcons.addMembers,
            tooltip = "Add remembrances",
            onClick = {},
        )
        TooltipIconButton(
            icon = ActionIcons.cover,
            tooltip = "Change cover",
            onClick = {},
        )
        TooltipIconActionButton(
            icon = AzkarIcons.Settings,
            tooltip = "Settings",
            onClick = {},
            tint = AzkarTheme.colors.textSecondary,
        )
    }
}

@AzkarPreview
@Composable
internal fun AzkarToggleSwitchPreview() {
    Row(
        modifier = Modifier.width(340.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        AzkarToggleSwitch(checked = true, onCheckedChange = {})
        AzkarToggleSwitch(checked = false, onCheckedChange = {})
        AzkarToggleSwitch(checked = false, onCheckedChange = {}, enabled = false)
        AzkarToggleSwitch(checked = true, onCheckedChange = {}, enabled = false)
    }
}

@AzkarPreview
@Composable
internal fun PillTogglePreview() {
    Row(
        modifier = Modifier.width(420.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PillToggle(text = "Grid", selected = true, onClick = {}, leadingIcon = AzkarIcons.Library)
        PillToggle(text = "List", selected = false, onClick = {})
    }
}

@AzkarPreview
@Composable
internal fun AzkarFilterChipPreview() {
    Row(
        modifier = Modifier.width(420.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AzkarFilterChip(text = "All", selected = true, onClick = {})
        AzkarFilterChip(text = "Official", selected = false, onClick = {})
        AzkarFilterChip(text = "Yours", selected = false, onClick = {})
        AzkarFilterChip(text = "Selected, disabled", selected = true, onClick = {}, enabled = false)
        AzkarFilterChip(text = "Disabled", selected = false, onClick = {}, enabled = false)
    }
}

@AzkarPreview
@Composable
internal fun AzkarOverflowMenuPreview() {
    Row(
        modifier = Modifier.width(340.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AzkarOverflowMenu(
            icon = Icons.Outlined.MoreVert,
            contentDescription = "More actions",
            items =
                listOf(
                    AzkarMenuItem(label = "Edit", onClick = {}, icon = ActionIcons.edit),
                    AzkarMenuItem(label = "Duplicate", onClick = {}, icon = ActionIcons.duplicate),
                    AzkarMenuItem(label = "Delete", onClick = {}, icon = ActionIcons.delete),
                ),
        )
        AzkarOverflowMenu(
            icon = Icons.Outlined.MoreVert,
            contentDescription = "More actions",
            items = emptyList(),
            enabled = false,
        )
    }
}

@AzkarPreview
@Composable
internal fun AzkarOutlinedFieldPreview() {
    Column(
        modifier = Modifier.width(340.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AzkarOutlinedField(
            value = "",
            onValueChange = {},
            placeholder = "Search remembrances",
            leadingIcon = Icons.Outlined.Search,
        )
        AzkarOutlinedField(
            value = "Ar-Rahman",
            onValueChange = {},
            placeholder = "Name",
            active = true,
            supportingText = "Use the name readers will recognize.",
        )
        AzkarOutlinedField(
            value = "Every morning after Fajr",
            onValueChange = {},
            placeholder = "Description",
            singleLine = false,
            trailingContent = {
                IconActionButton(
                    icon = Icons.Outlined.Search,
                    contentDescription = "Search",
                    onClick = {},
                )
            },
        )
        AzkarOutlinedField(
            value = "Read only",
            onValueChange = {},
            placeholder = "Disabled field",
            enabled = false,
        )
    }
}

@AzkarPreview
@Composable
internal fun AzkarTabStripPreview() {
    Column(
        modifier = Modifier.width(420.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        AzkarTabStrip(tabs = listOf("All", "Official", "Yours"), selectedIndex = 0, onSelect = {})
        AzkarTabStrip(tabs = listOf("All", "Official", "Yours"), selectedIndex = 2, onSelect = {})
        AzkarTabStrip(tabs = emptyList(), selectedIndex = -1, onSelect = {})
    }
}

@AzkarPreview
@Composable
internal fun BorderedStateBoxPreview() {
    BorderedStateBox(modifier = Modifier.width(340.dp)) {
        Text(text = "Nothing here yet", fontWeight = FontWeight.SemiBold)
        Text(
            text = "The empty and loading panes draw their chrome from here.",
            color = AzkarTheme.colors.textSecondary,
            fontSize = 13.sp,
        )
    }
}

@AzkarPreview
@Composable
internal fun IconTintPreview() {
    Row(
        modifier = Modifier.width(340.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        IconTint(color = AzkarTheme.colors.accentInk) {
            Icon(imageVector = AzkarIcons.Star, contentDescription = null)
        }
        IconTint(color = AzkarTheme.colors.primaryStrong) {
            Icon(imageVector = AzkarIcons.Clock, contentDescription = null)
        }
        IconTint(color = AzkarTheme.colors.textSecondary) {
            Icon(imageVector = AzkarIcons.Bell, contentDescription = null)
        }
    }
}

@AzkarPreview
@Composable
internal fun AzkarDialogSurfacePreview() {
    AzkarDialogSurface(modifier = Modifier.width(340.dp)) {
        Text(text = "Remove reminder?", fontWeight = FontWeight.SemiBold)
        Text(
            text = "Every dialog in the app draws on this pane, so they share a padding.",
            color = AzkarTheme.colors.textSecondary,
            fontSize = 13.sp,
        )
    }
}
