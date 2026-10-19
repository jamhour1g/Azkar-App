package com.azkar.ui.shell

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.azkar.generated.resources.Res
import com.azkar.generated.resources.settingsThemeBody
import com.azkar.generated.resources.settingsThemeDark
import com.azkar.generated.resources.settingsThemeHeader
import com.azkar.generated.resources.settingsThemeLight
import com.azkar.generated.resources.settingsThemeSystem
import com.azkar.generated.resources.settingsTypographyAzkarFont
import com.azkar.generated.resources.settingsTypographyBody
import com.azkar.generated.resources.settingsTypographyFontHint
import com.azkar.generated.resources.settingsTypographyFontLabel
import com.azkar.generated.resources.settingsTypographyFontMissing
import com.azkar.generated.resources.settingsTypographyHeader
import com.azkar.generated.resources.settingsTypographySizeHint
import com.azkar.generated.resources.settingsTypographySizeLabel
import com.azkar.ui.components.AzkarIcons
import com.azkar.ui.components.AzkarOutlinedField
import com.azkar.ui.components.pressableClick
import com.azkar.ui.library.AzkarDropdown
import com.azkar.ui.notifications.presentation.NotificationPresentationSettings
import com.azkar.ui.preview.previewAzkarAppState
import com.azkar.ui.preview.rememberPreviewNotificationSettingsState
import com.azkar.ui.theme.AppTypography
import com.azkar.ui.theme.AzkarSpacing
import com.azkar.ui.theme.AzkarTheme
import com.azkar.ui.theme.AzkarThemePreview
import com.azkar.ui.theme.SystemFontCatalogue
import com.azkar.ui.theme.azkarTypography
import org.jetbrains.compose.resources.stringResource

// `ui/shell/SettingsAppearanceSections.kt` — everything on this page that decides what the reader
// *sees*, and only that: the theme the whole app is painted in, the typeface and size the reader's own
// prose is set in, and the shape and size of a reminder card.
//
// It is one file because the three answers are one kind of answer. A reader who has arrived here has
// already agreed to look at something, and the only question left is how it should look; the settings
// that decide whether a reminder is *due* or *where it comes from* are in the sibling files, one per
// family, so that "Appearance" never has to hold something that is not an appearance.
//
// Every colour in this file that is *not* `AzkarTheme.colors` is a deliberate exception, and the four
// at the top are the only ones. They draw the theme previews, and a preview of the dark theme has to
// be dark whether or not the app currently is — reading them from the live palette would make the
// preview of the theme you are not using into a picture of the theme you are.

// The dark palette as it actually ships, so the preview a reader sees on the "Light" tile is the
// screen they would get rather than an impression of it.
private val PreviewDarkSurface = Color(0xFF0F1A2B)
private val PreviewDarkTrack = Color(0xFF35486A)
private val PreviewDarkAccent = Color(0xFF60A5FA)

// And the light one, for the same reason and in the same direction.
private val PreviewLightSurface = Color(0xFFF1F5F9)
private val PreviewLightTrack = Color(0xFFCBD5E1)
private val PreviewLightAccent = Color(0xFF3B82F6)

private val TILE_CORNER = 14.dp

private val PREVIEW_HEIGHT = 40.dp

/** The thumbnail's own frame, so its control does not touch the tile's padding. */
private val PREVIEW_INSET = 14.dp

/** The thumbnail's corner, smaller than the tile's own so the two read as nested shapes. */
private val PREVIEW_CORNER = 9.dp

/** How far each half's control keeps off the seam, so neither one leans against the other's palette. */
private val PREVIEW_SEAM_GAP = 6.dp

/** A hairline that reads on both a near-white and a near-black surface. */
private val PreviewHairline = Color(0x1F000000)

private val PREVIEW_CONTROL_GAP = 8.dp

private val PREVIEW_TRACK_WIDTH = 44.dp

private val PREVIEW_DOT_CORNER = 6.dp

private val PREVIEW_DOT_SIZE = 12.dp

/** The thumbnail's track, drawn as a capsule so it reads as a control at 40dp tall. */
private val PREVIEW_TRACK_HEIGHT = 8.dp

/**
 * The theme, as three tiles rather than a toggle.
 *
 * A toggle has to answer "what does pressing this do?" from a glyph alone, and it cannot offer a third
 * option at all — which is how [ThemePreference.SYSTEM], still the launch default, became unreachable
 * the moment the bar stopped cycling through it. Three tiles answer it with the label instead, which is
 * also what lets the system option exist honestly: "System" is not a glyph anyone would have
 * recognised on its own.
 *
 * The tiles carry a thumbnail of the theme they select, which is the part a name cannot do. "Light" and
 * "Dark" describe the choice; the thumbnail shows the result, and a reader who has never seen the app
 * in one of them can tell from the tile alone which is which. The system tile is half of each, because
 * that is what it is: not a third appearance but the desktop's, whichever that turns out to be.
 */
@Composable
internal fun SettingsThemeSection(
    state: AzkarAppState,
    modifier: Modifier = Modifier,
) {
    SettingsSectionBlock(
        title = stringResource(Res.string.settingsThemeHeader),
        subtitle = stringResource(Res.string.settingsThemeBody),
        modifier = modifier,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(AzkarSpacing.md)) {
            ThemeTile(
                option =
                    ThemeOption(
                        ThemePreference.DARK,
                        stringResource(Res.string.settingsThemeDark),
                        AzkarIcons.Moon,
                    ),
                selected = state.themePreference == ThemePreference.DARK,
                onSelect = state::selectTheme,
                preview = { DarkPreview() },
                modifier = Modifier.weight(1f),
            )
            ThemeTile(
                option =
                    ThemeOption(
                        ThemePreference.LIGHT,
                        stringResource(Res.string.settingsThemeLight),
                        AzkarIcons.Sun,
                    ),
                selected = state.themePreference == ThemePreference.LIGHT,
                onSelect = state::selectTheme,
                preview = { LightPreview() },
                modifier = Modifier.weight(1f),
            )
            ThemeTile(
                option =
                    ThemeOption(
                        ThemePreference.SYSTEM,
                        stringResource(Res.string.settingsThemeSystem),
                        Icons.Filled.BrightnessAuto,
                    ),
                selected = state.themePreference == ThemePreference.SYSTEM,
                onSelect = state::selectTheme,
                preview = { SystemPreview() },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * One of the three choices, as the things that do not change: which preference, what it is called, and
 * the glyph that stands for it.
 *
 * A type rather than three arguments because a tile already takes five of its own — the selection, the
 * callback, the thumbnail and the modifier are all that is left to say — and threading a label and a
 * glyph through every call site as two extra arguments each is how a seven-argument composable happens.
 */
@Immutable
private data class ThemeOption(
    val preference: ThemePreference,
    val label: String,
    val icon: ImageVector,
)

/**
 * One theme choice: a glyph, its name, a thumbnail of what it produces, and the ring that marks it.
 *
 * The whole tile is the target rather than just the label, because the label is a word the reader has
 * to have decided they trust and the thumbnail is a picture of the thing they actually want. It used to
 * be a filter chip, which asked for the click to land on eleven millimetres of text.
 *
 * Selection is marked three ways — a `primary` border, a `highlight` fill and the ring — because this is
 * the one row on the page whose effect repaints the entire page underneath the pointer. A single tint
 * is exactly the kind of change that gets read as the palette moving rather than as the selection, so
 * the border and the ring are there to survive that misreading.
 */
@Composable
private fun ThemeTile(
    option: ThemeOption,
    selected: Boolean,
    onSelect: (ThemePreference) -> Unit,
    preview: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AzkarTheme.colors
    Column(
        modifier =
            modifier
                .clip(RoundedCornerShape(TILE_CORNER))
                .background(if (selected) colors.highlight else Color.Transparent)
                .border(1.dp, if (selected) colors.primary else colors.border, RoundedCornerShape(TILE_CORNER))
                .pressableClick(scale = 1f) { onSelect(option.preference) }
                .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(AzkarSpacing.md),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AzkarSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = option.icon,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(17.dp),
            )
            Text(
                text = option.label,
                color = colors.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            SettingsSelectionRing(selected = selected)
        }
        preview()
    }
}

/**
 * What a theme looks like, at the size of a thumbnail.
 *
 * A surface with a control on it rather than a colour chip: the things that change first when the palette
 * flips are the contrast between the surface and its control, and the colour of the accent — and a flat
 * square of the background colour would show none of it. The layout is borrowed from the header, which is
 * the one place a reader has already seen the app at full size.
 */
@Composable
private fun DarkPreview(modifier: Modifier = Modifier) {
    PreviewFrame(background = SolidColor(PreviewDarkSurface), modifier = modifier) {
        PreviewControl(accent = PreviewDarkAccent, track = SolidColor(PreviewDarkTrack))
    }
}

/** The light palette, same drawing. */
@Composable
private fun LightPreview(modifier: Modifier = Modifier) {
    PreviewFrame(background = SolidColor(PreviewLightSurface), modifier = modifier) {
        PreviewControl(accent = PreviewLightAccent, track = SolidColor(PreviewLightTrack))
    }
}

/**
 * The system theme, shown as the two it can turn out to be, each half drawn in its own palette.
 *
 * Two controls rather than one laid across the seam. A single control straddling the join was tidier on
 * paper and wrong on screen: half of it was drawn in the colours of the palette it was *not* sitting on,
 * so the light half read as an empty panel with a stray mark in it — the tile was claiming a preview of the
 * system theme and showing an empty half of it. A reader who has not chosen yet is exactly the reader
 * this tile exists for, and an empty half is the one thing that would make them wonder whether the app
 * can draw in light at all.
 *
 * The halves share the one frame rather than being two frames, because two frames put a seam of their own
 * down the middle on top of the seam that is the point. The frame's own inset already holds each control
 * off the tile's padding, so all that is left here is a gap either side of the join — without it the dark
 * control would sit against the light half and the light control against the dark one, which reads as one
 * tile with the colours swapped by mistake.
 *
 * The seam in the surface is two stops a hair apart rather than two solid halves: a gradient between a
 * near-black and a near-white would put a band of mud through the middle and read as a third palette.
 */
@Composable
private fun SystemPreview(modifier: Modifier = Modifier) {
    PreviewFrame(background = seamBrush(), modifier = modifier) {
        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxHeight().padding(end = PREVIEW_SEAM_GAP),
                contentAlignment = Alignment.CenterStart,
            ) {
                PreviewControl(accent = PreviewDarkAccent, track = SolidColor(PreviewDarkTrack))
            }
            Box(
                modifier = Modifier.weight(1f).fillMaxHeight().padding(start = PREVIEW_SEAM_GAP),
                contentAlignment = Alignment.CenterStart,
            ) {
                PreviewControl(accent = PreviewLightAccent, track = SolidColor(PreviewLightTrack))
            }
        }
    }
}

/** The two palettes side by side with no blend between them. */
private fun seamBrush(
    from: Color = PreviewDarkSurface,
    to: Color = PreviewLightSurface,
): Brush =
    Brush.horizontalGradient(
        0f to from,
        SEAM_END - 0.001f to from,
        SEAM_START to to,
        1f to to,
    )

/** Where the dark palette stops and the light one begins, as a fraction of the frame's width. */
private const val SEAM_START = 0.5f

/** The other side of the same seam; kept as its own constant so the two stops can be ordered apart. */
private const val SEAM_END = 0.501f

/**
 * The thumbnail's surface, and the only thing that clips its content.
 *
 * A [Brush] rather than a [Color] so the system tile can put a hard seam in the surface without drawing a
 * second frame over the first — two boxes side by side, each with its own corner radius, is what put the
 * gap down the middle of the earlier version.
 *
 * The clip, the background and the border are all three on this one Box, in that order, because they all
 * measure the same bounds and the order is what decides who is on top. The inset that keeps the control
 * off the tile's own padding has to come *after* them: a `matchParentSize()` sibling measures the Box
 * *including* that padding, which draws the border 14dp inside the surface instead of around it — a
 * frame you can only see on the light thumbnail, where there is something to see it against.
 */
@Composable
private fun PreviewFrame(
    background: Brush,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(PREVIEW_HEIGHT)
                .clip(RoundedCornerShape(PREVIEW_CORNER))
                .background(background)
                .border(1.dp, PreviewHairline, RoundedCornerShape(PREVIEW_CORNER))
                .padding(horizontal = PREVIEW_INSET),
        contentAlignment = Alignment.CenterStart,
    ) {
        content()
    }
}

/**
 * A dot and a track, at thumbnail size: the header's own shape, shrunk.
 *
 * Drawn once, inset by the frame, whatever the surface underneath it is.
 */
@Composable
private fun PreviewControl(
    accent: Color,
    track: Brush,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(PREVIEW_CONTROL_GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(PREVIEW_DOT_SIZE)
                    .background(accent, RoundedCornerShape(PREVIEW_DOT_CORNER)),
        )
        Box(
            modifier =
                Modifier
                    .height(PREVIEW_TRACK_HEIGHT)
                    .width(PREVIEW_TRACK_WIDTH)
                    .background(track, RoundedCornerShape(PREVIEW_TRACK_HEIGHT)),
        )
    }
}

/**
 * The typeface and the size, as two rows and a paragraph that shows them.
 *
 * This is the block a reader arrives at when the app is *legible* to them but not comfortable, which is
 * a different complaint from the theme above it: a theme that is wrong is wrong at a glance, whereas
 * text that is the wrong size is only wrong after ten minutes, and ten minutes is long enough to forget
 * that the setting exists. So the block is named "Text" rather than "Typography" — the word in the title
 * is the word people search Settings for.
 *
 * The paragraph at the bottom is not decoration and not an example. It is the only place on the page
 * where the answer can be judged instead of chosen: the dropdown and the slider both say what is set,
 * and neither of them says whether it is any good. A paragraph of the app's own prose, set in the face
 * and at the size now in force, answers the second question before it is asked.
 *
 * Both controls write straight through — there is no Save — because they already apply to the page they
 * sit on. Every other block here has a row that changes something further away and is therefore allowed
 * to wait for a press; these two change the letters the reader is reading the row with, so the only
 * honest way to show the effect is to not wait.
 */
@Composable
internal fun SettingsTypographySection(
    state: AzkarAppState,
    modifier: Modifier = Modifier,
) {
    SettingsSectionBlock(
        title = stringResource(Res.string.settingsTypographyHeader),
        subtitle = stringResource(Res.string.settingsTypographyBody),
        modifier = modifier,
    ) {
        SettingsGroupCard {
            TypographyFontRow(state = state)
            SettingsRowDivider()
            TypographySizeRow(state = state)
        }
    }
}

/**
 * The face, as a dropdown whose first entry is the app's own pairing.
 *
 * The bundled pairing is offered as a named option rather than being the absence of one, because after a
 * reader has picked something else "put it back" needs a target: an empty family string is a value only
 * this file knows how to read, and a row offering no way to say "the one you had" is a row with a trap
 * in it.
 *
 * The stored family is shown even when it is not in the list. That case is real rather than theoretical —
 * fonts get uninstalled, and a laptop that had Segoe UI last month may not have it now — and silently
 * substituting a different face would leave the reader staring at the app wondering why their setting
 * never seems to apply. So the name stays on screen and the row says underneath it that the face is gone.
 */
@Composable
private fun TypographyFontRow(
    state: AzkarAppState,
    modifier: Modifier = Modifier,
) {
    val name: @Composable (String) -> String = { candidate ->
        if (candidate.isEmpty()) {
            stringResource(Res.string.settingsTypographyAzkarFont)
        } else {
            candidate
        }
    }
    val stored = state.typography.family
    val options = remember { listOf(AppTypography.AZKAR_FONT) + SystemFontCatalogue.choices }
    SettingsValueRow(
        value = null,
        label = stringResource(Res.string.settingsTypographyFontLabel),
        description =
            if (stored.isNotEmpty() && !SystemFontCatalogue.knows(stored)) {
                stringResource(Res.string.settingsTypographyFontMissing)
            } else {
                stringResource(Res.string.settingsTypographyFontHint)
            },
        control = {
            TypographyFontDropdown(
                selected = stored,
                options = options,
                label = name,
                onSelect = state::selectFont,
            )
        },
        modifier = modifier,
    )
}

/**
 * The system-family picker, whose popup is lazy because installing several hundred system faces is
 * ordinary and resolving every one while opening a regular DropdownMenu is visibly slow.
 *
 * The popup is a fixed-size scrolling viewport; only its visible rows ask Compose to build a
 * [androidx.compose.ui.text.font.FontFamily]. That keeps the complete system catalogue available while
 * making the cost of opening the menu independent of how many faces the desktop has installed.
 */
@Composable
private fun TypographyFontDropdown(
    selected: String,
    options: List<String>,
    label: @Composable (String) -> String,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    Box(modifier = Modifier.width(FONT_FIELD_WIDTH)) {
        AzkarOutlinedField(
            value = label(selected),
            onValueChange = {},
            placeholder = "",
            singleLine = true,
            active = expanded,
            modifier = Modifier.fillMaxWidth(),
            trailingContent = {
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    tint = if (expanded) AzkarTheme.colors.primary else AzkarTheme.colors.textSecondary,
                    modifier = Modifier.size(18.dp),
                )
            },
        )
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { expanded = !expanded },
        )
        if (expanded) {
            LaunchedEffect(selected) {
                options.indexOf(selected).takeIf { it >= 0 }?.let { listState.scrollToItem(it) }
            }
            Popup(
                popupPositionProvider = FontPickerPositionProvider(gap = with(density) { FONT_MENU_GAP.roundToPx() }),
                onDismissRequest = { expanded = false },
                properties = PopupProperties(focusable = true),
            ) {
                FontPickerMenu(
                    options = options,
                    selected = selected,
                    listState = listState,
                    defaultFamily = azkarTypography(AzkarTheme.locale).bodyMedium.fontFamily,
                    onSelect = { choice ->
                        expanded = false
                        onSelect(choice)
                    },
                )
            }
        }
    }
}

/** The fixed viewport and lazily composed rows for the full system font catalogue. */
@Composable
private fun FontPickerMenu(
    options: List<String>,
    selected: String,
    listState: androidx.compose.foundation.lazy.LazyListState,
    defaultFamily: androidx.compose.ui.text.font.FontFamily?,
    onSelect: (String) -> Unit,
) {
    val colors = AzkarTheme.colors
    Surface(
        modifier = Modifier.size(FONT_MENU_SIZE),
        shape = RoundedCornerShape(FONT_MENU_CORNER),
        color = colors.cardBackground,
        border = BorderStroke(FONT_MENU_BORDER, colors.border),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.padding(FONT_MENU_PADDING),
            verticalArrangement = Arrangement.spacedBy(FONT_MENU_ROW_GAP),
        ) {
            items(items = options, key = { it.ifEmpty { AZKAR_FONT_OPTION_KEY } }) { option ->
                FontPickerOption(
                    option = option,
                    label = if (option.isEmpty()) stringResource(Res.string.settingsTypographyAzkarFont) else option,
                    selected = option == selected,
                    defaultFamily = defaultFamily,
                    onClick = { onSelect(option) },
                )
            }
        }
    }
}

/** One lazily created option, rendered in the actual face it names. */
@OptIn(ExperimentalTextApi::class)
@Composable
private fun FontPickerOption(
    option: String,
    label: String,
    selected: Boolean,
    defaultFamily: androidx.compose.ui.text.font.FontFamily?,
    onClick: () -> Unit,
) {
    val colors = AzkarTheme.colors
    val family = if (option.isEmpty()) defaultFamily else SystemFontCatalogue.family(option)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = FONT_MENU_ROW_HEIGHT)
                .clip(RoundedCornerShape(FONT_MENU_ROW_CORNER))
                .background(if (selected) colors.highlight else Color.Transparent)
                .clickable(onClick = onClick)
                .padding(horizontal = FONT_MENU_ROW_HORIZONTAL, vertical = FONT_MENU_ROW_VERTICAL),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.fillMaxWidth(),
            color = if (selected) colors.primary else colors.textPrimary,
            fontFamily = family,
            fontSize = FONT_MENU_TEXT_SIZE,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Position the fixed popup below its field, or above it when the window has less room below. */
private class FontPickerPositionProvider(
    private val gap: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val left =
            if (layoutDirection == androidx.compose.ui.unit.LayoutDirection.Ltr) {
                anchorBounds.left
            } else {
                anchorBounds.right - popupContentSize.width
            }
        val maxX = (windowSize.width - popupContentSize.width).coerceAtLeast(0)
        val below = anchorBounds.bottom + gap
        val above = anchorBounds.top - popupContentSize.height - gap
        val maxY = (windowSize.height - popupContentSize.height).coerceAtLeast(0)
        val top = if (below + popupContentSize.height <= windowSize.height) below else above.coerceAtLeast(0)
        return IntOffset(x = left.coerceIn(0, maxX), y = top.coerceIn(0, maxY))
    }
}

/**
 * The size, as a slider under a label that carries the number.
 *
 * The number sits beside the label rather than being left to the thumb's position because the thumb's
 * position is not a quantity anyone can hold in their head — "a bit past the middle" is a description of
 * where the thumb is, not of how big the text now is — and the one thing a reader who has just dragged a
 * a slider wants to be told is what they now have. It is the percentage, because that is the unit the
 * platform itself uses for this setting and the one a reader who has set it elsewhere will be looking for.
 *
 * Steps rather than a continuous range because 113% is a size nobody chose. Five-point steps keep the
 * reachable set to the values somebody would actually tap, and every one of them is a round number in the
 * scale they are already thinking in.
 */
@Composable
private fun TypographySizeRow(
    state: AzkarAppState,
    modifier: Modifier = Modifier,
) {
    SettingsValueRow(
        label = stringResource(Res.string.settingsTypographySizeLabel),
        value = null,
        description = stringResource(Res.string.settingsTypographySizeHint),
        control = {
            AzkarDropdown(
                selected = state.typography.scalePercent,
                options = AppTypography.SCALE_STEPS,
                label = { "$it%" },
                onSelect = state::setTextScale,
                fieldWidth = 90.dp,
            )
        },
        modifier = modifier,
    )
}

/**
 * Wide enough for a family name like "SF Pro Display" without the chevron crowding it, and narrow enough
 * that the field does not out-measure the sentence of prose beside it.
 */
private val FONT_FIELD_WIDTH = 180.dp

/** The picker stays this size while the installed-family list scrolls inside it. */
private val FONT_MENU_SIZE = DpSize(width = 320.dp, height = 360.dp)

/** Inset between the popup boundary and its first/last selectable family. */
private val FONT_MENU_PADDING = 4.dp

/** A small separation keeps adjacent font rows distinct while preserving a compact viewport. */
private val FONT_MENU_ROW_GAP = 2.dp

/** Each option has a predictable hit target, regardless of its own typeface metrics. */
private val FONT_MENU_ROW_HEIGHT = 40.dp

private val FONT_MENU_ROW_HORIZONTAL = 10.dp
private val FONT_MENU_ROW_VERTICAL = 6.dp
private val FONT_MENU_ROW_CORNER = 8.dp
private val FONT_MENU_CORNER = 12.dp
private val FONT_MENU_BORDER = 1.dp
private val FONT_MENU_TEXT_SIZE = 13.sp
private val FONT_MENU_GAP = 4.dp

/** The empty family name represents the bundled Inter/Amiri choice and must be a stable LazyColumn key. */
private const val AZKAR_FONT_OPTION_KEY = "__azkar_bundled_font__"

@Preview
@Composable
private fun SettingsAppearanceSectionsPreview() {
    AzkarThemePreview {
        Column(
            modifier = Modifier.width(760.dp).padding(AzkarSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(AzkarSpacing.section),
        ) {
            SettingsThemeSection(state = previewAzkarAppState())
            SettingsTypographySection(state = previewAzkarAppState())
            SettingsReminderCardSection(
                presentation = NotificationPresentationSettings(),
                settings = rememberPreviewNotificationSettingsState(),
            )
        }
    }
}
