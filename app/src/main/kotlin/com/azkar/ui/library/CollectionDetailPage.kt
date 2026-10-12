package com.azkar.ui.library

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.azkar.domain.model.RemembranceCollection
import com.azkar.generated.resources.Res
import com.azkar.generated.resources.collectionsActionBack
import com.azkar.ui.library.CollectionDetailState
import com.azkar.ui.library.model.CollectionMemberView
import com.azkar.ui.library.model.RemembranceTile
import com.azkar.ui.shell.ShowToast
import org.jetbrains.compose.resources.stringResource

/**
 * The scrollable page: the band and the toolbar *inside* the same lazy container as the members
 * rather than beside it.
 *
 * That placement is the whole fix, because a lazy container is a scroll container of its own: when
 * the members alone were in one, everything above them went inert under the pointer and a wheel
 * dragged down the page did nothing until it crossed a card. A container that spans the page takes
 * the wheel everywhere, so the members can have the lazy layout and the settle animation for the drop
 * without the band paying for it.
 *
 * One container serves the list too, as a grid of a single column. A lazy column and a lazy grid
 * differ in the item scope their builder is handed and in nothing else this page uses, and sharing
 * one means the span handling above the members is written once rather than twice.
 */
@Composable
internal fun DetailLazyPage(
    collections: CollectionsCatalogue,
    detail: CollectionDetailState,
    dialogs: DetailDialogState,
    navigation: DetailNavigation,
    showToast: ShowToast,
    modifier: Modifier = Modifier,
) {
    val footer =
        DetailFooterActions(
            backLabel = stringResource(Res.string.collectionsActionBack),
            onBack = navigation.onBack,
        )
    val members = memberActions(collections, detail, dialogs, navigation.onOpenRemembrance, showToast)

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val grid = detail.memberView == CollectionMemberView.GRID
        // The padding is the container's content padding, so it is taken off the width here rather
        // than applied to the box, or the columns would be fitted against a width nothing lays out in.
        val usable = maxWidth - DETAIL_PAGE_PADDING * 2
        val columns =
            if (grid) {
                fittedColumns(available = usable, gap = MEMBER_GAP, minWidth = MEMBER_TILE_MIN_WIDTH)
            } else {
                1
            }
        val reorder = remember { MemberReorderState() }
        reorder.columns = columns
        val row = MemberRow(detail = detail, actions = members, tiles = detail.visibleMemberTiles(), reorder = reorder)
        val tileWidth =
            if (grid) {
                fittedCellWidth(available = usable, columns = columns, gap = MEMBER_GAP)
            } else {
                0.dp
            }

        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            contentPadding = PaddingValues(DETAIL_PAGE_PADDING),
            horizontalArrangement = Arrangement.spacedBy(MEMBER_GAP),
            verticalArrangement = Arrangement.spacedBy(MEMBER_GAP),
            modifier = Modifier.fillMaxSize(),
        ) {
            detail.collection?.let { collection ->
                regionItem(collection, detail, collections, dialogs, navigation)
            }
            membersOrState(collections, row, grid, tileWidth, footer)
            infoLine(collections)
        }
    }
}

/**
 * The band and the toolbar, spanning the page's full width above the members.
 *
 * The column is not decoration: a lazy item places whatever it is given at one origin, so the band's
 * three pieces would be painted on top of each other without it.
 */
private fun LazyGridScope.regionItem(
    collection: RemembranceCollection,
    detail: CollectionDetailState,
    collections: CollectionsCatalogue,
    dialogs: DetailDialogState,
    navigation: DetailNavigation,
) {
    item(key = REGION_KEY, span = { GridItemSpan(maxLineSpan) }) {
        Column {
            CollectionRegion(
                detail = detail,
                collections = collections,
                dialogs = dialogs,
                collection = collection,
                navigation = navigation,
            )
        }
    }
}

/**
 * The members, or the one panel that says why there are none.
 *
 * Reading the stage here rather than branching around the container is what keeps the empty, missing
 * and loading pages on the same scroll container as the loaded one, so arriving at a collection never
 * swaps the page underneath it.
 */
private fun LazyGridScope.membersOrState(
    collections: CollectionsCatalogue,
    row: MemberRow,
    grid: Boolean,
    tileWidth: Dp,
    footer: DetailFooterActions,
) {
    when (val stage = detailStage(collections, row.detail)) {
        DetailStage.MEMBERS ->
            itemsIndexed(row.tiles, key = { _, tile -> tile.id }) { index, tile ->
                tileWithReorder(
                    tile = tile,
                    index = index,
                    row = row,
                    modifier = memberTileModifier(grid, tileWidth),
                    asRow = !grid,
                )
            }

        else ->
            item(key = stage.name, span = { GridItemSpan(maxLineSpan) }) {
                DetailStatePanel(stage = stage, detail = row.detail, footer = footer)
            }
    }
}

/**
 * The shape one member takes: a fixed cell in the grid, the full width in the list.
 *
 * [animateItem] is the sanctioned settle: when the drop lands and the page reorders, the tiles that
 * gave way glide to their new homes instead of jumping. It needs a key to find the moved tile by,
 * which is why every member above is keyed by its id.
 */
private fun LazyGridItemScope.memberTileModifier(
    grid: Boolean,
    tileWidth: Dp,
): Modifier =
    (
        if (grid) {
            Modifier
                .width(
                    tileWidth,
                ).height(MEMBER_TILE_HEIGHT)
        } else {
            Modifier.fillMaxWidth()
        }
    ).animateItem()

/** The "Loading…" / "Couldn't load…" footer, last in the scroll. */
private fun LazyGridScope.infoLine(collections: CollectionsCatalogue) {
    item(key = INFO_KEY, span = { GridItemSpan(maxLineSpan) }) {
        CollectionsInfoLine(text = collections.infoLine, modifier = Modifier.fillMaxWidth())
    }
}

/**
 * What every tile on this page shares, handed down together.
 *
 * Not tidiness for its own sake. A tile needs the list to count itself against, the page to ask
 * whether rearranging is allowed, the callbacks to build its own, and the reorder state to measure
 * into — and a signature that carried those four beside the tile and its own index made the one
 * argument that actually differs per tile the hardest to find.
 */
private class MemberRow(
    val detail: CollectionDetailState,
    val actions: MemberActions,
    val tiles: List<RemembranceTile>,
    val reorder: MemberReorderState,
)

/**
 * One member, wired for reading and — where the page allows it — for rearranging.
 *
 * Split out because the grid and the list want different tiles with different shapes, and every other
 * difference between them was in the container: this part is the same for both and would otherwise be
 * written twice.
 *
 * The three modifiers do three different jobs and the order is the point, so each is one named
 * step rather than an anonymous chain: [onGloballyPositioned] is outside so the reorder state knows
 * where the tile is even while it is being moved; [memberDragLayer] is next so the dragged card
 * rides above its neighbours; the gesture goes last, inside the card's own clickable, or it would
 * never see the press.
 */
@Composable
private fun tileWithReorder(
    tile: RemembranceTile,
    index: Int,
    row: MemberRow,
    modifier: Modifier = Modifier,
    asRow: Boolean = false,
) {
    val detail = row.detail
    val tiles = row.tiles
    val reorder = row.reorder
    val tileActions = remember(tile, row.actions) { memberTileActions(tile, row.actions) }
    val dragGesture = Modifier.memberDragHandle(reorder, detail, index, tiles.size)
    val placed =
        modifier
            .onGloballyPositioned { coordinates ->
                reorder.measure(index, coordinates.boundsInRoot(), tiles.size)
            }.memberDragLayer(reorder, index)

    @Suppress("UNUSED_EXPRESSION")
    if (asRow) {
        RemembranceTileRow(
            tile = tile,
            actions = tileActions,
            modifier = placed,
            selected = tile.id in detail.selectedToRemove,
            memberCount = detail.memberCount,
            dragGesture = dragGesture,
        )
    } else {
        RemembranceTileCard(
            tile = tile,
            actions = tileActions,
            modifier = placed,
            selected = tile.id in detail.selectedToRemove,
            memberCount = detail.memberCount,
            dragGesture = dragGesture,
        )
    }
}

/**
 * The drag gesture for the handle on one tile, or nothing at all where the page will not reorder.
 *
 * The handle's own position is measured rather than the tile's, because the handle is the node the
 * press lands on and the difference between the two is the grab offset — which is what lets a tile
 * be drawn under the pointer instead of under its own corner.
 *
 * [detectDragGestures], not [detectDragGesturesAfterLongPress]. The long-press variant is a touch
 * idiom: on a pointer device it means holding still for half a second before anything moves, which
 * reads as the app having missed the click. The web equivalent — dnd-kit, SortableJS — starts the
 * drag once the pointer has travelled past a small threshold, which is what this already does via
 * the touch slop. The handle makes the gesture unambiguous: only a press on the handle starts one.
 */
@Composable
private fun Modifier.memberDragHandle(
    reorder: MemberReorderState,
    detail: CollectionDetailState,
    index: Int,
    tileCount: Int,
): Modifier {
    if (!detail.canReorderMembers) return this
    var handleAt by remember(index, tileCount) { mutableStateOf(Offset.Zero) }
    return this
        .onGloballyPositioned { handleAt = it.positionInRoot() }
        .pointerInput(index, tileCount) {
            detectDragGestures(
                onDragStart = { local -> reorder.begin(index, MEMBER_GAP.toPx(), handleAt + local) },
                onDragEnd = { reorder.release()?.let { (from, to) -> detail.moveMember(from, to) } },
                onDragCancel = { reorder.cancel() },
                onDrag = { change, amount ->
                    change.consume()
                    reorder.drag(amount)
                },
            )
        }
}

/**
 * Draws the tile under the pointer on top of its neighbours, and steps the tiles it displaces one
 * slot along.
 *
 * Reading the reorder state inside [graphicsLayer] rather than composing with it is the same bargain
 * [Controls.rememberHoverAlpha] makes on every button: the offset changes on every pointer move, and
 * a layer block is evaluated in the draw phase, so a drag costs draw invalidations instead of a
 * recomposition per frame.
 */
private fun Modifier.memberDragLayer(
    reorder: MemberReorderState,
    index: Int,
): Modifier =
    graphicsLayer {
        val offset = reorder.offsetFor(index)
        translationX = offset.x
        translationY = offset.y
        if (reorder.liftedFor(index)) {
            scaleX = MEMBER_DRAG_SCALE
            scaleY = MEMBER_DRAG_SCALE
            shadowElevation = MEMBER_DRAG_SHADOW.toPx()
        }
    }.zIndex(if (reorder.liftedFor(index)) DRAGGED_Z else 0f)

private const val DRAGGED_Z = 1f
private const val MEMBER_DRAG_SCALE = 1.03f
private val MEMBER_DRAG_SHADOW = 16.dp
