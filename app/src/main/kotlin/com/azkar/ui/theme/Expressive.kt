package com.azkar.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonShapes
import androidx.compose.material3.ChipShapes
import androidx.compose.material3.IconButtonShapes
import androidx.compose.material3.MenuItemShapes
import androidx.compose.material3.Shapes
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * The corner ramp every Azkar surface is cut from, as raw shapes.
 *
 * [AzkarShapes] is this list handed to `MaterialTheme`; the per-component recipes in [AzkarExpressive]
 * are built from the same values so a button's pressed silhouette and the card it sits on can never
 * drift apart. Material components ask `MaterialTheme.shapes` for a `Shape`, but they ask the
 * `*Shapes` recipes below for their *pressed* and *selected* states, and those are the ones
 * Material 3 Expressive actually moved the design language on.
 */
internal object AzkarShapeRamp {
    val extraSmall = RoundedCornerShape(8.dp)
    val small = RoundedCornerShape(12.dp)
    val medium = RoundedCornerShape(16.dp)
    val large = RoundedCornerShape(24.dp)
    val largeIncreased = RoundedCornerShape(28.dp)
    val extraLarge = RoundedCornerShape(32.dp)
    val extraLargeIncreased = RoundedCornerShape(40.dp)
    val extraExtraLarge = RoundedCornerShape(48.dp)

    /**
     * The pressed half of the morph, one step below each resting radius.
     *
     * This is the shape behaviour Expressive is actually built on: a pressed control does not fade or
     * ripple alone, it *tightens* — the corner radius pulls in toward a squarer silhouette and then
     * springs back on release. It replaces [com.azkar.ui.components.pressFeedback], which faked the
     * same moment by shrinking the whole control by 12%, an effect that moved large cards enough to
     * overlap their neighbours.
     *
     * Spelled out rather than derived from the resting radius above, because `CornerBasedShape`
     * exposes its corners as `CornerSize` and reaching through one to read a number back is a
     * round-trip through a type that exists to describe a rendering, not to be measured. The
     * relationship is ~60% and it is held to by hand: 12→8, 16→10, 24→14, 32→18.
     */
    val smallPressed = RoundedCornerShape(8.dp)
    val mediumPressed = RoundedCornerShape(10.dp)
    val largePressed = RoundedCornerShape(14.dp)
    val extraLargePressed = RoundedCornerShape(18.dp)

    /** A full round. The resting shape of every chip and icon button in the app. */
    val pill = CircleShape

    /**
     * A pill that has been squeezed.
     *
     * `CircleShape` has no smaller step to pull in to, so a chip pressed with no pressed variant
     * snaps to the next-largest corner on the ramp. That is why the chips need one and the cards
     * on this file do not.
     */
    val pillPressed = RoundedCornerShape(14.dp)
}

/**
 * The corner ramp as Material 3 sees it.
 *
 * Eight steps, not the five the Material 3 baseline defines: `largeIncreased`, `extraLargeIncreased`
 * and `extraExtraLarge` exist so a component can ask for "a bit more than `large`" without every
 * caller inventing its own value. The bottom of the ramp is left where it was — `extraSmall` stays at
 * 8dp because it only ever backs indicators and micro-chips, where more radius starts to read as a
 * pill — and the top three are where Expressive's larger silhouettes come from.
 *
 * Every slot is a [CornerBasedShape], which is what `Shapes` requires. Material 3 Expressive's own
 * shape library (`MaterialShapes` — the polygons, puffs and clamshells) is therefore *not* usable
 * here: those are `RoundedPolygon`, not `CornerBasedShape`, and they reach components one at a time
 * through a per-component `shape` parameter instead. That is the right trade for this app — a
 * polygon silhouette on every card would be the opposite of the hybrid this theme settled on — but
 * it means the expressive geometry the app *does* want arrives through the components themselves
 * (`LoadingIndicator`, the toggle morphs below, the button press) rather than through this ramp.
 */
val AzkarShapes =
    Shapes(
        extraSmall = AzkarShapeRamp.extraSmall,
        small = AzkarShapeRamp.small,
        medium = AzkarShapeRamp.medium,
        large = AzkarShapeRamp.large,
        largeIncreased = AzkarShapeRamp.largeIncreased,
        extraLarge = AzkarShapeRamp.extraLarge,
        extraLargeIncreased = AzkarShapeRamp.extraLargeIncreased,
        extraExtraLarge = AzkarShapeRamp.extraExtraLarge,
    )

/**
 * Per-state shape recipes — the part of Material 3 Expressive the app hand-rolled before.
 *
 * Each Material component asks one of these for its resting, pressed and selected silhouettes and
 * animates between them itself, so a control no longer needs a `graphicsLayer` scale standing in for
 * the press. That is the single biggest thing the Expressive adoption buys: the interaction is now
 * drawn by the component from a recipe, which means it is spring-interpolated, interruptible, and
 * identical everywhere, rather than twenty-odd copies of `pressFeedback` each with its own spring.
 *
 * Resting silhouettes are deliberately unchanged from the hand-drawn originals. Only the *pressed*
 * shapes are new, and where a control's resting and selected silhouettes coincide — every chip in
 * this app is already a pill — the recipe says so rather than inventing a morph that would move
 * something users are used to finding where they left it.
 */
object AzkarExpressive {
    /**
     * Filled and tonal buttons: `small` at rest, tightening to `smallPressed`.
     *
     * 12dp is the radius the JavaFX `.btn-primary` used, and at a 10dp vertical pad a 12dp radius
     * is very nearly a pill already — which is why the press morph has to be small to read as a
     * tightening rather than as a flash of square corners.
     */
    val buttonShapes = ButtonShapes(AzkarShapeRamp.small, AzkarShapeRamp.smallPressed)

    /**
     * The navigation tabs: `medium` at rest, tightening to `mediumPressed`.
     *
     * A step up from [buttonShapes] because these are the widest buttons in the app and the ones the
     * eye reads the silhouette of most closely — a 32dp-wide icon, a 24dp radius next to it. The
     * selected state is carried entirely by the colour ramp, so there is no third silhouette to
     * define and nothing about the tab's size changes when the destination does.
     */
    val navButtonShapes = ButtonShapes(AzkarShapeRamp.medium, AzkarShapeRamp.mediumPressed)

    /** Icon buttons are circles at rest and stay circles when pressed. */
    val iconButtonShapes = IconButtonShapes(AzkarShapeRamp.pill, AzkarShapeRamp.pill)

    /**
     * Chips.
     *
     * Both the resting and the selected silhouette are the full round these controls have always
     * been, so [ChipShapes.selectedShape] repeats the resting shape: selection is carried entirely
     * by the colour ramp, which is what it was before, and only the press morph is new.
     */
    val chipShapes =
        ChipShapes(
            shape = AzkarShapeRamp.pill,
            selectedShape = AzkarShapeRamp.pill,
            pressedShape = AzkarShapeRamp.pillPressed,
        )

    /** Menu rows. Tightens on press; the menu container around them keeps its own corner. */
    val menuItemShapes = MenuItemShapes(AzkarShapeRamp.small, AzkarShapeRamp.smallPressed)

    /**
     * The surface a full-bleed empty or loading pane is drawn on.
     *
     * Not part of the Material recipes — there is no `CardShapes` in the Expressive artifact — so it
     * is here to keep every other shape on this file answerable from one object.
     */
    val stateBoxShape: Shape = AzkarShapeRamp.medium

    /** The dialog pane, which is the largest thing the app asks for a corner on. */
    val dialogShape: Shape = AzkarShapeRamp.extraLarge
}
