package com.azkar.ui.notifications.presentation

import kotlinx.serialization.Serializable

/**
 * Which corner of the screen the overlay stacks against.
 *
 * Named for corners rather than for alignment because the overlay is a one-dimensional stack: an
 * alignment would also allow a centred or middle-edge position, and neither of those is something
 * the notification ever wants to be. Four names, no other states.
 */
@Serializable
enum class OverlayPlacement {
    TOP_START,
    TOP_END,
    BOTTOM_START,
    BOTTOM_END,
}

/**
 * How a fired reminder reaches the user, as opposed to *when* it should.
 *
 * Deliberately separate from [com.azkar.ui.notifications.ReminderQueueConfig]: the queue decides
 * that something is due and this decides only how it is shown. Nothing here can change whether a
 * reminder fires, which is what makes the overlay safe to add without touching the scheduler, and
 * what lets a user turn the overlay's cosmetics around without disturbing which passages they
 * actually receive.
 *
 * The overlay itself is not optional. There is no "overlay off" setting because the user asked for
 * one consistent notification appearance everywhere — including while the main window is in front —
 * and a windowed Compose card is the only thing that can be that. What *is* optional is the
 * operating system's own notification channel: it draws the same announcement in whatever style the
 * desktop is themed with, which is the opposite of consistent, so it stays opt-in
 * ([systemNotificationsEnabled]).
 */
@Serializable
data class NotificationPresentationSettings(
    /** Also publish to the OS notification service, on top of the overlay. Off by default. */
    val systemNotificationsEnabled: Boolean = false,
    val durationSeconds: Int = 8,
    val placement: OverlayPlacement = OverlayPlacement.TOP_END,
    /** How many cards the stack holds at once; a newer card evicts the oldest beyond this. */
    val maxVisible: Int = 3,
) {
    init {
        require(durationSeconds in MIN_DURATION_SECONDS..MAX_DURATION_SECONDS)
        require(maxVisible in MIN_VISIBLE..MAX_VISIBLE)
    }

    val durationMillis: Long get() = durationSeconds * 1_000L

    /** Clamps a decoded or edited value back into range instead of throwing on it. */
    fun validated(): NotificationPresentationSettings =
        copy(
            durationSeconds = durationSeconds.coerceIn(MIN_DURATION_SECONDS, MAX_DURATION_SECONDS),
            maxVisible = maxVisible.coerceIn(MIN_VISIBLE, MAX_VISIBLE),
        )
}

// File-private rather than a `private companion object` on a `@Serializable` class: the generated
// serializer reaches `Companion` from outside the class, and a private one fails at runtime with an
// `IllegalAccessError` the first time anybody encodes or decodes it. The bounds are the same either
// way — both names are invisible past this file.
private const val MIN_DURATION_SECONDS = 3
private const val MAX_DURATION_SECONDS = 120
private const val MIN_VISIBLE = 1
private const val MAX_VISIBLE = 5
