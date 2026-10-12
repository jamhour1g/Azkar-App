package com.azkar.ui.theme

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.height

/**
 * How long things take and how they ease, in one place.
 *
 * The app already animates — `animateColorAsState` on every hover, the spring on the switch thumb, the
 * chevron that turns when a dropdown opens — but each of those wrote its own numbers inline, so
 * nothing could be retimed without hunting through [com.azkar.ui.components.Controls] and every
 * screen that copied its pattern. Durations and curves are the two properties that decide whether a
 * set of animations reads as *one* motion system or as a pile of effects, so they belong beside
 * [AzkarSpacing] and [CardSize] rather than inside the controls themselves.
 *
 * The bands, and why they are far apart rather than all in the 150-250ms middle:
 *
 * - [INSTANT] is a press. It has to feel like the control reacting to the finger, so anything long
 *   enough to watch is already wrong.
 * - [QUICK] is a control changing its own state — hover, selection, a chevron turning. The user is
 *   looking straight at the thing that changed, so the change has to be nearly immediate.
 * - [SCREEN] is one page replacing another. The slowest of the three, because this is the only
 *   transition where the user is *not* already looking at the moving thing and has to be shown where
 *   it went.
 * - [OVERLAY] is a dialog or a menu landing on top of a page that stays put. Slower than a page swap
 *   because the eye has two things to follow and needs longer to accept the new one.
 * - [FILL] is a bar growing. The slowest of all, and the only one that deliberately outlasts the
 *   gesture: the number it is drawing towards is the one the user just changed, so arriving late
 *   draws attention to the new total rather than letting it appear as a fact.
 *
 * [enter] and [exit] are Material's two standard curves, named once. Anything that arrives
 * decelerates; anything that leaves accelerates. Mixing them up is the single most common way a
 * transition set ends up feeling sluggish, because the outgoing half fights the user's attention.
 */
object AzkarMotion {
    /** A press response. Below conscious observation on purpose. */
    const val INSTANT = 90

    /** A control changing its own state. */
    const val QUICK = 140

    /** One screen replacing another. */
    const val SCREEN = 240

    /** A dialog or menu opening over a page that stays. */
    const val OVERLAY = 260

    /** A progress bar growing towards a new total. */
    const val FILL = 420

    /** Decelerate curve — for motion that arrives. */
    val enter: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** Accelerate curve — for motion that leaves. */
    val exit: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    /** [QUICK] on the enter curve: hover, selection, a chevron turning. */
    fun <T> quick(): FiniteAnimationSpec<T> = tween(QUICK, easing = enter)

    /** [SCREEN] on the enter curve: a page arriving. */
    fun <T> screen(): FiniteAnimationSpec<T> = tween(SCREEN, easing = enter)

    /** [OVERLAY] on the enter curve: a surface arriving over the page. */
    fun <T> overlay(): FiniteAnimationSpec<T> = tween(OVERLAY, easing = enter)

    /** [OVERLAY] on the exit curve: a surface leaving. */
    fun <T> exitOverlay(): FiniteAnimationSpec<T> = tween(OVERLAY, easing = exit)

    /** [FILL] on the enter curve: a bar growing to its new total. */
    fun <T> fill(): FiniteAnimationSpec<T> = tween(FILL, easing = enter)

    /** [INSTANT] on the enter curve: a press tint. */
    fun <T> instant(): FiniteAnimationSpec<T> = tween(INSTANT, easing = enter)
}

/**
 * Fade up from below and out downwards — the default pairing for something appearing in place.
 *
 * Deliberately a *short* travel ([OVERLAY_TRAVEL_DIVISOR]). A toast and an inline expansion sit at
 * very different distances from the eye, so the travel has to be small enough that the shorter of
 * them reads as a fade with a hint of motion rather than a slide. Anything that needs to say *which
 * way* it went builds its own slide instead — the page transitions in `com.azkar.ui.shell` — because
 * this pair is for surfaces that arrive on top of a page which stays put, and those have no direction
 * to report.
 */
fun fadeThroughEnter(): EnterTransition =
    fadeIn(animationSpec = AzkarMotion.overlay()) +
        slideInVertically(animationSpec = AzkarMotion.overlay()) { height -> height / OVERLAY_TRAVEL_DIVISOR }

/** The exit half of [fadeThroughEnter]: the same travel, reversed and accelerated. */
fun fadeThroughExit(): ExitTransition =
    fadeOut(animationSpec = AzkarMotion.exitOverlay()) +
        slideOutVertically(animationSpec = AzkarMotion.exitOverlay()) { height -> -height / OVERLAY_TRAVEL_DIVISOR }

/**
 * A dialog scaling up from just inside its resting size while it fades in.
 *
 * The window's own platform fade covers the arrival of the *scrim*, which is a full-screen change and
 * reads fine as a plain fade. What it cannot cover is the pane appearing at all, which is the part
 * that tells the user something was just summoned. Scaling from [DIALOG_START_SCALE] rather than
 * growing the pane out of nothing is what keeps it tied to where it ended up.
 *
 * There is deliberately no exit partner. A `Dialog` leaves the composition the instant the state
 * behind it flips, so an exit animation would have to be run by the caller *before* dismissing —
 * which means twelve call sites that could each get it subtly wrong, and a dialog that cannot be
 * closed if one of them does.
 */
fun dialogEnter(): EnterTransition =
    fadeIn(animationSpec = AzkarMotion.overlay()) +
        scaleIn(
            animationSpec = AzkarMotion.overlay(),
            initialScale = DIALOG_START_SCALE,
        )

/** How far [fadeThroughEnter] travels, as a fraction of the moving element's height. */
private const val OVERLAY_TRAVEL_DIVISOR = 4

/** A dialog starts this far below its resting size. */
private const val DIALOG_START_SCALE = 0.94f
