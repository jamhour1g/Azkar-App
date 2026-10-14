package com.azkar.ui.shell

import com.azkar.ui.notifications.NotificationAction
import com.azkar.ui.notifications.NotificationNotice
import dev.nucleusframework.notification.common.NotificationManager
import dev.nucleusframework.notification.common.notification
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * How a due reminder reaches the user when the app is not the window in front.
 *
 * Behind an interface for the same reason every other data seam in this app is: the tick fires from
 * a coroutine, and a test has to be able to assert that it fired without a desktop session.
 *
 * The tray icon and its menu are not this interface's business — they are Compose state and live in
 * [ReminderTray]. What is here is the announcement itself, because that has to survive the window
 * being hidden and so has to come from the process rather than from a composition.
 *
 * [show] is expected to be safe to call from any thread and is expected to do nothing at all rather
 * than throw when a notification cannot be shown. A reminder that cannot be announced is still worth
 * recording as fired — the in-app toast is the other half of the delivery — so a notifier that
 * failed loudly would turn a missing notification service into a crash on a background thread.
 */
interface ReminderNotifier {
    /** Whether this machine can show anything at all. False means [show] is a no-op. */
    val isSupported: Boolean

    /** Announces [notice], or does nothing if it cannot. */
    fun show(notice: NotificationNotice)

    /** The action the most recent notice carried, for the tray icon's primary click. */
    val latestAction: NotificationAction

    /**
     * Releases anything the notifier is holding outside this process's own objects.
     *
     * A no-op for most. A tray icon is visible for as long as it exists and outlives the window that
     * registered it, but that one is now composition state and leaves with the composition that drew
     * it, so the only thing left to release is whatever the notification backend cached.
     */
    fun dispose() = Unit
}

/**
 * A notifier that announces nothing.
 *
 * The default for the screenshot harness and for anything composed outside a real desktop session,
 * so that no code path has to ask whether a notifier exists before using one.
 */
object SilentReminderNotifier : ReminderNotifier {
    override val isSupported: Boolean = false

    override val latestAction: NotificationAction = NotificationAction.OpenApp

    override fun show(notice: NotificationNotice) = Unit
}

/**
 * Desktop notifications through the OS's own notification service.
 *
 * Each platform is answered natively rather than approximated: `UNUserNotificationCenter` on macOS,
 * a WinRT toast on Windows, and an `org.freedesktop.Notifications` message over D-Bus on Linux.
 * That is the same ground [ReminderTray] covers for the icon, and it is why this class has no
 * drawing code in it — the tray artifact owns the icon, and an announcement has to come from
 * whichever channel the OS actually surfaces to the user, which is not a tray icon's own tooltip.
 *
 * Delivery is unevenly available: macOS drops notifications from an unbundled jar (jpackage does
 * bundle the app, so the packaged build is the one that works), Linux needs a notification daemon,
 * and a machine with no display has neither. [NotificationManager.isAvailable] answers for the
 * common case and [isSupported] defers to it, so a missing service degrades to the in-app toast
 * rather than to an exception on a scheduler thread.
 */
class NativeReminderNotifier(
    private val onAction: (NotificationAction) -> Unit,
) : ReminderNotifier {
    @Volatile
    private var action: NotificationAction = NotificationAction.OpenApp

    override val latestAction: NotificationAction
        get() = action

    override val isSupported: Boolean =
        runCatching {
            // Eager, because on Windows it is what registers the toast's app identity, and the
            // first reminder of the session should not be the one that pays for it. Everywhere else
            // this is a no-op and `send` would have initialized on its own anyway.
            NotificationManager.initialize()
            NotificationManager.isAvailable()
        }.getOrDefault(false)

    override fun show(notice: NotificationNotice) {
        action = notice.action
        if (!isSupported) return
        runCatching {
            notification(
                title = notice.title,
                message = notice.message,
                onActivated = { onAction(notice.action) },
            ).send()
        }.onFailure {
            logger.warn(it) { "Desktop notification could not be delivered" }
        }
    }

    override fun dispose() {
        action = NotificationAction.OpenApp
    }
}
