package com.azkar.ui.notifications.presentation

import com.azkar.ui.notifications.NotificationAction
import com.azkar.ui.notifications.NotificationNotice
import kotlinx.coroutines.flow.StateFlow

/**
 * The one thing the scheduler is allowed to ask for.
 *
 * [com.azkar.ui.notifications.NotificationRuntime] knows that something is due and produces a
 * [NotificationNotice]. It does not know, and must not learn, whether that notice becomes a Compose
 * card in a borderless window, a toast in the OS's notification centre, both, or — if the desktop
 * cannot show anything — nothing. Everything it would otherwise need to know lives behind this
 * interface, and everything that would otherwise have to be a second scheduler lives behind
 * [NotificationDispatcher]'s implementation.
 */
interface NotificationPublisher {
    /**
     * The action the most recently published notice carried.
     *
     * Read by the tray icon's primary click, which has no notion of what was last announced and so
     * needs somewhere to look it up.
     */
    val latestAction: NotificationAction

    /** Hands [notice] to whichever channels are configured. Safe to call from any thread. */
    fun publish(notice: NotificationNotice)

    /** Releases every channel. Called once, from the app's Quit path. */
    fun dispose()
}

/**
 * Routes a fired reminder to the overlay, and to the OS notification service when the user has asked
 * for that as well.
 *
 * Two policies live here and nowhere else, because both of them are about the relationship *between*
 * channels rather than about either one:
 *
 * - **The overlay is the primary.** A notice is always offered to it, whatever the settings say.
 *   There is no way to leave the user without any notification at all.
 * - **The native channel follows the overlay's verdict.** It only fires when the overlay accepted
 *   the notice. That is what makes de-duplication hold across both: a passage the overlay refused
 *   because an identical card is already on screen is not also posted to the notification centre, so
 *   the user does not get the same reminder twice from two different places. It is also why the
 *   native channel can never be described as firing "automatically alongside" the overlay — it
 *   cannot fire unless the user turned [NotificationPresentationSettings.systemNotificationsEnabled]
 *   on, and even then only for notices the overlay actually showed.
 */
class NotificationDispatcher(
    private val overlay: NotificationPresenter,
    private val system: SystemNotificationPresenter,
    private val settings: StateFlow<NotificationPresentationSettings>,
) : NotificationPublisher {
    @Volatile
    private var action: NotificationAction = NotificationAction.OpenApp

    override val latestAction: NotificationAction
        get() = action

    /** Whether the OS channel is currently able to show anything; surfaced for diagnostics only. */
    val systemChannelAvailable: Boolean
        get() = system.isSupported

    override fun publish(notice: NotificationNotice) {
        action = notice.action
        val accepted = overlay.present(notice)
        if (accepted != null && settings.value.systemNotificationsEnabled) {
            system.show(notice)
        }
    }

    override fun dispose() {
        overlay.dispose()
        system.dispose()
        action = NotificationAction.OpenApp
    }
}
