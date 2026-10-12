package com.azkar.ui.library

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.azkar.generated.resources.Res
import com.azkar.generated.resources.collectionDetailAdd
import com.azkar.generated.resources.collectionDetailAddPickerEmpty
import com.azkar.generated.resources.collectionDetailAddPickerHint
import com.azkar.generated.resources.collectionDetailAddPickerTitle
import com.azkar.generated.resources.collectionDetailClearSelection
import com.azkar.generated.resources.collectionDetailRemove
import com.azkar.generated.resources.collectionDetailRemoveBody
import com.azkar.generated.resources.collectionDetailRemoveTitle
import com.azkar.generated.resources.collectionDetailSelectAll
import com.azkar.generated.resources.collectionsActionCancel
import com.azkar.generated.resources.collectionsSearchRemembrances
import com.azkar.generated.resources.collectionsSelectionCount
import com.azkar.ui.components.AzkarCard
import com.azkar.ui.components.AzkarDialogSurface
import com.azkar.ui.components.AzkarFilterChip
import com.azkar.ui.components.AzkarOutlinedField
import com.azkar.ui.components.PrimaryButton
import com.azkar.ui.components.SecondaryButton
import com.azkar.ui.components.pressableClick
import com.azkar.ui.library.model.RemembranceTile
import com.azkar.ui.preview.previewRemembranceTile
import com.azkar.ui.theme.AzkarTheme
import com.azkar.ui.theme.AzkarThemePreview
import com.azkar.ui.theme.CardSize
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/*
 * The dialogs this page raises over its grid: the add picker, the cover picker and the removal
 * confirmation.
 *
 * They live apart from the page body because they are the one part of it that has nothing to do with
 * layout — each is a self-contained surface over the whole window — and because keeping them here
 * leaves `CollectionDetailScreen` reading as the page it describes. All of them read their labels
 * from `CollectionDetailState` and decide for themselves whether they are open.
 */

/**
 * Multi-select over everything the collection does not already hold.
 *
 * Ticking a hundred azkar one at a time is how a shelf feature loses to a flat list, so this is a
 * picker with checkboxes rather than a stack of "add" buttons: the operation people actually want
 * is "put these twenty in there", not "add this one" twenty times.
 */
@Composable
internal fun AddRemembrancesDialog(detail: CollectionDetailState) {
    val picker = detail.addPicker
    val candidates = picker.candidates
    Dialog(onDismissRequest = picker::close) {
        AzkarDialogSurface(
            modifier =
                Modifier
                    .widthIn(min = 560.dp, max = 760.dp)
                    .heightIn(min = 320.dp, max = 620.dp),
        ) {
            Column {
                PickerHeading()
                Spacer(Modifier.height(12.dp))
                AzkarOutlinedField(
                    value = picker.query,
                    onValueChange = picker::onQueryChanged,
                    placeholder = stringResource(Res.string.collectionsSearchRemembrances),
                    leadingIcon = Icons.Filled.Search,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                PickerSelectionBar(picker = picker, candidateCount = candidates.size)
                Spacer(Modifier.height(10.dp))

                val tiles = picker.tiles
                if (tiles.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(Res.string.collectionDetailAddPickerEmpty),
                            color = AzkarTheme.colors.textSecondary,
                            fontSize = 12.sp,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(items = tiles, key = { it.id }) { tile ->
                            PickerRow(
                                tile = tile,
                                selected = tile.id in picker.selected,
                                onToggle = { picker.toggleSelection(tile.id) },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                DetailDialogActions(
                    confirmLabel = stringResource(Res.string.collectionDetailAdd),
                    confirmEnabled = picker.selected.isNotEmpty(),
                    onDismiss = picker::close,
                    onConfirm = picker::confirm,
                )
            }
        }
    }
}

/** What the picker is for, above its search box. */
@Composable
internal fun PickerHeading() {
    Text(
        text = stringResource(Res.string.collectionDetailAddPickerTitle),
        color = AzkarTheme.colors.textPrimary,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
    )
    Text(
        text = stringResource(Res.string.collectionDetailAddPickerHint),
        color = AzkarTheme.colors.textSecondary,
        fontSize = 11.sp,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Preview
@Composable
private fun PickerHeadingPreview() {
    AzkarThemePreview {
        PickerHeading()
    }
}

/**
 * How many are ticked, and the two bulk actions.
 *
 * Both chips appear only when they would do something. A disabled chip still invites a click and
 * answers with nothing.
 */
@Composable
internal fun PickerSelectionBar(
    picker: CollectionDetailAddPicker,
    candidateCount: Int,
) {
    val selectedCount = picker.selected.size
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = pluralStringResource(Res.plurals.collectionsSelectionCount, selectedCount, selectedCount),
            color = AzkarTheme.colors.textSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.width(10.dp))
        if (candidateCount > 0) {
            AzkarFilterChip(
                text = stringResource(Res.string.collectionDetailSelectAll),
                selected = selectedCount > 0 && selectedCount == candidateCount,
                onClick = picker::selectAllCandidates,
            )
        }
        if (selectedCount > 0) {
            Spacer(Modifier.width(8.dp))
            AzkarFilterChip(
                text = stringResource(Res.string.collectionDetailClearSelection),
                selected = false,
                onClick = picker::clearSelection,
            )
        }
    }
}

/** One candidate row: a checkbox and enough of the remembrance to recognise it. */
@Composable
internal fun PickerRow(
    tile: RemembranceTile,
    selected: Boolean,
    onToggle: () -> Unit,
) {
    val colors = AzkarTheme.colors
    AzkarCard(
        modifier =
            Modifier
                .fillMaxWidth()
                .border(
                    width = if (selected) 1.5.dp else 1.dp,
                    color = if (selected) colors.primary else colors.border,
                    shape = OptionShape,
                ).pressableClick(scale = 1f, onClick = onToggle),
        size = CardSize.SM,
        shape = OptionShape,
        contentPadding = 10.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = selected, onCheckedChange = { onToggle() })
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tile.category,
                    color = colors.primary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = tile.primaryPreview,
                    color = colors.textPrimary,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
    }
}

@Preview
@Composable
private fun PickerRowPreview() {
    AzkarThemePreview {
        Column(
            modifier = Modifier.width(520.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PickerRow(tile = previewRemembranceTile(), selected = false, onToggle = {})
            PickerRow(tile = previewRemembranceTile(id = 102L, isFavorite = true), selected = true, onToggle = {})
        }
    }
}

// ---- confirmations ------------------------------------------------------------------------------------

@Composable
internal fun ConfirmRemovalDialog(
    count: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        AzkarDialogSurface(modifier = Modifier.widthIn(min = 380.dp, max = 520.dp)) {
            Column {
                Text(
                    text = stringResource(Res.string.collectionDetailRemoveTitle),
                    color = AzkarTheme.colors.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(Res.string.collectionDetailRemoveBody, count),
                    color = AzkarTheme.colors.textSecondary,
                    fontSize = 12.sp,
                )
                Spacer(Modifier.height(16.dp))
                DetailDialogActions(
                    confirmLabel = stringResource(Res.string.collectionDetailRemove),
                    confirmEnabled = true,
                    onDismiss = onDismiss,
                    onConfirm = onConfirm,
                )
            }
        }
    }
}

/** Cancel on the left, the one committing action on the right — the order every other dialog uses. */
@Composable
internal fun DetailDialogActions(
    confirmLabel: String,
    confirmEnabled: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    Row {
        SecondaryButton(
            text = stringResource(Res.string.collectionsActionCancel),
            onClick = onDismiss,
        )
        Spacer(Modifier.weight(1f))
        PrimaryButton(
            text = confirmLabel,
            onClick = onConfirm,
            enabled = confirmEnabled,
        )
    }
}

@Preview
@Composable
private fun DetailDialogActionsPreview() {
    AzkarThemePreview {
        Column(
            modifier = Modifier.width(420.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DetailDialogActions(confirmLabel = "Add", confirmEnabled = true, onDismiss = {}, onConfirm = {})
            DetailDialogActions(confirmLabel = "Add", confirmEnabled = false, onDismiss = {}, onConfirm = {})
        }
    }
}

// ---- plumbing -----------------------------------------------------------------------------------------

/** The option rows are their own shape so the confirmations read as one family of dialogs. */
internal val OptionShape = RoundedCornerShape(10.dp)
