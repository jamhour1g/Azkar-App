package com.azkar.ui.library

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.StarOutline
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.azkar.generated.resources.Res
import com.azkar.generated.resources.collectionsEmptyHint
import com.azkar.generated.resources.collectionsEmptyTitle
import com.azkar.generated.resources.libraryLoading
import com.azkar.ui.components.ActionIcons
import com.azkar.ui.components.AzkarOutlinedField
import com.azkar.ui.components.BorderedStateBox
import com.azkar.ui.components.TooltipIconButton
import com.azkar.ui.theme.AzkarTheme
import com.azkar.ui.theme.AzkarThemePreview
import org.jetbrains.compose.resources.stringResource

/** How long the open/close chevron takes to flip, long enough to read as a rotation. */
private const val CHEVRON_ROTATION_MILLIS = 140

/**
 * The app's dropdown: a read-only field with a popup under it.
 *
 * Built from [AzkarOutlinedField] rather than `ExposedDropdownMenu` so it keeps the popup radius,
 * the card background and the hover tint the rest of the library uses, and so the field and the popup
 * cannot drift apart the way a platform dropdown and a custom one do.
 *
 * @param selected the currently chosen option, or `null` when nothing has been chosen.
 * @param label renders an option as the single string both the field and the menu show, so the two
 * can never disagree about what an option is called.
 * @param fillAvailableWidth lets a caller pass `Modifier.weight(..)` so the field shares a row
 * instead of holding [fieldWidth], which would otherwise override the weight.
 */
@Composable
fun <T> AzkarDropdown(
    selected: T?,
    options: List<T>,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    fieldWidth: Dp = 200.dp,
    minHeight: Dp = 40.dp,
    fillAvailableWidth: Boolean = false,
) {
    var expanded by remember { mutableStateOf(false) }
    val sizing = if (fillAvailableWidth) Modifier.fillMaxWidth() else Modifier.width(fieldWidth)
    Box(modifier = modifier.then(sizing)) {
        AzkarOutlinedField(
            value = selected?.let { label(it) }.orEmpty(),
            onValueChange = {},
            placeholder = placeholder,
            singleLine = true,
            minHeight = minHeight,
            active = expanded,
            modifier = Modifier.fillMaxWidth(),
            trailingContent = { DropdownChevron(expanded = expanded) },
        )
        // The click target is an overlay rather than the field itself, so it swallows presses before
        // they reach the field. It has no content to scale, and the chevron flip plus the focus ring
        // already report the press, so the default ripple is dropped for consistency with the rest
        // of the controls.
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { expanded = true },
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier =
                Modifier
                    .background(AzkarTheme.colors.cardBackground)
                    .border(1.dp, AzkarTheme.colors.border, RoundedCornerShape(10.dp))
                    .padding(4.dp),
        ) {
            options.forEach { option ->
                DropdownOption(
                    label = label(option),
                    selected = option == selected,
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}

/**
 * The open/closed affordance for [AzkarDropdown].
 *
 * A single static arrow gave the picker no sign of being open other than the menu appearing over the
 * page, so the arrow flips and takes the accent colour while the menu is up.
 */
@Composable
private fun DropdownChevron(expanded: Boolean) {
    val colors = AzkarTheme.colors
    val rotation by
        animateFloatAsState(
            targetValue = if (expanded) 180f else 0f,
            animationSpec = tween(durationMillis = CHEVRON_ROTATION_MILLIS),
            label = "dropdownChevronRotation",
        )
    Icon(
        imageVector = Icons.Filled.ArrowDropDown,
        contentDescription = null,
        tint = if (expanded) colors.primary else colors.textSecondary,
        modifier =
            Modifier
                .size(16.dp)
                .rotate(rotation),
    )
}

@Composable
private fun DropdownOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = AzkarTheme.colors
    DropdownMenuItem(
        text = {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) colors.primary else colors.textPrimary,
            )
        },
        onClick = onClick,
        modifier =
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(if (selected) colors.highlight else Color.Transparent),
    )
}

/**
 * The accent mark shared by the empty and loading states.
 *
 * Empty keeps the outline star. Loading swaps it for a [LoadingIndicator], Material 3 Expressive's
 * morphing indicator, in the same slot and the same size. That is a deliberate change from the static
 * icon this used to draw for the loading case: a still glyph labelled "Loading …" asks the reader to
 * take the text's word for it, and a pane that does not move is indistinguishable from one that
 * already finished and never repainted.
 *
 * The backdrop circle is kept across both states so the two panes stay the same size and the label
 * beneath them does not jump between them.
 *
 * The indicator is left on its default polygon rather than given one of the Material 3 Expressive
 * shapes. `LoadingIndicator` takes a `polygons` list and the library ships a catalogue to fill it
 * from, but in `1.13.0-alpha02` that catalogue is `internal`, so the only reachable options are the
 * default or hand-building a `RoundedPolygon` — and a starburst drawn from scratch to sit behind a
 * 14dp glyph is not a trade this file makes.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun CollectionStateStar(
    loading: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = AzkarTheme.colors
    Box(
        modifier =
            modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(if (loading) colors.highlight else colors.accent.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            LoadingIndicator(
                color = colors.primary,
                modifier = Modifier.size(14.dp),
            )
        } else {
            Icon(
                imageVector = Icons.Filled.StarOutline,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Preview
@Composable
private fun CollectionStateStarPreview() {
    AzkarThemePreview {
        Row(
            modifier = Modifier.width(340.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CollectionStateStar(loading = true)
            CollectionStateStar(loading = false)
        }
    }
}

/** The "Loading …" pane. */
@Composable
internal fun CollectionsLoadingState(modifier: Modifier = Modifier) {
    BorderedStateBox(modifier = modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CollectionStateStar(loading = true)
            Column(modifier = Modifier.padding(top = 10.dp)) {
                Text(
                    text = stringResource(Res.string.libraryLoading),
                    color = AzkarTheme.colors.textSecondary,
                    fontSize = 13.sp,
                )
            }
        }
    }
}

@Preview
@Composable
private fun CollectionsLoadingStatePreview() {
    AzkarThemePreview {
        CollectionsLoadingState(modifier = Modifier.width(420.dp))
    }
}

/**
 * The shelf with nothing on it.
 *
 * [title] and [hint] are passed in rather than resolved here so the same pane can say "you have no
 * collections" and "nothing matches that search" without becoming two nearly identical composables.
 */
@Composable
internal fun CollectionsEmptyState(
    title: String,
    hint: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    val colors = AzkarTheme.colors
    BorderedStateBox(modifier = modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CollectionStateStar(loading = false)
            Text(
                text = title,
                color = colors.textPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 10.dp),
            )
            Text(
                text = hint,
                color = colors.textSecondary,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp),
            )
            if (action != null) {
                Column(modifier = Modifier.padding(top = 14.dp)) { action() }
            }
        }
    }
}

@Preview
@Composable
private fun CollectionsEmptyStatePreview() {
    AzkarThemePreview {
        Column(
            modifier = Modifier.width(420.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CollectionsEmptyState(
                title = "No collections yet",
                hint = "Start from a built-in set, or make one of your own.",
            )
            CollectionsEmptyState(
                title = "Nothing matches that search",
                hint = "Try a different word, or widen the filters.",
                action = {
                    CollectionsInfoLine(text = "3 of 12 collections")
                },
            )
        }
    }
}

/** The empty-shelf copy, kept next to the pane so the two cannot be changed apart. */
internal val EmptyShelfTitle: String
    @Composable get() = stringResource(Res.string.collectionsEmptyTitle)

internal val EmptyShelfHint: String
    @Composable get() = stringResource(Res.string.collectionsEmptyHint)

/**
 * The one line of text this library ever puts at the bottom of a screen — "Loading…" or "Couldn't
 * load the library" — and nothing at all when neither is true.
 *
 * Ellipsized rather than given its own scroll, which is the reverse of what it used to do: the footer
 * once carried a timestamp too, and the horizontal scroll existed so a date could never be cut to
 * "Updated M". With the timestamp gone the longest sentence left is a short one that fits any window
 * this app opens in, and a scroll nobody needs is a control that looks like it is hiding something.
 *
 * Returning early on an empty string is what makes the line cost nothing when it has nothing to say:
 * the call sites keep their padding and gap either way, so the shelf does not jump as a read finishes
 * and clears it.
 */
@Composable
internal fun CollectionsInfoLine(
    text: String,
    modifier: Modifier = Modifier,
) {
    if (text.isEmpty()) return
    Text(
        text = text,
        color = AzkarTheme.colors.textSecondary,
        fontSize = 11.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

@Preview
@Composable
private fun CollectionsInfoLinePreview() {
    AzkarThemePreview {
        CollectionsInfoLine(text = "12 collections · 96 remembrances")
    }
}

/**
 * The one thing an empty shelf offers, as a glyph rather than a sentence.
 *
 * Every empty state in the library — no collections, no members, the session finished, the
 * collection deleted — ends in the same shape: a title, a hint, and a single way out. All five said
 * that way out in words, and a word next to nothing else on the screen is a paragraph where a mark
 * would do. Each one is also a different verb — go back, add, start again — and each has exactly one
 * glyph that means only that verb, so the label was carrying information the picture could carry.
 *
 * The tooltip is not optional. A glyph is a label to somebody who already knows it and a puzzle to
 * everybody else, and a tooltip is how a control says its name without spending the width on it.
 *
 * Sized up from [CircleIconButton]'s 28dp because this sits in an empty box with room around it
 * rather than in a dense toolbar — it is often the only thing on the screen to aim at.
 */
@Composable
internal fun EmptyStateAction(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    TooltipIconButton(
        icon = icon,
        tooltip = description,
        onClick = onClick,
        modifier = Modifier.size(EMPTY_ACTION_SIZE),
        buttonSize = EMPTY_ACTION_SIZE,
        iconSize = EMPTY_ACTION_GLYPH,
    )
}

@Preview
@Composable
private fun EmptyStateActionPreview() {
    AzkarThemePreview {
        EmptyStateAction(
            icon = ActionIcons.addMembers,
            description = "Create a collection",
            onClick = {},
        )
    }
}

/** Comfortably past the 48dp a pointer target is asked to be, because this is often the only one. */
private val EMPTY_ACTION_SIZE = 52.dp
private val EMPTY_ACTION_GLYPH = 24.dp
