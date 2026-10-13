package com.azkar.ui.library

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.azkar.domain.model.RemembranceCollection
import com.azkar.generated.resources.Res
import com.azkar.generated.resources.collectionsActionDelete
import com.azkar.generated.resources.collectionsActionDuplicate
import com.azkar.generated.resources.collectionsActionEdit
import com.azkar.generated.resources.collectionsActionFavorite
import com.azkar.generated.resources.collectionsActionOpen
import com.azkar.generated.resources.collectionsActionPin
import com.azkar.generated.resources.collectionsActionUnfavorite
import com.azkar.generated.resources.collectionsActionUnpin
import com.azkar.generated.resources.collectionsDuplicateNameSuffix
import com.azkar.generated.resources.collectionsLabelsAll
import com.azkar.generated.resources.collectionsLabelsScrollBack
import com.azkar.generated.resources.collectionsLabelsScrollForward
import com.azkar.generated.resources.collectionsSubtitle
import com.azkar.generated.resources.collectionsTitle
import com.azkar.ui.components.ActionIcons
import com.azkar.ui.components.AzkarFilterChip
import com.azkar.ui.components.AzkarIcons
import com.azkar.ui.components.AzkarMenuItem
import com.azkar.ui.components.TooltipIconButton
import com.azkar.ui.library.data.CollectionPresenter
import com.azkar.ui.library.model.CollectionTile
import com.azkar.ui.theme.AzkarTheme
import com.azkar.ui.theme.AzkarWindowSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import kotlin.math.floor

/**
 * One column: what the library is called, how it is being narrowed, and the collections.
 *
 * There were three columns here once, and two of them went for the same reason. A sidebar listing
 * every collection with its count said the same thing as the chip row directly beneath it, further
 * from the shelf. A rail holding one "featured" collection and a verse said it worse: the featured
 * panel was a second door to a collection the shelf already offers a card for, on a strip of screen
 * that the shelf could have used for another column of cards, and its figures — how many
 * remembrances, how long they take — are now on every card instead. What it took away is real: a
 * quotation is no longer on the shelf, and that was the trade.
 *
 * [AzkarWindowSize.isCompactWidth] still decides the shape, because the header and the banner are
 * what adapt. They stop trying to share a row with each other and stack instead.
 */
@Composable
internal fun LibraryHub(
    state: CollectionsState,
    callbacks: ShelfCallbacks,
    modifier: Modifier = Modifier,
) {
    // The pane paints its own background. `AzkarApp` paints one behind the whole window already, but
    // a screen that renders anywhere else — the screenshot harness does exactly that — would otherwise
    // leave whatever the window underneath happens to be showing through the gutters.
    //
    // The inset lives here rather than on each child, which is the whole of the fix for the padding
    // that used to disagree: the header carried a top inset of its own, the rail carried the same one
    // separately, the compact branch carried none at all, and nothing anywhere carried a bottom one,
    // so the columns were inset from the top and flush against the bottom. One inset, applied once,
    // on the only thing the shelf lives inside.
    ShelfColumn(
        state = state,
        callbacks = callbacks,
        compact = AzkarWindowSize.isCompactWidth,
        modifier = modifier.fillMaxSize().background(AzkarTheme.colors.background).padding(HUB_PADDING),
    )
}

/**
 * The shelf column: what the library is called, how it is being narrowed, and the collections.
 *
 * The column itself is the scroll container, and that is deliberate rather than incidental: when the
 * card grid was the scroll container, the wheel did nothing anywhere above it, so the banner and the
 * chip row were a dead zone the user had to steer around. One scroll for the page means the wheel
 * works wherever the pointer is. It also means nothing below may scroll on its own — a nested
 * vertical scroll inside this one has no bounded height to measure against — which is why the grid
 * lays its cards out plainly and why the empty states below are given a height instead of filling.
 */
@Composable
private fun ShelfColumn(
    state: CollectionsState,
    callbacks: ShelfCallbacks,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    // Held here rather than inside the banner so that opening the filters cannot move the banner's
    // contents: a popover that pushed the page down would slide the cards out from under the pointer
    // at the exact moment the user is choosing which of them to see.
    var filtersOpen by remember { mutableStateOf(false) }
    Column(modifier = modifier.verticalScroll(rememberScrollState())) {
        ShelfHeading()
        Spacer(Modifier.height(HUB_GAP))
        LibraryHero(
            controls =
                LibraryHeroControls(
                    query = state.collectionQuery,
                    onQueryChanged = { state.collectionQuery = it },
                    narrowing =
                        ShelfNarrowing(
                            scope = state.scopeFilter,
                            onScopeChanged = { state.scopeFilter = it },
                            sort = state.sort,
                            onSortSelected = { state.sort = it },
                            filtersOpen = filtersOpen,
                            onFiltersToggle = { filtersOpen = !filtersOpen },
                        ),
                    onImport = callbacks.onImport,
                    onNew = callbacks.onCreate,
                    onManageTags = callbacks.onManageTags,
                ),
            compact = compact,
        )
        Spacer(Modifier.height(HUB_GAP))
        // Nothing is rendered when no collection carries a label, so a shelf the user has not
        // organised does not grow a row of chips that narrow it to nothing.
        if (state.shelfLabels.isNotEmpty()) {
            LabelChips(state = state)
            Spacer(Modifier.height(HUB_GAP))
        }
        ShelfBody(state = state, callbacks = callbacks)
        CollectionsInfoLine(text = state.infoLine, modifier = Modifier.padding(top = HUB_GAP))
    }
}

/**
 * The page's name and what the page is for, and nothing else.
 *
 * It used to carry the two buttons that add and import a collection, on the reasoning that a row of
 * labelled buttons inside a banner looks like decoration. That reasoning was about the labels and not
 * about the buttons, so both moved down into the banner's own control row as glyphs and the header is
 * left with the two lines it was always really for. Nothing was lost by it: there is no longer a
 * second toolbar competing with the one the user is looking at.
 */
@Composable
private fun ShelfHeading(modifier: Modifier = Modifier) {
    val colors = AzkarTheme.colors
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(Res.string.collectionsTitle),
            color = colors.textPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(Res.string.collectionsSubtitle),
            color = colors.textSecondary,
            fontSize = 12.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/**
 * Every label in use on the shelf, with an "All" chip, narrowing by what a collection is about.
 *
 * This used to be the second of two rows. The row above it listed every collection by name, and the
 * two answered the same question — "which of these am I looking at" — from two different taxonomies,
 * once from the seeded collection names and once from the tag rows. Two controls for one job is how
 * a shelf ends up narrowed by something the user can no longer see, and the cards below already say
 * which collection is which. So the row of names went and this one stayed.
 *
 * Kept on one line rather than wrapped. Wrapping is what [LibraryFilterPanel] does with its seven
 * options and is the better answer there; a shelf can carry any number of labels, because they are
 * the user's own, and a row that grows to a screenful would push the cards it filters off the page
 * to make room for them. The cost of keeping the height fixed is that the row can overflow, and two
 * things answer that: the plain wheel scrolls it, and a pair of chevrons stands in the same row at
 * either end of it.
 *
 * The chevrons are members of the row rather than drawn over its ends. Overlaying them put a lit
 * circle on top of whichever chip happened to be underneath, which covered a label to announce that
 * the label was not the end of the row. Sharing the row instead lets the chips give up the width
 * themselves, which costs a chip and a half at the extremes and never covers one.
 *
 * Both chevrons are drawn only while there is somewhere to go, and the two that are drawn at the
 * ends go dead rather than disappearing — a control that vanishes between presses is a control the
 * user has to find again, and a control that is always there is one that can be aimed at.
 */
@Composable
private fun LabelChips(
    state: CollectionsState,
    modifier: Modifier = Modifier,
) {
    val labels = state.shelfLabels
    val locale = AzkarTheme.locale
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    // `maxValue` is only known after the row has been measured once, which is why the chevrons are
    // driven off it rather than off the number of labels: a row that fits needs no affordance at all,
    // and one that does not needs them whatever it holds.
    val overflows = scroll.maxValue > 0
    // A page rather than all of it, so that the chevrons keep the user oriented: stepping to the end
    // of the row in one press is the same as not having them at all.
    val page = { scroll.viewportSize * SCROLL_PAGE_FRACTION }

    Row(
        modifier = modifier.fillMaxWidth().padding(top = CHIP_GAP),
        horizontalArrangement = Arrangement.spacedBy(CHIP_GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (overflows) {
            TooltipIconButton(
                icon = AzkarIcons.ChevronStart,
                tooltip = stringResource(Res.string.collectionsLabelsScrollBack),
                onClick = { scope.launch { scroll.animateScrollBy(-page()) } },
                enabled = scroll.value > 0,
            )
        }
        Row(
            modifier =
                Modifier
                    // The row is what gives the chips their width, so the chevrons are paid for out
                    // of the overflow rather than out of the shelf.
                    .weight(1f)
                    .horizontalScroll(scroll)
                    .wheelScrollsX(scroll, scope),
            horizontalArrangement = Arrangement.spacedBy(CHIP_GAP),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AzkarFilterChip(
                text = stringResource(Res.string.collectionsLabelsAll),
                selected = state.labelFilter == null,
                onClick = { state.labelFilter = null },
            )
            labels.forEach { label ->
                val id = label.id
                AzkarFilterChip(
                    text = label.displayName(locale),
                    selected = state.labelFilter != null && state.labelFilter == id,
                    onClick = {
                        state.labelFilter = if (state.labelFilter == id) null else id
                    },
                    // The mark is read from the stored name, not from the one the chip shows: the chip
                    // says a label in the reader's language, the mark says which label it is.
                    leadingIcon = iconForCollection(CollectionPresenter.iconKeyFor(label.name)),
                )
            }
        }
        if (overflows) {
            TooltipIconButton(
                icon = AzkarIcons.ChevronRight,
                tooltip = stringResource(Res.string.collectionsLabelsScrollForward),
                onClick = { scope.launch { scroll.animateScrollBy(page()) } },
                enabled = scroll.value < scroll.maxValue,
            )
        }
    }
}

/**
 * Lets a plain mouse wheel drive a sideways-scrolling row.
 *
 * Compose gives a wheel's vertical delta to a vertical scroller and leaves a horizontal one alone,
 * because on every platform a wheel means "up and down". That is the right default everywhere else
 * and the wrong answer for a strip of chips: the one gesture everybody already has moves the page
 * instead, so the row could only be moved by holding shift down — a shortcut nothing on screen
 * mentions, and the reason a dozen labels used to be unreachable without knowing it.
 *
 * The event is consumed, and that is what keeps this row and the column that holds it from both
 * acting on one turn of the wheel. Only turns the row can actually use are consumed: a delta of zero
 * belongs to something else, and a turn aimed past the end of the row is left alone so that it goes
 * on to scroll the page. A row that has run out of chips stops eating the wheel rather than
 * stranding the reader on it.
 *
 * The row jumps rather than animating, because a wheel arrives as a series of notches and easing
 * between them would leave the chips lagging behind the gesture that is moving them.
 *
 * The scroll is handed to [scope] rather than called here, because the scope this loop runs in is a
 * restricted one that will not let it suspend into a scroll of its own — and [scope] is the
 * composition's, so it is cancelled with the row rather than outliving it.
 */
private fun Modifier.wheelScrollsX(
    state: ScrollState,
    scope: CoroutineScope,
): Modifier =
    pointerInput(state) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                val delta = event.horizontalDeltaRowCanUse(state) ?: continue
                scope.launch { state.scrollBy(delta) }
                event.changes.forEach { it.consume() }
            }
        }
    }

/**
 * The sideways distance this event asks the row to move, or null when the row must not act on it.
 *
 * Null covers the three cases the loop used to `continue` past — not a scroll, a scroll with no
 * sideways delta, and a turn aimed past the end of the row — so that only one decision to skip is
 * left to the caller.
 */
private fun PointerEvent.horizontalDeltaRowCanUse(state: ScrollState): Float? {
    if (type != PointerEventType.Scroll) return null
    val delta =
        changes
            .first()
            .scrollDelta.y
    val room = if (delta > 0f) state.maxValue - state.value else state.value
    return delta.takeIf { it != 0f && room > 0f }
}

/**
 * A line of passage for a card to show under its title.
 *
 * The first member rather than the collection's description: the descriptions are one clause about
 * what the collection is for, and a card wants a hint of what is actually in it. An empty
 * collection falls back to the description, which is then the only thing there is to say.
 */
private fun collectionPreview(
    presenter: CollectionPresenter,
    collection: RemembranceCollection,
): String =
    collection.members
        .firstOrNull()
        ?.let { presenter.preview(presenter.localizedPrimaryText(it), PREVIEW_MAX_LENGTH) }
        ?.takeIf { it.isNotEmpty() }
        ?: collection.description.orEmpty()

/**
 * The collections themselves, as a grid of cover cards.
 *
 * A plain grid of rows rather than a `LazyVerticalGrid`, and the reason is where the scrolling lives.
 * A lazy grid is its own scroll container, so the wheel only works with the pointer over it — while
 * the banner, the heading and the chip row above it sat inert, and a user dragging the wheel across
 * the shelf got nothing until they happened to be over a card. Since the whole page scrolls as one
 * now, the grid cannot be a scroll container of its own; it measures its own rows instead, which is
 * why every card is an exact width rather than a weighted one — a short last row of stretched cards
 * would read as a mistake rather than as the end of the shelf.
 */
@Composable
internal fun CollectionShelf(
    state: CollectionsState,
    callbacks: ShelfCallbacks,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val columns = fittedColumns(available = maxWidth, gap = HUB_GAP, minWidth = SHELF_CARD_MIN_WIDTH)
        val cardWidth = fittedCellWidth(available = maxWidth, columns = columns, gap = HUB_GAP)
        Column(verticalArrangement = Arrangement.spacedBy(HUB_GAP)) {
            state.visibleCollections.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(HUB_GAP)) {
                    row.forEach { collection ->
                        ShelfCard(
                            state = state,
                            callbacks = callbacks,
                            collection = collection,
                            modifier = Modifier.width(cardWidth),
                        )
                    }
                }
            }
        }
    }
}

/**
 * One card, at the width the grid worked out for it.
 *
 * Its own composable only so that the grid's loop stays a loop: the body below is long enough that
 * inlining it would bury the one thing worth reading, which is that the height comes from the card's
 * contract and the width from the grid.
 */
@Composable
private fun ShelfCard(
    state: CollectionsState,
    callbacks: ShelfCallbacks,
    collection: RemembranceCollection,
    modifier: Modifier = Modifier,
) {
    val tile = state.presenter.shelfTile(collection, state.recitedCounts)
    val id = collection.id
    CollectionCard(
        tile = tile,
        preview = collectionPreview(state.presenter, collection),
        items = tile.overflowItems(state, callbacks, collection),
        onOpen = { id?.let(callbacks.onOpen) },
        // Without this the card has no way to decode the picture the user chose and falls back to the
        // generated wash, so the shelf looks as though the cover was never picked. It has no default
        // for that reason: a parameter this easy to leave out should not compile when it is.
        covers = state.covers,
        // The height is the card's own contract. A row is sized by its tallest item, so without it
        // one card whose preview needed a third line would make every card beside it taller than its
        // own content.
        modifier = modifier.height(COLLECTION_CARD_HEIGHT),
    )
}

/**
 * How many columns of at least [minWidth] fit across [available].
 *
 * The arithmetic a lazy grid would do for itself, and it needs [gap] in both places because the last
 * cell in a row has a gap after it that no cell occupies. Never fewer than one: a window narrower
 * than a single cell still has to show the cell, shrunk, rather than nothing at all.
 *
 * [minWidth] is a parameter rather than this file's own constant because two pages now lay their cards
 * out this way — the shelf and the collection's members — and they disagree about how narrow a card
 * may get before it stops reading as one. Sharing the arithmetic is worth a parameter; sharing the
 * answer would not be.
 */
internal fun fittedColumns(
    available: Dp,
    gap: Dp,
    minWidth: Dp,
): Int = maxOf(1, floor((available + gap) / (minWidth + gap)).toInt())

/**
 * The width of one cell in a row of [columns], exact so every row lines up.
 *
 * Worked out in raw dp rather than through `Dp`'s own operators: they divide by a `Dp` and scale by
 * a `Float`, neither of which is the shape of the arithmetic here, and a `Dp` divided by an `Int` is
 * not a thing that compiles.
 */
internal fun fittedCellWidth(
    available: Dp,
    columns: Int,
    gap: Dp,
): Dp {
    val perRow = columns.coerceAtLeast(1)
    val usable = available.value - gap.value * (perRow - 1)
    return Dp(usable / perRow)
}

/**
 * The name a copy starts from.
 *
 * Localized in a composable because the lambdas that hand it on are not composable: a
 * `stringResource` inside one of them would not compile, and the sentence has to be built where it
 * is used rather than passed around already formatted.
 */
@Composable
internal fun duplicateNameFor(name: String): String = stringResource(Res.string.collectionsDuplicateNameSuffix, name)

/**
 * A tile's menu.
 *
 * Rename and delete are offered only for a collection the user owns. A shipped collection's contents
 * come from the app's own catalogue, so the honest offer there is "duplicate it" — which is why
 * duplicate is the one entry every tile has.
 *
 * Built here rather than assembled by the caller out of six lambdas, because every one of those
 * lambdas closed over the same two objects and the reader of the call site had to work out which
 * button each one belonged to. The tile that draws the menu knows what it is offering.
 *
 * Split by concern rather than built in one list, so that "which entries depend on the kind" is a
 * question each group answers for itself — and so that a fourth group added later has somewhere obvious
 * to go.
 */
@Composable
private fun CollectionTile.overflowItems(
    state: CollectionsState,
    callbacks: ShelfCallbacks,
    collection: RemembranceCollection,
): List<AzkarMenuItem> =
    identityItems(callbacks, collection) +
        preferenceItems(state, collection) +
        destructiveItems(callbacks, collection)

/**
 * What the tile *is*: opening it, and changing or copying what it contains.
 *
 * The only group that varies with the kind in the middle of it — edit sits after open and before
 * duplicate, so that a menu which has it reads open → edit → duplicate and a menu which has not reads
 * open → duplicate, and the entry that is missing is the same place in both.
 */
@Composable
private fun CollectionTile.identityItems(
    callbacks: ShelfCallbacks,
    collection: RemembranceCollection,
): List<AzkarMenuItem> {
    val id = collection.id
    // Resolved before the list is built rather than inside the duplicate entry's click handler, so
    // that building the menu does not have to be a composable call the caller of onClick can make.
    val duplicateName = duplicateNameFor(name)
    return buildList {
        add(
            AzkarMenuItem(
                label = stringResource(Res.string.collectionsActionOpen),
                onClick = { id?.let(callbacks.onOpen) },
                icon = ActionIcons.open,
            ),
        )
        add(
            AzkarMenuItem(
                label = stringResource(Res.string.collectionsActionEdit),
                onClick = { callbacks.onEdit(collection) },
                icon = ActionIcons.edit,
            ),
        )
        add(
            AzkarMenuItem(
                label = stringResource(Res.string.collectionsActionDuplicate),
                onClick = { callbacks.onDuplicate(collection, duplicateName) },
                icon = ActionIcons.duplicate,
            ),
        )
    }
}

/**
 * What the user thinks of the tile: pinned to the top of the shelf, or starred.
 *
 * Both are the same kind of claim — a per-device opinion about a collection the user owns or was given —
 * which is why they sit together and neither sits with the entries above. The starring wording comes
 * from the session band's star pill so the same star is not called two things in two places, which is
 * how a user ends up unsure whether the card's star and the band's agree.
 */
@Composable
private fun CollectionTile.preferenceItems(
    state: CollectionsState,
    collection: RemembranceCollection,
): List<AzkarMenuItem> {
    val id = collection.id
    return buildList {
        add(
            AzkarMenuItem(
                label =
                    stringResource(
                        if (isPinned) Res.string.collectionsActionUnpin else Res.string.collectionsActionPin,
                    ),
                onClick = {
                    id?.let {
                        state.setDisplay(
                            it,
                            CollectionDisplayPreference.PINNED,
                            !collection.isPinned,
                        )
                    }
                },
                icon = ActionIcons.pin(isPinned),
            ),
        )
        add(
            AzkarMenuItem(
                label =
                    stringResource(
                        if (isFavorite) {
                            Res.string.collectionsActionUnfavorite
                        } else {
                            Res.string.collectionsActionFavorite
                        },
                    ),
                onClick = {
                    id?.let { state.setDisplay(it, CollectionDisplayPreference.FAVORITE, !collection.isFavorite) }
                },
                icon = ActionIcons.favorite(isFavorite),
            ),
        )
    }
}

/**
 * What cannot be undone from the menu, and is therefore last and alone.
 *
 * Drawn last so that it is the entry a thumb reaches last, which is the only affordance a menu has for
 * saying "this one is different from the rest".
 */
@Composable
private fun CollectionTile.destructiveItems(
    callbacks: ShelfCallbacks,
    collection: RemembranceCollection,
): List<AzkarMenuItem> =
    listOf(
        AzkarMenuItem(
            label = stringResource(Res.string.collectionsActionDelete),
            onClick = { callbacks.onDelete(collection) },
            icon = ActionIcons.delete,
        ),
    )

private val HUB_PADDING = 16.dp
private val HUB_GAP = 12.dp
private val CHIP_GAP = 8.dp

/** How much of the row's width one press of a chevron travels. Short of a page, so they work. */
private const val SCROLL_PAGE_FRACTION = 0.8f

/** Narrow enough that a 1440dp window lays out six cards across rather than five. */
private val SHELF_CARD_MIN_WIDTH = 190.dp

private const val PREVIEW_MAX_LENGTH = 90
