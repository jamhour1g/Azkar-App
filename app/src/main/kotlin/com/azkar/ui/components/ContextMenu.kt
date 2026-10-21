package com.azkar.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.DropdownMenuPopupPositionProvider
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Where a context menu was summoned, and whether it is showing.
 *
 * A state rather than a plain flag, because the menu needs to remember *where* the pointer was as
 * well as that it was pressed: a menu that opens under the card is a menu the user has to chase
 * across the screen, and on a shelf of twenty cards the one that opens is not always the one under
 * the pointer.
 */
@Stable
internal class ContextMenuState {
    /** The pointer's place in the window, taken at the moment the menu was summoned. */
    var at by mutableStateOf(Offset.Zero)
        private set

    /** Whether the menu is showing. */
    var open by mutableStateOf(false)
        private set

    /**
     * This node's own top-left in the window.
     *
     * A plain field rather than a state on purpose: it is read inside the pointer handler on every
     * press, and a state read inside a `pointerInput` block is observed by it — and an observed
     * state that changes restarts the block, which would reset the "was the button already down"
     * memory the handler keeps.
     */
    var origin: Offset = Offset.Zero

    internal fun show(at: Offset) {
        this.at = at
        open = true
    }

    internal fun hide() {
        open = false
    }
}

/** A context menu's state, remembered for as long as the surface that owns it. */
@Composable
internal fun rememberContextMenuState(): ContextMenuState = remember { ContextMenuState() }

/**
 * Makes this node answer a secondary press by opening a context menu where the pointer is.
 *
 * The press is detected on the *transition* to the secondary button being down rather than on every
 * event while it is held, because a menu that re-opens itself on every move of a held button is a
 * menu the user cannot close by moving the mouse away.
 *
 * On the desktop a secondary press does not reach a `clickable` at all — Compose's tap detector
 * only treats the primary button as a press — so right-clicking a card does not also open the card.
 * That is why this needs no flag to suppress the click: there is no click to suppress.
 */
internal fun Modifier.contextMenuTarget(state: ContextMenuState): Modifier =
    this
        .onGloballyPositioned { state.origin = it.positionInWindow() }
        .pointerInput(state) {
            awaitPointerEventScope {
                var wasSecondary = false
                while (true) {
                    val event = awaitPointerEvent()
                    val secondary = event.buttons.isSecondaryPressed
                    if (secondary && !wasSecondary) {
                        val change = event.changes.firstOrNull { it.pressed }
                        if (change != null) {
                            state.show(state.origin + change.position)
                        }
                    }
                    wasSecondary = secondary
                }
            }
        }

/**
 * Draws the context menu, if one is open.
 *
 * Separate from the surface that owns the state because a popup has to be rendered inside the
 * composition rather than inside the box it belongs to, and because the surface that watches for
 * the press is often several calls away from the one that draws the card.
 */
@Composable
internal fun ContextMenuHost(
    items: List<AzkarMenuItem>,
    state: ContextMenuState,
) {
    if (state.open && items.isNotEmpty()) {
        AzkarContextMenu(
            items = items,
            at = state.at,
            onDismissRequest = { state.hide() },
        )
    }
}

/**
 * The menu itself, anchored to the pointer rather than to the thing that was pressed.
 *
 * Material's own [DropdownMenuPopup] does the work, but its content is a bare column with no
 * chrome: the [Surface] below is here so the menu looks like the "⋯" menu it shares its items
 * with, rather than like a list of words floating over the app.
 */
@Composable
internal fun AzkarContextMenu(
    items: List<AzkarMenuItem>,
    at: Offset,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DropdownMenuPopup(
        expanded = true,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        popupPositionProvider = CursorMenuPosition { at },
        properties = MenuDefaults.DefaultMenuProperties,
    ) {
        Surface(
            shape = MenuDefaults.shape,
            color = MenuDefaults.containerColor,
            tonalElevation = MenuDefaults.TonalElevation,
            shadowElevation = MenuDefaults.ShadowElevation,
        ) {
            Column(
                modifier =
                    Modifier
                        .padding(vertical = MENU_VERTICAL_PADDING)
                        .width(IntrinsicSize.Max),
            ) {
                items.forEach { item ->
                    AzkarMenuItemRow(item = item, onDismiss = onDismissRequest)
                }
            }
        }
    }
}

/**
 * Where the menu goes: under the pointer, and never off the window.
 *
 * The anchor bounds are ignored deliberately. They describe the card that was pressed, and a menu
 * that opens under the card is a menu the pointer is no longer on — so the position comes from the
 * pointer instead, and the bounds are only ever used to know how much room is left.
 */
internal class CursorMenuPosition(
    private val cursor: () -> Offset,
) : DropdownMenuPopupPositionProvider {
    override val transformOrigin: TransformOrigin = TransformOrigin(0f, 0f)

    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val at = cursor()
        val maxX = (windowSize.width - popupContentSize.width).coerceAtLeast(0)
        val maxY = (windowSize.height - popupContentSize.height).coerceAtLeast(0)
        return IntOffset(
            x = at.x.roundToInt().coerceIn(0, maxX),
            y = at.y.roundToInt().coerceIn(0, maxY),
        )
    }
}

/**
 * The gap above and below the items, matching the menu Material draws for itself.
 *
 * Material's own constant is internal, so it is repeated here rather than imported; the two are
 * the same number, and a menu whose items sit tighter than the "⋯" menu's would look like a
 * different menu.
 */
private val MENU_VERTICAL_PADDING = 8.dp
