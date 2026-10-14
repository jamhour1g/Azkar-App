package com.azkar.ui.notifications.presentation

import com.azkar.ui.notifications.NotificationAction
import com.azkar.ui.notifications.NotificationNotice
import kotlinx.coroutines.flow.StateFlow

/**
 * A notice a presenter has accepted and is currently holding on screen.
 *
 * The identity is the presenter's, not the scheduler's: [com.azkar.ui.notifications.NotificationNotice]
 * has no id because the scheduler has no reason to care whether the same passage was announced last
 * month. The overlay does, because it has to expire a card, pause its timer when the pointer lands
 * on it, and animate it away — and it cannot do any of that to a value it did not mint.
 */
data class PresentedNotice(
    val id: Long,
    val title: String,
    val message: String,
    val action: NotificationAction,
) {
    /** Whether [notice] is the same announcement, for [OverlayNotificationPresenter]'s de-duplication. */
    fun isSameAnnouncementAs(notice: NotificationNotice): Boolean =
        title == notice.title && message == notice.message && action == notice.action
}

/**
 * A presentation channel that holds notices as state rather than pushing them somewhere and
 * forgetting them.
 *
 * The narrowness is the point. The scheduler fires from a coroutine on a background dispatcher and
 * must not know whether the user is looking at a Compose card, at the OS's own notification
 * centre, or at nothing at all; so the only thing it is given is somewhere to hand a notice to. What
 * the presenter does with it — hold it, expire it, drop it — is entirely its own business, and
 * because the overlay's answer is a [StateFlow] rather than a window, every rule in it (the stack
 * cap, the timeout, de-duplication, pause-on-hover) is a plain function that a unit test can
 * exercise with no display attached. That is the whole reason the drawing lives somewhere else.
 */
interface NotificationPresenter {
    /**
     * What is on screen right now, newest first.
     *
     * Newest-first rather than oldest-first because the stack is anchored to whichever corner
     * [NotificationPresentationSettings.placement] names, and an anchored stack has to grow inwards
     * from the anchor: a card arriving at the top edge has to go in above the others, and one
     * arriving at the bottom edge has to go in below. Ordering the list newest-first and laying it
     * out in that order is the one arrangement that reads correctly in all four corners.
     */
    val notices: StateFlow<List<PresentedNotice>>

    /**
     * Offers [notice] for display.
     *
     * Returns the id it was accepted under, or `null` when it was suppressed — because the exact
     * same announcement is already on screen, or because this presenter has been disposed. The
     * caller uses that answer to decide whether the *other* channel should also fire, which is how
     * "never send the same reminder twice" holds across both channels at once.
     *
     * Safe to call from any thread.
     */
    fun present(notice: NotificationNotice): Long?

    /** Removes one card early, which is what the overlay's dismiss control calls. */
    fun dismiss(id: Long)

    /**
     * Freezes or resumes [id]'s countdown.
     *
     * Hover, not focus: the overlay window is created unfocusable so it never takes the keyboard
     * away from the app the user is typing in, which means it cannot be focused in the first place.
     * Pause-on-hover is the honest signal that the reader has looked at it.
     */
    fun setPaused(
        id: Long,
        paused: Boolean,
    )

    /** Drops everything on screen without cancelling the presenter. */
    fun clear()

    /** Releases everything and stops accepting. Called once, from the app's Quit path. */
    fun dispose()
}
