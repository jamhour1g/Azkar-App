package com.azkar.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.styleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.azkar.i18n.textDirectionOf
import com.azkar.ui.theme.AzkarColors
import com.azkar.ui.theme.AzkarExpressive
import com.azkar.ui.theme.AzkarMotion
import com.azkar.ui.theme.AzkarTheme
import com.azkar.ui.theme.CardSize
import com.azkar.ui.theme.dialogEnter
import com.azkar.ui.theme.inertStyleState
import com.azkar.ui.theme.isRtlLayout

// Shared building blocks that replace the CSS classes in `com/azkar/styles/global.css`.
//
// Every composable here maps to one or more of the original style classes so the migrated
// screens keep the exact same look without any of the stylesheet plumbing.

/** How far a pressed control shrinks. Kept small so a quick tap does not read as a glitch. */
private const val PRESSED_SCALE = 0.88f

/** Opacity a pressed control fades to — a tint shift, not a fade-out. */
private const val PRESSED_TINT = 0.7f

/** Spring stiffness for the press shrink: fast enough to feel like a response, slow enough to see. */
private const val PRESSED_STIFFNESS = 700f

/**
 * `.card` — the app's general surface, and now Material 3's [Card] rather than a hand-drawn one.
 *
 * What actually changed is invisible in a still: the elevation lifts on hover, focus and press
 * instead of sitting at one fixed blur radius for ever, and the fill is resolved through
 * [CardDefaults.cardColors] rather than a raw [Color], so a card participates in the scheme's
 * surface treatment the way every other Material surface does. The silhouette is deliberately left
 * alone — [shape] still defaults to the ramp's `large` step, and [size] still maps to the three
 * paddings and elevations the stylesheet defined — so nothing moves where the user left it.
 *
 * [content] stays a plain lambda rather than a [ColumnScope] one. Callers stack their own [Row]s and
 * [Box]es inside, and widening the scope would invite `.weight()` calls that resolve against the
 * card's own column while the padding below already hands them a box to live in.
 *
 * The padding is a nested [Box] rather than a `contentPadding` argument because M3's [Card] has none;
 * putting it on the card itself would inset the border along with the content.
 */
@Composable
fun AzkarCard(
    modifier: Modifier = Modifier,
    size: CardSize = CardSize.MD,
    shape: Shape = MaterialTheme.shapes.large,
    background: Color = AzkarTheme.colors.cardBackground,
    border: BorderStroke? = BorderStroke(1.dp, AzkarTheme.colors.border),
    contentPadding: Dp = size.padding,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier,
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = background),
        elevation = CardDefaults.cardElevation(defaultElevation = size.elevation),
        border = border,
    ) {
        Box(modifier = Modifier.padding(contentPadding)) { content() }
    }
}

/**
 * [AzkarCard] for surfaces that are painted rather than filled.
 *
 * The JavaFX cards were all flat `--card-bg` rectangles, so [AzkarCard]'s single `Color` was
 * enough. The redesigned dashboard leans on gradients and artwork — a brand-coloured tile, the pale
 * wash behind the azkar card, the always-dark promotional band — and a `Color` cannot express any
 * of those. [CardDefaults.cardColors] resolves exactly one container colour, so M3's [Card] has
 * nowhere to put a second gradient stop and cannot be used here at all.
 *
 * This is also the one shared component that deliberately does *not* move to the Styles API, unlike
 * [BorderedStateBox] and [AzkarDialogSurface]. Those two have one chrome across every call site, so
 * there is a single [Style] to define. This one is a family: the hero uses a 20dp radius with no
 * elevation and no padding, the rail cards use 32dp with 12dp of padding, the azkar card 32dp with
 * 20dp. A shared style here would either flatten those into one silhouette or push a `Style` literal
 * into each of the eleven call sites, which is the migration the API is meant to remove. The shadow
 * has to trace the same outline as the fill, so the shape cannot be decided behind a modifier the
 * caller cannot see.
 *
 * The content scope is a [BoxScope] rather than the plain lambda [AzkarCard] uses, because every
 * painted card here needs a watermark or a photo pinned to an edge behind its text.
 */
@Composable
fun AzkarSurfaceCard(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.extraLarge,
    background: Brush,
    elevation: Dp = CardSize.MD.elevation,
    border: BorderStroke? = BorderStroke(1.dp, AzkarTheme.colors.border),
    contentPadding: Dp = CardSize.MD.padding,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier =
            modifier
                .shadow(
                    elevation = elevation,
                    shape = shape,
                    ambientColor = Color.Black.copy(alpha = 0.15f),
                    spotColor = Color.Black.copy(alpha = 0.15f),
                ).clip(shape)
                .background(background)
                .then(border?.let { Modifier.border(it, shape) } ?: Modifier)
                .padding(contentPadding),
    ) {
        content()
    }
}

/**
 * A horizontal gradient that runs from the padded edge towards the illustrated edge.
 *
 * Every painted surface on the dashboard is composed for one direction: a scrim or a wash starts
 * near the leading edge, where the text sits, and fades out towards the trailing edge where the
 * artwork is. `Brush.horizontalGradient` works in absolute pixels, so a fixed direction would leave
 * the gradient sitting behind the artwork in Arabic and behind the text in English. Reversing the
 * stop list — rather than the gradient's `startX`/`endX` — is what keeps the composition identical
 * under both layout directions.
 *
 * @param colors the stops in LTR order, leading edge first.
 */
fun mirrorHorizontalGradient(
    colors: List<Color>,
    isRtl: Boolean,
): Brush = Brush.horizontalGradient(if (isRtl) colors.reversed() else colors)

/**
 * `.btn-primary` — the app's filled call to action, and now Material 3's [Button].
 *
 * The hand-drawn version drew its own press response: `indication = null` plus a whole-control scale
 * down to [PRESSED_SCALE] on a spring. Material 3 Expressive replaced that idea with something better
 * and this is the swap. [ButtonShapes] carries a resting and a *pressed* silhouette, so the press
 * signal is the corner geometry tightening rather than the whole control shrinking — which is what
 * was wanted, since a twelve percent shrink of a long label moves the button's own edges and reads as
 * a layout change. The ripple that used to be switched off here comes back for free.
 *
 * The resting silhouette and the padding are M3's own, so a button is now 40dp tall with 24dp of
 * side padding rather than the 20/10 the stylesheet asked for. That is the Expressive opinion being
 * adopted rather than imitated; every other surface in the app has already been sized to match it.
 *
 * Colours are still read from [AzkarTheme.colors] rather than [MaterialTheme.colorScheme], because the
 * app has a palette of its own and `toMaterialScheme` is only a one-way bridge.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    fontSize: TextUnit = 14.sp,
) {
    val colors = AzkarTheme.colors
    Button(
        onClick = onClick,
        // Ahead of nothing in particular: the shapes slot is where the press morph now lives, so the
        // caller needs no way to see or bypass it.
        shapes = AzkarExpressive.buttonShapes,
        modifier = modifier,
        enabled = enabled,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = colors.primary,
                contentColor = colors.onPrimary,
                disabledContainerColor = colors.primary.copy(alpha = 0.4f),
                disabledContentColor = colors.onPrimary,
            ),
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = text,
            fontSize = fontSize,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * `.btn-secondary` — the quieter sibling of [PrimaryButton], and now Material 3's [OutlinedButton].
 *
 * The original drew a card-coloured fill under a one pixel border. That pairing has no Material
 * equivalent worth copying — M3's outlined button is fill-less — so the fill stays, supplied as the
 * button's `containerColor`, because on this app's dashboard a border alone disappears against the
 * pale washes these sit on. Everything that *was* hand-drawn is gone: the press scale is now the same
 * [AzkarExpressive.buttonShapes] corner morph [PrimaryButton] uses, the ripple is back, and the
 * disabled state is the ripple and colour system's answer rather than a whole-control `alpha(0.5f)`
 * that used to fade the border along with the label.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /**
     * An optional glyph before the label, for the buttons whose label names a file or an object
     * rather than an action ("Choose image…" next to a picture frame, say) and would otherwise be the
     * only text on the row.
     *
     * Deliberately not given a tint of its own — it inherits the button's content colour, exactly as
     * [PrimaryButton]'s leading icon does, so a glyph can never disagree with the label about
     * whether the button is enabled.
     */
    leadingIcon: ImageVector? = null,
) {
    val colors = AzkarTheme.colors
    OutlinedButton(
        onClick = onClick,
        shapes = AzkarExpressive.buttonShapes,
        modifier = modifier,
        enabled = enabled,
        colors =
            ButtonDefaults.outlinedButtonColors(
                containerColor = colors.cardBackground,
                contentColor = colors.textSecondary,
                disabledContainerColor = colors.cardBackground,
                disabledContentColor = colors.textSecondary.copy(alpha = 0.5f),
            ),
        border = BorderStroke(1.dp, if (enabled) colors.border else colors.border.copy(alpha = 0.5f)),
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

/**
 * `.nav-btn` + `.nav-btn-active` — header navigation and the favourites prev/next pair.
 *
 * Drawn as a [FilledTonalButton] whose colours carry the whole selected/unselected distinction.
 * Material 3 has a real [ToggleButton] here and it was the obvious choice, but its checked state owns
 * the label colour as well as the container, and the header tabs need `primaryStrong` on `highlight`
 * for a contrast reason — the label sits *on* the highlight and owes small text 4.5:1, which `primary`
 * does not clear. Borrowing the container but colouring the content by hand keeps that decision where
 * it was made.
 *
 * [leadingIcon] is the icon-first half of the header's tabs. Its tint is left to the button's content
 * colour rather than named, so the glyph and the label can never disagree about whether the tab is
 * the current one.
 *
 * The elevation is pinned flat, and that is the whole of the fix for a bug this component shipped
 * with for a while: a grey pill under the pointer, in light mode only. [FilledTonalButton] raises its
 * tonal elevation on hover, [Surface] resolves a container colour through `surfaceColorAtElevation`
 * once that elevation is above zero, and surface tint is applied in light themes only. A transparent
 * container therefore silently becomes a filled one the instant the pointer arrives. Every other
 * control here passes an opaque container, which is why it never showed up anywhere else — the
 * resting state is what makes a control vulnerable, not the component type.
 *
 * It is worth recording what this was *not*, because the obvious guesses all look right and none of
 * them are: the ripple's hovered state layer, and the `LocalIndication`/`LocalRippleConfiguration`
 * pair that controls it. Both were switched off and the grey stayed. `ButtonColors` has no
 * `hoveredContainerColor` to set in the first place, and the ripple falls back to
 * [androidx.compose.material3.LocalContentColor] for its base colour, which here is the label
 * itself — which is exactly why the hover layer alone would have tinted the tab near-black and why
 * that theory looked so convincing. It was never the cause.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NavButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
) {
    val colors = AzkarTheme.colors
    // Still animated, still by hand: M3's tonal button has no two-tone "selected" story to borrow,
    // and the header's tabs changing destination should slide rather than snap.
    val containerColor by
        animateColorAsState(
            targetValue = if (selected) colors.highlight else Color.Transparent,
            label = "navButtonContainer",
        )
    // primaryStrong rather than primary: this label sits *on* `highlight`, so it has to clear the
    // 4.5:1 that small text owes, and `primary` is a fill colour that does not.
    val contentColor by
        animateColorAsState(
            targetValue = if (selected) colors.primaryStrong else colors.textPrimary,
            label = "navButtonContent",
        )
    FilledTonalButton(
        onClick = onClick,
        shapes = AzkarExpressive.navButtonShapes,
        modifier = modifier,
        colors =
            ButtonDefaults.filledTonalButtonColors(
                containerColor = containerColor,
                contentColor = contentColor,
                disabledContainerColor = Color.Transparent,
                disabledContentColor = colors.textPrimary,
            ),
        elevation =
            ButtonDefaults.filledTonalButtonElevation(
                defaultElevation = 0.dp,
                pressedElevation = 0.dp,
                focusedElevation = 0.dp,
                hoveredElevation = 0.dp,
                disabledElevation = 0.dp,
            ),
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                modifier = Modifier.size(17.dp),
            )
            // `Button`'s content is a `RowScope`, and in this alpha the row applies no spacing of
            // its own — the hand-drawn version got its 8dp gap from `Arrangement.spacedBy`, which
            // the lambda has no way to reach. Without this the glyph sits flush against the label.
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

/**
 * An alpha that follows the pointer over [interactionSource], between [resting] and [hovered].
 *
 * It returns the [Animatable] rather than its value, and that is the entire point: callers read
 * `.value` inside a `drawBehind` block, which is deferred to the draw phase, so a hover costs one
 * draw invalidation instead of a composition per animation frame. Reading it at composition instead —
 * through a `by animateColorAsState` delegate, as these controls used to — is the defect
 * `RecompositionSuite` exists to catch.
 *
 * A disabled control holds [resting] and does not collect at all, so a pointer already resting on a
 * control that is then disabled settles rather than staying stuck at its hovered value.
 */
@Composable
private fun rememberHoverAlpha(
    interactionSource: MutableInteractionSource,
    resting: Float,
    hovered: Float,
    enabled: Boolean = true,
): Animatable<Float, AnimationVector1D> {
    val alpha = remember { Animatable(resting) }
    val spec = tween<Float>(AzkarMotion.QUICK)
    LaunchedEffect(interactionSource, resting, hovered, enabled) {
        if (!enabled) {
            alpha.snapTo(resting)
            return@LaunchedEffect
        }
        // The targets changed while nothing was hovering. The collect below only wakes on hover
        // events, so without landing here first the alpha would sit at the previous target — which
        // is how toggling `favorite` used to leave the dot invisible until the next hover.
        if (alpha.value != hovered) alpha.snapTo(resting)
        interactionSource.interactions.collect { event ->
            when (event) {
                is HoverInteraction.Enter -> alpha.animateTo(hovered, spec)
                is HoverInteraction.Exit -> alpha.animateTo(resting, spec)
                else -> Unit
            }
        }
    }
    return alpha
}

/**
 * `.icon-action-btn` — circular transparent header action with a hover highlight.
 *
 * Now Material 3's [IconButton]. The 36dp box was already the size this draws at, and M3's ripple
 * covers the whole circle rather than just the glyph — which is what the hand-drawn version had to
 * argue for in a comment, since it paired a scale on the glyph with a fill on the circle and the two
 * could disagree about what the pointer had touched. The shape recipe keeps the circle a circle;
 * there is no pressed variant to pull in to.
 *
 * The hover fill stays hand-computed rather than handed to Material's state layer. This control has a
 * *selected* state that claims the same slot hover used to have, and the two rules — selected wins,
 * hover only when not — are the whole of the original behaviour, so they are kept verbatim.
 *
 * **The fill is drawn, not composed.** As with [CircleIconButton], the fill is a `drawBehind` circle
 * behind a transparent container rather than a colour argument the `by` delegate re-reads. The two
 * states get one alpha each so that selecting while hovered crossfades between them instead of
 * snapping, and they are drawn in the order that preserves "selected wins": hover first, selected on
 * top.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun IconActionButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    iconSize: Dp = 20.dp,
    tint: Color = AzkarTheme.colors.textPrimary,
    enabled: Boolean = true,
) {
    val colors = AzkarTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val selectedAlpha = remember { Animatable(if (selected) 1f else 0f) }
    // Selected claims the slot hover used to have, so a selected control does not collect hover at all.
    val hoverAlpha = rememberHoverAlpha(interaction, resting = 0f, hovered = 1f, enabled = !selected)
    LaunchedEffect(selected) {
        selectedAlpha.animateTo(if (selected) 1f else 0f, tween(AzkarMotion.QUICK))
    }
    Box(
        modifier
            .size(36.dp)
            .drawBehind {
                val radius = size.minDimension / 2f
                hoverAlpha.value.let { if (it > 0.001f) drawCircle(colors.highlight, radius, alpha = it) }
                selectedAlpha.value.let { if (it > 0.001f) drawCircle(colors.primary, radius, alpha = it) }
            },
    ) {
        IconButton(
            onClick = onClick,
            shapes = AzkarExpressive.iconButtonShapes,
            modifier = Modifier.fillMaxSize(),
            enabled = enabled,
            interactionSource = interaction,
            colors =
                IconButtonDefaults.iconButtonColors(
                    containerColor = Color.Transparent,
                    contentColor = if (selected) colors.onPrimary else tint,
                ),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(iconSize),
            )
        }
    }
}

/**
 * The press feedback the JavaFX `:pressed` pseudo-class used to supply for free.
 *
 * Every interactive control here passes `indication = null` so the flat, hand-drawn look of the
 * original stylesheet survives, which also threw away the press state along with the ripple. Hover
 * was rebuilt by hand on the components that needed it, but nothing reacted to a mouse-down, so
 * clicking an icon looked identical to hovering it. This restores it once, in a single place, as a
 * slight shrink plus a tint.
 *
 * Apply it to whatever the user perceives as the button — the glyph for an icon button, the whole
 * thumb for a switch. Note that `graphicsLayer` only scales the modifiers chained *after* it, so
 * placing it after `.background(..)` gives a stable surface with a moving glyph, which is the
 * subtler of the two readings. Pass `scale = 1f` where geometry should not move at all, such as a
 * text field that a click is about to focus.
 *
 * **The feedback is driven, not declared.** This was an `animateFloatAsState` pair, which reads its
 * value at composition, so every frame of the press re-ran the modifier's caller. With this on
 * roughly fifty surfaces that is a lot of recomposition for an effect nobody can see frame to frame.
 * The two [Animatable]s are now driven from the interaction stream and read inside the
 * `graphicsLayer` block, which is deferred to the draw phase — so a press costs one draw
 * invalidation rather than a composition per frame. Behaviour is unchanged: the scale springs and
 * the tint fades, and `scale = 1f` still skips the geometry entirely.
 */
@Composable
fun Modifier.pressFeedback(
    interactionSource: MutableInteractionSource,
    scale: Float = PRESSED_SCALE,
): Modifier {
    val scaleAnim = remember { Animatable(1f) }
    val tintAnim = remember { Animatable(1f) }
    val pressSpec = remember { spring<Float>(dampingRatio = 0.6f, stiffness = PRESSED_STIFFNESS) }
    LaunchedEffect(interactionSource, scale) {
        interactionSource.interactions.collect { event ->
            when (event) {
                is PressInteraction.Press -> {
                    if (scale != 1f) scaleAnim.animateTo(scale, pressSpec)
                    tintAnim.animateTo(PRESSED_TINT, AzkarMotion.instant())
                }
                is PressInteraction.Release -> {
                    if (scale != 1f) scaleAnim.animateTo(1f, pressSpec)
                    tintAnim.animateTo(1f, AzkarMotion.instant())
                }
                is PressInteraction.Cancel -> {
                    if (scale != 1f) scaleAnim.animateTo(1f, pressSpec)
                    tintAnim.animateTo(1f, AzkarMotion.instant())
                }
                else -> Unit
            }
        }
    }
    return graphicsLayer {
        scaleX = scaleAnim.value
        scaleY = scaleAnim.value
        alpha = tintAnim.value
    }
}

/**
 * [pressFeedback] and the matching `clickable` in one call, for the click targets that are drawn
 * locally on a screen rather than built from the controls above.
 *
 * Roughly a dozen surfaces act as buttons — a shelf card, a rail card, a language row, a link — and
 * each one had been reaching for bare `clickable`, which is the same defect [pressFeedback] exists
 * to fix. Folding the two modifiers together also removes the failure mode where they disagree:
 * press feedback driven by one interaction source while the click comes from another animates on a
 * press that never reaches the handler.
 *
 * Pass the same [MutableInteractionSource] that drives any hover state on the surface so the two
 * compose on one source rather than competing for the press.
 *
 * [scale] is worth thinking about per call site. Button-sized targets want the default shrink;
 * large surfaces — a card three hundred pixels wide — should pass `scale = 1f` and keep only the
 * tint, because a twelve percent shrink of something that size moves its edges far enough to read
 * as a layout change and to overlap whatever sits next to it.
 */
@Composable
fun Modifier.pressableClick(
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    enabled: Boolean = true,
    scale: Float = PRESSED_SCALE,
    onClick: () -> Unit,
): Modifier =
    pressFeedback(interactionSource, scale).clickable(
        interactionSource = interactionSource,
        indication = null,
        enabled = enabled,
        onClick = onClick,
    )

/**
 * `.icon-btn` / `.icon-circle-btn` / `.library-icon-btn` — small round action used in cards.
 *
 * Material 3's [IconButton] at this control's own 28dp rather than the 40dp the default asks for.
 * These sit inside dense card rows where a 40dp target would break the layout they are placed in,
 * so the size stays; what changes is that the ripple is now drawn across the whole target instead of
 * only the glyph. That was the one deliberate oddity in the hand-drawn version — [pressFeedback]
 * applied to the icon so the *glyph* shrank while the circle underneath stayed put — and M3 says it
 * was wrong in a way that is easy to agree with.
 *
 * The `background` escape hatch and the hover/favourite precedence below are unchanged. The
 * precedence in particular is worth keeping verbatim: hover has to outrank [favorite], or a
 * favourited button pins its background to the accent tint and stops acknowledging the pointer.
 *
 * **The fill is drawn, not composed.** This was the one place the M3 swap quietly cost real work.
 * Hovering used to be an `animateColorAsState` read at composition, which means every frame of the
 * fade re-ran this function *and* the whole `IconButton` beneath it. A sweep across a 24-button grid
 * measured 324 recompositions against a baseline of **zero** — the same grid of raw Material 3
 * `IconButton`s with a static colour recomposes not at all, because M3's own hover is a draw-phase
 * ripple layer. So the animation is driven from the interaction source's `interactions` flow, and
 * the colour is read inside `drawBehind`; hover now costs one draw-phase invalidation rather than a
 * dozen compositions, and a `favorite` toggle is the only thing that recomposes at all.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CircleIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    buttonSize: Dp = 28.dp,
    iconSize: Dp = 12.dp,
    favorite: Boolean = false,
    background: Color? = null,
    tint: Color = if (favorite) AzkarTheme.colors.accent else AzkarTheme.colors.textSecondary,
) {
    val colors = AzkarTheme.colors
    val interaction = remember { MutableInteractionSource() }
    // The fill is a colour *and* an opacity, and only the opacity ever animates: `favorite` picks the
    // hue, hover moves the alpha. Animating the pair as a `Color` would need a vector converter this
    // artifact does not ship, and splitting it keeps the hue a plain captured value the draw phase
    // can read without ever invalidating composition.
    val fillColor = if (favorite) colors.accent else colors.highlight
    val restingAlpha = if (favorite) 0.18f else 0f
    // A supplied `background` is the whole story and must not be animated — the original read it as
    // a plain override that simply outranked everything below.
    val fillAlpha =
        rememberHoverAlpha(
            interactionSource = interaction,
            resting = restingAlpha,
            hovered = if (favorite) 0.32f else 1f,
            enabled = enabled && background == null,
        )

    Box(
        modifier
            .size(buttonSize)
            .drawBehind {
                val radius = size.minDimension / 2f
                val override = background
                when {
                    override != null ->
                        if (override.alpha > 0f) drawCircle(override, radius = radius)
                    else -> {
                        val alpha = fillAlpha.value
                        if (alpha > 0.001f) drawCircle(fillColor, radius = radius, alpha = alpha)
                    }
                }
            },
    ) {
        IconButton(
            onClick = onClick,
            shapes = AzkarExpressive.iconButtonShapes,
            modifier = Modifier.fillMaxSize(),
            enabled = enabled,
            interactionSource = interaction,
            colors =
                IconButtonDefaults.iconButtonColors(
                    containerColor = Color.Transparent,
                    contentColor = tint.copy(alpha = if (enabled) 1f else 0.4f),
                    disabledContentColor = tint.copy(alpha = 0.4f),
                ),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(iconSize),
            )
        }
    }
}

/**
 * The one tooltip the app draws, and every control that wants one goes through here.
 *
 * **The container is the whole point.** [TooltipBox]'s `tooltip` slot *replaces* Material's default
 * content rather than decorating it, so a bare `Text` in that slot gets no surface, no padding, no
 * corner radius, no shadow and no caret — it is floating text. Which is what this used to be, in
 * `onPrimary`: white type over a white page in the light palette. [PlainTooltip] is the piece that
 * was missing, and it is a one-word change for a chip that reads as a chip.
 *
 * **No colour is named here.** [PlainTooltip] defaults to the scheme's inverse pair, and
 * [toMaterialScheme] projects that pair onto [AzkarColors.textPrimary]/[AzkarColors.cardBackground],
 * so the tooltip inverts with the theme — dark chip on the light palette, light chip on the dark one
 * — without this file knowing anything about it. A tooltip sits on cards, artwork and hover fills
 * alike, which is precisely why it cannot be drawn from the page's own colours.
 *
 * **The text comes from the type scale** rather than a literal. `PlainTooltip` sets `LocalTextStyle`
 * to Material's own supporting-text token, which is Roboto — in an app that ships Amiri for Arabic
 * that is a tooltip switching to a second typeface mid-sentence, so [AzkarTheme.typography] is named
 * explicitly and gets Inter or Amiri to match its surroundings.
 *
 * [TooltipAnchorPosition.Above] is a *preference*, not a placement: Material flips the chip below the
 * anchor when there is no room above, which is the difference between a tooltip and a tooltip that
 * renders off the top of the window.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AzkarTooltip(
    tooltip: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    // The caller's modifier goes on a box that *wraps* [TooltipBox], and that placement is
    // load-bearing rather than incidental. `TooltipBox` buries its anchor two boxes deep — one it
    // adds for the popup, one for the gesture handler — so anything with parent data (`align`,
    // `weight`, `matchParentSize`) ends up read by a box the caller never wrote, and silently does
    // nothing. The control then falls back to its parent's `contentAlignment`, which is how the
    // settings gear came to sit in the middle of the header on top of the library tab: the header is
    // a `Box` with `contentAlignment = Center`, and the gear's `align(CenterEnd)` was being read by
    // the wrong box entirely.
    //
    // Wrapping puts this box directly in the caller's scope, where `align(CenterEnd)` means what the
    // caller wrote. Passing it to `TooltipBox` instead would look equivalent and is not, and the
    // failure is silent either way — the gear stays present, enabled, clickable and correctly named,
    // and only its position is wrong.
    Box(modifier = modifier) {
        TooltipBox(
            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
            tooltip = {
                PlainTooltip {
                    Text(
                        text = tooltip,
                        style = AzkarTheme.typography.labelMedium,
                    )
                }
            },
            state = rememberTooltipState(),
            modifier = Modifier,
        ) {
            content()
        }
    }
}

/**
 * [CircleIconButton] with a hover tooltip naming it — the small round action used inside cards.
 *
 * [tooltip] doubles as the button's `contentDescription` because for a glyph there is nothing else it
 * could say, and a control that announced one string to a screen reader and another to a mouse would
 * be describing two different buttons.
 *
 * [tint] is here because [CircleIconButton] has it: the favourites pager draws its chevrons in
 * [AzkarColors.favoritesNavText] rather than the default grey, and dropping the parameter is how that
 * call site got left without a tooltip in the first place.
 */
@Composable
fun TooltipIconButton(
    icon: ImageVector,
    tooltip: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    buttonSize: Dp = 28.dp,
    iconSize: Dp = 12.dp,
    favorite: Boolean = false,
    background: Color? = null,
    tint: Color = if (favorite) AzkarTheme.colors.accent else AzkarTheme.colors.textSecondary,
) {
    AzkarTooltip(tooltip = tooltip, modifier = modifier) {
        CircleIconButton(
            icon = icon,
            contentDescription = tooltip,
            onClick = onClick,
            enabled = enabled,
            buttonSize = buttonSize,
            iconSize = iconSize,
            favorite = favorite,
            background = background,
            tint = tint,
        )
    }
}

/**
 * [IconActionButton] with a hover tooltip — the 36dp header action, such as the settings gear.
 *
 * A sibling of [TooltipIconButton] rather than a parameter on it, for the reason the two are separate
 * components in the first place: they draw at different sizes, settle hover against different states
 * and take different tints, and a tooltip is an orthogonal wrapper rather than a variant of either
 * one. A control that already carries its label — a [PrimaryButton], a [PillToggle] — gets no
 * tooltip at all; a glyph is a label to whoever already knows it and a puzzle to everybody else, and
 * the chip is how the control says its name without spending the width on it.
 */
@Composable
fun TooltipIconActionButton(
    icon: ImageVector,
    tooltip: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    iconSize: Dp = 20.dp,
    tint: Color = AzkarTheme.colors.textPrimary,
    enabled: Boolean = true,
) {
    AzkarTooltip(tooltip = tooltip, modifier = modifier) {
        IconActionButton(
            icon = icon,
            contentDescription = tooltip,
            onClick = onClick,
            selected = selected,
            iconSize = iconSize,
            tint = tint,
            enabled = enabled,
        )
    }
}

/**
 * `.toggle-switch` — the ControlsFX switch, and now Material 3's [Switch].
 *
 * Everything this control had to build by hand is the part [Switch] gives back for free: the thumb
 * travel, the press response, the focus ring, and the hover highlight on the track that used to need
 * its own `collectIsHoveredAsState`. Only the two track colours and the thumb colour are named here,
 * and they are named through [AzkarTheme.colors] rather than the colour scheme.
 *
 * The switch is also physically bigger — 52 by 32 rather than 44 by 24. That is Material's opinion
 * and it is the right one to defer to: the old track was below the minimum comfortable size for a
 * pointer-driven target, and every settings row in the app has room to give it.
 */
@Composable
fun AzkarToggleSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = AzkarTheme.colors
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        colors =
            SwitchDefaults.colors(
                checkedThumbColor = colors.switchThumb,
                checkedTrackColor = colors.primary,
                checkedBorderColor = colors.switchThumbBorder,
                uncheckedThumbColor = colors.switchThumb,
                uncheckedTrackColor = colors.switchTrackOff,
                uncheckedBorderColor = colors.switchThumbBorder,
            ),
    )
}

/**
 * `.library-filter-chip` / `.library-dialog-toggle` — pill used for view modes and toggles.
 *
 * Material 3's [FilterChip] is the right control for this, but only its *selected* colour is worth
 * taking. Unselected, this pill is a label sitting on the page, and Material's default gives an
 * unselected chip a `ColorScheme.surfaceVariant` fill — a grey lozenge on a white toolbar, which
 * turns a row of quiet toggles into a row of buttons. So the unselected fill is set back to
 * transparent and only [AzkarColors.highlight] is allowed to say "this one is on".
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PillToggle(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
) {
    val colors = AzkarTheme.colors
    FilterChip(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        shapes = AzkarExpressive.chipShapes,
        label = { Text(text = text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) },
        leadingIcon =
            leadingIcon?.let {
                {
                    Icon(
                        imageVector = it,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                    )
                }
            },
        colors =
            FilterChipDefaults.filterChipColors(
                containerColor = Color.Transparent,
                labelColor = colors.textSecondary,
                iconColor = colors.textSecondary,
                selectedContainerColor = colors.highlight,
                selectedLabelColor = colors.primary,
                selectedLeadingIconColor = colors.primary,
            ),
    )
}

/**
 * `.library-tab-chip` — a solid "selected" pill used by the library filter row.
 *
 * [PillToggle] only tints its background, which reads as a hover affordance. The tab pills above
 * a data table need the chosen one to be unmistakable, so the selected state fills with the theme
 * primary colour and flips to its on-primary colour, while the rest stay quiet.
 *
 * Same component as [PillToggle] and the same reason for the transparent unselected fill — the two
 * differ only in what the selected state looks like. That was two hand-drawn `Row`s before; it is
 * now two colour arguments.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AzkarFilterChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val colors = AzkarTheme.colors
    FilterChip(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shapes = AzkarExpressive.chipShapes,
        label = { Text(text = text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) },
        leadingIcon =
            leadingIcon?.let {
                {
                    Icon(
                        imageVector = it,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                    )
                }
            },
        colors =
            FilterChipDefaults.filterChipColors(
                containerColor = Color.Transparent,
                labelColor = colors.textSecondary,
                iconColor = colors.textSecondary,
                selectedContainerColor = colors.primary,
                selectedLabelColor = colors.onPrimary,
                selectedLeadingIconColor = colors.onPrimary,
            ),
    )
}

/**
 * `.row-overflow-menu` — the "…" affordance that hides row actions that do not fit inline.
 *
 * Follows the [AzkarSelect] trick: the visible button stays inert while a transparent overlay
 * `Box` owns the click, so opening the popup never fights the button's own press feedback.
 *
 * The trigger is an [IconButton] and the popup is Material 3's [DropdownMenu], but the overlay is
 * the part that had to survive: Material's menu anchors to and steals focus from the composable
 * that owns it, and the whole reason the visible button is inert is that a button which both
 * reacts to a press *and* summons a popup underneath itself fires twice. That is unchanged.
 *
 * The popup's own chrome is left to Material. It used to carry a hand-drawn 1dp border and a 4dp
 * inset, which is what a flat popup looked like before menus were given elevation; Material now
 * gives it a tonal fill over a shadow, and [toMaterialScheme]'s `surfaceContainer` already points
 * at [AzkarColors.cardBackground], so the palette lands without anyone naming a colour here.
 *
 * The trigger is a [TooltipIconButton] and the popup is a [TooltipBox] sibling of it, which is the
 * one place the two overlays meet: an ellipsis is the least self-explaining glyph in the app, and it
 * is the one whose contents are hidden — so it is the control that most needs to say "more actions"
 * before the click rather than after it. The tooltip sits on the *anchor* rather than over the menu,
 * so opening the menu does not summon a second popup underneath the first.
 */
@Composable
fun AzkarOverflowMenu(
    icon: ImageVector,
    contentDescription: String,
    items: List<AzkarMenuItem>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    buttonSize: Dp = 28.dp,
    iconSize: Dp = 12.dp,
    background: Color? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    val catcher = remember { MutableInteractionSource() }
    AzkarTooltip(tooltip = contentDescription, modifier = modifier) {
        Box {
            CircleIconButton(
                icon = icon,
                contentDescription = contentDescription,
                onClick = {},
                enabled = enabled,
                buttonSize = buttonSize,
                iconSize = iconSize,
                background = background,
            )
            if (enabled) {
                Box(
                    modifier =
                        Modifier
                            .matchParentSize()
                            .clip(CircleShape)
                            .clickable(interactionSource = catcher, indication = null) { expanded = true },
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                items.forEach { item ->
                    AzkarMenuItemRow(item = item, onDismiss = { expanded = false })
                }
            }
        }
    }
}

/**
 * `.library-search-wrap` / `.library-collection-picker` / `.library-dialog-input` /
 * `.library-reminder-input` — the app's one text field, now a Material 3 `OutlinedTextField`.
 *
* [minHeight] is a floor, and is applied as one. The hand-drawn field this replaced was
 * `heightIn(min = 40.dp)`, so treating the parameter as a minimum rather than a fixed height is what
 * its name has always meant here, and the two call sites that pass more (72dp, 110dp) are multi-line
 * fields that need to grow past their minimum anyway. An earlier version of this used `height` on the
 * single-line branch, which did work — Compose clamps a child's minimum against the incoming maximum,
 * so the library search field really did measure 40dp — but it made the same argument mean two
 * different things depending on `singleLine`, which is the kind of surprise a caller cannot see.
 *
 * The cost is that the dense desktop toolbars get Material's own 56dp rather than the 40dp they had
 * before. That is the right trade: 56dp is the height Material 3 sizes this component for, and the
 * toolbar rows read as balanced with it.
 *
 * [active] draws the focus ring for a control that owns its own engaged state. A dropdown never
 * takes focus — its click target is an overlay `Box`, not this text input — so without it the
 * picker gives no sign of being open. It is reproduced on the *unfocused* colours rather than by
 * forcing focus, which would have meant a second interaction source and a focus ring on a control
 * the user cannot tab into.
 *
 * Two smaller things did not survive the swap, and are worth saying plainly rather than leaving a
 * future reader to wonder. The resting border is now 2dp where the hand-drawn field drew 1dp:
 * Material picks its outline width from a private constant, sized for a touch target on a phone, and
 * derives the stroke as the larger of the focused and unfocused values, so it draws 2dp in both
 * states and offers no public way to ask for less. [active] is therefore now a change of *colour*
 * over an unchanged width. The inner padding moved with it, from 10dp/6dp to Material's 16dp/8dp,
 * for the same reason: `contentPadding` is only a parameter on the state-based overload, and
 * switching to that would have meant rewriting every one of these fields around a `TextFieldState`
 * the app has no reason to own.
 */
@Composable
fun AzkarOutlinedField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    leadingIcon: ImageVector? = null,
    fontSize: TextUnit = 13.sp,
    minHeight: Dp = 40.dp,
    active: Boolean = false,
    trailingContent: (@Composable () -> Unit)? = null,
    /**
     * A quiet line under the field saying what the field expects, rather than what it holds.
     *
     * Separate from [placeholder] because the placeholder disappears the moment there is anything typed
     * — which is exactly when a field with a non-obvious format needs its hint most. A comma-separated
     * label list is the case: by the third character the placeholder is gone and the user is left
     * guessing whether commas are wanted.
     */
    supportingText: String? = null,
    /**
     * How many lines of text this field may hold, for the fields that are deliberately shorter than a
     * free-growing box.
     *
     * Defaulted to unbounded so that every field which does not pass this keeps whatever limit Material
     * would have given it if the parameter did not exist. The one caller that does pass it is the
     * collection description, which fixes a height so that a character counter can sit in the bottom
     * corner of the field without landing on the last line of the text it is counting.
     */
    maxLines: Int = Int.MAX_VALUE,
    /**
     * Which way the text inside this field should read, overriding the direction the interface would
     * otherwise give it.
     *
     * The interface's direction is the right one for anything the app wrote, and wrong for anything
     * the user wrote: a person with an English interface who names a collection in Arabic wants the
     * caret on the right and the text growing leftwards, and without this they get an Arabic name
     * trailing off the right edge of a left-to-right field. See [textDirectionOf] for how the answer
     * is worked out.
     *
     * Left null — which is what every field holding no user-authored text passes — the field keeps
     * whatever `AzkarTheme` set, so the override reaches only the fields that ask for it. It is
     * carried on the [TextStyle] rather than by wrapping the field in a `LocalLayoutDirection`, because
     * the theme provides that locally and any value a caller supplied around the field would be
     * silently discarded on the way down ([AzkarThemePreview] records the same trap for previews).
     */
    textDirection: LayoutDirection? = null,
    /**
     * Keyboard configuration for fields that need one — a different input type, or an action key.
     *
     * Passed straight through rather than given parameters of its own, because every one of those
     * would be one more thing this component had to know about keyboards to keep in step with
     * Compose. Null leaves Material's own defaults, which is what all but one field wants.
     */
    keyboardOptions: KeyboardOptions? = null,
    enabled: Boolean = true,
) {
    val colors = AzkarTheme.colors
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.heightIn(min = minHeight),
        enabled = enabled,
        singleLine = singleLine,
        maxLines = maxLines,
        keyboardOptions = keyboardOptions ?: KeyboardOptions.Default,
        textStyle =
            TextStyle(
                color = colors.textPrimary,
                fontSize = fontSize,
                textDirection = textDirection.toTextDirection(),
            ),
        placeholder = { Text(text = placeholder, fontSize = fontSize) },
        leadingIcon =
            leadingIcon?.let {
                { Icon(imageVector = it, contentDescription = null, modifier = Modifier.size(14.dp)) }
            },
        trailingIcon =
            trailingContent?.let {
                {
                    Spacer(Modifier.width(6.dp))
                    it()
                }
            },
        supportingText = supportingText?.let { { FieldHint(it, colors) } },
        shape = AzkarExpressive.stateBoxShape,
        colors =
            OutlinedTextFieldDefaults.colors(
                focusedTextColor = colors.textPrimary,
                unfocusedTextColor = colors.textPrimary,
                focusedContainerColor = colors.cardBackground,
                unfocusedContainerColor = colors.cardBackground,
                focusedBorderColor = colors.primary,
                unfocusedBorderColor = if (active) colors.primary else colors.border,
                focusedLeadingIconColor = colors.textSecondary,
                unfocusedLeadingIconColor = colors.textSecondary,
                focusedTrailingIconColor = colors.textSecondary,
                unfocusedTrailingIconColor = colors.textSecondary,
                cursorColor = colors.primary,
            ),
    )
}

/**
 * This direction as the one a [TextStyle] carries, with `null` becoming [TextDirection.Content].
 *
 * `Content` is the meaningful null: it defers to whatever layout direction is already in effect,
 * which is what a field with no override wants. Keeping this conversion to one place means the rest
 * of the app can talk in [LayoutDirection] — the vocabulary [LocalLayoutDirection] and
 * [isRtlLayout] already use — without every caller having to know there are two similar enums.
 */
private fun LayoutDirection?.toTextDirection(): TextDirection =
    when (this) {
        LayoutDirection.Ltr -> TextDirection.Ltr
        LayoutDirection.Rtl -> TextDirection.Rtl
        null -> TextDirection.Content
    }

@Composable
private fun FieldHint(
    text: String,
    colors: AzkarColors,
) {
    Text(
        text = text,
        color = colors.textSecondary,
        fontSize = 11.sp,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
    )
}

/**
 * `.library-tabs-pane` — the segmented tab strip that replaced the JavaFX `TabPane`, now a Material 3
 * [PrimaryTabRow].
 *
 * The indicator is the one place this swap deliberately changes how the control *reads*. The strip
 * used to underline each label with a hand-measured 2dp bar, which meant estimating text width in
 * character units and re-deriving the offset on every selection change; Material's indicator is a
 * pill that stretches across the whole tab and slides between them on its own. So the selected state
 * is no longer a thin mark floating under the words but a shape that encloses them, which is the
 * expressive half of the Hybrid this app settled on — decorative geometry follows Expressive, colour
 * still comes from `AzkarTheme`.
 *
 * That swap is also what deleted the estimate. Sixty-odd lines of character-width arithmetic existed
 * only to place the bar without measuring, and the KDoc on the old [tabUnderlineOffset] explains the
 * real reason it stayed unmeasured: a measured position would not resolve until after first
 * composition, and the frame before that is the one the screenshot harness captures.
 *
 * No divider is drawn — the strip sits directly above the content it filters, and the old version
 * never had a rule here either.
 */
@Composable
fun AzkarTabStrip(
    tabs: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (tabs.isEmpty()) return
    val colors = AzkarTheme.colors
    PrimaryTabRow(
        selectedTabIndex = selectedIndex,
        modifier = modifier,
        containerColor = Color.Transparent,
        contentColor = colors.textSecondary,
        divider = {},
    ) {
        tabs.forEachIndexed { index, title ->
            Tab(
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
                selectedContentColor = colors.primary,
                unselectedContentColor = colors.textSecondary,
                text = { Text(text = title, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1) },
            )
        }
    }
}

/**
 * Full-bleed container used by the empty/loading panes inside the browse stack.
 *
 * The first component in the app to go through the Styles API, and the clearest argument for it.
 * This one has no Material equivalent worth adopting — an empty-state pane is a rounded, bordered
 * box and nothing more, and `Card` would have brought a hover elevation with it — so what it needs
 * is a way to say "this is the shape, this is the padding" once, somewhere the theme owns it,
 * rather than as four parameters that every call site has to remember.
 *
 * [style] merges over the app's own, so a caller can override the chrome without restating the
 * rest, and passing [Style] leaves the component exactly as it was.
 */
@Composable
fun BorderedStateBox(
    modifier: Modifier = Modifier,
    style: Style = Style,
    content: @Composable () -> Unit,
) {
    Box(
        modifier =
            modifier.styleable(
                inertStyleState(),
                AzkarTheme.styles.borderedStateBox,
                style,
            ),
    ) {
        content()
    }
}

/** Utility that keeps icon tint inheritance consistent inside card rows. */
@Composable
fun IconTint(
    color: Color,
    content: @Composable () -> Unit,
) = CompositionLocalProvider(LocalContentColor provides color, content = content)

/**
 * `.library-dialog-pane` — the chrome every `Dialog` in the app is drawn on.
 *
 * Lives here rather than beside whichever screen happened to introduce it because it is shared by
 * three of them: the library's read and collection dialogs, the home screen's hadith dialog, and the
 * language picker the settings screen replaced. They all want the same rounded card with the same
 * border over the same scrim, and the one thing they must agree on is the padding inside it — a dialog
 * whose content sits 4dp further in than its neighbours reads as a mistake rather than a choice.
 *
 * The inner scope is a plain [ColumnScope] lambda, so a caller lays its own rows out and nothing here
 * decides how many of them there are.
 *
 * Its chrome is a [Style] rather than a run of modifiers for the same reason as [BorderedStateBox]:
 * twelve call sites draw this exact pane, and the one thing they must all agree on is the padding
 * inside it — a dialog whose content sits 4dp further in than its neighbours reads as a mistake
 * rather than a choice. Spelling the padding out in twelve places is how that agreement gets lost.
 *
 * One place to put the entrance, rather than one per dialog: twelve call sites all open the same pane,
 * and the window's own platform fade already covers the scrim arriving, which leaves the pane itself
 * as the only part that had nothing to say about having been summoned. There is deliberately no exit —
 * a `Dialog` is torn out of the composition the moment its state flips false, so a leaving animation
 * would have to be paid for with deferred dismissal at all twelve sites, and an undismissable dialog
 * is a far worse defect than one that closes abruptly.
 */
@Composable
fun AzkarDialogSurface(
    modifier: Modifier = Modifier,
    style: Style = Style,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(visible = true, enter = dialogEnter(), modifier = modifier) {
        Box(
            modifier =
                Modifier.styleable(
                    inertStyleState(),
                    AzkarTheme.styles.dialogSurface,
                    style,
                ),
        ) {
            content()
        }
    }
}
