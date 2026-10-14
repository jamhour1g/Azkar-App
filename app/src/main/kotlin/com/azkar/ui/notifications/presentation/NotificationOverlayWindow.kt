package com.azkar.ui.notifications.presentation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import com.azkar.ui.theme.AzkarSpacing
import com.azkar.ui.theme.AzkarTheme
import com.azkar.ui.theme.fadeThroughEnter
import com.azkar.ui.theme.fadeThroughExit
import java.util.Locale

/**
 * The reminder overlay: one transparent, undecorated, always-on-top window that lives for as long
 * as the application does and is shown or hidden rather than created or destroyed.
 *
 * ## Why one window, toggled
 *
 * A window per notification would be the obvious thing to write and it is wrong for three reasons.
 * Every extra `JFrame` is a native window the compositor has to track, so a reminder every twenty
 * minutes would be paying for a window allocation it immediately throws away. Creating and disposing
 * one per reminder also means the OS's own "app has opened a window" animations play each time,
 * which is exactly the kind of attention-stealing a reminder should not do. And a window that
 * outlives the notice it was made for has to be torn down by a timer, which is a second scheduler.
 *
 * Toggling `visible` instead makes the AWT window itself the cache: Compose Desktop keeps the window
 * and its composition alive while `visible` is `false` and only releases them when the `Window`
 * leaves the composition, so the second reminder arrives into a window that has already been sized,
 * positioned and made transparent.
 *
 * ## The flags, and what each one is for
 *
 * - `transparent = true` — so the corners outside the cards are actually see-through rather than a
 *   black rectangle. AWT only permits this on an undecorated window that is not yet showing, which
 *   is why both `undecorated` and the initial `visible = false` below are not optional.
 * - `undecorated = true` — no title bar, no border, no shadow to fight the transparency.
 * - `alwaysOnTop = true` — the reason this window exists at all. Mapped to `_NET_WM_STATE_ABOVE` on
 *   X11, to a topmost Win32 window on Windows and to a floating window level on macOS.
 * - `focusable = false` — so it never takes the caret away from whatever the user is typing in. This
 *   is the one flag with a known cost: some window managers refuse keyboard focus to a window that
 *   asks not to have it, which is the intended behaviour here, and pointer input is unaffected.
 * - `resizable = false` — its size is derived from the stack cap; a user-resizable frame around a
 *   fixed-size card would only ever be wrong.
 *
 * ## Positioning
 *
 * The window is placed with `WindowPosition.Aligned`, which Compose resolves against the screen the
 * window is already on, minus that screen's own insets. That screen is the one the app's main
 * window was last on, because Compose seeds a new window's graphics configuration from the last
 * active one — so a user who has dragged Azkar to a second monitor is reminded on that monitor
 * without this code knowing anything about their display setup.
 *
 * ## Visibility
 *
 * The window is shown whenever there is anything to show and hidden when there is not, and it is
 * composed whether or not the main window is visible at all — which is the tray case, and the reason
 * a reminder can arrive while the app has no visible surface at all.
 */
@Composable
fun NotificationOverlayWindow(
    presenter: NotificationPresenter,
    settings: NotificationPresentationSettings,
    darkTheme: Boolean,
    locale: Locale,
    onOpen: (com.azkar.ui.notifications.NotificationAction) -> Unit,
) {
    val notices by presenter.notices.collectAsState()
    val windowState =
        rememberWindowState(
            size = overlaySize(settings.maxVisible),
            position = WindowPosition(settings.placement.alignment()),
        )
    // The state object is created once, but its size and position are not part of that creation, so
    // a change made on the settings screen has to be pushed in. Both are re-applied on the next
    // update, which for the position means the window has to be hidden at that moment — Compose
    // deliberately refuses to move a window that is showing and is not floating, and every change to
    // these settings arrives while the overlay is empty.
    LaunchedEffect(settings.maxVisible) { windowState.size = overlaySize(settings.maxVisible) }
    LaunchedEffect(settings.placement) {
        windowState.position = WindowPosition(settings.placement.alignment())
    }
    Window(
        onCloseRequest = {},
        visible = notices.isNotEmpty(),
        state = windowState,
        undecorated = true,
        transparent = true,
        resizable = false,
        enabled = true,
        focusable = false,
        alwaysOnTop = true,
    ) {
        AzkarTheme(darkTheme = darkTheme, uiLocale = locale) {
            // The same `key` the main tree uses, for the same reason: Compose Multiplatform's
            // resources resolve against a snapshot-backed locale that `Locale.setDefault` does not
            // notify, so an Arabic overlay would otherwise keep drawing its English labels after the
            // app switched.
            key(locale) {
                AnimatedContent(
                    targetState = notices,
                    transitionSpec = { fadeThroughEnter().togetherWith(fadeThroughExit()) },
                    label = "azkar-notification-stack",
                ) { stack ->
                    NotificationStack(
                        stack = stack,
                        placement = settings.placement,
                        onOpen = onOpen,
                        onDismiss = presenter::dismiss,
                        onPauseChanged = presenter::setPaused,
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationStack(
    stack: List<PresentedNotice>,
    placement: OverlayPlacement,
    onOpen: (com.azkar.ui.notifications.NotificationAction) -> Unit,
    onDismiss: (Long) -> Unit,
    onPauseChanged: (Long, Boolean) -> Unit,
) {
    val isBottom = placement == OverlayPlacement.BOTTOM_END || placement == OverlayPlacement.BOTTOM_START
    val isEnd = placement == OverlayPlacement.TOP_END || placement == OverlayPlacement.BOTTOM_END
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .width(OVERLAY_WIDTH)
                .padding(EDGE_INSET),
        verticalArrangement = if (isBottom) Arrangement.Bottom else Arrangement.Top,
        horizontalAlignment = if (isEnd) Alignment.End else Alignment.Start,
    ) {
        stack.forEach { item ->
            key(item.id) {
                NotificationCard(
                    item = item,
                    onOpen = { onOpen(item.action) },
                    onDismiss = { onDismiss(item.id) },
                    onPauseChanged = { paused -> onPauseChanged(item.id, paused) },
                )
            }
        }
    }
}

private fun OverlayPlacement.alignment(): Alignment =
    when (this) {
        OverlayPlacement.TOP_START -> Alignment.TopStart
        OverlayPlacement.TOP_END -> Alignment.TopEnd
        OverlayPlacement.BOTTOM_START -> Alignment.BottomStart
        OverlayPlacement.BOTTOM_END -> Alignment.BottomEnd
    }

/**
 * The window is sized for the whole stack rather than measured around it.
 *
 * A window's size cannot follow its content in Compose Desktop — it is what the content is laid out
 * *in* — so the height has to be derived from the cap instead. Every card is given a generous
 * fixed slot so that a long Arabic remembrance wraps to four lines without the stack being clipped,
 * and short cards simply leave the rest of their slot empty.
 */
private fun overlaySize(maxVisible: Int): DpSize =
    DpSize(
        width = OVERLAY_WIDTH + EDGE_INSET * 2,
        height = CARD_SLOT * maxVisible + EDGE_INSET * 2,
    )

private val OVERLAY_WIDTH = 380.dp

/** One card's slot. Wider than the tallest four-line card at any locale the app ships. */
private val CARD_SLOT = 132.dp

/** How far the stack keeps from the screen edge, so it never sits under a taskbar or a menu bar. */
private val EDGE_INSET = AzkarSpacing.lg
