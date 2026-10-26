package com.azkar.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.azkar.ui.theme.AzkarPreview
import com.azkar.ui.theme.AzkarTheme
import kotlin.math.cos
import kotlin.math.sin

/**
 * The app's Islamic geometric texture, tiled across whatever it is given and tinted by the caller.
 *
 * Drawn rather than shipped as an asset for two reasons. It has to be a *watermark* — something that
 * can sit over artwork, over a card wash or over the page at three different alphas without the
 * caller having to pick between three files — and a vector lets the alpha come from the tint alone.
 * And the motif is a plain eight-pointed star, which is a handful of line segments; the alternative
 * would be a PNG that has to be re-exported for every density the desktop app might run at.
 *
 * The tile is clipped by its own size rather than drawn to fill, so a caller that wants a half-bleed
 * or a corner ornament just changes the modifier and gets the same texture.
 *
 * Deliberately unbounded on both axes: there is no repeat count parameter, because every caller so
 * far wants "as much as fits" and a repeat count would only ever be a way of getting that wrong.
 */
@Composable
internal fun KhatimPattern(
    tint: Color,
    modifier: Modifier = Modifier,
    tile: Dp = KHATIM_TILE,
) {
    Canvas(modifier = modifier) {
        if (tint.alpha <= 0f) return@Canvas
        val step = tile.toPx()
        if (step <= 0f) return@Canvas
        val stroke = Stroke(width = KHATIM_STROKE.toPx(), cap = StrokeCap.Round)
        val rows = (size.height / step).toInt() + 2
        val columns = (size.width / step).toInt() + 2
        // The motif is drawn at the centre of each cell rather than at each cell's corner, so the
        // stars tile edge to edge without a seam: an eight-pointed star is symmetric about its own
        // centre, so neighbouring cells overlap into a continuous lattice instead of repeating with
        // a visible gap, which is what a corner-anchored star would do.
        for (row in 0..rows) {
            for (column in 0..columns) {
                khatimStar(
                    center = Offset(step * (column + 0.5f), step * (row + 0.5f)),
                    radius = step * KHATIM_RADIUS,
                    draw = { path ->
                        drawPath(path, tint, style = stroke)
                    },
                )
            }
        }
    }
}

@AzkarPreview
@Composable
internal fun KhatimPatternPreview() {
    Row(
        modifier = Modifier.width(420.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        KhatimPattern(
            tint = AzkarTheme.colors.accent.copy(alpha = 0.22f),
            modifier = Modifier.size(180.dp),
        )
        KhatimPattern(
            tint = AzkarTheme.colors.primary.copy(alpha = 0.18f),
            modifier = Modifier.size(180.dp),
            tile = 24.dp,
        )
        KhatimPattern(
            tint = Color.Transparent,
            modifier = Modifier.size(180.dp),
        )
    }
}

/**
 * The eight-pointed star the khatim is built from: two squares, one turned a quarter turn.
 *
 * Two squares rather than a single eight-pointed polygon because a single polygon has to be
 * hand-listed to eight points and would need its radius recomputed by hand for every stroke width,
 * whereas two squares share one radius and two rotations and stay correct however either is tuned.
 */
private inline fun khatimStar(
    center: Offset,
    radius: Float,
    draw: (Path) -> Unit,
) {
    val path = Path()
    path.addPolygonSquare(center, radius, rotationRadians = 0.0)
    path.addPolygonSquare(center, radius, rotationRadians = Math.PI / 4.0)
    draw(path)
}

/** Appends a square of circumradius [radius], rotated by [rotationRadians], to this path. */
private fun Path.addPolygonSquare(
    center: Offset,
    radius: Float,
    rotationRadians: Double,
) {
    val corners = 4
    val start = rotationRadians - HALF_PI
    for (index in 0..corners) {
        // `+ corners` rather than `until corners` so the path closes on its own first point; closing
        // by hand would leave a hairline seam where the stroke's caps do not quite meet.
        val angle = start + index * (TWO_PI / corners)
        val x = center.x + (radius * cos(angle)).toFloat()
        val y = center.y + (radius * sin(angle)).toFloat()
        if (index == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

/**
 * The size of one repeat of the motif.
 *
 * Large on purpose. The pattern's job is to make a flat surface feel like it belongs to the app, and
 * a fine lattice at this contrast reads as noise on a large panel — moiré on the hero band, and a
 * shimmering mess of hairlines behind small text on a card.
 */
private val KHATIM_TILE = 96.dp

/** The stroke is thin relative to the tile, so the stars read as outlines rather than as blobs. */
private val KHATIM_STROKE = 1.dp

/**
 * The motif's radius as a fraction of its tile.
 *
 * Just over a half, so the two squares overlap into a lattice: at exactly a half the corners of one
 * square touch the edge midpoints of the other and the star reads as eight separate triangles.
 */
private const val KHATIM_RADIUS = 0.42f

private const val TWO_PI = 2.0 * Math.PI
private const val HALF_PI = Math.PI / 2.0
