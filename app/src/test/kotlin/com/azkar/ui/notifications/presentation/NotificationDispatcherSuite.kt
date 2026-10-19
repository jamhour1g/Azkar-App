package com.azkar.ui.notifications.presentation

import com.azkar.ui.notifications.NotificationAction
import com.azkar.ui.notifications.NotificationNotice
import de.infix.testBalloon.framework.core.testSuite
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.assertj.core.api.Assertions.assertThat

/**
 * The dispatcher's contract with the scheduler: every notice reaches the overlay, and the last one's
 * action is remembered for the tray.
 *
 * The overlay's own rules — the stack cap, de-duplication, expiry — are `OverlayPresenterSuite`'s.
 * What is pinned here is only what the dispatcher adds on top of forwarding, which is the one thing
 * that is not the presenter's: where the last notice wanted to go.
 */
val NotificationDispatcherSuite by testSuite("The notification dispatcher") {
    test("the overlay gets every notice") {
        val harness = Harness()

        harness.dispatcher.publish(harness.notice)

        assertThat(harness.overlay.accepted).containsExactly(harness.notice)
    }

    test("the tray's primary click can find where the last notice wanted to go") {
        val harness = Harness()

        harness.dispatcher.publish(harness.notice)

        assertThat(harness.dispatcher.latestAction)
            .isEqualTo(NotificationAction.Remembrance(42L))
    }

    test("a notice the overlay refused is still where the tray would go next") {
        // The tray is not a second channel, so a refusal does not hide the notice from it: the
        // passage was still announced — an identical card is already on screen for it — and opening
        // it is what the icon is there to do.
        val harness = Harness(overlayAccepts = false)

        harness.dispatcher.publish(harness.notice)

        assertThat(harness.overlay.accepted).isEmpty()
        assertThat(harness.dispatcher.latestAction)
            .isEqualTo(NotificationAction.Remembrance(42L))
    }

    test("quitting releases the overlay and forgets the last notice") {
        val harness = Harness()
        harness.dispatcher.publish(harness.notice)

        harness.dispatcher.dispose()

        assertThat(harness.overlay.disposed).isTrue()
        assertThat(harness.dispatcher.latestAction).isEqualTo(NotificationAction.OpenApp)
    }
}

private class Harness(
    overlayAccepts: Boolean = true,
) {
    val notice =
        NotificationNotice(
            title = "Fajr",
            message = "Say it once, slowly.",
            action = NotificationAction.Remembrance(42L),
        )
    val overlay = RecordingOverlayPresenter(accepts = overlayAccepts)
    val dispatcher = NotificationDispatcher(overlay = overlay)
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
