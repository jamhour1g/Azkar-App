package com.azkar.ui.library

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

/**
 * Where a member tile is being dragged to, and how far the tiles it passes have moved out of its way.
 *
 * One value for the grid and the list, because the thing being ordered is the same in both — the
 * collection's own sequence — and the only thing that differs is the shape the tiles are laid out in.
 * That difference arrives as [columns]: one column is the list, and every grid row is the same
 * handful of equal cells.
 *
 * ## What is frozen, and why
 *
 * [measure] records where each tile is, but it **does nothing while a drag is in flight**. The tiles
 * being pushed aside are moved by [offsetFor], so a state that kept re-reading their positions would
 * chase itself: every displaced tile would report the position it was just shifted to, the target
 * index would walk backwards, and the drag would oscillate in the user's hand. The geometry is
 * therefore snapshotted when the drag starts and the running tiles stop being a source of truth for
 * the length of it.
 *
 * ## Why the displaced tiles move by one *slot*, not by the distance dragged
 *
 * The list is packed, so taking the dragged tile out and putting it back one slot along displaces
 * exactly the tiles in between by exactly one row. Measuring that displacement from the drag itself
 * would be wrong the moment the pointer outruns the tiles: the tiles would chase the finger with a
 * lag that grows the further it goes. The one exception is a grid, where "one slot" is sometimes a
 * column to the left and sometimes a row up, and which slot depends on where the dragged tile sat
 * in its row — see [offsetFor].
 *
 * ## Read at draw time
 *
 * Every value here is read from inside a `graphicsLayer` block rather than from composition, so a
 * drag costs one invalidation per frame rather than a recomposition of the whole member area sixty
 * times a second. That is the same bargain [com.azkar.ui.components.rememberHoverAlpha] makes.
 */
@Stable
internal class MemberReorderState {
    /** The index of the tile being dragged, or [NONE]. Read at draw time, so it is a plain int. */
    var draggingIndex by mutableIntStateOf(NONE)
        private set

    /** How far the pointer has travelled since the drag began, in pixels. This is the drag itself. */
    private var travel by mutableStateOf(Offset.Zero)
        private set

    /**
     * Where inside the dragged tile the pointer took hold, as an offset from its top-left.
     *
     * The drag is started from a handle rather than from the card, so the pointer never begins on the
     * tile's top-left corner. Without this the tile would jump by the difference the instant the drag
     * began, which is the single most common way a reorder looks broken before it has moved anything.
     *
     * Held apart from [travel] because the two answer different questions. [travel] is how far the
     * user has moved, and only it decides where the tile lands — see [target]. [grab] is only where
     * the tile is drawn. Mixing them is what made a press on the handle read as a move before the
     * pointer had gone anywhere: the handle sits at the bottom-right of a card, so the grab offset
     * alone was already most of the way to the next slot.
     */
    private var grab by mutableStateOf(Offset.Zero)
        private set

    /** How far the dragged tile is drawn from the slot it was measured in. */
    val draggedBy: Offset get() = travel + grab

    /** Each tile's bounds in root coordinates, as last measured. Root, because a grid's tiles do not share a parent. */
    private var slots = Array<Rect?>(0) { null }

    /** The snapshot [travel] is measured against. Empty until the first drag. */
    private var frozen = Array<Rect?>(0) { null }

    /** The dragged tile's own height plus the gap below it: one list row. */
    private var rowPitch = 0f

    /** The gap between two grid cells. Only a grid has one; the list's is [rowPitch]. */
    private var columnPitch = 0f

    /** Tiles per row. One, for the list. */
    var columns: Int = 1

    val isDragging: Boolean get() = draggingIndex != NONE

    /**
     * Records where a tile ended up.
     *
     * Ignored mid-drag for the reason the class comment gives. A [count] that disagrees with what has
     * already been measured means the list itself changed — a member was added, a filter came back —
     * and the stale rectangles are worse than none.
     */
    fun measure(
        index: Int,
        bounds: Rect,
        count: Int,
    ) {
        if (isDragging) return
        if (slots.size != count) slots = Array(count) { null }
        if (index !in slots.indices) return
        slots[index] = bounds
    }

    /**
     * Starts a drag of the tile at [index], given the [gap] the tiles are laid out with.
     *
     * [grabbedAt] is where the pointer went down in root coordinates, and only matters because the
     * drag begins on a handle. Pass `null` when the press was on the tile's own top-left corner.
     */
    fun begin(
        index: Int,
        gap: Float,
        grabbedAt: Offset? = null,
    ) {
        val slot = slots.getOrNull(index) ?: return
        grab = grabbedAt?.let { it - Offset(slot.left, slot.top) } ?: Offset.Zero
        draggingIndex = index
        travel = Offset.Zero
        frozen = slots.copyOf()
        rowPitch = slot.height + gap
        columnPitch =
            columns
                .takeIf { it > 1 }
                ?.let { count ->
                    val next = slots.getOrNull(index + 1) ?: slot
                    (next.left - slot.left).takeIf { it > 0f } ?: (slot.width + gap)
                }
                ?: slot.width + gap
    }

    /** Carries the dragged tile along by [delta]. */
    fun drag(delta: Offset) {
        if (!isDragging) return
        travel += delta
    }

    /**
     * Ends the drag, reporting the move the user made.
     *
     * `null` when the tile was put back where it started, or when the drag never really happened —
     * both of which mean the same thing to the collection, which is nothing.
     */
    fun release(): Pair<Int, Int>? {
        val from = draggingIndex
        val to = target()
        reset()
        if (from == NONE || to == NONE || from == to) return null
        return from to to
    }

    /** Abandons the drag, putting every tile back where it was measured. */
    fun cancel() = reset()

    private fun reset() {
        draggingIndex = NONE
        travel = Offset.Zero
        grab = Offset.Zero
        frozen = Array(0) { null }
    }

    /**
     * The index whose slot is nearest [left], [top] in [source].
     *
     * Nearest rather than first crossed, for the reason [target] gives: a tile that starts between
     * two rows would otherwise jump before the pointer had gone anywhere. Slots that were never
     * measured read as infinitely far away rather than as the origin, so an unmeasured index can
     * never win by accident.
     */
    private fun nearest(
        source: Array<Rect?>,
        left: Float,
        top: Float,
    ): Int =
        source.indices.minByOrNull { index ->
            val slot = source[index] ?: return@minByOrNull Float.MAX_VALUE
            val dx = slot.left - left
            val dy = slot.top - top
            dx * dx + dy * dy
        } ?: NONE

    /**
     * The tile a press at [point] would pick up, or [NONE] if nothing has been measured.
     *
     * Not used by the drag itself — the handle already knows which tile it is on — but it is what
     * tells the page whether there is anything here to drag at all, and what a test asserts against.
     */
    fun indexAt(point: Offset): Int = nearest(slots, point.x, point.y)

    /**
     * Where the dragged tile would land if released now.
     *
     * The frozen slot the tile started in, moved by [travel] — and by nothing else. The nearest slot,
     * not the next one crossed: a tile that starts between two rows would otherwise jump before the
     * pointer had gone anywhere, which reads as the collection reacting to a touch the user has
     * barely made.
     *
     * Measured against the tile's own origin rather than the pointer's for the reason [grab] gives:
     * the two differ by wherever inside the tile the handle is, and only one of them is zero when the
     * user starts moving.
     */
    private fun target(): Int {
        val origin = frozen.getOrNull(draggingIndex) ?: return NONE
        return nearest(frozen, origin.left + travel.x, origin.top + travel.y)
    }

    /**
     * How far the tile at [index] has been pushed out of the way.
     *
     * Zero for every tile the dragged one is not passing. For the rest it is one slot, and which slot
     * depends on where in its own row the displaced tile sat: a tile that has a neighbour to its left
     * steps left into the gap, and one already at the leading edge wraps to the row above instead.
     */
    fun offsetFor(index: Int): Offset {
        val from = draggingIndex
        val to = target()
        if (from == NONE || to == NONE) return Offset.Zero
        return when (index) {
            from -> draggedBy
            else -> pushed(index, from, to)
        }
    }

    /**
     * How far a tile the drag is passing has stepped aside, which is one slot and not the length of
     * the drag. Split out so that [offsetFor] reads as the one question it answers — is this the tile
     * under the pointer, or a tile it has pushed, or a tile it has not reached.
     */
    private fun pushed(
        index: Int,
        from: Int,
        to: Int,
    ): Offset {
        val towardsEnd = index in (from + 1)..to
        if (!towardsEnd && index !in to..(from - 1)) return Offset.Zero
        return if (columns <= 1) {
            Offset(0f, if (towardsEnd) -rowPitch else rowPitch)
        } else {
            val column = index % columns
            when {
                towardsEnd && column > 0 -> Offset(-columnPitch, 0f)
                towardsEnd -> Offset(0f, -rowPitch)
                column < columns - 1 -> Offset(columnPitch, 0f)
                else -> Offset(0f, rowPitch)
            }
        }
    }

    /** How far the tile at [index] should be raised, if at all. The dragged tile alone is lifted. */
    fun liftedFor(index: Int): Boolean = draggingIndex == index

    private companion object {
        /** The index no tile has. */
        const val NONE = -1
    }
}
