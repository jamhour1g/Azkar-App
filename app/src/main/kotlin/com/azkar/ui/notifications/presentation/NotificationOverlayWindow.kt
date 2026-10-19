package com.azkar.ui.notifications.presentation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.rememberWindowState
import com.azkar.ui.theme.AzkarSpacing
import com.azkar.ui.theme.AzkarTheme
import com.azkar.ui.theme.fadeThroughEnter
import com.azkar.ui.theme.fadeThroughExit
import java.awt.GraphicsConfiguration
import java.awt.Toolkit

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
 * - `resizable = false` — its size is derived from the stack — from the card height under a fixed
 *   slot, from measuring the stack under a fitted one — so a user-resizable frame around it would
 *   only ever be wrong.
 *
 * ## Positioning
 *
 * A fixed stack keeps the window's size and so is placed with `WindowPosition.Aligned`, which Compose
 * resolves against the screen the window is already on, minus that screen's own insets. That screen is
 * the one the app's main window was last on, because Compose seeds a new window's graphics
 * configuration from the last active one — so a user who has dragged Azkar to a second monitor is
 * reminded on that monitor without this code knowing anything about their display setup.
 *
 * A fitted stack does not keep the window's size, and that is the one case an aligned position cannot
 * cover: Compose resolves the alignment against the height the window had when the position was set
 * and does not revisit it when the window shrinks to its passage, so a bottom-anchored card would
 * climb the screen. Fitting therefore positions the window itself, from the size it just measured, with
 * [cornerPosition].
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
    theme: OverlayTheme,
    onOpen: (com.azkar.ui.notifications.NotificationAction) -> Unit,
) {
    val notices by presenter.notices.collectAsState()
    val windowState =
        rememberWindowState(
            size = overlaySize(settings),
            position = WindowPosition(settings.placement.alignment()),
        )
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
        ReminderOverlay(
            windowState = windowState,
            settings = settings,
            theme = theme,
            presenter = presenter,
            onOpen = onOpen,
        )
    }
}

/**
 * Everything inside the overlay window: the effects that push the settings into the window's own state,
 * and the stack itself.
 *
 * Split out of [NotificationOverlayWindow] because only the `Window` call has to sit beside
 * `rememberWindowState`, and keeping the rest there made that function about two things — the frame
 * and the reminder drawn in it. It is a [FrameWindowScope] extension because the fitted branch places
 * the window from the measurements it takes, and the screen to place it on comes from the AWT window.
 */
@Composable
private fun FrameWindowScope.ReminderOverlay(
    windowState: WindowState,
    settings: NotificationPresentationSettings,
    theme: OverlayTheme,
    presenter: NotificationPresenter,
    onOpen: (com.azkar.ui.notifications.NotificationAction) -> Unit,
) {
    val notices by presenter.notices.collectAsState()
    val density = LocalDensity.current
    // The state object is created once, but its size and position are not part of that creation, so
    // a change made on the settings screen has to be pushed in. Both are re-applied on the next
    // update; moving a floating window while it is showing is allowed, and in any case every change
    // to these settings arrives while the overlay is empty.
    //
    // The size keys are the three that decide it, so dragging either slider on the settings screen
    // resizes the window before the next card ever arrives. The preview on that screen is sized the
    // same way, so what it showed while the slider moved is what the corner is now doing.
    //
    // The sizing mode is a key as well, and in the direction that matters most: an overlay fitted to
    // its content has a short window, so switching back to a fixed slot has to restore the tall one
    // even though none of the three numbers moved. Under `FIT` the height written here is a
    // placeholder the measurement below immediately corrects — it is the fallback the fixed slot would
    // have given the same card, and starting there means the first frame is never a stretched one.
    LaunchedEffect(settings.cardSizeKey, settings.cardSizing) {
        windowState.size = overlaySize(settings)
        // Reset the position to the aligned one whenever the size is written from the settings, so a
        // window that fitted its passage — and was therefore positioned by [cornerPosition] — re-anchors
        // to its corner at the new size rather than keeping the top-left a short window left behind.
        windowState.position = WindowPosition(settings.placement.alignment())
    }
    LaunchedEffect(settings.placement) {
        windowState.position = WindowPosition(settings.placement.alignment())
    }
    AzkarTheme(darkTheme = theme.darkTheme, uiLocale = theme.locale, typography = theme.typography) {
        // The same `key` the main tree uses, for the same reason: Compose Multiplatform's
        // resources resolve against a snapshot-backed locale that `Locale.setDefault` does not
        // notify, so an Arabic overlay would otherwise keep drawing its English labels after the
        // app switched.
        key(theme.locale) {
            // A window's size cannot follow its content in Compose Desktop — the window is what the
            // content is laid out *in*, and its size is read before anything inside it is measured.
            // A card that fits its passage therefore has its height measured instead: this box lays
            // the stack out with no height ceiling and then takes whatever height that turned out to
            // need, and the number that comes back — already including the stack's own inset — is
            // pushed straight into the window state.
            //
            // The width is deliberately not taken from the measurement. `wrapContentHeight` leaves
            // the horizontal axis alone, so the width reported here is the current window's, and the
            // width that matters is the setting — which the effect above has already applied.
            Box(
                modifier =
                    if (settings.usesFittedHeight) {
                        Modifier
                            .wrapContentHeight(unbounded = true)
                            .onSizeChanged { measured ->
                                windowState.size =
                                    windowState.size.copy(
                                        height = with(density) { measured.height.toDp() },
                                    )
                                // The window is positioned once, for the height it had when the
                                // placement was applied, and Compose does not revisit that when
                                // the size later changes to fit the passage. A bottom-anchored
                                // window therefore keeps its top-left as it shrinks and its card
                                // drifts up the screen. Re-deriving the corner from the size just
                                // measured is what keeps the fit and the placement honest with each
                                // other; it only has to be done here, on the axis that moves.
                                window.graphicsConfiguration?.let { configuration ->
                                    windowState.position =
                                        cornerPosition(
                                            placement = settings.placement,
                                            size = measured,
                                            configuration = configuration,
                                        )
                                }
                            }
                    } else {
                        // A fixed stack fills the window it was sized for, which is what lets a card
                        // shorter than its slot keep the slot: the column still spans the window, so
                        // the cards below it do not move up into the gap.
                        Modifier.fillMaxSize()
                    },
            ) {
                AnimatedContent(
                    targetState = notices,
                    transitionSpec = { fadeThroughEnter().togetherWith(fadeThroughExit()) },
                    label = "azkar-notification-stack",
                ) { stack ->
                    NotificationStack(
                        stack = stack,
                        placement = settings.placement,
                        // The settings, not a width and a height read off them: the window, the column
                        // and each card's slot are all derived from the same two numbers, and handing
                        // three places a pair of Dps they each re-read is how a card ends up 480 wide
                        // in a 400-wide window until the next restart.
                        presentation = settings,
                        onOpen = onOpen,
                        // The stack does not dismiss or pause cards itself — the presenter owns the
                        // stack's own rules about when a card expires — so it is handed the thing
                        // that owns them rather than two bound references to it.
                        presenter = presenter,
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
    presentation: NotificationPresentationSettings,
    onOpen: (com.azkar.ui.notifications.NotificationAction) -> Unit,
    presenter: NotificationPresenter,
) {
    val isBottom = placement == OverlayPlacement.BOTTOM_END || placement == OverlayPlacement.BOTTOM_START
    val isEnd = placement == OverlayPlacement.TOP_END || placement == OverlayPlacement.BOTTOM_END
    val fillsSlot = !presentation.usesFittedHeight
    Column(
        modifier =
            Modifier
                .then(if (fillsSlot) Modifier.fillMaxSize() else Modifier.fillMaxWidth())
                .width(presentation.cardWidth)
                .padding(EDGE_INSET),
        verticalArrangement = if (isBottom) Arrangement.Bottom else Arrangement.Top,
        horizontalAlignment = if (isEnd) Alignment.End else Alignment.Start,
    ) {
        stack.forEach { item ->
            key(item.id) {
                NotificationCard(
                    item = item,
                    modifier =
                        if (fillsSlot) {
                            // One slot per card rather than letting each find its own height: the window
                            // was sized from this number, so a card that drew taller would be clipped by
                            // the window instead of by its own slot — and one that drew shorter would
                            // leave a gap the cards below it did not move up into.
                            Modifier.size(presentation.cardWidth, presentation.cardHeight)
                        } else {
                            // Under a fitted height the card is handed a width and nothing else: it is
                            // measured at the width the reader chose and is then as tall as its passage
                            // needs, which the window was just resized to accept.
                            Modifier.width(presentation.cardWidth)
                        },
                    sizing = presentation.cardSizing,
                    onOpen = { onOpen(item.action) },
                    onDismiss = { presenter.dismiss(item.id) },
                    onPauseChanged = { paused -> presenter.setPaused(item.id, paused) },
                    // The card asks for its own remaining time rather than being handed a flow of
                    // every card's: a shared flow would invalidate the whole stack on every tick to
                    // animate one three-pixel strip.
                    remaining = { presenter.remainingFraction(item.id) },
                )
            }
        }
    }
}

/**
 * Where the window's top-left goes, in the pixels AWT wants, for a window of [size].
 *
 * Derived here rather than left to [WindowPosition.Aligned] because an aligned position is resolved
 * once, against the size the window had at that moment, and a window that later changes height to fit
 * its passage is never re-aligned — a bottom-anchored reminder keeps its top-left as it shrinks and
 * climbs the screen. This mirrors what Compose itself does for an aligned window — the same screen
 * bounds, the same insets, the same [Alignment.align] — and hands the result back as an absolute
 * position, which is a value Compose re-applies.
 *
 * [WindowPosition.Absolute] carries its coordinates as plain numbers that Compose passes straight to
 * `Window.setLocation` without a density conversion, so these values are pixels; the `.dp` below is
 * only how a float is spelled on the way in.
 */
private fun cornerPosition(
    placement: OverlayPlacement,
    size: IntSize,
    configuration: GraphicsConfiguration,
): WindowPosition {
    val bounds = configuration.bounds
    val insets = Toolkit.getDefaultToolkit().getScreenInsets(configuration)
    val available = IntSize(bounds.width - insets.left - insets.right, bounds.height - insets.top - insets.bottom)
    val offset = placement.alignment().align(size, available, LayoutDirection.Ltr)
    return WindowPosition(
        (bounds.x + insets.left + offset.x).toFloat().dp,
        (bounds.y + insets.top + offset.y).toFloat().dp,
    )
}

private fun OverlayPlacement.alignment(): Alignment =
    when (this) {
        OverlayPlacement.TOP_START -> Alignment.TopStart
        OverlayPlacement.TOP_END -> Alignment.TopEnd
        OverlayPlacement.BOTTOM_START -> Alignment.BottomStart
        OverlayPlacement.BOTTOM_END -> Alignment.BottomEnd
    }

/**
 * The window's size as the settings describe it: the whole stack, derived rather than measured.
 *
 * A window's size cannot follow its content in Compose Desktop — it is what the content is laid out
 * *in* — so the height has to be derived from the cap instead: one card's height per card, plus the
 * inset at each end. The card's own dimensions are the user's, read from
 * [NotificationPresentationSettings] rather than written here, which is what makes the corner of the
 * screen a thing somebody can have an opinion about.
 *
 * A short card — a prayer time, a bare notice — leaves the rest of its slot empty. That is the price
 * of the guarantee the other way round, and it is the right way round for
 * [OverlayCardSizing.FIXED]: a stack whose cards changed height as they arrived would put the card
 * the reader is looking at under the one they just dismissed.
 *
 * Under [OverlayCardSizing.FIT] it is instead the *starting* size and the fallback — the shape the
 * window opens at, and the shape it holds if the measurement cannot be trusted. The height that
 * actually applies is measured from the stack by the overlay above; this is what makes the window
 * rational on its first frame and what it reverts to when the reader stops fitting cards.
 */
private fun overlaySize(settings: NotificationPresentationSettings): DpSize =
    DpSize(
        width = settings.cardWidth + EDGE_INSET * 2,
        height = settings.cardHeight * settings.maxVisible.toFloat() + EDGE_INSET * 2,
    )

/** How far the stack keeps from the screen edge, so it never sits under a taskbar or a menu bar. */
private val EDGE_INSET = AzkarSpacing.lg
