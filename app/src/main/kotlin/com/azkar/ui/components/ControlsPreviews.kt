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
import com.azkar.generated.resources.*
import com.azkar.ui.theme.AzkarControlVariantsPreview
import com.azkar.ui.theme.AzkarTheme
import com.azkar.ui.theme.CardSize
import com.azkar.ui.theme.isRtlLayout
import org.jetbrains.compose.resources.stringResource

@AzkarControlVariantsPreview
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
                        text = stringResource(Res.string.componentsPreviewCardHint),
                        color = AzkarTheme.colors.textSecondary,
                        fontSize = 13.sp,
                    )
                }
            }
        }
    }
}

@AzkarControlVariantsPreview
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
                Text(
                    text = stringResource(Res.string.componentsPreviewPaintedCard),
                    color = AzkarTheme.colors.onPrimary,
                )
                Text(
                    text = stringResource(Res.string.componentsPreviewSurfaceHint),
                    color = AzkarTheme.colors.onPrimary.copy(alpha = 0.8f),
                    fontSize = 13.sp,
                )
            }
        }
    }
}

@AzkarControlVariantsPreview
@Composable
internal fun PrimaryButtonPreview() {
    Column(
        modifier = Modifier.width(340.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton(
                text = stringResource(Res.string.homeCollectionActionStart),
                onClick = {},
                leadingIcon = ActionIcons.read,
            )
            PrimaryButton(
                text = stringResource(Res.string.componentsPreviewWithIcon),
                onClick = {},
                leadingIcon = ActionIcons.duplicate,
            )
        }
        PrimaryButton(
            text = stringResource(Res.string.componentsPreviewDisabledField),
            onClick = {},
            enabled = false,
        )
    }
}

@AzkarControlVariantsPreview
@Composable
internal fun SecondaryButtonPreview() {
    Column(
        modifier = Modifier.width(340.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton(text = stringResource(Res.string.collectionsActionCancel), onClick = {})
            SecondaryButton(
                text = stringResource(Res.string.componentsPreviewDisabledField),
                onClick = {},
                enabled = false,
            )
        }
        SecondaryButton(
            text = stringResource(Res.string.collectionsActionChooseCover),
            onClick = {},
            leadingIcon = Icons.Outlined.Image,
        )
    }
}

@AzkarControlVariantsPreview
@Composable
internal fun NavButtonPreview() {
    Row(
        modifier = Modifier.width(420.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        NavButton(
            text = stringResource(Res.string.home),
            selected = true,
            onClick = {},
            leadingIcon = AzkarIcons.Home,
        )
        NavButton(
            text = stringResource(Res.string.azkarLibrary),
            selected = false,
            onClick = {},
            leadingIcon = AzkarIcons.Library,
        )
        NavButton(
            text = stringResource(Res.string.settingsNavGroup),
            selected = false,
            onClick = {},
            leadingIcon = AzkarIcons.Settings,
        )
    }
}

@AzkarControlVariantsPreview
@Composable
internal fun IconActionButtonPreview() {
    Row(
        modifier = Modifier.width(340.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconActionButton(
            icon = ActionIcons.pin(on = false),
            contentDescription = stringResource(Res.string.collectionsActionPin),
            onClick = {},
        )
        IconActionButton(
            icon = ActionIcons.favorite(on = true),
            contentDescription = stringResource(Res.string.collectionsActionFavorite),
            onClick = {},
            selected = true,
        )
        IconActionButton(
            icon = ActionIcons.delete,
            contentDescription = stringResource(Res.string.collectionsActionDelete),
            onClick = {},
            tint = AzkarTheme.colors.dialogValidation,
        )
    }
}

@AzkarControlVariantsPreview
@Composable
internal fun CircleIconButtonPreview() {
    Row(
        modifier = Modifier.width(340.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CircleIconButton(
            icon = ActionIcons.copy,
            contentDescription = stringResource(Res.string.collectionsActionDuplicate),
            onClick = {},
        )
        CircleIconButton(
            icon = ActionIcons.favorite(on = true),
            contentDescription = stringResource(Res.string.collectionsActionFavorite),
            onClick = {},
            favorite = true,
        )
        CircleIconButton(
            icon = ActionIcons.delete,
            contentDescription = stringResource(Res.string.collectionsActionDelete),
            onClick = {},
            enabled = false,
        )
    }
}

@AzkarControlVariantsPreview
@Composable
internal fun TooltipIconButtonPreview() {
    Row(
        modifier = Modifier.width(340.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TooltipIconButton(
            icon = ActionIcons.addMembers,
            tooltip = stringResource(Res.string.collectionsActionAddMembers),
            onClick = {},
        )
        TooltipIconButton(
            icon = ActionIcons.cover,
            tooltip = stringResource(Res.string.componentsPreviewChangeCover),
            onClick = {},
        )
        TooltipIconActionButton(
            icon = AzkarIcons.Settings,
            tooltip = stringResource(Res.string.settingsNavGroup),
            onClick = {},
            tint = AzkarTheme.colors.textSecondary,
        )
    }
}

@AzkarControlVariantsPreview
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

@AzkarControlVariantsPreview
@Composable
internal fun PillTogglePreview() {
    Row(
        modifier = Modifier.width(420.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PillToggle(
            text = stringResource(Res.string.collectionMembersViewGrid),
            selected = true,
            onClick = {},
            leadingIcon = AzkarIcons.Library,
        )
        PillToggle(text = stringResource(Res.string.collectionMembersViewList), selected = false, onClick = {})
    }
}

@AzkarControlVariantsPreview
@Composable
internal fun AzkarFilterChipPreview() {
    Row(
        modifier = Modifier.width(420.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AzkarFilterChip(text = stringResource(Res.string.collectionsScopeAll), selected = true, onClick = {})
        AzkarFilterChip(text = stringResource(Res.string.collectionsScopeOfficial), selected = false, onClick = {})
        AzkarFilterChip(text = stringResource(Res.string.collectionsScopeCustom), selected = false, onClick = {})
        AzkarFilterChip(
            text = stringResource(Res.string.componentsPreviewSelectedDisabled),
            selected = true,
            onClick = {},
            enabled = false,
        )
        AzkarFilterChip(
            text = stringResource(Res.string.componentsPreviewDisabledField),
            selected = false,
            onClick = {},
            enabled = false,
        )
    }
}

@AzkarControlVariantsPreview
@Composable
internal fun AzkarOverflowMenuPreview() {
    Row(
        modifier = Modifier.width(340.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AzkarOverflowMenu(
            icon = Icons.Outlined.MoreVert,
            contentDescription = stringResource(Res.string.libraryActionMore),
            items =
                listOf(
                    AzkarMenuItem(
                        label = stringResource(Res.string.collectionsActionEdit),
                        onClick = {},
                        icon = ActionIcons.edit,
                    ),
                    AzkarMenuItem(
                        label = stringResource(Res.string.collectionsActionDuplicate),
                        onClick = {},
                        icon = ActionIcons.duplicate,
                    ),
                    AzkarMenuItem(
                        label = stringResource(Res.string.collectionsActionDelete),
                        onClick = {},
                        icon = ActionIcons.delete,
                    ),
                ),
        )
        AzkarOverflowMenu(
            icon = Icons.Outlined.MoreVert,
            contentDescription = stringResource(Res.string.libraryActionMore),
            items = emptyList(),
            enabled = false,
        )
    }
}

@AzkarControlVariantsPreview
@Composable
internal fun AzkarOutlinedFieldPreview() {
    Column(
        modifier = Modifier.width(340.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AzkarOutlinedField(
            value = "",
            onValueChange = {},
            placeholder = stringResource(Res.string.collectionsSearchRemembrances),
            leadingIcon = Icons.Outlined.Search,
        )
        AzkarOutlinedField(
            value = stringResource(Res.string.componentsPreviewSampleName),
            onValueChange = {},
            placeholder = stringResource(Res.string.componentsPreviewNameValue),
            active = true,
            supportingText = stringResource(Res.string.componentsPreviewNameHint),
        )
        AzkarOutlinedField(
            value = stringResource(Res.string.componentsPreviewMorningDescription),
            onValueChange = {},
            placeholder = stringResource(Res.string.collectionsDialogDescriptionLabel),
            singleLine = false,
            trailingContent = {
                IconActionButton(
                    icon = Icons.Outlined.Search,
                    contentDescription = stringResource(Res.string.collectionsSearchRemembrances),
                    onClick = {},
                )
            },
        )
        AzkarOutlinedField(
            value = stringResource(Res.string.componentsPreviewReadOnly),
            onValueChange = {},
            placeholder = stringResource(Res.string.componentsPreviewDisabledField),
            enabled = false,
        )
    }
}

@AzkarControlVariantsPreview
@Composable
internal fun AzkarTabStripPreview() {
    Column(
        modifier = Modifier.width(420.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        val tabs =
            listOf(
                stringResource(Res.string.collectionsScopeAll),
                stringResource(Res.string.collectionsScopeOfficial),
                stringResource(Res.string.collectionsScopeCustom),
            )
        AzkarTabStrip(tabs = tabs, selectedIndex = 0, onSelect = {})
        AzkarTabStrip(tabs = tabs, selectedIndex = 2, onSelect = {})
        AzkarTabStrip(tabs = emptyList(), selectedIndex = -1, onSelect = {})
    }
}

@AzkarControlVariantsPreview
@Composable
internal fun BorderedStateBoxPreview() {
    BorderedStateBox(modifier = Modifier.width(340.dp)) {
        Text(text = stringResource(Res.string.componentsPreviewEmptyStateTitle), fontWeight = FontWeight.SemiBold)
        Text(
            text = stringResource(Res.string.componentsPreviewEmptyStateHint),
            color = AzkarTheme.colors.textSecondary,
            fontSize = 13.sp,
        )
    }
}

@AzkarControlVariantsPreview
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

@AzkarControlVariantsPreview
@Composable
internal fun AzkarDialogSurfacePreview() {
    AzkarDialogSurface(modifier = Modifier.width(340.dp)) {
        Text(text = stringResource(Res.string.componentsPreviewRemoveReminder), fontWeight = FontWeight.SemiBold)
        Text(
            text = stringResource(Res.string.componentsPreviewDialogHint),
            color = AzkarTheme.colors.textSecondary,
            fontSize = 13.sp,
        )
    }
}
