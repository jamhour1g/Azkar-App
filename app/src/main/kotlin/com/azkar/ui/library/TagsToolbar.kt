package com.azkar.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.azkar.generated.resources.Res
import com.azkar.generated.resources.tagsScopeAll
import com.azkar.generated.resources.tagsScopeDeleted
import com.azkar.generated.resources.tagsScopeUnused
import com.azkar.generated.resources.tagsScopeUsed
import com.azkar.generated.resources.tagsSearchPlaceholder
import com.azkar.generated.resources.tagsShowDeletedCount
import com.azkar.generated.resources.tagsSortLeastUsed
import com.azkar.generated.resources.tagsSortMostUsed
import com.azkar.generated.resources.tagsSortNameAsc
import com.azkar.generated.resources.tagsSortNameDesc
import com.azkar.generated.resources.tagsSortNewest
import com.azkar.ui.components.AzkarFilterChip
import com.azkar.ui.components.AzkarMenuItem
import com.azkar.ui.components.AzkarOutlinedField
import com.azkar.ui.components.AzkarSelect
import org.jetbrains.compose.resources.stringResource

/**
 * The widest a dropdown may get, and the only size it is ever given.
 *
 * A cap, not a width: each select is as wide as the label it is currently showing, and this only
 * catches the translations long enough to push the dropdowns into the search field. Fixing every
 * select at one width instead would spend the spare dp inside the boxes rather than in the field
 * beside them — a closed select padded out to its widest possible label is an empty box, and the
 * search bar is the one control on this row with something to say in the space.
 */
private val SELECT_MAX_WIDTH = 168.dp

/** The one distance that is genuinely not the cluster's: the search field to everything else. */
private val TOOLBAR_GAP = 10.dp

/**
 * The gap *inside* [NarrowingControls], and the only reason it is its own composable.
 *
 * Four dp against the row's [TOOLBAR_GAP] of ten: close enough that the three read as one cluster,
 * far enough apart that none of them looks like the other's caption.
 */
private val CLUSTER_GAP = 4.dp

/**
 * The one height all four controls are held to.
 *
 * Left alone they do not agree, and the row looks assembled rather than drawn: Material's text field
 * carries a 56dp intrinsic minimum, [AzkarSelect]'s trigger asks for 40dp, and [AzkarFilterChip] has
 * whatever height Material gives a chip. Three heights in one row of `verticalAlignment = CenterVertically`
 * is three baselines, and none of them is the one the other two are sitting on.
 *
 * 40dp is the app's own field height — it is [AzkarOutlinedField]'s default and [AzkarSelect]'s trigger —
 * so this is not a new size but the one the two largest controls already agreed on, with the chip
 * raised to meet them rather than the other way round.
 */
private val TOOLBAR_HEIGHT = 40.dp

/**
 * What narrows the list: the search field, the two dropdowns, and the deleted toggle.
 *
 * The search and the dropdowns share a row because all three answer the same question — which tags —
 * and only one of them can be answered by typing. The two dropdowns are fixed-width so the search
 * keeps whatever is left: a sort menu that grew to fit a translated label would eat the field it is
 * meant to sit beside. The other three are nested into a cluster of their own — see
 * [NarrowingControls] for why the distance between them is not the distance between anything else
 * here.
 *
 * The deleted toggle is hidden rather than disabled once the scope dropdown already says "Deleted",
 * because at that point the toggle is a second control for a choice that has been made. It also wears
 * the count of what it would reveal, which is the only reason anyone presses a control that ends in
 * "deleted" — and a count of zero would be the honest argument for not pressing it.
 */
@Composable
internal fun TagsToolbar(
    state: TagsState,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TOOLBAR_GAP),
    ) {
        AzkarOutlinedField(
            value = state.query,
            onValueChange = state::onQueryChanged,
            placeholder = stringResource(Res.string.tagsSearchPlaceholder),
            leadingIcon = Icons.Filled.Search,
            modifier = Modifier.weight(1f).height(TOOLBAR_HEIGHT),
        )
        NarrowingControls(state)
    }
}

/**
 * The three controls that are not the search field, drawn as one object.
 *
 * They are the second half of the row: the search answers *which* tags by typing, and these three
 * answer it by opening something — the scope, the sort, and whether to include the removed ones. On
 * the row's own even spacing each of them sat the same distance from everything else, so the eye read
 * four unrelated controls rather than a field and a cluster, and the group drifted toward the middle
 * of the window with nothing marking where it began.
 *
 * Nested, with a gap narrower than the row's, they become a cluster with a start and an end. That is
 * the whole reason this is a composable: [TOOLBAR_GAP] can then mean "between the search and the
 * cluster" and nothing else, which is the one distance on this row that is genuinely different from
 * the one inside the cluster.
 *
 * The gap is [CLUSTER_GAP] rather than zero because three squircles sharing an edge read as one
 * control with two seams down it, and because these are still three controls with three menus.
 */
@Composable
private fun NarrowingControls(
    state: TagsState,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CLUSTER_GAP),
    ) {
        AzkarSelect(
            label = scopeLabelOf(state.scopeFilter),
            icon = Icons.Filled.Label,
            items = scopeItems(state),
            modifier = Modifier.widthIn(max = SELECT_MAX_WIDTH).height(TOOLBAR_HEIGHT),
        )
        AzkarSelect(
            label = sortLabelOf(state.sort),
            icon = Icons.Outlined.Sort,
            items = sortItems(state),
            modifier = Modifier.widthIn(max = SELECT_MAX_WIDTH).height(TOOLBAR_HEIGHT),
        )
        if (state.deletedToggleRelevant) {
            AzkarFilterChip(
                text = stringResource(Res.string.tagsShowDeletedCount, state.deleted.size),
                selected = state.deletedFilterOn,
                onClick = state::toggleDeleted,
                leadingIcon = Icons.Filled.RestoreFromTrash,
                modifier = Modifier.height(TOOLBAR_HEIGHT),
            )
        }
    }
}

/**
 * The scope dropdown's five entries, with the chosen one checked.
 *
 * Built one entry at a time rather than by mapping over the enum, and that is forced rather than
 * chosen: a `map` lambda is not a composable, so it cannot read the label string for the entry it is
 * building. Enumerating the cases is what puts each `stringResource` back in composable position.
 */
@Composable
private fun scopeItems(state: TagsState): List<AzkarMenuItem> =
    listOf(
        scopeItem(TagScope.ALL, state) { state.selectScope(TagScope.ALL) },
        scopeItem(TagScope.USED, state) { state.selectScope(TagScope.USED) },
        scopeItem(TagScope.UNUSED, state) { state.selectScope(TagScope.UNUSED) },
        scopeItem(TagScope.DELETED, state) { state.selectScope(TagScope.DELETED) },
    )

/** The sort dropdown's five entries, same shape as [scopeItems] and for the same reason. */
@Composable
private fun sortItems(state: TagsState): List<AzkarMenuItem> =
    listOf(
        sortItem(TagSort.NAME_ASC, state) { state.selectSort(TagSort.NAME_ASC) },
        sortItem(TagSort.NAME_DESC, state) { state.selectSort(TagSort.NAME_DESC) },
        sortItem(TagSort.MOST_USED, state) { state.selectSort(TagSort.MOST_USED) },
        sortItem(TagSort.LEAST_USED, state) { state.selectSort(TagSort.LEAST_USED) },
        sortItem(TagSort.NEWEST, state) { state.selectSort(TagSort.NEWEST) },
    )

/**
 * One scope entry. The unchosen ones wear the outline label and the chosen one a tick, so the open
 * menu reads as an answer to "what am I looking at?" rather than as a list of verbs.
 */
@Composable
private fun scopeItem(
    candidate: TagScope,
    state: TagsState,
    onSelect: () -> Unit,
): AzkarMenuItem =
    AzkarMenuItem(
        label = scopeLabelOf(candidate),
        icon = if (candidate == state.scopeFilter) Icons.Filled.Check else Icons.Outlined.Label,
        onClick = onSelect,
    )

/** One sort entry. */
@Composable
private fun sortItem(
    candidate: TagSort,
    state: TagsState,
    onSelect: () -> Unit,
): AzkarMenuItem =
    AzkarMenuItem(
        label = sortLabelOf(candidate),
        icon = if (candidate == state.sort) Icons.Filled.Check else Icons.Outlined.Sort,
        onClick = onSelect,
    )

/** The scope's name in the user's language. Both the closed control and the menu read this. */
@Composable
private fun scopeLabelOf(scope: TagScope): String =
    when (scope) {
        TagScope.ALL -> stringResource(Res.string.tagsScopeAll)
        TagScope.USED -> stringResource(Res.string.tagsScopeUsed)
        TagScope.UNUSED -> stringResource(Res.string.tagsScopeUnused)
        TagScope.DELETED -> stringResource(Res.string.tagsScopeDeleted)
    }

/** The sort's name in the user's language. */
@Composable
private fun sortLabelOf(sort: TagSort): String =
    when (sort) {
        TagSort.NAME_ASC -> stringResource(Res.string.tagsSortNameAsc)
        TagSort.NAME_DESC -> stringResource(Res.string.tagsSortNameDesc)
        TagSort.MOST_USED -> stringResource(Res.string.tagsSortMostUsed)
        TagSort.LEAST_USED -> stringResource(Res.string.tagsSortLeastUsed)
        TagSort.NEWEST -> stringResource(Res.string.tagsSortNewest)
    }
