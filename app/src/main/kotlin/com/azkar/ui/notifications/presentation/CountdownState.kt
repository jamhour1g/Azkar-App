package com.azkar.ui.notifications.presentation

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue

/**
 * The bar's own idea of how much time the card has left.
 *
 * A countdown is the one thing on a card that can only go one way, and the bar along the card's top
 * edge is the only place that says so out loud. So the bar keeps the *smallest* fraction it has been
 * told and ignores a sample that comes back larger.
 *
 * That is not defensiveness for its own sake. A card on its way off the screen is still composed for
 * the length of the exit transition, and it is still asking how much time it has. The answer it
 * gets back can therefore arrive out of order — a reader that has already forgotten the card will
 * answer "all of it" — and believing that would fill the bar up again on the way out, which is the
 * one motion a countdown must never make.
 *
 * The state is held here rather than in the composable so that the rule is a rule about *time*
 * rather than about drawing: it is worth stating in one line, and worth a test of its own.
 */
@Stable
internal class CountdownState(
    initial: Float = 1f,
) {
    /**
     * Where the bar is heading, as a fraction of the card's time.
     *
     * Observed by the animation effect and written only by [sample].
     */
    var target by mutableFloatStateOf(initial.coerceIn(0f, 1f))
        private set

    /**
     * Takes [sampled] as the card's latest word on its own remaining time.
     *
     * A word that would make the bar longer is not heard. The card's time only ever runs out, so a
     * larger answer is either a card being retired and forgotten about, or a stale reading arriving
     * late — and neither is a reason to put time back on the clock.
     */
    fun sample(sampled: Float) {
        val heard = sampled.coerceIn(0f, 1f)
        if (heard < target) target = heard
    }
}
