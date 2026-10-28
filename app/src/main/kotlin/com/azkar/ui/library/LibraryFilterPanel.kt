package com.azkar.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.azkar.generated.resources.Res
import com.azkar.generated.resources.collectionsScopeLabel
import com.azkar.generated.resources.collectionsSortLabel
import com.azkar.ui.components.AzkarCard
import com.azkar.ui.components.AzkarFilterChip
import com.azkar.ui.library.model.CollectionScope
import com.azkar.ui.library.model.CollectionSort
import com.azkar.ui.theme.AzkarTheme
import com.azkar.ui.theme.AzkarVariantsPreview
import com.azkar.ui.theme.CardSize
import org.jetbrains.compose.resources.stringResource

/**
 * The two decisions that shape the shelf, in one place.
 *
 * They used to be two dropdowns sitting beside the search field, and both were dropdowns for a
 * reason that has since gone away. A dropdown is the right control for a long list where only one
 * option can be seen at a time; these two have four options and three, all of which fit on a line,
 * so each dropdown was hiding three answers behind a field that showed one — and the field spent
 * 150dp of the banner to say "All", which is also what the chip row below says about everything.
 *
 * Chips instead, because a chip shows the whole set. It says what else there is without being
 * opened, which is the whole reason the two controls are worth showing at all: "Recently Opened"
 * is a statement about how the shelf is ordered, and hiding that inside a closed dropdown means
 * most of the shelf is in an order nobody chose deliberately.
 *
 * Two groups rather than one flat row of seven chips, because the two answers are not the same kind
 * of answer. Scope says *which* collections are on the shelf and changes what the shelf *is*; sort
 * says *in what order* they arrive and changes nothing about which. A reader who has seen one row
 * knows what the other one is for.
 *
 * Wrapped rather than scrolled, which is what this row used to do. A sideways-scrolling strip of
 * chips can only be moved with a wheel gesture held down — shift, on a desktop — and the row gives
 * no sign that anything is off the end of it, so the scope the user did not choose stayed silently
 * unchoosable. Wrapping costs a second line at most and puts every option on the screen at once,
 * which is the whole argument for showing chips rather than a dropdown.
 *
 * Sized to its content rather than to the page. Seven chips do not need 1400dp, and a full-width
 * panel with an empty right-hand half reads as a section of the page rather than as something the
 * button opened. Wrapping is what keeps that cap honest in the other direction too: a translation
 * that needs more room takes another line rather than a wider panel.
 */
@Composable
internal fun LibraryFilterPanel(
    scope: CollectionScope,
    onScopeSelected: (CollectionScope) -> Unit,
    sort: CollectionSort,
    onSortSelected: (CollectionSort) -> Unit,
    modifier: Modifier = Modifier,
) {
    AzkarCard(
        modifier = modifier.widthIn(max = PANEL_MAX_WIDTH),
        size = CardSize.SM,
        shape = RoundedCornerShape(PANEL_SHAPE.dp),
        contentPadding = PANEL_PADDING,
    ) {
        Column {
            FilterGroup(label = stringResource(Res.string.collectionsScopeLabel)) {
                CollectionScope.entries.forEach { option ->
                    AzkarFilterChip(
                        text = stringResource(option.labelResource),
                        selected = option == scope,
                        onClick = { onScopeSelected(option) },
                    )
                }
            }
            FilterGroup(
                label = stringResource(Res.string.collectionsSortLabel),
                topGap = GROUP_GAP,
            ) {
                CollectionSort.entries.forEach { option ->
                    AzkarFilterChip(
                        text = stringResource(option.labelResource),
                        selected = option == sort,
                        onClick = { onSortSelected(option) },
                    )
                }
            }
        }
    }
}

@AzkarVariantsPreview
@Composable
internal fun LibraryFilterPanelPreview() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        LibraryFilterPanel(
            scope = CollectionScope.ALL,
            onScopeSelected = {},
            sort = CollectionSort.RECENTLY_UPDATED,
            onSortSelected = {},
            modifier = Modifier.width(320.dp),
        )
        LibraryFilterPanel(
            scope = CollectionScope.PINNED,
            onScopeSelected = {},
            sort = CollectionSort.NAME,
            onSortSelected = {},
            modifier = Modifier.width(320.dp),
        )
    }
}

/**
 * One labelled group of chips, wrapped onto as many lines as it needs.
 *
 * The label sits above the chips rather than inline because a chip row with a word in front of it
 * has to break somewhere, and inline is where it breaks: the first chip gets pushed to a second
 * line while the label stays on the first, so the group reads as a heading and one stray option.
 *
 * The line spacing is tighter than the chip gap, because the gap that separates two chips is doing
 * a different job from the gap that separates two lines: too much of the former and a wrapped group
 * reads as a paragraph rather than as one set of options.
 */
@Composable
private fun FilterGroup(
    label: String,
    topGap: Dp = 0.dp,
    content: @Composable FlowRowScope.() -> Unit,
) {
    Column(modifier = Modifier.padding(top = topGap)) {
        Text(
            text = label,
            color = AzkarTheme.colors.textSecondary,
            fontSize = LABEL_SP,
            fontWeight = FontWeight.SemiBold,
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(top = GROUP_INSET),
            horizontalArrangement = Arrangement.spacedBy(CHIP_GAP.dp),
            verticalArrangement = Arrangement.spacedBy(LINE_GAP),
            content = content,
        )
    }
}

/** The panel's corner radius, in dp. Matches the card's, so the two read as one set of surfaces. */
private const val PANEL_SHAPE = 12

/** The gap between the two groups, in dp. */
private val GROUP_GAP = 12.dp

/** The gap between the label and its chips, and between neighbouring chips, in dp. */
private val GROUP_INSET = 6.dp
private const val CHIP_GAP = 6

/**
 * The gap between two lines of a wrapped group, in dp.
 *
 * Less than the gap between two chips on one line, so that a wrapped group still reads as one set
 * of options rather than as two.
 */
private val LINE_GAP = 4.dp

/** The panel's own padding, in dp. */
private val PANEL_PADDING = 14.dp

/**
 * How wide the panel may grow, in dp.
 *
 * Enough for the widest group of chips plus its label's worth of breathing room on the longest
 * translations, and no more — the panel is anchored under a button at the end of a search row, so a
 * panel that reached the far edge of the window would point at the wrong thing.
 */
private val PANEL_MAX_WIDTH = 460.dp

/** The group labels, in sp. */
private val LABEL_SP = 11.sp
