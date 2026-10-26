package com.azkar.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.azkar.generated.resources.Res
import com.azkar.generated.resources.settingsNavGroup
import com.azkar.generated.resources.settingsNavStorageNote
import com.azkar.ui.components.AzkarIcons
import com.azkar.ui.components.pressableClick
import com.azkar.ui.theme.AzkarPreview
import com.azkar.ui.theme.AzkarSpacing
import com.azkar.ui.theme.AzkarTheme
import org.jetbrains.compose.resources.stringResource

// `ui/shell/SettingsNavRail.kt` — the left-hand column: what is on the page and where the reader is
// inside it.

// The width of the accent bar down the selected row's leading edge. Thin on purpose: it marks the row
// without becoming a second thing to read, and at the size of the type it sits beside it reads as an
// indent rather than as an icon.
private val RAIL_ACCENT_WIDTH = 3.dp

/** How tall a row is, set by the label's line height plus its padding. */
private val RAIL_ROW_HEIGHT = 38.dp

private val RAIL_ROW_CORNER = 10.dp

private val RAIL_INSET = 16.dp

/** The gap between the group row's icon and its name. */
private val RAIL_ICON_GAP = 12.dp

/**
 * The air between one row and the next.
 *
 * The rows used to sit two dp apart, which packed them tight enough to read as one block of text with a
 * few words missing. A list you are meant to scan rather than read needs enough room that each row's
 * highlight pill is a separate thing.
 */
private val RAIL_ROW_SPACING = 6.dp

/**
 * The extra air above a group header, so the families read as four lists rather than as nine rows.
 *
 * [RAIL_ROW_SPACING] is right between rows that are siblings and wrong between rows that are not: the
 * gap that separates two blocks in one family is not the gap that says "these four are a different
 * thing from those two". Without the extra, the eye has to use the icons and the spine to recover a
 * structure that the spacing could have carried on its own.
 */
private val RAIL_GROUP_GAP = 14.dp

/** The box the group row's icon sits in, which is also what puts the tree's spine where it is. */
private val RAIL_GROUP_ICON_BOX = 18.dp

/** How far the group row — and therefore its children — are set in from the rail's edge. */
private val RAIL_GROUP_INSET = AzkarSpacing.md

/** The tree's spine, in from the rail's edge: down the centre of the group row's icon. */
private val RAIL_TREE_X = RAIL_GROUP_INSET + RAIL_GROUP_ICON_BOX / 2

/** How far a branch runs from the spine across to a child's accent bar. */
private val RAIL_BRANCH_LENGTH = 10.dp

/**
 * The air between a child's accent bar and its name.
 *
 * The bar used to sit flush against the word it belongs to, so a selected row read as a bar with text
 * touching it rather than as a row that had been marked. Ten dp is enough to separate two things without
 * making the label look like it belongs to the next row along.
 */
private val RAIL_LABEL_GAP = 10.dp

private val RAIL_TREE_STROKE = 1.dp

/** Where the child's accent begins: past the spine and its branch. */
private val RAIL_CHILD_INSET = RAIL_TREE_X + RAIL_BRANCH_LENGTH

/**
 * The settings page's navigation column.
 *
 * It exists because the page outgrew a tab strip. Three tabs hid the fact that the reminder blocks are
 * several times the height of the language one, so "Notifications" read as a peer of "Language" and gave
 * no hint that choosing it was a longer scroll than the others. A rail lists what is actually on the
 * page, and [active] lets the row for the block currently on screen mark itself.
 *
 * It is a two-level tree — a row per [SettingsGroup], a row per [SettingsSection] under it — rather than
 * one flat list, because the page is two-level: theme, text and reminder cards are all ways of looking at
 * something, and a flat rail called them peers with "when reminders are due". The groups carry no
 * selection of their own and are not clickable; a header that jumped anywhere would be a second way to
 * reach a block under a different name, which is how two rows end up lighting up for one destination.
 *
 * Row labels come from [settingsSectionLabel] and group names from [settingsGroupLabel] rather than
 * being passed in, so the one mapping from a section to its name is shared with the heading the page
 * draws for it. Two copies of a translation drift apart, and the rail would then be able to say
 * "Notifications" over a block called "Reminders".
 *
 * The footer is one line saying where preferences are kept. It earns its place because this page is
 * where a reader looks for anything about what the app remembers, and the honest answer — that all of
 * it stays on this machine — is worth stating before they go looking for a sync switch that is not here.
 *
 * Nothing above the rows says what page this is. A mark, a wordmark and the current language used to
 * sit at the top of this column, and every one of them was saying something the window had already said:
 * the page header draws "Settings" at 28sp directly across from them, and the reader arrived here
 * through a gear in the app's own chrome. Repeating it cost the rail the height its rows needed and put
 * the language, which is set a screen away, on a page about something else entirely.
 */
@Composable
fun SettingsNavRail(
    active: SettingsSection,
    selectedGroup: SettingsGroup,
    onSelect: (SettingsSection) -> Unit,
    onSelectGroup: (SettingsGroup) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AzkarTheme.colors
    Column(
        modifier =
            modifier
                .width(SETTINGS_RAIL_WIDTH)
                .fillMaxHeight()
                .background(colors.cardBackground)
                .padding(horizontal = RAIL_INSET, vertical = AzkarSpacing.xl),
    ) {
        Text(
            text = stringResource(Res.string.settingsNavGroup).uppercase(AzkarTheme.locale),
            color = colors.textSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.1.sp,
        )
        Spacer(Modifier.height(AzkarSpacing.md))
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(RAIL_GROUP_GAP),
        ) {
            SettingsGroup.entries.forEach { group ->
                SettingsRailGroup(
                    group = group,
                    active = active,
                    selectedGroup = selectedGroup,
                    onSelect = onSelect,
                    onSelectGroup = onSelectGroup,
                )
            }
        }
        Spacer(Modifier.height(AzkarSpacing.lg))
        RailFooter()
    }
}

@Composable
private fun SettingsRailGroup(
    group: SettingsGroup,
    active: SettingsSection,
    selectedGroup: SettingsGroup,
    onSelect: (SettingsSection) -> Unit,
    onSelectGroup: (SettingsGroup) -> Unit,
) {
    val colors = AzkarTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(RAIL_ROW_SPACING)) {
        RailGroupHeader(
            icon = settingsGroupIcon(group),
            label = settingsGroupLabel(group),
            selected = selectedGroup == group,
            onClick = { onSelectGroup(group) },
        )
        // Draw the spine behind each group's children so it grows with them and mirrors in RTL.
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        val spine =
                            if (layoutDirection == LayoutDirection.Ltr) {
                                RAIL_TREE_X.toPx()
                            } else {
                                size.width - RAIL_TREE_X.toPx()
                            }
                        drawLine(
                            color = colors.border,
                            start = Offset(spine, 0f),
                            end = Offset(spine, size.height),
                            strokeWidth = RAIL_TREE_STROKE.toPx(),
                        )
                    },
            verticalArrangement = Arrangement.spacedBy(RAIL_ROW_SPACING),
        ) {
            group.sections.forEach { section ->
                RailRow(
                    label = settingsSectionLabel(section),
                    selected = active == section,
                    onClick = { onSelect(section) },
                )
            }
        }
    }
}

/**
 * The footer: a rule, then the one thing about this page a reader cannot get from the rows above it.
 *
 * Anchored to the bottom of the column rather than placed under the list, so it stays put while the
 * page scrolls behind it and reads as belonging to the rail rather than to the last setting on it.
 */
@Composable
private fun RailFooter(modifier: Modifier = Modifier) {
    val colors = AzkarTheme.colors
    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalDivider(color = colors.border)
        Spacer(Modifier.height(AzkarSpacing.md))
        Text(
            text = stringResource(Res.string.settingsNavStorageNote),
            color = colors.textSecondary,
            fontSize = 11.sp,
        )
    }
}

/**
 * The glyph that stands for a whole family.
 *
 * Four icons for four families, which is what makes the headers read as parents rather than as four
 * more labels. Each is the mark the app already uses for that subject, so the rail does not introduce a
 * fifth vocabulary beside the one the rest of the window speaks: the gear for the settings-adjacent
 * things, the moon for anything about how the app looks, the bell for reminders, and the mosque for the
 * prayer — the same three that head their subjects on the home screen and the reminders screen.
 *
 * The moon rather than a palette because there is no palette glyph in the set, and the sun/moon pair is
 * the pair the app's own theme choice is made of.
 */
@Composable
private fun settingsGroupIcon(group: SettingsGroup): ImageVector =
    when (group) {
        SettingsGroup.GENERAL -> AzkarIcons.Settings
        SettingsGroup.REMINDERS -> AzkarIcons.Bell
        SettingsGroup.PRAYER -> AzkarIcons.Mosque
    }

/** Opens the group; a text tint distinguishes it from the filled selection of an individual section. */
@Composable
private fun RailGroupHeader(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AzkarTheme.colors
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(
                    RAIL_ROW_HEIGHT,
                ).clip(RoundedCornerShape(RAIL_ROW_CORNER))
                .pressableClick(scale = 1f, onClick = onClick)
                .padding(start = RAIL_GROUP_INSET, end = AzkarSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(RAIL_ICON_GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(RAIL_GROUP_ICON_BOX), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) colors.primaryStrong else colors.textSecondary,
                modifier = Modifier.size(17.dp),
            )
        }
        Text(
            text = label,
            color = if (selected) colors.primaryStrong else colors.textSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * One block's row: the branch that hangs it off its parent, its name, and the accent that marks the
 * block the reader is inside.
 *
 * Set in from the group header above it, and with no icon of its own, because these four are children of
 * that row rather than more top-level destinations. The accent and label share one inset after the branch,
 * so the bar touches its branch and the label keeps its gap from the bar.
 *
 * The branch is drawn by the row and not built out of boxes, because a box would take part in the row's
 * click and in its highlight pill, and a hairline that highlights when you hover it is not a hairline
 * any more. Drawn after the fill, the branch survives the selection.
 *
 * The selected fill and the accent bar together carry the state. The fill alone is a tint close enough to
 * the hover wash to be ambiguous; the bar alone is a hairline that is hard to find while reading. Both
 * are cheap and neither competes with the label.
 */
@Composable
private fun RailRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AzkarTheme.colors
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(RAIL_ROW_HEIGHT)
                .clip(RoundedCornerShape(RAIL_ROW_CORNER))
                .background(if (selected) colors.highlight else Color.Transparent)
                .semantics { this.selected = selected }
                .pressableClick(scale = 1f, onClick = onClick)
                .drawBehind {
                    val direction = if (layoutDirection == LayoutDirection.Ltr) 1f else -1f
                    val spine =
                        if (layoutDirection == LayoutDirection.Ltr) {
                            RAIL_TREE_X.toPx()
                        } else {
                            size.width - RAIL_TREE_X.toPx()
                        }
                    drawLine(
                        color = colors.border,
                        start = Offset(spine, center.y),
                        end = Offset(spine + RAIL_BRANCH_LENGTH.toPx() * direction, center.y),
                        strokeWidth = RAIL_TREE_STROKE.toPx(),
                    )
                }.padding(start = RAIL_CHILD_INSET, end = AzkarSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .width(RAIL_ACCENT_WIDTH)
                    .height(RAIL_ROW_HEIGHT - AzkarSpacing.md)
                    .background(
                        if (selected) colors.primary else Color.Transparent,
                        RoundedCornerShape(2.dp),
                    ),
        )
        Spacer(Modifier.width(RAIL_LABEL_GAP))
        Text(
            text = label,
            color = if (selected) colors.primaryStrong else colors.textPrimary,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@AzkarPreview
@Composable
internal fun SettingsNavRailPreview() {
    SettingsNavRail(
        active = SettingsSection.THEME,
        selectedGroup = SettingsGroup.GENERAL,
        onSelect = {},
        onSelectGroup = {},
    )
}
