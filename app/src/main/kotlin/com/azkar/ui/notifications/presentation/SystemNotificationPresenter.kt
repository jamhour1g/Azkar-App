package com.azkar.ui.notifications.presentation

import com.azkar.ui.notifications.NotificationAction
import com.azkar.ui.notifications.NotificationNotice
import com.azkar.ui.shell.ReminderNotifier

/**
 * The operating system's own notification channel, wrapped as a presenter so the dispatcher cannot
 * tell which channel it is talking to.
 *
 * Deliberately *not* a [NotificationPresenter]. The state half of that interface — a stack, a
 * deadline, a pause — describes something this app draws and the OS does not: a native toast is
 * owned by the desktop from the moment it is handed over, it expires on the notification centre's
 * schedule rather than the app's, and it cannot be paused by hovering a card in another process.
 * Modelling that as a presenter would mean writing a second, fake implementation of a contract whose
 * only implementer would be lying about most of it.
 *
 * What the two channels *do* share — where a click should take the user — is
 * [ReminderNotifier.latestAction], which the tray icon's primary click also reads.
 */
class SystemNotificationPresenter(
    private val notifier: ReminderNotifier,
) {
    /** Whether this machine can surface anything at all; `false` makes [show] a no-op. */
    val isSupported: Boolean get() = notifier.isSupported

    /** The action the last delivered notice carried. */
    val latestAction: NotificationAction get() = notifier.latestAction

    fun show(notice: NotificationNotice) = notifier.show(notice)

    fun dispose() = notifier.dispose()
}
