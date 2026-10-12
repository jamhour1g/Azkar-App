package com.azkar.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.People
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.azkar.domain.model.CollectionKind
import com.azkar.ui.components.AzkarIcons
import com.azkar.ui.preview.previewMosaicSnippets
import com.azkar.ui.theme.AzkarTheme
import com.azkar.ui.theme.AzkarThemePreview

/**
 * Resolves a collection's stored `icon_key` to a glyph.
 *
 * The key is a plain string rather than an enum because it is written by a seed file, and a seed file
 * that names a glyph the app has since dropped must not stop the database from migrating. An
 * unrecognised key falls back to the bookshelf rather than to nothing: the tile is still
 * identifiable by its name, and a missing icon is a smaller failure than a blank one.
 *
 * The mapping lives here, in the UI, rather than next to the seed data — the data layer has no
 * business depending on a set of hand-drawn vector paths.
 */
fun iconForCollection(iconKey: String): ImageVector = COLLECTION_ICONS[iconKey] ?: AzkarIcons.Library

/**
 * Every `icon_key` a seed file may name, and the glyph it means.
 *
 * A table rather than a `when` because the set is data, not control flow: the seed catalogue in
 * `:data` picks these keys and the UI draws them, and a `when` would make "is there a glyph for this
 * key?" a question about indentation.
 */
private val COLLECTION_ICONS: Map<String, ImageVector> =
    mapOf(
        "sunrise" to AzkarIcons.Sunrise,
        "sunset" to AzkarIcons.Sunset,
        "sun" to AzkarIcons.Sun,
        "moon" to AzkarIcons.Moon,
        "mosque" to AzkarIcons.Mosque,
        "compass" to AzkarIcons.Compass,
        "home" to AzkarIcons.Home,
        "khatim" to AzkarIcons.Khatim,
        "star" to AzkarIcons.Star,
        "library" to AzkarIcons.Library,
        "book" to AzkarIcons.BookOpen,
        "nature" to Icons.Outlined.Eco,
        "people" to Icons.Outlined.People,
        "heart" to Icons.Outlined.FavoriteBorder,
        "folder" to Icons.Outlined.Folder,
        "sparkle" to Icons.Filled.AutoAwesome,
    )

/** What a custom collection with no icon of its own falls back to. */
internal fun defaultIconKeyFor(kind: CollectionKind): String =
    if (kind ==
        CollectionKind.OFFICIAL
    ) {
        "mosque"
    } else {
        "folder"
    }

/**
 * How many members a mosaic will draw, and how it arranges them.
 *
 * A Steam shelf tile is a 2×2 block, and the count is fixed at four because the block is: a fifth
 * member has nowhere to go. Spreading them over a taller block would make a 40-member collection
 * four times the height of a 3-member one on the same shelf, which is the wrong signal — the number
 * is a count, and it is already printed under the tile.
 */
private const val MOSAIC_TILE_COUNT = 4

/**
 * The cover art for a collection.
 *
 * Two forms, chosen by whether the user pinned one of the collection's own remembrances:
 *
 *  * **Mosaic** (the default) — up to four members, each showing a fragment of its own text, tinted
 *    from the palette. There is no artwork in the app to crop, and a shelf of identical photographs
 *    would be worse than no shelf at all; the text *is* the content, so the cover is made of it.
 *  * **Pinned** — the chosen member alone, filling the whole block.
 *
 * A collection with no members draws its icon instead, so an empty custom collection is still
 * identifiable on the shelf rather than being a grey square that says nothing.
 */
@Composable
fun CollectionMosaic(
    iconKey: String,
    memberSnippets: List<MosaicSnippet>,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 12.dp,
) {
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier =
            modifier
                .clip(shape)
                .background(AzkarTheme.colors.cardBackground),
    ) {
        when {
            memberSnippets.isEmpty() -> MosaicIcon(iconKey = iconKey, modifier = Modifier.fillMaxSize())
            memberSnippets.size == 1 ->
                MosaicTextTile(
                    snippet = memberSnippets.first(),
                    modifier = Modifier.fillMaxSize(),
                )

            else -> MosaicGrid(snippets = memberSnippets, modifier = Modifier.fillMaxSize())
        }
    }
}

@Preview
@Composable
private fun CollectionMosaicPreview() {
    AzkarThemePreview {
        Row(
            modifier = Modifier.width(1100.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CollectionMosaic(
                iconKey = "sunrise",
                memberSnippets = previewMosaicSnippets(),
                modifier = Modifier.size(220.dp),
            )
            CollectionMosaic(
                iconKey = "compass",
                memberSnippets = previewMosaicSnippets(),
                modifier = Modifier.size(220.dp),
                cornerRadius = 24.dp,
            )
            CollectionMosaic(
                iconKey = "khatim",
                memberSnippets = emptyList(),
                modifier = Modifier.size(220.dp),
            )
        }
    }
}

/**
 * One member's contribution to the mosaic: enough of its text to be recognisable, and the id the
 * tint is derived from.
 *
 * The id is carried rather than a colour so that the palette stays in one place and a tile's colour
 * follows the *same* remembrance on the shelf, on the detail page and in the picker — a shelf whose
 * colours reshuffled between screens would make two collections look like the same one.
 */
data class MosaicSnippet(
    val id: Long,
    val text: String,
)

/** The 2×2 block. The first member takes the leading half; the rest stack on the other side. */
@Composable
private fun MosaicGrid(
    snippets: List<MosaicSnippet>,
    modifier: Modifier = Modifier,
) {
    val tiles = snippets.take(MOSAIC_TILE_COUNT)
    Row(modifier = modifier) {
        MosaicTextTile(snippet = tiles.first(), modifier = Modifier.weight(1f).fillMaxSize())
        Column(modifier = Modifier.weight(1f).fillMaxSize()) {
            tiles.drop(1).forEach { snippet ->
                MosaicTextTile(
                    snippet = snippet,
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    alignStart = true,
                )
            }
        }
    }
}

/**
 * One cell of the mosaic: a wash tinted from the palette, with the text floated over it.
 *
 * The text is deliberately bottom-aligned and clipped rather than centred: a 96-character preview
 * cannot fit legibly in a quarter of a tile at any size, and what the cell has to do is *suggest* the
 * passage — enough of the first line to place it, none of it required to be read.
 */
@Composable
private fun MosaicTextTile(
    snippet: MosaicSnippet,
    modifier: Modifier = Modifier,
    alignStart: Boolean = false,
    fontSize: TextUnit = MOSAIC_TEXT_SP,
) {
    val colors = AzkarTheme.colors
    Box(
        modifier =
            modifier
                .background(mosaicBrush(snippet.id))
                .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = if (alignStart) Alignment.TopStart else Alignment.BottomStart,
    ) {
        Text(
            text = snippet.text,
            color = colors.textPrimary.copy(alpha = TEXT_ALPHA),
            fontSize = fontSize,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Start,
        )
    }
}

/** The empty-collection cover: the collection's own icon, large and quiet. */
@Composable
private fun MosaicIcon(
    iconKey: String,
    modifier: Modifier = Modifier,
) {
    val colors = AzkarTheme.colors
    Box(
        modifier =
            modifier.background(
                Brush.linearGradient(listOf(colors.azkarCardStart, colors.azkarCardEnd)),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = iconForCollection(iconKey),
            contentDescription = null,
            tint = colors.primaryStrong.copy(alpha = ICON_ALPHA),
            modifier = Modifier.size(28.dp),
        )
    }
}

/**
 * The wash behind one mosaic cell.
 *
 * Four stops chosen to read as *the same material* at four tints rather than as four colours: a
 * shelf of unrelated hues would compete with the app's own accent, which is the one thing on a
 * remembrance tile that must stay the loudest mark on screen. The choice is keyed on the
 * remembrance's id, so a given passage keeps its tile colour everywhere it appears.
 */
@Composable
private fun mosaicBrush(id: Long): Brush {
    val colors = AzkarTheme.colors
    val stops =
        when ((id % PALETTE_SIZE).toInt().let { if (it < 0) it + PALETTE_SIZE else it }) {
            0 -> colors.azkarCardStart to colors.azkarCardEnd
            1 -> colors.favoriteCardStart to colors.favoriteCardEnd
            2 -> colors.reminderSection to colors.cardBackground
            else -> colors.heroScrimEnd to colors.nextPrayerSurface
        }
    return Brush.linearGradient(listOf(stops.first, stops.second))
}

/** The four washes [mosaicBrush] chooses between. */
private const val PALETTE_SIZE = 4L

/**
 * How opaque the mosaic text is drawn.
 *
 * Low on purpose. The text is texture here, not content — the same passage is one tap away — and
 * anything that cleared the contrast threshold for reading would compete with the collection's
 * name printed directly underneath.
 */
private const val TEXT_ALPHA = 0.55f

private const val ICON_ALPHA = 0.35f

/** Small enough that a cell reads as texture rather than as a paragraph. */
private val MOSAIC_TEXT_SP = 11.sp
