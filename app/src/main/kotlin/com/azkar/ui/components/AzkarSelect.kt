package com.azkar.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.azkar.ui.theme.AzkarExpressive
import com.azkar.ui.theme.AzkarPreview
import com.azkar.ui.theme.AzkarTheme

private val TRIGGER_HEIGHT = 40.dp
private val TRIGGER_TEXT_SP = 13.sp
private val TRIGGER_GAP = 8.dp

/**
 * A closed control that shows one value and opens a menu of the others.
 *
 * Built here rather than taken from Material's `ExposedDropdownMenu` for the reason every other
 * control in this file was: the stock one sizes itself for a phone and paints itself from the Material
 * palette, so a toolbar of them in a desktop window reads as a row of web form controls dropped into
 * the middle of the app. This draws the same affordance in the app's own border, radius and ink.
 *
 * [items] is [AzkarMenuItem] rather than a list of `(value, label)` pairs, because that is the type
 * [AzkarOverflowMenu] already opens and the option list here is the same list with the labels of one
 * control — reusing it means a caller assembling a filter already has the type it needs, and there is
 * one menu item shape in the codebase rather than two that behave identically.
 *
 * [label] is the *current* value, which is what makes this a select rather than a button: the caller
 * supplies the text for what is chosen, so a caller holding an enum does not have to translate it
 * twice. The menu marks the chosen entry with [AzkarMenuItem.icon] the same way it marks every entry
 * with an icon — a caller passes a check glyph for the row it has selected and leaves the rest's icons
 * as they are. That keeps "which one am I on" in the caller's hands, where the enum is, rather than
 * duplicating the comparison here against a value this signature does not carry.
 *
 * The border reproduces [AzkarOutlinedField]'s and its resting width for the same reason that one
 * cannot ask for 1dp: Material derives the stroke from a private constant sized for touch. Matching it
 * matters more here than on a text field, because these sit *beside* fields — two controls in one
 * toolbar row whose outlines differ by a pixel read as two different design systems.
 */
@Composable
fun AzkarSelect(
    label: String,
    items: List<AzkarMenuItem>,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        AzkarSelectTrigger(label = label, icon = icon, onClick = { expanded = true })
        AzkarSelectMenu(items = items, expanded = expanded, onDismiss = { expanded = false })
    }
}

/**
 * The closed half of [AzkarSelect]: the row that reports the current value and opens the menu.
 *
 * Split from its menu so that each half is readable on its own. The two are separate composables
 * rather than one long one for a practical reason as much as a tidiness one: the menu is only in the
 * tree while it is open, so returning early on [expanded] keeps a closed dropdown's rows out of
 * composition entirely rather than merely out of sight.
 *
 * [fillMaxWidth] is deliberately *not* here. The trigger wraps its label so that a dropdown is as wide
 * as the value it is showing, because a closed select with eighty spare dp inside it reads as an empty
 * box rather than a chosen value. The consequence for callers is that they cap this with `widthIn`,
 * never with `width`: a pinned width sizes the [Box] but not the row drawing inside it, so the control
 * would occupy more space than it fills and every gap beside it would be measured from a reservation
 * nobody can see. Size it from the label, cap it for long translations, and let the caller give the
 * slack to something else.
 */
@Composable
private fun AzkarSelectTrigger(
    label: String,
    icon: ImageVector?,
    onClick: () -> Unit,
) {
    val colors = AzkarTheme.colors
    Row(
        modifier =
            Modifier
                .heightIn(min = TRIGGER_HEIGHT)
                .clip(AzkarExpressive.stateBoxShape)
                .background(colors.cardBackground)
                .border(2.dp, colors.border, AzkarExpressive.stateBoxShape)
                .clickable { onClick() }
                .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TRIGGER_GAP),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(14.dp),
            )
        }

        Text(
            text = label,
            color = colors.textPrimary,
            fontSize = TRIGGER_TEXT_SP,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(
            imageVector = Icons.Filled.ArrowDropDown,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** The open half of [AzkarSelect]. Its rows are the same shape [AzkarOverflowMenu] draws. */
@Composable
private fun AzkarSelectMenu(
    items: List<AzkarMenuItem>,
    expanded: Boolean,
    onDismiss: () -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
    ) {
        AzkarSelectMenuItems(items = items, onDismiss = onDismiss)
    }
}

@Composable
private fun AzkarSelectMenuItems(
    items: List<AzkarMenuItem>,
    onDismiss: () -> Unit,
) {
    val colors = AzkarTheme.colors
    items.forEach { item ->
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
                    textColor = colors.textPrimary,
                    leadingIconColor = colors.textSecondary,
                ),
            onClick = {
                onDismiss()
                item.onClick()
            },
        )
    }
}

@AzkarPreview
@Composable
internal fun AzkarSelectPreview() {
    Row(
        modifier = Modifier.width(360.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AzkarSelect(
            label = "Name A-Z",
            icon = Icons.AutoMirrored.Outlined.Sort,
            items =
                listOf(
                    AzkarMenuItem(label = "Name A-Z", onClick = {}, icon = Icons.Filled.Check),
                    AzkarMenuItem(label = "Most used", onClick = {}, icon = Icons.AutoMirrored.Filled.Label),
                ),
        )
    }
}

@AzkarPreview
@Composable
internal fun AzkarSelectExpandedPreview() {
    Column(modifier = Modifier.width(240.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        AzkarSelectTrigger(label = "Most used", icon = Icons.AutoMirrored.Outlined.Sort, onClick = {})
        Surface(
            shape = MaterialTheme.shapes.extraSmall,
            color = AzkarTheme.colors.cardBackground,
            tonalElevation = 3.dp,
            shadowElevation = 3.dp,
        ) {
            Column(modifier = Modifier.width(220.dp)) {
                AzkarSelectMenuItems(
                    items =
                        listOf(
                            AzkarMenuItem(label = "Name A-Z", onClick = {}, icon = Icons.AutoMirrored.Filled.Label),
                            AzkarMenuItem(label = "Most used", onClick = {}, icon = Icons.Filled.Check),
                        ),
                    onDismiss = {},
                )
            }
        }
    }
}
