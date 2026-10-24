package com.azkar.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.PlaylistRemove
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The one glyph per action, in one place, so that the same thing never wears two faces.
 *
 * This exists because it did happen, twice, and both times it was invisible to a compiler: a starred
 * remembrance was a bookmark on the collection's member cards and a star on the library's search
 * results, and the collection's "Pin to Top" pill wore a bookmark too — so a bookmark meant both "I
 * want this" and "put this at the top of the shelf" in the same window. Two names for one action is
 * the kind of thing a user only notices when it costs them: they learn the star once, meet a bookmark
 * for the same thing on the next screen, and conclude the app has two different kinds of favourite.
 *
 * So the rules this file encodes are these.
 *
 * - **A star means favourite, and only favourite.** Filled when it is on, outlined when it is off,
 *   because the two states differ in nothing but the claim being made and a filled shape is how this
 *   app says "this one is already on" everywhere else.
 * - **A pushpin means pin.** Also a filled/outlined pair, because a pin is the same kind of claim: a
 *   position the user chose. It is drawn in outline when the collection is not pinned so that the mark
 *   on a card means what the pill in the band means.
 * - **A pin and a star are different claims and both can be true.** A pin says *where* a collection
 *   sits; a star says which one the user wants. Nothing here merges them, because merging them is
 *   exactly how the first version of this went wrong.
 * - **An open book means the source**, never "saved" and never "bookmarked". The brand set's
 *   [AzkarIcons.BookOpen] is used rather than a material glyph so the source line looks the same on
 *   Home, on the session card and on the continue-reading card.
 * - **A pair is used only where the action genuinely has two states** — favourite, pin, Home — and a
 *   single glyph everywhere else. An action with one meaning gets one shape; inventing an
 *   "un-action" glyph for it would only give the two shapes something to be confused with.
 *
 * The pairs are functions rather than two properties because the caller almost always holds a boolean
 * and would otherwise have to remember which way round the two names go. Every member is `internal`
 * because nothing outside this module has business choosing a glyph; the point is that the choice is
 * made here, not merely available here.
 */
internal object ActionIcons {
    /** Favourite, as the two states the app ever draws it in. */
    fun favorite(on: Boolean): ImageVector = if (on) Icons.Filled.Star else Icons.Outlined.StarOutline

    /** Pin, in the same two states, for the same reason. */
    fun pin(on: Boolean): ImageVector = if (on) Icons.Filled.PushPin else Icons.Outlined.PushPin

    /**
     * Whether the collection appears on the Home rail.
     *
     * An eye rather than a house: the action is not "go home" and not "add to a list of homes", it is
     * "stop hiding this from the screen you land on every day". A house glyph would promise a
     * destination rather than a visibility, and there is no honest "un-home" glyph to pair it with —
     * a pair needs two shapes that mean opposite things, which a house does not have.
     */
    fun onHome(on: Boolean): ImageVector = if (on) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility

    /**
     * Open the thing this row is about.
     *
     * An arrow rather than [Icons.Outlined.OpenInNew] because nothing here opens in another window:
     * every one of these rows leads to a page inside this app, and an "open in new window" glyph on a
     * row that does not would be a small lie repeated once per card.
     */
    val open: ImageVector get() = Icons.AutoMirrored.Outlined.ArrowForward

    /** Edit, in every menu and every editor. The pencil, not a text cursor — a cursor means "edit here". */
    val edit: ImageVector get() = Icons.Outlined.Edit

    /** Copy, and duplication too: both write a second copy of the same thing somewhere. */
    val copy: ImageVector get() = Icons.Outlined.ContentCopy

    /** Making a second collection out of the first. Deliberately the same glyph as [copy]. */
    val duplicate: ImageVector get() = Icons.Outlined.ContentCopy

    /** Change the picture this collection is shown with. */
    val cover: ImageVector get() = Icons.Outlined.Image

    /** Delete, and always in the same place in the menu so it is never the item under the thumb. */
    val delete: ImageVector get() = Icons.Outlined.Delete

    /** Read the passage in full, as opposed to the preview on the card. */
    val read: ImageVector get() = Icons.AutoMirrored.Outlined.MenuBook

    /**
     * Go back to the passage the session was on before it was pinned elsewhere.
     *
     * A replay rather than a play, and the distinction is the whole point: the session's own primary
     * action is a play-shaped triangle that *advances*, so borrowing that shape for a control that goes
     * backwards would leave two buttons that look alike and do opposite things.
     */
    val resume: ImageVector get() = Icons.Outlined.Replay

    /** Begin the session again from the first passage. */
    val restart: ImageVector get() = Icons.Outlined.RestartAlt

    /**
     * Put more remembrances into a collection.
     *
     * Stacked layers rather than a plus, and deliberately not a plus: a plus next to a count reads as
     * "add one of these", where what actually happens is "open a picker of everything that is not here
     * yet", which is a list of several things arriving at once.
     */
    val addMembers: ImageVector get() = Icons.Outlined.Layers

    /** Throw the recorded progress away and start counting afresh. The same shape as [restart]. */
    val resetProgress: ImageVector get() = Icons.Outlined.RestartAlt

    /**
     * Take a passage out of a collection.
     *
     * Not the favourite glyph, and not a minus: the favourite glyph here would say "stop wanting this",
     * which is not what happens — the passage stays exactly as wanted and only leaves *this* list.
     */
    val removeFromCollection: ImageVector get() = Icons.Outlined.PlaylistRemove

    /**
     * Dismiss: make the thing on screen go away, as an X everywhere it is drawn.
     *
     * The material glyph rather than the brand set's crossed strokes. The crossed strokes existed
     * for one place — the 14dp corner of the notification overlay, where a heavier X blobbed — and
     * the button has since been accepted at the material glyph there too; everywhere else an X is
     * drawn at normal button sizes and a second shape for the same action would only be something
     * to confuse it with.
     */
    val dismiss: ImageVector get() = Icons.Filled.Close

    /** Where a passage came from. A book, everywhere it is printed. */
    val source: ImageVector
        @Composable get() = AzkarIcons.BookOpen

    /**
     * Whether the queue draws from this thing.
     *
     * A bell, and a bell with a slash when it is off — the same pair the notification overlay uses,
     * because "the reminder is on" and "the reminder is off" are the same claim in both places and
     * one claim should wear one face.
     *
     * A bell rather than the star: the star already means favourite, and a collection can be both
     * starred and reminded about at once. Two claims, two shapes, or the reader has to learn that
     * one of them changed meaning.
     */
    @Composable
    fun reminder(on: Boolean): ImageVector = if (on) AzkarIcons.Bell else AzkarIcons.BellOff

    /**
     * The glyph an "add to reminders" action wears: the bell it will start ringing.
     *
     * The pair above answers "is this thing reminded about?"; a menu item answers "what will
     * happen if I press this?" — so the menu wears the *result* of the action, not the state it
     * will leave behind. Adding wears the bell that will ring; removing wears the bell that will
     * stop.
     */
    val addReminder: ImageVector
        @Composable get() = AzkarIcons.Bell

    /** The glyph a "remove from reminders" action wears: the bell that will stop ringing. */
    val removeReminder: ImageVector
        @Composable get() = AzkarIcons.BellOff
}
