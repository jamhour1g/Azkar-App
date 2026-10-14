package com.azkar.ui.notifications.presentation

import com.azkar.ui.notifications.NotificationAction
import com.azkar.ui.notifications.NotificationNotice
import com.azkar.ui.shell.ReminderNotifier
import de.infix.testBalloon.framework.core.testSuite
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.assertj.core.api.Assertions.assertThat

/**
 * The channel policy, which is the one thing the dispatcher exists to hold.
 *
 * Everything here is about the relationship between the two channels rather than about either: the
 * overlay is always offered a notice, the OS channel is never fired without being asked for, and
 * never for a notice the overlay refused.
 */
val NotificationDispatcherSuite by testSuite("The notification dispatcher") {
    test("the overlay gets every notice, with the native channel off") {
        val harness = Harness(systemNotificationsEnabled = false)

        harness.dispatcher.publish(harness.notice)

        assertThat(harness.overlay.accepted).containsExactly(harness.notice)
        assertThat(harness.notifier.shown).isEmpty()
    }

    test("the native channel fires only once the user has asked for it") {
        val harness = Harness(systemNotificationsEnabled = true)

        harness.dispatcher.publish(harness.notice)

        assertThat(harness.notifier.shown).containsExactly(harness.notice)
    }

    test("a notice the overlay refused is not also posted to the notification centre") {
        val harness = Harness(systemNotificationsEnabled = true, overlayAccepts = false)

        harness.dispatcher.publish(harness.notice)

        assertThat(harness.notifier.shown).isEmpty()
    }

    test("the tray's primary click can find where the last notice wanted to go") {
        val harness = Harness(systemNotificationsEnabled = false)

        harness.dispatcher.publish(harness.notice)

        assertThat(harness.dispatcher.latestAction)
            .isEqualTo(NotificationAction.Remembrance(42L))
    }

    test("quitting releases both channels") {
        val harness = Harness(systemNotificationsEnabled = true)
        harness.dispatcher.publish(harness.notice)

        harness.dispatcher.dispose()

        assertThat(harness.overlay.disposed).isTrue()
        assertThat(harness.notifier.disposed).isTrue()
        assertThat(harness.dispatcher.latestAction).isEqualTo(NotificationAction.OpenApp)
    }
}

private class Harness(
    systemNotificationsEnabled: Boolean = false,
    overlayAccepts: Boolean = true,
) {
    val notice =
        NotificationNotice(
            title = "Fajr",
            message = "Say it once, slowly.",
            action = NotificationAction.Remembrance(42L),
        )
    val overlay = RecordingOverlayPresenter(accepts = overlayAccepts)
    val notifier = RecordingNotifier()
    val dispatcher =
        NotificationDispatcher(
            overlay = overlay,
            system = SystemNotificationPresenter(notifier),
            settings = MutableStateFlow(NotificationPresentationSettings(systemNotificationsEnabled)),
        )
}

/** A presenter that answers with a verdict instead of drawing anything. */
private class RecordingOverlayPresenter(
    private val accepts: Boolean,
) : NotificationPresenter {
    val accepted = mutableListOf<NotificationNotice>()
    var disposed = false

    private val flow = MutableStateFlow<List<PresentedNotice>>(emptyList())
    override val notices: StateFlow<List<PresentedNotice>> = flow

    override fun present(notice: NotificationNotice): Long? {
        if (!accepts) return null
        accepted += notice
        return accepted.size.toLong()
    }

    override fun dismiss(id: Long) = Unit

    override fun setPaused(
        id: Long,
        paused: Boolean,
    ) = Unit

    override fun clear() = Unit

    override fun dispose() {
        disposed = true
    }
}

/** The OS channel, with the OS left out. */
private class RecordingNotifier : ReminderNotifier {
    val shown = mutableListOf<NotificationNotice>()
    var disposed = false

    override val isSupported: Boolean = true
    override val latestAction: NotificationAction = NotificationAction.OpenApp

    override fun show(notice: NotificationNotice) {
        shown += notice
    }

    override fun dispose() {
        disposed = true
    }
}
