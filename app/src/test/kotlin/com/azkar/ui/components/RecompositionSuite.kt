package com.azkar.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.azkar.ui.theme.AzkarTheme
import de.infix.testBalloon.framework.core.testSuite
import org.assertj.core.api.Assertions.assertThat
import java.util.Locale

/**
 * Counts how many times the composable a [composed] modifier sits on is recomposed.
 *
 * `SideEffect` runs after every *successful* composition of the scope it is in. Put on a control's
 * `modifier`, that scope is the control's own body, so this counts the control's recompositions and
 * not its parent's. A sibling probe would only ever prove that Compose's skipping works, which is
 * not the question being asked here.
 *
 * It is a plain function rather than a `Modifier` extension so it has to be applied with `.then()`.
 * That is deliberate: as an extension the receiver would be implicit and a future edit could
 * silently chain it in the wrong order relative to the control's own modifiers.
 */
private fun countingProbe(onRecompose: () -> Unit): Modifier =
    Modifier.composed {
        SideEffect { onRecompose() }
        this
    }

/**
 * Applies [pressFeedback] the way the app does, which is through [pressableClick] — it is the larger
 * of the two call-site populations (36 of roughly 51) and, more to the point, [pressFeedback] on its
 * own receives nothing: the interaction source that emits press events belongs to the `clickable`
 * that owns the press.
 *
 * [onClick] is there to prove the gesture landed. A callback either fires or it does not, which is
 * the one unambiguous signal available: without it, a "zero recompositions" assertion would pass just
 * as happily if the injected press had never reached the node at all.
 */
@Composable
private fun PressSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .size(120.dp)
            .background(Color.White)
            .pressableClick(onClick = onClick),
    )
}

/**
 * Every control here used to read its hover and press animation through a `by` delegate, which reads
 * at composition — so each animation frame recomposed the control, and after the move to Material 3
 * Expressive that meant recomposing a whole `Surface`/`IconButton` subtree per frame. They now drive
 * [androidx.compose.animation.core.Animatable] from the interaction stream and read it inside a
 * deferred draw-phase block.
 *
 * These tests are the regression net for that. The budgets are **zero by intent**, not zero by
 * measurement luck: a control whose feedback is expressed declaratively will recompose once per
 * animation frame, so any non-zero count here is the old defect returning rather than noise. The
 * control case is Material 3's own `IconButton`, which already worked this way — a control should
 * cost no more to hover than the Material control it wraps.
 *
 * The failure this guards against is not hypothetical. Screenshot-based checking cannot see it (the
 * hover fill is invisible in a still), and it was found by counting compositions, not by looking.
 */
@OptIn(ExperimentalTestApi::class)
val RecompositionSuite by testSuite("Control recomposition budgets") {
    test("hovering a circle icon button recomposes nothing") {
        runComposeUiTest {
            mainClock.autoAdvance = false
            var recompositions = 0
            setContent {
                AzkarTheme(darkTheme = false, uiLocale = Locale.ENGLISH) {
                    Box(Modifier.size(300.dp).background(Color.White)) {
                        CircleIconButton(
                            icon = AzkarIcons.Star,
                            contentDescription = "favourite",
                            onClick = {},
                            modifier = countingProbe { recompositions++ },
                        )
                    }
                }
            }
            mainClock.advanceTimeBy(1_000)
            val settled = recompositions
            onNodeWithContentDescription("favourite").performMouseInput { moveTo(center) }
            repeat(40) { mainClock.advanceTimeBy(16) }
            assertThat(recompositions - settled).describedAs("hover should be draw-phase only").isZero()
        }
    }

    test("hovering an icon action button recomposes nothing") {
        runComposeUiTest {
            mainClock.autoAdvance = false
            var recompositions = 0
            setContent {
                AzkarTheme(darkTheme = false, uiLocale = Locale.ENGLISH) {
                    Box(Modifier.size(300.dp).background(Color.White)) {
                        IconActionButton(
                            icon = ActionIcons.favorite(on = true),
                            contentDescription = "favourite",
                            onClick = {},
                            modifier = countingProbe { recompositions++ },
                        )
                    }
                }
            }
            mainClock.advanceTimeBy(1_000)
            val settled = recompositions
            onNodeWithContentDescription("favourite").performMouseInput { moveTo(center) }
            repeat(40) { mainClock.advanceTimeBy(16) }
            assertThat(recompositions - settled).describedAs("hover should be draw-phase only").isZero()
        }
    }

    /**
     * [pressFeedback] is the highest-leverage of these: it is applied by roughly fifty call sites, so
     * a per-frame composition here was costing the whole app.
     *
     * The click count is the part that makes the zero meaningful. It is asserted first and on the
     * same node, so "zero recompositions" can only be read alongside "the press demonstrably landed
     * here and still cost nothing" — not as "the press never arrived".
     */
    test("pressing a press-feedback surface recomposes nothing") {
        runComposeUiTest {
            mainClock.autoAdvance = false
            var recompositions = 0
            var clicks = 0
            setContent {
                AzkarTheme(darkTheme = false, uiLocale = Locale.ENGLISH) {
                    PressSurface(
                        onClick = { clicks++ },
                        modifier = countingProbe { recompositions++ }.testTag("press-surface"),
                    )
                }
            }
            mainClock.advanceTimeBy(1_000)
            val settled = recompositions
            onNodeWithTag("press-surface").performMouseInput {
                moveTo(center)
                press()
                repeat(40) { mainClock.advanceTimeBy(16) }
                release()
                repeat(40) { mainClock.advanceTimeBy(16) }
            }
            assertThat(clicks).describedAs("the press must actually land on this node").isEqualTo(1)
            assertThat(recompositions - settled).describedAs("press should be draw-phase only").isZero()
        }
    }

    /**
     * Every icon-only control in the app now ships inside a tooltip, so the hover path the budgets
     * above guard is no longer the one that runs.
     *
     * Showing a tooltip is a genuine state change — Material reads `TooltipState.isVisible` at
     * composition — so the appearance itself is allowed to cost something. What must not happen is
     * the failure this suite exists for: a *per-frame* cost while the chip is simply sitting there.
     * So the count is taken once the chip has appeared and settled, and only the frames after that
     * are asserted. A budget of zero across the settle would be a stronger claim than is true and
     * would eventually fail for reasons that are not a defect.
     */
    test("a tooltip button does not recompose per frame while its tooltip is on screen") {
        runComposeUiTest {
            mainClock.autoAdvance = false
            var recompositions = 0
            setContent {
                AzkarTheme(darkTheme = false, uiLocale = Locale.ENGLISH) {
                    Box(Modifier.size(300.dp).background(Color.White)) {
                        TooltipIconButton(
                            icon = AzkarIcons.Star,
                            tooltip = "Add to favourites",
                            onClick = {},
                            modifier = countingProbe { recompositions++ },
                        )
                    }
                }
            }
            mainClock.advanceTimeBy(1_000)
            onNodeWithContentDescription("Add to favourites").performMouseInput { moveTo(center) }
            // Long enough for the popup to compose and its scale/fade transition to finish.
            repeat(60) { mainClock.advanceTimeBy(16) }
            val shown = recompositions
            repeat(40) { mainClock.advanceTimeBy(16) }
            assertThat(recompositions - shown)
                .describedAs("a settled tooltip must not keep recomposing the control beneath it")
                .isZero()
        }
    }

    /**
     * [NavButton] is allowed one: it has to animate its container between destinations, and that is a
     * real state change rather than a hover reaction. What must not happen is a per-frame cost while
     * the pointer merely rests on it — measured at zero over a 40-frame hover.
     */
    test("hovering a nav button does not recompose per frame") {
        runComposeUiTest {
            mainClock.autoAdvance = false
            var recompositions = 0
            setContent {
                AzkarTheme(darkTheme = false, uiLocale = Locale.ENGLISH) {
                    Box(Modifier.size(600.dp).background(Color.White)) {
                        NavButton(
                            text = "Home",
                            selected = false,
                            onClick = {},
                            leadingIcon = AzkarIcons.Home,
                            modifier = countingProbe { recompositions++ },
                        )
                    }
                }
            }
            mainClock.advanceTimeBy(1_000)
            val settled = recompositions
            // Selected, so the container animation is actually in flight while hovered — this is the
            // case that would surface a per-frame cost if one existed.
            onNodeWithText("Home").performMouseInput { moveTo(center) }
            repeat(40) { mainClock.advanceTimeBy(16) }
            assertThat(recompositions - settled)
                .describedAs("hover must not animate, so must not compose")
                .isLessThanOrEqualTo(1)
        }
    }

    /**
     * The question that decides whether the per-control cost is worth fixing: does it multiply?
     * Twenty-four buttons in a grid, pointer swept across them, compared against the same grid of
     * raw Material 3 `IconButton`s. Any gap between the two lines is ours to account for; anything
     * both share is the cost of the M3 control itself.
     */
    test("sweeping a grid of 24 circle icon buttons costs what 24 raw Material 3 icon buttons cost") {
        var ours = 0
        var raw = 0

        runComposeUiTest {
            mainClock.autoAdvance = false
            setContent {
                AzkarTheme(darkTheme = false, uiLocale = Locale.ENGLISH) {
                    Box(Modifier.size(600.dp).background(Color.White)) {
                        Column {
                            repeat(6) { row ->
                                Row {
                                    repeat(4) { col ->
                                        CircleIconButton(
                                            icon = AzkarIcons.Star,
                                            contentDescription = "ours-$row-$col",
                                            onClick = {},
                                            buttonSize = 40.dp,
                                            modifier = countingProbe { ours++ },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            mainClock.advanceTimeBy(1_000)
            val settled = ours
            // Sweep the pointer across each row, stepping frames so any animation would run.
            for (row in 0 until 6) {
                for (col in 0 until 3) {
                    onNodeWithContentDescription("ours-$row-$col").performMouseInput { moveTo(center) }
                    repeat(8) { mainClock.advanceTimeBy(16) }
                }
            }
            assertThat(ours - settled).describedAs("24 buttons, swept").isZero()
        }

        runComposeUiTest {
            mainClock.autoAdvance = false
            setContent {
                AzkarTheme(darkTheme = false, uiLocale = Locale.ENGLISH) {
                    Box(Modifier.size(600.dp).background(Color.White)) {
                        Column {
                            repeat(6) { row ->
                                Row {
                                    repeat(4) { col ->
                                        IconButton(
                                            onClick = {},
                                            modifier =
                                                Modifier
                                                    .size(40.dp)
                                                    .then(countingProbe { raw++ }),
                                        ) {
                                            Icon(AzkarIcons.Star, contentDescription = "raw-$row-$col")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            mainClock.advanceTimeBy(1_000)
            val settled = raw
            for (row in 0 until 6) {
                for (col in 0 until 3) {
                    onNodeWithContentDescription("raw-$row-$col").performMouseInput { moveTo(center) }
                    repeat(8) { mainClock.advanceTimeBy(16) }
                }
            }
            assertThat(raw - settled).describedAs("Material 3's own hover baseline").isZero()
        }
    }
}
