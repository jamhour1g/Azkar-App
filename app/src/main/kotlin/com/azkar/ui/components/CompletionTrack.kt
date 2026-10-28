package com.azkar.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.azkar.ui.theme.AzkarControlVariantsPreview
import com.azkar.ui.theme.AzkarMotion
import com.azkar.ui.theme.AzkarTheme

/**
 * A completion rule that grows to [completion] instead of jumping to it.
 *
 * Four surfaces draw this bar — the dashboard rail card, the collection detail strip, the shelf card
 * cover and the session header — and all four were `fillMaxWidth(completion)` on a plain `Box`,
 * copied from the JavaFX `ProgressBar` styling where the fill was driven straight from the CSS. The
 * copies differ only in height, in how the fill is painted, and in whether the ends are rounded, so
 * they live here once; the *animation* is the part that could not be left to four separate call
 * sites, because four independently chosen timings would visibly disagree with each other the moment
 * two of them were on screen together.
 *
 * The fill is interpolated on [AzkarMotion.FILL], the slowest band in the system and the only one
 * that outlasts the gesture that caused it. Every one of these bars moves because something the user
 * just did changed a number underneath it — recited a passage, finished a collection — and the
 * interval between the gesture and the new total is exactly when the eye is looking for the
 * consequence. Arriving promptly would spend the attention on nothing.
 *
 * The fill is guarded on the *animated* value rather than [completion] so that a bar dropping back to
 * empty retracts at the same speed it grew instead of vanishing the frame the target changed.
 *
 * @param completion the fraction complete, clamped to `0f..1f`.
 * @param height the rule's thickness. Also the default source of the corner radius, which is why it
 *   is a parameter rather than a constant: a bar drawn over cover art is thinner than one drawn in a
 *   panel and the rounding has to follow it or the ends look cut off.
 * @param shape the ends' shape. Defaults to fully rounded for [height]; pass [RectangleShape] for a
 *   square-ended rule.
 * @param trackColor the unfilled part.
 * @param fillColor the fill, when [fillBrush] is absent.
 * @param fillBrush the fill, when it is painted rather than filled — the gradient the detail and rail
 *   bars use.
 */
@Composable
fun CompletionTrack(
    completion: Float,
    modifier: Modifier = Modifier,
    height: Dp = COMPLETION_TRACK_HEIGHT,
    shape: Shape = RoundedCornerShape(height / 2),
    trackColor: Color = AzkarTheme.colors.border,
    fillColor: Color = AzkarTheme.colors.primary,
    fillBrush: Brush? = null,
) {
    val fill by animateFloatAsState(
        targetValue = completion.coerceIn(0f, 1f),
        animationSpec = AzkarMotion.fill(),
        label = "completionTrackFill",
    )
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(height)
                .clip(shape)
                .background(trackColor),
    ) {
        if (fill > 0f) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(fill)
                        .fillMaxHeight()
                        .background(fillBrush ?: SolidColor(fillColor)),
            )
        }
    }
}

@AzkarControlVariantsPreview
@Composable
internal fun CompletionTrackPreview() {
    Column(
        modifier = Modifier.width(420.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        listOf(0f, 0.17f, 0.5f, 1f).forEach { value ->
            CompletionTrack(completion = value, modifier = Modifier.fillMaxWidth())
        }
        CompletionTrack(completion = 0.5f, modifier = Modifier.fillMaxWidth(), height = 12.dp)
    }
}

/**
 * The thickness the detail strip, the rail card and the session header use. [CollectionCard]'s cover
 * bar is thinner because it sits on top of cover art rather than in a panel of its own.
 */
val COMPLETION_TRACK_HEIGHT = 6.dp
