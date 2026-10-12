package com.azkar.ui.shell

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import com.azkar.ui.theme.AzkarMotion
import kotlin.math.roundToInt

/**
 * How far a page travels as the other one leaves, as a fraction of its own width.
 *
 * Nine percent is the number that keeps this from becoming a slideshow. A full-width slide is the
 * right choice on a phone held in one hand, where the thumb and the screen move together; on a
 * desktop window it is far too large a thing to watch swing across a display the user is already
 * looking past. At this distance the direction is legible from the corner of the eye — which is the
 * only thing the movement has to carry, since the header's own tab highlight has already said where
 * the user is going — without the transition outlasting the user's patience with it.
 */
private const val PAGE_TRAVEL = 0.09f

/** Which way "further in" points. Mirrored for Arabic, where forward is toward the left edge. */
private fun forwardSign(isRtl: Boolean): Float = if (isRtl) -1f else 1f

/**
 * How far the arriving side of a step starts from the edge it came in by, for a surface [width]
 * wide.
 *
 * The single place the sign of the movement is decided, so that going back is arithmetically the
 * exact negative of going forwards and mirroring to Arabic is the exact negative again — rather than
 * four lambdas each re-deriving "which way is forward" from a boolean.
 *
 * @return a positive number for a leftward start, negative for rightward.
 */
internal fun pageTravelOffset(
    isRtl: Boolean,
    forwards: Boolean,
    width: Int,
): Int {
    val sign = if (forwards) forwardSign(isRtl) else -forwardSign(isRtl)
    return (sign * width * PAGE_TRAVEL).roundToInt()
}

/**
 * What [NavDisplay](androidx.navigation3.ui.NavDisplay) plays when the back stack grows.
 *
 * Every destination in the app is either a top-level tab or something reached from one, and there is
 * no case where a page should arrive without saying which way it came from: the header's own tab
 * highlight has already moved by the time this runs, so this is the only thing confirming that the
 * move went *in* rather than sideways. Before any of this the desktop build of `NavDisplay` shipped
 * a hard cut — both transition specs compiled down to `EnterTransition.None` — so navigation simply
 * replaced one screen with another and left direction entirely to guesswork.
 */
internal fun pushPageTransition(isRtl: Boolean): ContentTransform = stepTransition(isRtl, forwards = true)

/**
 * What [NavDisplay](androidx.navigation3.ui.NavDisplay) plays when the back stack shrinks.
 *
 * Exactly [pushPageTransition] with the halves swapped, so going back retraces the path forwards
 * took rather than cutting somewhere else: the page that comes back settles where it stood before it
 * left, instead of arriving from a direction the user has not just navigated from.
 */
internal fun popPageTransition(isRtl: Boolean): ContentTransform = stepTransition(isRtl, forwards = false)

/**
 * One step in either direction: the newcomer arrives off the edge it came from while the thing it
 * replaces retreats the opposite way.
 *
 * Shared rather than spelled out four times, because the direction a move reads as is one fact about
 * the app rather than four facts about two screens. A passage advancing through a recitation session
 * travels along exactly the same axis as the page underneath it moving in or out of the back stack,
 * and if the two disagreed — the passage sliding left while the screen slid right — the second one
 * would read as a mistake.
 *
 * @param isRtl mirrors the whole gesture. In Arabic "further in" is toward the left edge, so a
 *   transition that ignores this would send every arriving screen in the opposite direction to the
 *   reading direction.
 * @param forwards whether the newcomer is further along than what it replaces.
 */
internal fun stepTransition(
    isRtl: Boolean,
    forwards: Boolean,
): ContentTransform =
    fadeIn(animationSpec = AzkarMotion.screen()) +
        slideInHorizontally(animationSpec = AzkarMotion.screen()) { width ->
            pageTravelOffset(isRtl, forwards, width)
        } togetherWith
        fadeOut(animationSpec = AzkarMotion.screen()) +
        slideOutHorizontally(animationSpec = AzkarMotion.screen()) { width ->
            -pageTravelOffset(isRtl, forwards, width)
        }
