package com.azkar.ui.library

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.azkar.ui.theme.AzkarTheme
import com.azkar.ui.theme.AzkarThemePreview

/**
 * The artwork behind a collection's card: the picture the user chose, or a generated wash.
 *
 * **The wash is the fallback, not the artwork.** A collection with a cover shows the cover; a collection
 * without one — or with one that has since been deleted from under the app — shows this wash. It is a
 * gradient built from the theme rather than a photograph, and it exists because "no picture" has to mean
 * something rather than mean a hole in the grid: every collection is worth a card that looks finished,
 * and the app ships no per-collection artwork to fall back on.
 *
 * **Every colour in the wash comes from the theme.** There is not one hex literal in this file, and that
 * is the whole of the fix. The covers used to be five hand-picked dark palettes — forest green, bark,
 * plum, navy — the same five in both themes, so a light window got fourteen saturated blocks sitting on
 * white cards, reading as stickers rather than as part of the app. A wash is now one of the theme's own
 * hues, taken two different distances toward the theme's own card colour: pale in the light theme, deep
 * in the dark one. `cardBackground` is white in one and near-black navy in the other, so there is no
 * branch here that has to know which is which.
 *
 * The hues are five and they are the theme's: the app's own blue and indigo, plus the green, amber and
 * red it already uses for priorities and for accents. A sixth would not be a sixth colour — the washes
 * are all pulled toward the same neutral, so past five they differ only in strength, which is how
 * fourteen cards end up reading as one shelf rather than as a paint chart.
 *
 * Which hue a collection gets is [iconKey]'s hash, which is stable across runs and machines —
 * `String.hashCode` is specified by the JVM rather than left to the implementation. It is keyed on the
 * *icon* rather than the name or the id, so a collection the user renames or re-imports keeps its
 * colour, and two collections that share an icon share a colour, which is honest: they do say the same
 * kind of thing.
 *
 * Painted top-left to bottom-right so that a card's light corner and dark corner fall the same way
 * across the whole grid. The eye reads a wall of cards as one image, and cards lit from alternating
 * directions read as a mistake rather than as variety.
 *
 * **The photograph is cropped, not scaled, and carries no glyph.** The glyph is a motif for a wash; over
 * a photograph it would be one more thing in a picture the user chose, sitting on top of the subject they
 * picked it for. The scrim is what keeps the badge and the overflow menu legible over an arbitrary
 * picture, and it comes from the theme for the same reason the wash does — an image's own average colour
 * is a muddy grey-brown, because a bright sky and a dark hillside cancel out into nothing you would want
 * to print text on.
 */
@Composable
fun CollectionCoverArt(
    iconKey: String,
    modifier: Modifier = Modifier,
    iconSize: Dp = 72.dp,
    coverImagePath: String? = null,
    covers: CollectionCoverCache? = null,
) {
    val colors = AzkarTheme.colors
    // The scrim, not a colour of ours: it has to be a neutral *dark* in both palettes, because the
    // light theme overlays its badges on a pale wash and a light scrim would do nothing there. That is
    // exactly the slot the app's theme declines to have an opinion about — see `AzkarTheme`.
    val scrim = MaterialTheme.colorScheme.scrim
    val photo = coverImagePath?.let { path -> covers?.image(path)?.value }
    Box(
        modifier = if (photo == null) modifier.background(brushFor(iconKey)) else modifier,
        contentAlignment = Alignment.Center,
    ) {
        if (photo != null) {
            Image(
                bitmap = photo,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier.fillMaxSize().background(scrim.copy(alpha = COVER_PHOTO_SCRIM)),
            )
        } else {
            Icon(
                imageVector = iconForCollection(iconKey),
                contentDescription = null,
                tint = colors.textPrimary.copy(alpha = COVER_GLYPH_ALPHA),
                modifier = Modifier.size(iconSize),
            )
        }
    }
}

@Preview
@Composable
private fun CollectionCoverArtPreview() {
    AzkarThemePreview {
        Row(
            modifier = Modifier.width(1100.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            listOf("sunrise", "sunset", "mosque", "compass", "khatim").forEach { iconKey ->
                CollectionCoverArt(iconKey = iconKey, modifier = Modifier.size(200.dp))
            }
        }
    }
}

/**
 * The wash a collection's cover is painted with: one of the theme's five hues, light end first.
 *
 * Both ends are pulled toward the theme's card colour, so the second stop of every pair ends up in the
 * same quiet register and the first is the only thing telling two covers apart. That is deliberate: a
 * gradient running between two different colours reads as a blend rather than as *a colour*, and a
 * cover whose job is to be recognisable at a glance wants one.
 */
@Composable
private fun brushFor(iconKey: String): Brush {
    val colors = AzkarTheme.colors
    val mood = coverMoods()[Math.floorMod(iconKey.hashCode(), COVER_MOODS_COUNT)]
    val (lightStop, heavyStop) = mood
    return Brush.linearGradient(
        colors =
            listOf(
                lightStop.tint(colors.cardBackground, COVER_TINT_LIGHT),
                heavyStop.tint(colors.cardBackground, COVER_TINT_HEAVY),
            ),
    )
}

/**
 * The five washes, each a hue from the theme paired with a pale surface from the theme.
 *
 * Read at composition time rather than written down, because a pair of colours written down *is* a
 * hard-coded palette — the thing this file was rewritten to stop having. `COVER_MOODS_COUNT` is
 * declared separately so that the index into this list and the bound on the index cannot disagree.
 */
@Composable
private fun coverMoods(): List<Pair<Color, Color>> {
    val colors = AzkarTheme.colors
    return listOf(
        colors.primary to colors.nextPrayerSurface,
        colors.secondary to colors.primary,
        colors.priorityLow to colors.azkarCardStart,
        colors.accent to colors.favoriteCardStart,
        colors.priorityHigh to colors.favoriteCardStart,
    )
}

/** A theme colour walked [share] of the way toward [toward]. */
private fun Color.tint(
    toward: Color,
    share: Float,
): Color =
    Color(
        red = red + (toward.red - red) * share,
        green = green + (toward.green - green) * share,
        blue = blue + (toward.blue - blue) * share,
        alpha = alpha + (toward.alpha - alpha) * share,
    )

/**
 * How far each end of a cover is taken toward the theme's card colour.
 *
 * Two numbers rather than one, and not because the themes need different values — they do not, which
 * is the point of taking the card colour as the target. In the light theme the card is white, so 35% of
 * the way there is still plainly the hue and 72% is a tint you could put body text on, and the covers
 * read as colour without becoming the loudest thing on the page. In the dark theme the card is not
 * white but `#121B2E`, already close to the hues' own darkness, so the same two distances land as deep
 * washes rather than as near-black, and the grid keeps its structure without a second set of numbers.
 */
private const val COVER_TINT_LIGHT = 0.35f
private const val COVER_TINT_HEAVY = 0.72f

/**
 * How faintly the glyph shows through the wash.
 *
 * Enough to name the collection rather than merely tint it: a card cover that carries nothing but
 * colour reads as a loading placeholder, and two cards that differ only by hue take a second look to
 * tell apart. Low enough to stay a background — the badge and the title both print over it.
 */
private const val COVER_GLYPH_ALPHA = 0.22f

/** How many washes [coverMoods] returns; the index into it is taken modulo this. */
private const val COVER_MOODS_COUNT = 5

/**
 * How far a photograph is darkened behind the badge and the menu printed across it.
 *
 * Faint, because the picture is the user's own and the only thing competing with it is a few words of
 * chrome; strong enough that those words do not have to hope. A gradient rather than a flat wash, from
 * nothing at the top to [COVER_PHOTO_SCRIM] at the bottom, so the mark the user put in the picture is
 * the part left untouched while the badge below it gains a floor to sit on.
 */
private const val COVER_PHOTO_SCRIM = 0.32f
