package com.azkar.ui.library

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.azkar.generated.resources.Res
import com.azkar.generated.resources.collectionDetailActionResetProgress
import com.azkar.generated.resources.collectionDetailActionResume
import com.azkar.generated.resources.collectionDetailProgressFormat
import com.azkar.generated.resources.collectionMembersSortBy
import com.azkar.generated.resources.collectionMembersViewGrid
import com.azkar.generated.resources.collectionMembersViewList
import com.azkar.generated.resources.collectionSortCategory
import com.azkar.generated.resources.collectionSortSequence
import com.azkar.generated.resources.collectionSortTitle
import com.azkar.generated.resources.collectionsActionStart
import com.azkar.ui.components.CompletionTrack
import com.azkar.ui.components.TooltipIconButton
import com.azkar.ui.components.mirrorHorizontalGradient
import com.azkar.ui.components.pressableClick
import com.azkar.ui.library.data.CollectionStats
import com.azkar.ui.library.model.CollectionMemberSort
import com.azkar.ui.library.model.CollectionMemberView
import com.azkar.ui.preview.previewCollectionStats
import com.azkar.ui.preview.previewCollectionStatsVariants
import com.azkar.ui.theme.AzkarComponentShowcasePreview
import com.azkar.ui.theme.AzkarTheme
import org.jetbrains.compose.resources.stringResource

/**
 * The row between the band and the members: how far through the collection you are, and what to show
 * and in what order.
 *
 * The grid grew two axes of its own — twenty-nine members cannot all be scanned at once, and five
 * columns is the wrong shape for reading two passages side by side — and both of those axes are
 * choices made *about* the collection rather than facts *of* it. Putting them in one toolbar is what
 * keeps them from being scattered into the band above or duplicated as controls on the grid.
 *
 * It used to open with a row of category chips. What it carries now is narrower on purpose: a
 * category chip was one more row of pills to read past before reaching the first passage, and the
 * categories it offered were the same words the passages were already sorted into, so it filtered a
 * list the reader could equally well read. Sorting and view remain because they change *the shape of
 * the whole grid* rather than hiding a row of it — and with the chips gone the row no longer scrolls,
 * so nothing here can be pushed off the end of it by a long label again.
 *
 * Progress came out of the band and into this row for the same reason: it is a fact about the
 * collection rather than a way of changing it, so it sits with the other facts, and a summary that
 * owns its own bordered card beneath the band was one more box between the reader and the first
 * passage. See [MemberProgress] for what the row prints and [MemberViewToggle] for why the two view
 * buttons are drawn as one control.
 */
@Composable
internal fun CollectionMemberToolbar(
    stats: CollectionStats,
    sort: CollectionMemberSort,
    view: CollectionMemberView,
    gates: ToolbarGates,
    actions: MemberToolbarActions,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MemberProgress(stats = stats, modifier = Modifier.weight(1f))

        Spacer(Modifier.width(TOOLBAR_GAP))

        // Start and reset, together, because they are the same verb pointed both ways: one moves the
        // counts forward and the other throws them away. Split across a band and a row they read as
        // two unrelated controls, and the reader who had lost their place had to decide which end of
        // the page to look at to find out.
        //
        // Neither is drawn when it has nothing to do. A start button on an empty collection and a
        // disabled reset on one nobody has recited are both controls that say "you cannot do anything
        // here" before the reader has worked out whether they have done anything at all.
        if (gates.canStart) {
            ToolbarStartButton(
                inProgress = stats.completedMembers > 0,
                onClick = actions.onStart,
            )
            Spacer(Modifier.width(TOOLBAR_GAP))
        }
        if (gates.canResetProgress) {
            ToolbarIconAction(
                icon = Icons.Filled.RestartAlt,
                tooltip = stringResource(Res.string.collectionDetailActionResetProgress),
                onClick = actions.onRequestReset,
            )
            Spacer(Modifier.width(TOOLBAR_GAP))
        }

        MemberViewToggle(view = view, onViewSelected = actions.onViewSelected)

        Spacer(Modifier.width(TOOLBAR_GAP))

        Text(
            text = stringResource(Res.string.collectionMembersSortBy),
            color = AzkarTheme.colors.textSecondary,
            fontSize = TOOLBAR_LABEL_SP,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
        Spacer(Modifier.width(TOOLBAR_SORT_GAP))
        ToolbarSortPicker(sort = sort, onSortSelected = actions.onSortSelected)
    }
}

/**
 * The page's one forward control, at the row's height.
 *
 * It was a filled glyph in the band above for a while, which said "go" three hundred pixels from the
 * counts it advances and next to a row of pills about doing things once and forgetting them. Beside
 * the rule it reads for what it is — the thing that moves those numbers — and it is the only filled
 * control on this row, so it does not need to be the largest thing on the page to be findable.
 *
 * The wording is the honest one for the state: "Start" on a collection nobody has opened, "Resume"
 * once there is somewhere to resume to. A single label would have to be one of those two and would
 * be wrong half the time, and "Resume" on a fresh collection is a promise the page cannot keep.
 *
 * Painted rather than [PrimaryButton], which is Material's button at Material's height: this row is
 * one height throughout and a taller button in the middle of it undoes that.
 */
@Composable
private fun ToolbarStartButton(
    inProgress: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AzkarTheme.colors
    Row(
        modifier =
            modifier
                .height(TOOLBAR_ACTION_SIZE)
                .clip(RoundedCornerShape(TOOLBAR_SEGMENT_SHAPE))
                .background(colors.primary)
                // Scale 1f for the reason [MemberViewSegment] gives: the press feedback is the tint,
                // and a shrink here would shrink the page's primary action to half its size.
                .pressableClick(scale = 1f, onClick = onClick)
                .padding(horizontal = TOOLBAR_SEGMENT_PAD_X),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.PlayArrow,
            contentDescription = null,
            tint = colors.onPrimary,
            modifier = Modifier.size(TOOLBAR_SEGMENT_GLYPH),
        )
        Spacer(Modifier.width(TOOLBAR_SEGMENT_GAP))
        Text(
            text =
                stringResource(
                    if (inProgress) Res.string.collectionDetailActionResume else Res.string.collectionsActionStart,
                ),
            color = colors.onPrimary,
            fontSize = TOOLBAR_LABEL_SP,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

/**
 * How far through the collection the reader is, on one line, at the leading end of the toolbar.
 *
 * "0 of 61 recited" and the rule beside it are one sentence and its underline. They were a bordered
 * card of their own for a while, which spent a box, two gaps and a second Start/Resume button on a
 * question the row underneath was already in a position to answer — and the band above had to give up
 * the corner it printed the same count in. The count is the largest type on this row and the rule
 * stretches to whatever width is left over, because this is the one number on the page that changes
 * while you watch it.
 *
 * The rule takes the slack rather than the type: the digits grow as a collection gets longer
 * ("0 of 61" to "120 of 1,240"), and a layout that sized the bar from the text would have the bar
 * change width every time somebody added a member. [stats.hasMembers] is the only thing that hides
 * the whole thing — an empty collection has nothing to be part-way through.
 *
 * The count and the rule and nothing else. An estimate sat between them for a while, and it was the
 * wrong number on purpose: "About 20m left" is measured from how long the last hundred members took,
 * so it is a guess about the next hundred, and a guess printed beside a *count* is read as though the
 * two were the same kind of fact. It also moved on its own — the estimate drifts every time somebody
 * recites, which means the row was not stable while you watched it either. A count and a bar say the
 * same thing twice over and neither of them can be wrong.
 */
@Composable
private fun MemberProgress(
    stats: CollectionStats,
    modifier: Modifier = Modifier,
) {
    if (!stats.hasMembers) {
        return
    }

    val colors = AzkarTheme.colors
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text =
                stringResource(
                    Res.string.collectionDetailProgressFormat,
                    stats.completedMembers,
                    stats.totalMembers,
                ),
            color = colors.textPrimary,
            fontSize = TOOLBAR_COUNT_SP,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
        CompletionTrack(
            completion = stats.completion,
            fillBrush =
                mirrorHorizontalGradient(
                    listOf(colors.primary, colors.primaryStrong),
                    isRtl = false,
                ),
            modifier =
                Modifier
                    .weight(1f)
                    .padding(start = TOOLBAR_TRACK_GAP),
        )
    }
}

/**
 * The one edit this row still owns: clearing the counts.
 *
 * Boxed so that it reads as a control, and for the same reason the two glyphs were filled rather than
 * outlined — an *outlined* Material glyph at fifteen pixels is a hairline. It renders, it is present
 * in the semantics tree, and it is unreadable, which is worse than absent because nothing about it
 * looks broken.
 */
@Composable
private fun ToolbarIconAction(
    icon: ImageVector,
    tooltip: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .size(TOOLBAR_ACTION_SIZE)
                .clip(RoundedCornerShape(TOOLBAR_SEGMENT_SHAPE))
                .background(AzkarTheme.colors.highlight)
                // The outline has to be named to the same shape as the clip. A clip only governs what
                // is drawn *inside* a node, so a border left on its default shape draws a square
                // around a round button.
                .border(1.dp, AzkarTheme.colors.border, RoundedCornerShape(TOOLBAR_SEGMENT_SHAPE)),
        contentAlignment = Alignment.Center,
    ) {
        TooltipIconButton(
            icon = icon,
            tooltip = tooltip,
            onClick = onClick,
            tint = AzkarTheme.colors.primaryStrong,
            buttonSize = TOOLBAR_ACTION_SIZE,
            iconSize = TOOLBAR_ACTION_GLYPH,
        )
    }
}

/**
 * Grid and list as one control rather than two buttons.
 *
 * They were a pair of independent filter chips, and they read as a pair: two separately outlined
 * lozenges with their own fills, either of which could be pressed, neither of which said that the
 * other was the alternative. A reader could not tell from the row whether choosing List turned Grid
 * off, and the only thing that said so was the fill — a fact carried entirely by colour.
 *
 * Here they are two halves of one outline with a rule between them, so the alternatives are part of
 * the same shape and the chosen one is a filled half rather than a different-looking button. Both
 * halves stay labelled: the icons alone are two rectangles that differ by the number of dots inside
 * them, and the words are what makes this a view switch rather than a zoom.
 *
 * Material's own `SingleChoiceSegmentedButtonRow` would draw this, and would draw it in
 * [androidx.compose.material3.MaterialTheme]'s palette at 40dp, which is neither this app's palette
 * nor the height of anything else on this row. A control that answers "these are one choice" is
 * worth thirteen lines of this app's own drawing.
 */
@Composable
private fun MemberViewToggle(
    view: CollectionMemberView,
    onViewSelected: (CollectionMemberView) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(TOOLBAR_SEGMENT_SHAPE)
    Row(
        modifier =
            modifier
                .height(TOOLBAR_ACTION_SIZE)
                .clip(shape)
                .border(1.dp, AzkarTheme.colors.border, shape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MemberViewSegment(
            label = stringResource(Res.string.collectionMembersViewGrid),
            icon = Icons.Outlined.GridView,
            selected = view == CollectionMemberView.GRID,
            onClick = { onViewSelected(CollectionMemberView.GRID) },
        )
        Box(
            modifier =
                Modifier
                    .width(TOOLBAR_SEGMENT_RULE)
                    .height(TOOLBAR_SEGMENT_RULE_TALL)
                    .background(AzkarTheme.colors.border),
        )
        MemberViewSegment(
            label = stringResource(Res.string.collectionMembersViewList),
            icon = Icons.AutoMirrored.Outlined.ViewList,
            selected = view == CollectionMemberView.LIST,
            onClick = { onViewSelected(CollectionMemberView.LIST) },
        )
    }
}

/**
 * One half of [MemberViewToggle], filled when it is the answer.
 *
 * `scale = 1f` rather than the usual shrink, because this is the one pressable in the app whose edges
 * are shared with another control: a twelve percent shrink of the left half would pull it out from
 * under the rule and visibly tear the pair apart on the very press that confirms they belong
 * together. The tint that [pressableClick] still applies is the whole of the feedback instead.
 */
@Composable
private fun MemberViewSegment(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AzkarTheme.colors
    val ink = if (selected) colors.onPrimary else colors.textSecondary
    Row(
        modifier =
            modifier
                .height(TOOLBAR_ACTION_SIZE)
                .background(if (selected) colors.primary else Color.Transparent)
                .pressableClick(scale = 1f, onClick = onClick)
                .padding(horizontal = TOOLBAR_SEGMENT_PAD_X),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TOOLBAR_SEGMENT_GAP),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = ink,
            modifier = Modifier.size(TOOLBAR_SEGMENT_GLYPH),
        )
        Text(
            text = label,
            color = ink,
            fontSize = TOOLBAR_LABEL_SP,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

/**
 * The sort picker, drawn at this row's size rather than the app's.
 *
 * This was [AzkarDropdown], which is the right control in a dialog and the wrong one here. It wraps
 * [com.azkar.ui.components.AzkarOutlinedField], and an `OutlinedTextField` has a minimum height it
 * will not go below — `AzkarOutlinedField` asks for forty and Material gives its own fifty-six, which
 * is what made this a tall pill standing nearly twice the height of every other control on the row,
 * with a thirteen-sp label marooned in the middle of it. There is no smaller `OutlinedTextField` to ask
 * for; the minimum is the point of the component. The four dialog pickers still use it, because there
 * a field at Material's height is exactly right.
 *
 * So this is the same decision as [MemberViewToggle], one control further along: Material offers no
 * component that can be sized to thirty dots in this app's palette, and the row is thirty dots tall.
 * Which means the row is now one height throughout — progress, reset, view, sort — which is the whole
 * reason a reader can see that the first three and the fourth belong to the same sentence.
 *
 * The menu and its option rows are drawn here too rather than reaching for [AzkarDropdown]'s, because
 * those are private to [CollectionControls.kt] and a list of choices that did not match the trigger
 * that opened it would read as a different menu entirely.
 */
@Composable
private fun ToolbarSortPicker(
    sort: CollectionMemberSort,
    onSortSelected: (CollectionMemberSort) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val colors = AzkarTheme.colors
    val shape = RoundedCornerShape(TOOLBAR_SEGMENT_SHAPE)
    Box(modifier = modifier) {
        Row(
            modifier =
                Modifier
                    .width(TOOLBAR_SORT_WIDTH)
                    .height(TOOLBAR_ACTION_SIZE)
                    .clip(shape)
                    .border(1.dp, colors.border, shape)
                    .pressableClick { expanded = true }
                    .padding(start = TOOLBAR_SORT_PAD_X, end = TOOLBAR_SORT_CHEVRON_GAP),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(sort.resource()),
                color = if (expanded) colors.primary else colors.textPrimary,
                fontSize = TOOLBAR_LABEL_SP,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            ToolbarDropdownChevron(expanded = expanded)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier =
                Modifier
                    .background(colors.cardBackground)
                    .border(1.dp, colors.border, RoundedCornerShape(TOOLBAR_MENU_SHAPE))
                    .padding(TOOLBAR_MENU_PAD),
        ) {
            SORT_OPTIONS.forEach { option ->
                ToolbarDropdownOption(
                    label = stringResource(option.resource()),
                    selected = option == sort,
                    onClick = {
                        expanded = false
                        onSortSelected(option)
                    },
                )
            }
        }
    }
}

/**
 * The open/closed affordance for [ToolbarSortPicker].
 *
 * The same flip and accent as the app's other dropdowns, at this row's smaller glyph, so the trigger
 * reads as belonging to the row rather than as a dialog field shrunk down.
 */
@Composable
private fun ToolbarDropdownChevron(expanded: Boolean) {
    val colors = AzkarTheme.colors
    val rotation by
        animateFloatAsState(
            targetValue = if (expanded) 180f else 0f,
            animationSpec = tween(durationMillis = TOOLBAR_CHEVRON_MILLIS),
            label = "toolbarDropdownChevronRotation",
        )
    Icon(
        imageVector = Icons.Filled.ArrowDropDown,
        contentDescription = null,
        tint = if (expanded) colors.primary else colors.textSecondary,
        modifier =
            Modifier
                .size(TOOLBAR_CHEVRON_GLYPH)
                .rotate(rotation),
    )
}

/**
 * One order to choose, filled when it is the answer.
 *
 * [AzkarDropdown]'s option row, repeated here because that one is private to
 * [CollectionControls.kt] and a list of choices that did not match the trigger that opened it would
 * read as a different menu entirely.
 */
@Composable
private fun ToolbarDropdownOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = AzkarTheme.colors
    DropdownMenuItem(
        text = {
            Text(
                text = label,
                fontSize = TOOLBAR_OPTION_SP,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) colors.primary else colors.textPrimary,
            )
        },
        onClick = onClick,
        modifier =
            Modifier
                .clip(RoundedCornerShape(TOOLBAR_OPTION_SHAPE))
                .background(if (selected) colors.highlight else Color.Transparent),
    )
}

/**
 * What this row's own controls do.
 *
 * The same gathering [CollectionHeroActions] does for a hero band: the callbacks and the one
 * condition guarding them are one thing being described, and a signature that lists them one per
 * line is a signature nobody reads correctly.
 */
internal data class MemberToolbarActions(
    val onStart: () -> Unit,
    val onRequestReset: () -> Unit,
    val onSortSelected: (CollectionMemberSort) -> Unit,
    val onViewSelected: (CollectionMemberView) -> Unit,
)

/**
 * Which of the two editing controls this row may draw.
 *
 * Not a pair of bare flags on the row itself. They are the same question — is there anything here to
 * start, and is there anything here to throw away — asked of two buttons that sit in the same place
 * for the same reason, and a signature that carries them as `canStart`/`canResetProgress` one after
 * the other reads as two unrelated switches that a caller could plausibly want to set independently.
 */
internal data class ToolbarGates(
    val canStart: Boolean,
    val canResetProgress: Boolean,
)

@AzkarComponentShowcasePreview
@Composable
internal fun CollectionMemberToolbarPreview() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CollectionMemberToolbar(
            stats = previewCollectionStats(),
            sort = CollectionMemberSort.SEQUENCE,
            view = CollectionMemberView.GRID,
            gates = ToolbarGates(canStart = true, canResetProgress = true),
            actions =
                MemberToolbarActions(
                    onStart = {},
                    onRequestReset = {},
                    onSortSelected = {},
                    onViewSelected = {},
                ),
        )
        previewCollectionStatsVariants().forEach { stats ->
            CollectionMemberToolbar(
                stats = stats,
                sort = CollectionMemberSort.CATEGORY,
                view = CollectionMemberView.LIST,
                gates = ToolbarGates(canStart = true, canResetProgress = false),
                actions =
                    MemberToolbarActions(
                        onStart = {},
                        onRequestReset = {},
                        onSortSelected = {},
                        onViewSelected = {},
                    ),
            )
        }
        CollectionMemberToolbar(
            stats =
                previewCollectionStats(
                    totalMembers = 0,
                    completedMembers = 0,
                    totalRepeats = 0,
                    remainingRepeats = 0,
                ),
            sort = CollectionMemberSort.TITLE,
            view = CollectionMemberView.GRID,
            gates = ToolbarGates(canStart = false, canResetProgress = false),
            actions =
                MemberToolbarActions(
                    onStart = {},
                    onRequestReset = {},
                    onSortSelected = {},
                    onViewSelected = {},
                ),
        )
    }
}

/**
 * The orders offered, in the order they are offered.
 *
 * A list rather than a set so "In order" is first: it is the collection's own order and the one the
 * session will follow, so it is the answer most people want and the one they should not have to look
 * for. The rest are alphabetical so a reader who has scrolled once can find them again.
 */
private val SORT_OPTIONS: List<CollectionMemberSort> =
    listOf(
        CollectionMemberSort.SEQUENCE,
        CollectionMemberSort.TITLE,
        CollectionMemberSort.CATEGORY,
    )

/** The resource a sort is labelled with. Split out because [stringResource] cannot be called in a list literal. */
private fun CollectionMemberSort.resource() =
    when (this) {
        CollectionMemberSort.SEQUENCE -> Res.string.collectionSortSequence
        CollectionMemberSort.TITLE -> Res.string.collectionSortTitle
        CollectionMemberSort.CATEGORY -> Res.string.collectionSortCategory
    }

private val TOOLBAR_ACTION_GLYPH = 16.dp
private val TOOLBAR_ACTION_SIZE = 30.dp
private val TOOLBAR_CHEVRON_GLYPH = 14.dp
private val TOOLBAR_COUNT_SP = 13.sp
private val TOOLBAR_GAP = 16.dp
private val TOOLBAR_LABEL_SP = 11.sp
private val TOOLBAR_MENU_PAD = 4.dp
private val TOOLBAR_MENU_SHAPE = 10.dp
private val TOOLBAR_OPTION_SHAPE = 8.dp
private val TOOLBAR_OPTION_SP = 12.sp
private val TOOLBAR_SEGMENT_GAP = 5.dp
private val TOOLBAR_SEGMENT_GLYPH = 14.dp
private val TOOLBAR_SEGMENT_PAD_X = 10.dp
private val TOOLBAR_SEGMENT_RULE = 1.dp

/** The rule is shorter than the control so the pair reads as one outline with a seam, not as three boxes. */
private val TOOLBAR_SEGMENT_RULE_TALL = 16.dp

/** Half of [TOOLBAR_ACTION_SIZE], so the outline wraps its contents rather than cropping them. */
private val TOOLBAR_SEGMENT_SHAPE = TOOLBAR_ACTION_SIZE / 2

private const val TOOLBAR_CHEVRON_MILLIS = 150
private val TOOLBAR_SORT_CHEVRON_GAP = 6.dp
private val TOOLBAR_SORT_GAP = 8.dp
private val TOOLBAR_SORT_PAD_X = 12.dp
private val TOOLBAR_SORT_WIDTH = 160.dp
private val TOOLBAR_TRACK_GAP = 12.dp
