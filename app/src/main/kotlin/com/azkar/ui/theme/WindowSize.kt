package com.azkar.ui.theme

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

/**
 * Width buckets resolved once per layout pass by the shell, so screens branch on the window
 * instead of hard coding fixed pane sizes.
 *
 * The ranges follow the Material window size class idea, narrowed for a desktop app whose
 * header and horizontal screen padding are always present.
 */
enum class WindowWidthClass {
    /** Side-by-side panes no longer fit; stack them into one scrolling column. */
    COMPACT,

    /** Side-by-side works, but the fixed-width chrome has to yield space to content. */
    MEDIUM,

    /** Everything fits with room to spare. */
    EXPANDED,
}

/** Height buckets, used where a stacked layout needs to decide how much room to leave each pane. */
enum class WindowHeightClass {
    COMPACT,
    MEDIUM,
    EXPANDED,
}

/** The measured window, bucketed for layout decisions. */
data class WindowSizeClass(
    val width: Dp,
    val height: Dp,
    val widthClass: WindowWidthClass,
    val heightClass: WindowHeightClass,
) {
    val isCompactWidth: Boolean
        get() = widthClass == WindowWidthClass.COMPACT

    val isCompactHeight: Boolean
        get() = heightClass == WindowHeightClass.COMPACT
}

/**
 * Breakpoints in dp. `compactWidth` sits below the width the app opens at, so a window on any
 * ordinary screen keeps its side-by-side layout and only genuinely narrow windows stack.
 */
object AzkarBreakpoints {
    val compactWidth: Dp = 900.dp
    val expandedWidth: Dp = 1280.dp
    val compactHeight: Dp = 620.dp
    val expandedHeight: Dp = 900.dp
}

/** Pure width bucketing, kept free of Compose so it can be unit tested directly. */
fun widthClassFor(width: Dp): WindowWidthClass =
    when {
        width < AzkarBreakpoints.compactWidth -> WindowWidthClass.COMPACT
        width < AzkarBreakpoints.expandedWidth -> WindowWidthClass.MEDIUM
        else -> WindowWidthClass.EXPANDED
    }

/** Pure height bucketing, kept free of Compose so it can be unit tested directly. */
fun heightClassFor(height: Dp): WindowHeightClass =
    when {
        height < AzkarBreakpoints.compactHeight -> WindowHeightClass.COMPACT
        height < AzkarBreakpoints.expandedHeight -> WindowHeightClass.MEDIUM
        else -> WindowHeightClass.EXPANDED
    }

fun windowSizeClassFor(
    width: Dp,
    height: Dp,
): WindowSizeClass =
    WindowSizeClass(
        width = width,
        height = height,
        widthClass = widthClassFor(width),
        heightClass = heightClassFor(height),
    )

/**
 * The share of the screen the window opens at.
 *
 * A window that fills the screen exactly cannot be moved without hitting the display edge, and one
 * that only takes a fraction of a large screen looks like a mistake. Ninety per cent leaves a
 * strip of desktop on every side — enough to grab the edge and drag, which is the main way anyone
 * moves a desktop window. It also absorbs the title bar without a separate allowance.
 */
private const val SCREEN_SHARE = 0.9f

/**
 * The size the app opens at when there is no screen to measure, and the size every layout outside
 * a real window — previews, the screenshot harness — is given in place of one.
 *
 * Not a number any real screen has to match: [windowSizeFor] reads the screen, and this is only
 * what happens when there is nothing to read.
 */
val DEFAULT_WINDOW_WIDTH: Dp = 1440.dp
val DEFAULT_WINDOW_HEIGHT: Dp = 900.dp

/**
 * Beyond these the window stops being an app and starts being the desktop.
 *
 * A 4K panel at ninety per cent would open a window too wide to scan — the shelf's card columns and
 * the rail would end up on opposite sides of the screen — so the growth is capped and the extra
 * room is left as desktop, where a user can put something else.
 */
private val MAX_WINDOW_WIDTH: Dp = 1600.dp
private val MAX_WINDOW_HEIGHT: Dp = 1000.dp

/**
 * The size to open the window at, given the screen it is opening on.
 *
 * Ninety per cent of the screen per axis, capped at [MAX_WINDOW_WIDTH] and [MAX_WINDOW_HEIGHT].
 * Each axis is decided on its own, because sizing the window by one ratio couples a wide short
 * screen to a tall narrow one.
 *
 * There is deliberately **no lower bound**. A floor would have to be either smaller than the share,
 * in which case it never applies, or larger, in which case on a screen too small to grant it the
 * window either hangs off the edge or fills the screen outright — and both are worse than a small
 * window. A screen that can only give a narrow window gets one, and
 * [AzkarWindowSize.isCompactWidth] has the compact shelf standing by for it.
 *
 * Pure, so the sizing rules are testable without a display attached — which matters, because
 * [com.azkar.Main] can only reach the screen size when one is there.
 */
fun windowSizeFor(
    screenWidth: Dp,
    screenHeight: Dp,
): DpSize =
    DpSize(
        width = openingEdge(screenWidth, MAX_WINDOW_WIDTH),
        height = openingEdge(screenHeight, MAX_WINDOW_HEIGHT),
    )

/**
 * One axis of [windowSizeFor]: the screen's share, held under a ceiling and never over the screen.
 *
 * Rounded to whole dp. The share of an odd screen lands on values like 691.19995dp, which no window
 * manager can act on and no test can state — the desktop works in pixels regardless, so rounding
 * here costs nothing and keeps the answer a number a person could have chosen.
 *
 * The final [coerceAtMost] is belt and braces rather than caution — with no floor in play the
 * arithmetic cannot exceed the screen — but a window that hangs off the display is the one failure
 * here worth being certain about, and the screen size is not a number this function controls.
 */
private fun openingEdge(
    screen: Dp,
    ceiling: Dp,
): Dp = minOf(ceiling, screen * SCREEN_SHARE).let { Dp(kotlin.math.round(it.value)) }.coerceAtMost(screen)

/**
 * Defaults to the size the app opens at so a screen rendered outside [AzkarApp] (previews, tests)
 * still gets the layout it was designed for.
 */
val LocalWindowSizeClass =
    staticCompositionLocalOf {
        windowSizeClassFor(DEFAULT_WINDOW_WIDTH, DEFAULT_WINDOW_HEIGHT)
    }

/** The single question every responsive layout in the app asks. */
object AzkarWindowSize {
    val isCompactWidth: Boolean
        @Composable @ReadOnlyComposable
        get() = LocalWindowSizeClass.current.isCompactWidth
}
