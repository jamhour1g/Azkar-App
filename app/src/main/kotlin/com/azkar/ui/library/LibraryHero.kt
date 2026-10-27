package com.azkar.ui.library

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import com.azkar.generated.resources.Res
import com.azkar.generated.resources.collectionsActionNew
import com.azkar.generated.resources.collectionsSearchCollections
import com.azkar.generated.resources.hero_day
import com.azkar.generated.resources.hero_night
import com.azkar.generated.resources.libraryFiltersLabel
import com.azkar.generated.resources.libraryHeroLabel
import com.azkar.generated.resources.libraryHeroSubtitle
import com.azkar.generated.resources.libraryHeroTitle
import com.azkar.generated.resources.tagsActionManage
import com.azkar.ui.components.AzkarOutlinedField
import com.azkar.ui.components.TooltipIconButton
import com.azkar.ui.library.model.CollectionScope
import com.azkar.ui.library.model.CollectionSort
import com.azkar.ui.preview.previewLibraryHeroControls
import com.azkar.ui.theme.AzkarColors
import com.azkar.ui.theme.AzkarPreview
import com.azkar.ui.theme.AzkarTheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * The banner across the top of the shelf.
 *
 * It carries the page's title, the one search box that matters, and the button that opens the two
 * controls deciding whose collections are shown and in what order. The illustration behind it is the
 * app's own `hero_day` / `hero_night` pair — the only artwork the project has — washed with a scrim
 * so the type stays legible over either of them and so the banner dissolves into the page at the
 * bottom instead of ending in a hard edge.
 *
 * The scope filter is behind the umbrella button rather than in the chip row below, because the
 * chips below narrow by what a collection is about and this narrows by whose collections are shown:
 * one row cannot be both. It is behind a button rather than sitting beside the field
 * because two dropdowns spent half the banner on two of the shelf's nine answers.
 *
 * The filters open in a popover rather than in the page, so nothing moves when they do. The banner's
 * control row also holds the button that adds a collection, which used to sit in its own header
 * above the banner — see [HeroControlRow] for why that arrangement was worth giving up.
 */

@Composable
fun LibraryHero(
    controls: LibraryHeroControls,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = AzkarTheme.colors
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(if (compact) HERO_COMPACT_HEIGHT else HERO_HEIGHT)
                .clip(RoundedCornerShape(HERO_SHAPE.dp))
                .border(1.dp, colors.border, RoundedCornerShape(HERO_SHAPE.dp)),
    ) {
        Image(
            painter = painterResource(heroIllustration()),
            contentDescription = null,
            // Cropped rather than fitted: the illustration is a wide landscape and the banner is not
            // its shape, so filling the box means cropping the top and bottom — which is where the
            // sky is, and where nothing is printed.
            contentScale = ContentScale.Crop,
            alignment = Alignment.BottomEnd,
            modifier = Modifier.fillMaxSize(),
        )
        Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(HeroScrim(colors))))
        Column(modifier = Modifier.fillMaxSize().padding(HERO_INSET)) {
            HeroHeading()
            Spacer(Modifier.weight(1f))
            HeroControlRow(controls)
        }
    }
}

@AzkarPreview
@Composable
internal fun LibraryHeroPreview() {
    Column(
        modifier = Modifier.width(1200.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        LibraryHero(controls = previewLibraryHeroControls(), compact = false)
        LibraryHero(controls = previewLibraryHeroControls(query = "Ar-Rahman"), compact = true)
    }
}

/**
 * The wash over the illustration.
 *
 * A vertical gradient rather than a flat tint: the illustration runs bright at the top and dark at
 * the bottom, so one flat scrim either greys out the sky or leaves the title on bare pixels.
 *
 * Internal rather than private because [TagsHero] lays a banner the same way, and a second banner
 * that picked its own wash would be the first thing that made two pages look like different apps.
 */
@Composable
internal fun HeroScrim(colors: AzkarColors) = listOf(colors.heroScrimStart, colors.heroScrimEnd)

/** The eyebrow, the title and the standfirst, stacked at the top of the banner. */
@Composable
private fun HeroHeading() {
    val colors = AzkarTheme.colors
    HeroEyebrow()
    Text(
        text = stringResource(Res.string.libraryHeroTitle),
        color = colors.textPrimary,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 6.dp),
    )
    // Nearly the title's own colour rather than the muted one: the standfirst sits over the
    // brightest part of the illustration, where the secondary token drops under 4.5:1 in the dark
    // theme and the one line that says what the page is for becomes the hardest line to read.
    Text(
        text = stringResource(Res.string.libraryHeroSubtitle),
        color = colors.textPrimary.copy(alpha = HERO_STANDFIRST_ALPHA),
        fontSize = 12.sp,
        modifier = Modifier.padding(top = 4.dp),
    )
}

/**
 * The search field, the control that creates a collection, and the one that opens both
 * filters.
 *
 * All four controls in one row. The filters used to sit here as two dropdowns of their own, which spent 300dp
 * of the banner to show two of the nine answers the shelf is capable of giving; they are behind one
 * button now. The button that adds a collection used to live in a header above the banner, and moved
 * down into this row: as a glyph beside the field it is simply one of the things the shelf lets the
 * user do, and the header is left with nothing but the page's name. A row of labelled buttons inside
 * a banner looked like decoration, which was the whole reason they were kept out of it — but that
 * reasoning is about labels, and there are none here.
 *
 * The filter umbrella stays last. Its popover is aligned to the button's right edge, so anything in
 * front of it would pull the popover off the window edge and leave it hanging over the row it was
 * opened from.
 */
@Composable
private fun HeroControlRow(controls: LibraryHeroControls) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HERO_ROW_GAP),
    ) {
        AzkarOutlinedField(
            value = controls.query,
            onValueChange = controls.onQueryChanged,
            placeholder = stringResource(Res.string.collectionsSearchCollections),
            leadingIcon = Icons.Filled.Search,
            modifier = Modifier.weight(1f),
        )
        HeroAction(
            icon = Icons.Filled.Add,
            description = stringResource(Res.string.collectionsActionNew),
            onClick = controls.onNew,
            emphasized = true,
        )
        HeroAction(
            icon = Icons.AutoMirrored.Filled.Label,
            description = stringResource(Res.string.tagsActionManage),
            onClick = controls.onManageTags,
        )
        FilterUmbrellaButton(controls = controls)
    }
}

/**
 * One round button in the banner's control row.
 *
 * The same wash for every button in the row, so that it reads as one toolbar rather than as a
 * filter with two strangers standing beside it. What separates the one that makes a new collection is
 * the colour of its glyph rather than the colour of its circle: a filled circle next to two hollow
 * ones is a louder statement than adding a collection deserves, and an amber glyph on the same wash
 * says it without changing the row's shape.
 *
 * Internal rather than private because [TagsHero]'s band holds the same kind of control in the same
 * place, and it used to hold a labelled button where this row holds a glyph. A word beside the one
 * control that adds is the only thing on this page that needed reading before it could be pressed.
 */
@Composable
internal fun HeroAction(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    emphasized: Boolean = false,
) {
    TooltipIconButton(
        icon = icon,
        tooltip = description,
        onClick = onClick,
        modifier = Modifier.size(HERO_ACTION_SIZE),
        buttonSize = HERO_ACTION_SIZE,
        iconSize = HERO_ACTION_GLYPH,
        background = AzkarTheme.colors.cardBackground.copy(alpha = HERO_ACTION_WASH),
        favorite = emphasized,
    )
}

/**
 * The sliders button that opens the filter popover, with a dot when a filter is off its default.
 *
 * `Tune` rather than a funnel: a funnel says *narrow*, and one of the two decisions behind this
 * button does not narrow anything — it reorders. Sliders is the one glyph that covers both.
 *
 * The dot is the only sign that a filter is doing something while the panel is shut. Without it the
 * shelf silently stops showing official collections, or silently reorders, and the one piece of state
 * the user cannot see is the state they set.
 */
@Composable
private fun FilterUmbrellaButton(controls: LibraryHeroControls) {
    val colors = AzkarTheme.colors
    val narrowing = controls.narrowing
    val narrowed =
        narrowing.filtersOpen ||
            narrowing.scope != CollectionScope.ALL ||
            narrowing.sort != DEFAULT_SORT
    Box {
        TooltipIconButton(
            icon = Icons.Filled.Tune,
            tooltip = stringResource(Res.string.libraryFiltersLabel),
            onClick = narrowing.onFiltersToggle,
            modifier = Modifier.size(HERO_ACTION_SIZE),
            buttonSize = HERO_ACTION_SIZE,
            iconSize = HERO_ACTION_GLYPH,
            background = colors.cardBackground.copy(alpha = HERO_ACTION_WASH),
        )
        if (narrowed) {
            // Drawn outside the button's own bounds on purpose: a dot inside would sit on the glyph,
            // and the glyph is the thing the user is aiming at.
            Box(
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .size(FILTER_MARK_SIZE)
                        .clip(CircleShape)
                        .background(colors.primary),
            )
        }
        if (narrowing.filtersOpen) {
            Popup(
                popupPositionProvider = FilterPopoverPosition(gap = FILTER_POPOVER_GAP),
                onDismissRequest = narrowing.onFiltersToggle,
            ) {
                LibraryFilterPanel(
                    scope = narrowing.scope,
                    onScopeSelected = narrowing.onScopeChanged,
                    sort = narrowing.sort,
                    onSortSelected = narrowing.onSortSelected,
                )
            }
        }
    }
}

/**
 * Where the filter popover sits: under the button, flush with its right edge.
 *
 * Right-aligned rather than centred on the button because the button is the popover's right-hand
 * edge already — it sits at the end of the search row — so aligning to it keeps the popover inside
 * the window without a second guess about which way the window will grow.
 *
 * Both axes are clamped. The vertical clamp is what stops a popover taller than the space under the
 * button from being drawn off the bottom of a short window, which on a desktop means off-screen and
 * unreachable.
 */
private class FilterPopoverPosition(
    private val gap: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset =
        IntOffset(
            x = (anchorBounds.right - popupContentSize.width).coerceAtLeast(0),
            y = (anchorBounds.bottom + gap).coerceAtMost(windowSize.height - popupContentSize.height),
        )
}

/** The small blue "Azkar Library" line above the title, with the app's own glyph. */
@Composable
private fun HeroEyebrow() {
    val colors = AzkarTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.LibraryBooks,
            contentDescription = null,
            tint = colors.primary,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(Res.string.libraryHeroLabel),
            color = colors.primary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * Which of the two shipped illustrations the banner shows.
 *
 * The illustration follows the active theme so light and dark renders stay consistent and
 * reproducible regardless of when they are captured.
 *
 * Internal rather than private because [TagsHero] is a banner too, and drawing a third colour of
 * sky for it is how a page ends up looking borrowed from somewhere else.
 */
@Composable
internal fun heroIllustration() =
    if (AzkarTheme.colors.isDark) {
        Res.drawable.hero_night
    } else {
        Res.drawable.hero_day
    }

/** How tall the banner is, in dp. Room for the title, the subtitle and the control row. */
private val HERO_HEIGHT = 236.dp

/**
 * How much shorter the banner is on a narrow window.
 *
 * The illustration is cropped from the top and bottom, and the sky is what gets cropped, so losing
 * height costs nothing but the gap between the title and the search field. On a short window that
 * gap is the difference between the shelf being visible on arrival and being below the fold.
 */
private val HERO_COMPACT_HEIGHT = 188.dp

/**
 * How far the title's standfirst is faded over the illustration, and the banner's corner radius.
 *
 * The standfirst sits over the brightest part of the picture, where the muted secondary token drops
 * under 4.5:1 in the dark theme; the same note is repeated where the colour is chosen.
 */
private const val HERO_STANDFIRST_ALPHA = 0.82f
private const val HERO_SHAPE = 14

/**
 * The sort the shelf opens on, and therefore the one that is not worth a dot.
 *
 * Named here rather than read off `CollectionSort.entries.first()` so that reordering the enum —
 * which is a change nobody would think of as a change in meaning — cannot silently decide which
 * default the button stays quiet about.
 *
 * The full shelves first, because that is the order a library is browsed in: the biggest collections
 * are the ones worth opening, and a shelf ordered by when a collection was last opened puts the
 * emptiest ones in front of it.
 */
private val DEFAULT_SORT = CollectionSort.MOST_ITEMS

/**
 * Every button in the banner's control row, in one set of numbers.
 *
 * One set for all of them because they stand shoulder to shoulder in a single row: the create button
 * at 44dp beside the filter button at 40dp would read as an accident rather than as a decision, and
 * 44dp is also taller than the search field they all line up with. Sized to the field's own height,
 * because a row of controls at three different heights is the one thing a control row cannot do.
 */
private val HERO_ACTION_SIZE = 40.dp
private val HERO_ACTION_GLYPH = 18.dp

/** How opaque a button's own surface is over the illustration, and the gap along the row. */
private const val HERO_ACTION_WASH = 0.6f
private val HERO_ROW_GAP = 10.dp

/** The dot on the filter button's corner, drawn only while a filter is off its default. */
private val FILTER_MARK_SIZE = 8.dp

/** The gap between the button and the popover below it, in dp. */
private const val FILTER_POPOVER_GAP = 6

/** How far the banner's content sits from its edge, in dp. */
private val HERO_INSET = 20.dp

/**
 * Everything the banner's control row changes, bundled the way `ShelfCallbacks` bundles the
 * shelf's.
 *
 * Three groups rather than seven flat parameters: the field and its callback, the narrowing and the
 * two shelf-changing actions. Flat, this is a list where the compiler cannot check the pairing, and
 * the `on…Changed` halves have already been paired wrongly once — a button wired to the scope
 * callback would compile perfectly and filter on click.
 *
 * Declared after the composable rather than before it so that this file's name matches its first
 * declaration.
 */
@Immutable
data class LibraryHeroControls(
    val query: String,
    val onQueryChanged: (String) -> Unit,
    val narrowing: ShelfNarrowing,
    val onNew: () -> Unit,
    /**
     * Opens the Manage tags screen.
     *
     * The fourth of the shelf's own actions rather than a setting, and it sits with the other three
     * because that is what it is: a label applied to a collection is the same sort of fact as the
     * collection, and both are reached from the banner the shelf is already drawn under.
     */
    val onManageTags: () -> Unit,
)

/**
 * What the shelf is narrowed by, and the one thing that opens the panel saying so.
 *
 * The scope, the sort and the popover's open state are one decision the user makes in one place, so
 * they travel as one value. Held by the page rather than by the banner for the same reason the
 * banner's own layout does not know about them: a panel that opened by pushing the banner's contents
 * would have to undo the weighted spacer that holds the search field to the bottom, so the panel opens
 * in a popover and the banner never changes shape.
 */
@Immutable
data class ShelfNarrowing(
    val scope: CollectionScope,
    val onScopeChanged: (CollectionScope) -> Unit,
    val sort: CollectionSort,
    val onSortSelected: (CollectionSort) -> Unit,
    val filtersOpen: Boolean,
    val onFiltersToggle: () -> Unit,
)
