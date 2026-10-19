package com.azkar.ui.notifications.presentation

import com.azkar.ui.notifications.NotificationAction
import com.azkar.ui.notifications.NotificationNotice

/**
 * The one thing the scheduler is allowed to ask for.
 *
 * [com.azkar.ui.notifications.NotificationRuntime] knows that something is due and produces a
 * [NotificationNotice]. It does not know, and must not learn, whether that notice becomes a Compose
 * card in a borderless window or — if the desktop cannot show one — nothing. Everything it would
 * otherwise need to know lives behind this interface, and everything that would otherwise have to
 * be a second scheduler lives behind [NotificationDispatcher]'s implementation.
 */
interface NotificationPublisher {
    /**
     * The action the most recently published notice carried.
     *
     * Read by the tray icon's primary click, which has no notion of what was last announced and so
     * needs somewhere to look it up.
     */
    val latestAction: NotificationAction

    /** Hands [notice] to the overlay. Safe to call from any thread. */
    fun publish(notice: NotificationNotice)

    /** Releases the overlay. Called once, from the app's Quit path. */
    fun dispose()
}

/**
 * Routes a fired reminder to the overlay, and remembers where the last one wanted to go.
 *
 * There is one channel, and that is a decision rather than an absence. The operating system's own
 * notification service used to sit beside the overlay behind an opt-in: the same announcement drawn
 * by the desktop in the desktop's own styling, with a lifetime the app does not own and rules it
 * cannot read. Two drawings of one reminder is one reminder too many, and the second one is the one
 * that does not match the app — so the channel was removed rather than defaulted off. Everything
 * that survives here is what the overlay needs anyway: somewhere to hand a notice, and the last
 * notice's action for the tray icon's primary click.
 */
class NotificationDispatcher(
    private val overlay: NotificationPresenter,
) : NotificationPublisher {
    @Volatile
    private var action: NotificationAction = NotificationAction.OpenApp

    override val latestAction: NotificationAction
        get() = action

    override fun publish(notice: NotificationNotice) {
        action = notice.action
        overlay.present(notice)
    }

    override fun dispose() {
        overlay.dispose()
        action = NotificationAction.OpenApp
    }
}
