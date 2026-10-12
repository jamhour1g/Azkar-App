package com.azkar.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey

/**
 * A destination in the app's Navigation 3 back stack.
 *
 * These replaced the `AzkarTab` enum the shell used to switch on. The difference is not cosmetic:
 * an enum could name exactly one screen, so "go back" was not expressible and every move was a
 * replacement. A [NavKey] is one entry in a list, so the same history that decides which tab the
 * header highlights is also the list [androidx.navigation3.ui.NavDisplay] renders.
 *
 * [Home] and [Library] are top-level *peers* — the header switches between them rather than pushing
 * a history. [AzkarDestination.Settings] is the first key that is not: the gear reaches it from
 * whichever screen is on screen, so it is pushed and pops back to where it was opened from. It also
 * inherits back handling and state saving from the same list, which is the reason for the move,
 * rather than the move being the reason.
 */
sealed interface AzkarDestination : NavKey {
    /** The dashboard, and the root of the stack: every other destination is reachable back from it. */
    data object Home : AzkarDestination

    /** `ui/library` — the collections shelf, and the only view the library has. */
    data object Library : AzkarDestination

    /**
     * `ui/library/CollectionDetailScreen.kt` — one collection, opened from a tile on the shelf.
     *
     * The first destination that carries an argument, and the first that is a *child* of another
     * screen rather than a peer of it. That is why it is a key and not a screen with a parameter:
     * pushing it means the shelf stays underneath in the stack, so the header keeps highlighting
     * Library, back returns to the shelf, and the shelf's filters and search survive the trip.
     */
    data class CollectionDetail(
        val id: Long,
    ) : AzkarDestination

    /**
     * `ui/library/CollectionSessionScreen.kt` — reciting a collection one member at a time.
     *
     * A child of the collection's page rather than of the shelf, so the stack reads as the route the
     * user actually walked: shelf, then collection, then the session inside it. Back from here lands
     * on the collection, which is where the progress bar that started the session lives.
     */
    data class CollectionSession(
        val id: Long,
    ) : AzkarDestination

    /**
     * `ui/library/RemembranceReaderScreen.kt` — one remembrance, in full, on a page of its own.
     *
     * This is what a card used to open, and the change from a dialog is the point rather than a
     * refinement. A dialog is sized to prove it is temporary: it caps its own height, so the Arabic,
     * the citation, the meaning and the reference cannot all be shown at once at a size any of them is
     * legible at, and something has to be dropped or shrunk to fit. Everything here is the *same* text
     * — the tile's three-line preview was a hook, not the passage — so a container that hides most of
     * the text it was reached for was the wrong shape twice over.
     *
     * It takes the remembrance's id rather than the row so that the page reads through the same shared
     * catalogue as the screen it was opened from, the way every other screen here does. A screen given
     * a `Remembrance` would instead have to trust whatever snapshot the caller held, and the tile's
     * star and the reader's star would then disagree whenever one of them was acted on.
     */
    data class RemembranceReader(
        val id: Long,
    ) : AzkarDestination

    /**
     * `ui/shell/SettingsScreen.kt` — the theme and the language, which the gear opens.
     *
     * Deliberately a screen rather than the dialog it replaced. Both of the things it holds were
     * reachable before — the theme from a header icon, the language from a modal — but only one of
     * them at a time, and neither in a place a reader would go looking for a *setting*. This is where
     * both live now, so growing the list is a matter of adding a row instead of a new affordance in
     * the bar.
     */
    data object Settings : AzkarDestination
}

/**
 * The header tab a destination belongs to.
 *
 * A collection's page is a child of the library, so it highlights the Library tab rather than none of
 * them — the header is the app's answer to "where am I", and a collection page that greys out every
 * tab reads as having left the library entirely. Written as a projection rather than as a second
 * `selected` check at the call site so a fourth child destination cannot forget the rule.
 *
 * The reader is here for the same reason and by the same route, and the fact that it *had* to be added
 * by hand is worth stating: it was reached from a card on the shelf, from a card inside a collection,
 * from a search result and from the recitation session, and every one of those places already
 * highlighted Library. Forgetting it would have greyed out the whole header bar for the one screen
 * that is unambiguously still in the library.
 */
val AzkarDestination.rootTab: AzkarDestination
    get() =
        when (this) {
            is AzkarDestination.CollectionDetail,
            is AzkarDestination.CollectionSession,
            is AzkarDestination.RemembranceReader,
            -> AzkarDestination.Library

            else -> this
        }

/**
 * The back stack plus the navigation the shell and screens ask for by name.
 *
 * Navigation 3 is deliberately unopinionated: [androidx.navigation3.ui.NavDisplay] renders a list
 * and leaves moving through it to the caller. So the two conventions that used to be implied by
 * `state.tab` — which destinations are peers, and where the stack is allowed to bottom out — are
 * stated here instead of being spread across the call sites that happen to trigger them.
 */
@Stable
class AzkarNavigator(
    private val backStack: NavBackStack<AzkarDestination>,
) {
    /**
     * What `NavDisplay` renders.
     *
     * Typed as a read-only [List] on purpose. Every screen asks to *go somewhere* through this
     * class, and nothing needs to add or remove entries directly, so handing out the mutable
     * back stack would only make the stack's shape something a screen could break.
     */
    val entries: List<AzkarDestination> get() = backStack

    /** The destination on top, which is also the header's selected tab. */
    val current: AzkarDestination get() = backStack.last()

    /** Shows the dashboard, whether or not it is already on screen. */
    fun showHome() = showTopLevel(AzkarDestination.Home)

    /** Shows the library, whether or not it is already on screen. */
    fun showLibrary() = showTopLevel(AzkarDestination.Library)

    /**
     * Opens the settings screen on top of whatever is already on screen.
     *
     * Pushed rather than swapped in for a peer, because the gear is on every screen: pushing is what
     * lets [back] put the user back where they were, where [showTopLevel] would drop the library the
     * moment they left it. Re-opening while it is already up is a no-op, so a double click cannot
     * stack two settings screens that then need two back presses.
     */
    fun showSettings() {
        if (backStack.last() != AzkarDestination.Settings) {
            backStack.add(AzkarDestination.Settings)
        }
    }

    /**
     * Opens one collection's page on top of the shelf.
     *
     * Pushed rather than swapped in for the same reason [showSettings] is: the shelf is where the
     * user came from and where they are going back to, and the shelf's own state — what was searched,
     * what was filtered — is only preserved if it stays in the stack underneath.
     *
     * Re-opening the collection already on screen is a no-op, so a double click cannot stack two
     * copies that then need two back presses. Re-opening a *different* collection from its own page
     * does push, because that is a real move: the page on top has to change to something else.
     */
    fun showCollection(id: Long) {
        if (backStack.last() != AzkarDestination.CollectionDetail(id)) {
            backStack.add(AzkarDestination.CollectionDetail(id))
        }
    }

    /**
     * Opens a recitation session for one collection.
     *
     * Pushed for the same reason [showCollection] is, and with the same no-op guard: a user who
     * double-clicks Start must not end up two sessions deep, each of which would have to be backed out
     * of separately. Starting the *same* collection again from inside a session is a no-op rather than
     * a reset — restarting is a deliberate act with its own control on the session screen.
     */
    fun showSession(id: Long) {
        if (backStack.last() != AzkarDestination.CollectionSession(id)) {
            backStack.add(AzkarDestination.CollectionSession(id))
        }
    }

    /**
     * Opens one remembrance for reading.
     *
     * Pushed for the same reason every other [showX] here is, with the same no-op guard so a double
     * click cannot stack two readers. What is different is that *every* screen in the app can reach
     * this one — the shelf's search results, a collection's grid, and the session it can interrupt —
     * which is the one thing a shared stack buys over a dialog: dismissing the reader leaves each of
     * them exactly as it was, filters, scroll position and session progress included, because the
     * screen underneath never stopped being the thing on screen.
     */
    fun showRemembrance(id: Long) {
        if (backStack.last() != AzkarDestination.RemembranceReader(id)) {
            backStack.add(AzkarDestination.RemembranceReader(id))
        }
    }

    /**
     * Leaves the screen on top for the one underneath.
     *
     * Exactly one entry, and never the last: [NavDisplay] renders `backStack.last()`, so emptying the
     * stack would leave it with nothing to draw. Stopping at the root rather than failing means a back
     * press on the dashboard is swallowed instead of crashing, which is what the back gesture wants.
     */
    fun back() {
        if (backStack.size > ROOT_DEPTH) {
            backStack.removeAt(backStack.lastIndex)
        }
    }

    /**
     * Top-level destinations are peers, not a history.
     *
     * Everything above the root is dropped before the target is added, which is what stops the
     * header from quietly building a stack three entries deep that no screen knows how to unwind.
     * [AzkarDestination.Home] stays as the root under [AzkarDestination.Library], so the library
     * screen's "back to home" button has somewhere to land and a back press on the library has
     * somewhere to go.
     */
    private fun showTopLevel(destination: AzkarDestination) {
        while (backStack.size > ROOT_DEPTH) {
            backStack.removeAt(backStack.lastIndex)
        }
        if (backStack.last() != destination) {
            backStack.add(destination)
        }
    }

    private companion object {
        /** The stack never empties, so [AzkarDestination.Home] always has something to render. */
        const val ROOT_DEPTH = 1
    }
}

/**
 * The remembered back stack and its navigator, hoisted once by [AzkarApp].
 *
 * Deliberately *not* remembered inside the `key(state.locale)` block that rebuilds the tree on a
 * language switch: keying throws away everything remembered below it, so a navigator created there
 * would send the user back to the home screen every time they changed language.
 */
@Composable
fun rememberAzkarNavigator(): AzkarNavigator = remember { AzkarNavigator(NavBackStack(AzkarDestination.Home)) }
