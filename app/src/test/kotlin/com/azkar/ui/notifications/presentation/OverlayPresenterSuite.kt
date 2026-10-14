@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.azkar.ui.notifications.presentation

import com.azkar.ui.notifications.NotificationAction
import com.azkar.ui.notifications.NotificationNotice
import de.infix.testBalloon.framework.core.testSuite
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat

/**
 * The overlay presenter's whole contract, exercised without a window, a display or a real clock.
 *
 * The clock is injected and the scope is the test's, so every assertion here is about behaviour that
 * would otherwise need a person to watch a card and count seconds — expiry, pausing, the stack cap,
 * de-duplication — and every one of them runs in microseconds.
 */
val OverlayPresenterSuite by testSuite("The overlay notification presenter") {
    test("a card arrives newest first, so the stack grows away from the anchored corner") {
        presenterTest { presenter ->
            val first = presenter.present(notice(1))!!
            val second = presenter.present(notice(2))!!

            assertThat(presenter.notices.value.map { it.id }).containsExactly(second, first)
            assertThat(
                presenter.notices.value
                    .first()
                    .title,
            ).isEqualTo("Second")
        }
    }

    test("the same passage twice on screen is refused rather than stacked") {
        presenterTest { presenter ->
            presenter.present(notice(1))
            val duplicate = presenter.present(notice(1))

            assertThat(duplicate).isNull()
            assertThat(presenter.notices.value).hasSize(1)
        }
    }

    test("the same title with a different passage is a different notice") {
        presenterTest { presenter ->
            presenter.present(notice(1))

            assertThat(presenter.present(notice(2))).isNotNull()
            assertThat(presenter.notices.value).hasSize(2)
        }
    }

    test("exceeding the cap drops the oldest card instead of covering it") {
        presenterTest(maxVisible = 2) { presenter ->
            val first = presenter.present(notice(1))!!
            presenter.present(notice(2))
            presenter.present(notice(3))

            assertThat(presenter.notices.value.map { it.id })
                .doesNotContain(first)
                .hasSize(2)
        }
    }

    test("each card expires on its own deadline") {
        runPresenterTest(durationSeconds = 8) { presenter, scope ->
            presenter.present(notice(1))
            scope.advanceTimeBy(4_000)
            scope.runCurrent()
            presenter.present(notice(2))

            scope.advanceTimeBy(4_500)
            scope.runCurrent()

            // The first card's eight seconds are up; the second's are not, and dropping one has
            // left the other alone.
            assertThat(presenter.notices.value.map { it.title }).containsExactly("Second")

            scope.advanceTimeBy(4_000)
            scope.runCurrent()
            assertThat(presenter.notices.value).isEmpty()
        }
    }

    test("hovering freezes a card, and leaving resumes the remainder") {
        runPresenterTest(durationSeconds = 5) { presenter, scope ->
            val id = presenter.present(notice(1))!!
            scope.advanceTimeBy(2_000)
            scope.runCurrent()

            presenter.setPaused(id, true)
            scope.advanceTimeBy(30_000)
            scope.runCurrent()
            assertThat(presenter.notices.value).hasSize(1)

            presenter.setPaused(id, false)
            // Only the three seconds that were left before the pointer arrived, not five.
            scope.advanceTimeBy(2_900)
            scope.runCurrent()
            assertThat(presenter.notices.value).hasSize(1)

            scope.advanceTimeBy(200)
            scope.runCurrent()
            assertThat(presenter.notices.value).isEmpty()
        }
    }

    test("dismissing a card removes it and touches nothing else") {
        presenterTest { presenter ->
            val first = presenter.present(notice(1))!!
            presenter.present(notice(2))

            presenter.dismiss(first)

            assertThat(presenter.notices.value.map { it.id }).containsExactly(2L)
            // And the survivor is still on its own clock rather than being reset by the dismissal.
            assertThat(
                presenter.notices.value
                    .single()
                    .id,
            ).isEqualTo(2L)
        }
    }

    test("disposing empties the stack and refuses anything afterwards") {
        presenterTest { presenter ->
            presenter.present(notice(1))

            presenter.dispose()

            assertThat(presenter.notices.value).isEmpty()
            assertThat(presenter.present(notice(2))).isNull()
        }
    }
}

/** Runs [body] against a presenter on virtual time, with no display and no real sleeping. */
private fun runPresenterTest(
    durationSeconds: Int = 8,
    maxVisible: Int = 3,
    body: suspend (OverlayNotificationPresenter, TestScope) -> Unit,
) = runTest {
    val presenter =
        OverlayNotificationPresenter(
            scope = this,
            settings =
                MutableStateFlow(
                    NotificationPresentationSettings(durationSeconds = durationSeconds, maxVisible = maxVisible),
                ),
            // The test scheduler's virtual clock, so `advanceTimeBy` is what moves the presenter's
            // idea of "now" — the same relationship the real clock has to the real delays.
            now = { testScheduler.currentTime },
        )
    body(presenter, this)
}

private fun presenterTest(
    maxVisible: Int = 3,
    body: (OverlayNotificationPresenter) -> Unit,
) = runTest {
    // A frozen clock and no time advancing: these tests are about the shape of the stack rather than
    // about expiry. `dispose` at the end is what lets the scope end — a live card's countdown loop
    // is an infinite `while`, and `runTest` fails rather than leaks a coroutine that is still running.
    val presenter =
        OverlayNotificationPresenter(
            scope = this,
            settings = MutableStateFlow(NotificationPresentationSettings(maxVisible = maxVisible)),
            now = { 0L },
        )
    body(presenter)
    presenter.dispose()
    advanceUntilIdle()
}

private fun notice(index: Int) =
    NotificationNotice(
        title = if (index == 1) "First" else "Second",
        message = "Message $index",
        action = NotificationAction.Remembrance(index.toLong()),
    )
