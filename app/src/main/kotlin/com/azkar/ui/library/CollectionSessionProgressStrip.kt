package com.azkar.ui.library

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.azkar.generated.resources.Res
import com.azkar.generated.resources.collectionDetailHeroProgressFormat
import com.azkar.generated.resources.collectionDetailRemainingFormat
import com.azkar.ui.components.AzkarCard
import com.azkar.ui.components.CompletionTrack
import com.azkar.ui.components.mirrorHorizontalGradient
import com.azkar.ui.library.data.CollectionStats
import com.azkar.ui.preview.previewCollectionStatsVariants
import com.azkar.ui.theme.AzkarPreview
import com.azkar.ui.theme.AzkarTheme
import com.azkar.ui.theme.isRtlLayout
import org.jetbrains.compose.resources.stringResource

/**
 * How far through the collection the session has got, in one strip between the band and the passage.
 *
 * Its own card rather than part of the band above or part of the passage below, and the placement is
 * the argument: the band is about which collection this is, the passage is about this one remembrance,
 * and "eight of twenty-nine done" is about neither. It sits between them because it is the join — the
 * reader's eye crosses it on the way from "what am I reciting" to "what comes next" and the position
 * answers exactly the question that crossing raises.
 *
 * The ring is drawn rather than taken from a component because there is no ring component, and because
 * the one thing this needs that a bar cannot do is be read at a glance while the reader is not reading:
 * an arc's *shape* says "how far round" without its length being measured against anything. That claim
 * stops being true the moment the arc is more than about a third full and starts looking like a loading
 * spinner, which is why it is filled clockwise from the top like a clock rather than as a pie.
 */
@Composable
internal fun CollectionSessionProgressStrip(
    stats: CollectionStats,
    memberCount: Int,
    modifier: Modifier = Modifier,
) {
    val colors = AzkarTheme.colors
    val captions = stripCaptions(stats, memberCount)

    AzkarCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(STRIP_SHAPE),
        contentPadding = STRIP_PADDING,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(STRIP_GAP),
        ) {
            ProgressRing(
                completion = stats.completion,
                modifier = Modifier.size(RING_SIZE),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(STRIP_TRACK_GAP),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = captions.completed,
                        color = colors.textPrimary,
                        fontSize = STRIP_CAPTION_SP,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (captions.remaining != null) {
                        Spacer(Modifier.width(STRIP_REMAINING_GAP))
                        Text(
                            text = captions.remaining,
                            color = colors.textSecondary,
                            fontSize = STRIP_CAPTION_SP,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                CompletionTrack(
                    completion = stats.completion,
                    modifier = Modifier.fillMaxWidth(),
                    fillBrush =
                        mirrorHorizontalGradient(
                            listOf(colors.primary, colors.primaryStrong),
                            isRtlLayout(),
                        ),
                )
            }
        }
    }
}

/**
 * The two sentences the strip prints above its track.
 *
 * Their own function because deciding *whether* to print the second one is a rule rather than a
 * condition: the estimate is dropped rather than shown as "0m" once the collection is finished, for the
 * same reason the collection page's band drops it — a number that says nothing has stopped the reader
 * thinking about the number that does.
 */
@Composable
private fun stripCaptions(
    stats: CollectionStats,
    memberCount: Int,
): StripCaptions =
    StripCaptions(
        completed =
            stringResource(
                Res.string.collectionDetailHeroProgressFormat,
                stats.completedMembers,
                memberCount,
            ),
        remaining =
            if (stats.isComplete || stats.estimatedRemaining.isZero) {
                null
            } else {
                stringResource(
                    Res.string.collectionDetailRemainingFormat,
                    formatDuration(stats.estimatedRemaining),
                )
            },
    )

/** The count that is always there, and the estimate that is sometimes not. */
private data class StripCaptions(
    val completed: String,
    val remaining: String?,
)

@AzkarPreview
@Composable
internal fun CollectionSessionProgressStripPreview() {
    Column(
        modifier = Modifier.width(1200.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        previewCollectionStatsVariants().forEach { stats ->
            CollectionSessionProgressStrip(stats = stats, memberCount = stats.totalMembers)
        }
    }
}

/**
 * The arc that says how far round the session is.
 *
 * Drawn as a stroked circle with a round cap rather than as a filled wedge, so that the two ends of the
 * stroke are distinguishable from the ring itself at small sizes — a wedge's boundary is the same edge
 * as the track's, which makes a third of a circle read as a third of a *pie* and a full one read as a
 * disc.
 *
 * Starts at the top and sweeps clockwise, like a clock, which is the reading most people already have
 * for "how far round". In a right-to-left layout it starts at the top and sweeps the other way, for the
 * reason every other progress mark on this app mirrors: the same ring pointing a different way in two
 * languages would be read as two different amounts.
 *
 * The full sweep is inset by the cap rather than by a fudge factor, because the cap is drawn *past* the
 * arc's end: at a completion of zero the ring must look empty rather than like a dot, and at one it
 * must close rather than leave a notch.
 */
@Composable
private fun ProgressRing(
    completion: Float,
    modifier: Modifier = Modifier,
) {
    val colors = AzkarTheme.colors
    Canvas(modifier = modifier) {
        // Measured from the canvas rather than from a top-level constant, so the ring's outer edge lands
        // exactly on the box's edge whatever the box is: the inset is half the stroke, because a stroke
        // straddles its path and a path on the box's own edge would have half its width outside it.
        val stroke = RING_STROKE.toPx()
        val inset = stroke / 2f
        val diameter = size.minDimension - stroke
        val topLeft = Offset(inset, inset)
        val arcSize = Size(diameter, diameter)
        drawArc(
            color = colors.border,
            startAngle = RING_START_ANGLE,
            sweepAngle = RING_FULL_SWEEP,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        if (completion > 0f) {
            drawArc(
                color = colors.primary,
                startAngle = RING_START_ANGLE,
                sweepAngle = RING_FULL_SWEEP * completion.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
    }
}

private val RING_STROKE = 5.dp

/**
 * The ring's box, at the same 44dp as the pointer-sized controls the rest of the app uses, so the strip
 * does not read as a row of smaller buttons.
 */
private val RING_SIZE = 44.dp

/** Twelve o'clock, in the degrees `drawArc` measures from. */
private const val RING_START_ANGLE = -90f

private const val RING_FULL_SWEEP = 360f

private val STRIP_SHAPE = 16.dp

private val STRIP_PADDING = 14.dp

private val STRIP_GAP = 14.dp

private val STRIP_CAPTION_SP = 12.sp

private val STRIP_REMAINING_GAP = 10.dp

/** The air between the caption and the track it describes. */
private val STRIP_TRACK_GAP = 8.dp
