package com.azkar.ui.library

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.azkar.generated.resources.Res
import com.azkar.generated.resources.collectionDetailActionRemoveFromCollection
import com.azkar.generated.resources.collectionDetailPositionFormat
import com.azkar.generated.resources.collectionDragHandle
import com.azkar.generated.resources.libraryActionCopy
import com.azkar.generated.resources.libraryActionFavorite
import com.azkar.generated.resources.libraryActionMore
import com.azkar.generated.resources.remembranceEditorAction
import com.azkar.ui.components.ActionIcons
import com.azkar.ui.components.AzkarCard
import com.azkar.ui.components.AzkarMenuItem
import com.azkar.ui.components.AzkarOverflowMenu
import com.azkar.ui.components.TooltipIconButton
import com.azkar.ui.components.pressableClick
import com.azkar.ui.library.model.RemembranceTile
import com.azkar.ui.preview.previewRemembranceTile
import com.azkar.ui.preview.previewRemembranceTileActions
import com.azkar.ui.theme.AzkarTheme
import com.azkar.ui.theme.AzkarThemePreview
import org.jetbrains.compose.resources.stringResource

/**
 * One remembrance in a collection, as a card with a picture on top.
 *
 * The picture is the category's wash rather than a photograph, because the seed data ships no images
 * and an app that invented a stock photo per category would be claiming to show something it does not
 * have. What the wash does give a wall of text is a rhythm: five cards in a row stop being five
 * identical white boxes, and someone scanning for "the morning one" can find it by colour instead of
 * by reading twenty-nine first words.
 *
 * Nothing is printed over that wash. It carried a publisher badge, a category chip and the bookmark,
 * and all three are gone, which leaves the top of the card saying nothing at all — on purpose. Each of
 * them was a fact the reader had already been given: the publisher is the same for every row in the
 * library, the category is what the wash's own colour is chosen from and what the filter chips above
 * the grid are sorted by, and the bookmark's job is to be reachable from anywhere on the card rather
 * than from one privileged corner of it. What is left is the passage itself, which is the only thing
 * the card was opened for.
 *
 * The cost of that is honest and worth naming: a card no longer states its own category in words, so
 * recognising one by name rather than by colour now means reading it. That is a fair trade at five
 * columns across a desktop window and a bad one on a phone, which is why the list view — where the
 * width to spend on the words exists — is the shape to reach for when the words matter.
 *
 * The old tile also carried an "in 2 collections" line, and it is not here. That is a fact about how
 * *the user* has filed this passage, not about the passage, and it is shown in the add-member picker
 * where it is actionable; on a card whose job is to be recognisable at a glance it was the one line
 * that could not be scanned past.
 *
 * Everything below the wash is measured, and this file's constants add up to the height
 * [MEMBER_TILE_HEIGHT] reserves. That agreement is not a coincidence and is not to be edited on one
 * side alone: the card clips rather than scrolls, so a line added without raising the constant does
 * not push the footer down, it deletes it.
 *
 * [dragGesture] is **not** applied to the card. The card is one big press target that opens the
 * passage, so a drag detector on it would sit downstream of a `clickable` that has already taken the
 * press — Compose hands a pointer event to the innermost handler first — and the two would either
 * deadlock or the card would open at the end of every drag. The gesture is handed down to
 * [TileActions] instead, which paints it on a handle: a child of the clickable, so it sees the press
 * first, and a control that means only one thing.
 */
@Composable
internal fun RemembranceTileCard(
    tile: RemembranceTile,
    actions: RemembranceTileActions,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    memberCount: Int = 0,
    dragGesture: Modifier = Modifier,
) {
    val colors = AzkarTheme.colors
    AzkarCard(
        modifier =
            modifier
                .border(
                    width = if (selected) TILE_SELECTED_BORDER else TILE_BORDER,
                    color = if (selected) colors.primary else colors.border,
                    shape = RoundedCornerShape(TILE_SHAPE),
                ).pressableClick(scale = 1f, onClick = actions.onRead),
        shape = RoundedCornerShape(TILE_SHAPE),
        contentPadding = 0.dp,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TileWash(iconKey = tile.iconKey)
            TileBody(
                tile = tile,
                actions = actions,
                memberCount = memberCount,
                dragGesture = dragGesture,
            )
        }
    }
}

/**
 * The band of artwork across the top of the card, which is also the wash its category is chosen from.
 *
 * Fixed height and not a weight, so it is the same number of pixels on every card in a grid however
 * tall their text runs — a category-coloured strip whose height drifted with the passage would make
 * the grid look like it had a wrapping problem.
 */
@Composable
private fun TileWash(iconKey: String) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(TILE_HEADER_HEIGHT),
    ) {
        CollectionCoverArt(
            iconKey = iconKey,
            modifier = Modifier.matchParentSize(),
            iconSize = TILE_HEADER_GLYPH,
        )
    }
}

/**
 * Everything below the wash: the Arabic, the meaning, the reference and the two controls.
 *
 * The passage and the meaning are clamped to their line counts, and the space between the meaning and
 * the reference takes the slack (`Spacer(Modifier.weight(1f))`) rather than a fixed gap — so a card
 * whose text is shorter than its box pushes the reference down instead of leaving a hole under it, and
 * the footer stays where [MEMBER_TILE_HEIGHT] says it is.
 */
@Composable
private fun ColumnScope.TileBody(
    tile: RemembranceTile,
    actions: RemembranceTileActions,
    memberCount: Int,
    modifier: Modifier = Modifier,
    dragGesture: Modifier = Modifier,
) {
    val colors = AzkarTheme.colors
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(TILE_BODY_PADDING),
    ) {
        Text(
            text = tile.primaryPreview,
            color = colors.textPrimary,
            fontSize = TILE_PASSAGE_SP,
            lineHeight = TILE_PASSAGE_LINE_SP,
            fontWeight = FontWeight.Bold,
            maxLines = TILE_PASSAGE_LINES,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(TILE_PASSAGE_GAP))
        Text(
            text = TRANSLATION_QUOTE_OPEN + tile.secondaryPreview + TRANSLATION_QUOTE_CLOSE,
            color = colors.hadithEnglish,
            fontSize = TILE_TRANSLATION_SP,
            lineHeight = TILE_TRANSLATION_LINE_SP,
            fontStyle = FontStyle.Italic,
            maxLines = TILE_TRANSLATION_LINES,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = tileReferenceText(tile = tile, memberCount = memberCount),
            color = colors.hadithSource,
            fontSize = TILE_REFERENCE_SP,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(TILE_FOOTER_GAP))
        HorizontalDivider(color = colors.border)
        Spacer(Modifier.height(TILE_FOOTER_GAP))
        TileActions(
            tile = tile,
            actions = actions,
            modifier = Modifier.fillMaxWidth(),
            dragGesture = dragGesture,
        )
    }
}

@Preview
@Composable
private fun RemembranceTileCardPreview() {
    AzkarThemePreview {
        Row(
            modifier = Modifier.width(1100.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            previewRemembranceTile().let { tile ->
                RemembranceTileCard(
                    tile = tile,
                    actions = previewRemembranceTileActions(),
                    memberCount = 54,
                    modifier = Modifier.width(320.dp),
                )
                RemembranceTileCard(
                    tile = tile,
                    actions = previewRemembranceTileActions(removable = true),
                    selected = true,
                    modifier = Modifier.width(320.dp),
                )
            }
        }
    }
}

/**
 * What the card says about where this passage sits in the collection.
 *
 * Position only, and that is not a trimming: the publisher this line used to be qualified by is gone
 * from the card along with every other badge, so the ordinal is the card's one piece of provenance —
 * the answer to "which of the twenty-nine is this", which a wall of identical washes cannot give.
 *
 * Drawn only when the tile knows its position *and* the caller knows the collection's size, so a card
 * built from a search result rather than from a collection prints nothing rather than a confident
 * "1 / 1" that has nothing behind it.
 */
@Composable
private fun tileReferenceText(
    tile: RemembranceTile,
    memberCount: Int,
): String =
    if (!tile.hasPosition || memberCount <= 0) {
        ""
    } else {
        stringResource(Res.string.collectionDetailPositionFormat, tile.position, memberCount)
    }

/**
 * A passage's publisher, without the rest of the source line.
 *
 * The seed data packs a whole citation into one field — "Muslim Data Azkars #286 - How the Prophet
 * made Tasbeeh" — and a card's badge has room for the publisher and nothing else. Both separators
 * are stripped rather than the string truncated: a badge ending in "Muslim Data Azkars #286" reads
 * as a different, more specific citation than it is, while one reading "Muslim Data Azkars" is true
 * of every row in the data.
 *
 * Falls back to the whole source when neither separator is there, so a source that *is* just a name
 * is not silently emptied.
 */
internal fun remembranceSourceLabel(source: String): String =
    source
        .substringBefore(SOURCE_TITLE_SEPARATOR)
        .substringBefore(SOURCE_NUMBER_MARK)
        .trim()
        .ifBlank { source }

/**
 * Everything a reader can do to one tile that is not reading it, in one place.
 *
 * Both shapes draw from here — the grid's card and the list's row — and that is the whole point of
 * this function existing. They had drifted: the card offered bookmark, copy and the per-card menu
 * while the row offered a recite circle and the menu, so switching the view silently took away the
 * bookmark and the copy button, and put back a control the card no longer had. A reader who learned
 * where "save" lives on one shape had to find it again on the other, and the two shapes are supposed
 * to be the same tile drawn differently. One function both call is the only arrangement in which they
 * cannot disagree again.
 *
 * So the row loses its recite circle and gains the bookmark and the copy button. The circle went for
 * the reason it went from the card: the row is already one press target that reads the passage, and a
 * filled circle inside a target that does the same thing named the same action twice. What is left is
 * three quiet glyphs, in the same order, at the same size, in both shapes.
 *
 * [Arrangement.End] rather than a weighted spacer, so the group lands on the correct edge in a
 * right-to-left layout without either caller having to know which way it is being read. The card
 * passes a `fillMaxWidth` modifier because the footer under a divider has to reach both edges for the
 * divider above it to look deliberate; the row does not, because the actions simply follow the text.
 */
@Composable
private fun TileActions(
    tile: RemembranceTile,
    actions: RemembranceTileActions,
    modifier: Modifier = Modifier,
    dragGesture: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(TILE_ACTION_GAP, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The handle, and the only place a drag can start. It is a child of the tile rather than the
        // tile itself because the tile is one big press target that opens the passage: a drag detector
        // on the card would be downstream of that clickable, which either swallows the press or fights
        // it, and a control that is both "open this" and "move this" is a control that does neither
        // well. A handle also says out loud that the order can be changed — without it the whole
        // feature is a secret gesture, and a secret gesture is indistinguishable from no feature.
        if (dragGesture != Modifier) {
            TooltipIconButton(
                icon = Icons.Filled.DragHandle,
                tooltip = stringResource(Res.string.collectionDragHandle),
                onClick = {},
                modifier = dragGesture,
                buttonSize = TILE_ACTION_SIZE,
                iconSize = TILE_ACTION_GLYPH,
            )
        }
        TooltipIconButton(
            icon = ActionIcons.favorite(tile.isFavorite),
            tooltip = stringResource(Res.string.libraryActionFavorite),
            onClick = actions.onToggleFavorite,
            buttonSize = TILE_ACTION_SIZE,
            iconSize = TILE_ACTION_GLYPH,
            favorite = tile.isFavorite,
        )
        TooltipIconButton(
            icon = Icons.Filled.ContentCopy,
            tooltip = stringResource(Res.string.libraryActionCopy),
            onClick = actions.onCopy,
            buttonSize = TILE_ACTION_SIZE,
            iconSize = TILE_ACTION_GLYPH,
        )
        if (actions.onEdit != null) {
            // A pencil rather than a fourth entry in the overflow: editing is the one thing a user
            // opens this card to do, and burying it behind two taps of ⋯ next to a destructive
            // "remove from collection" is how a correction nobody makes turns into a wrong passage.
            TooltipIconButton(
                icon = Icons.Filled.Edit,
                tooltip = stringResource(Res.string.remembranceEditorAction),
                onClick = actions.onEdit,
                buttonSize = TILE_ACTION_SIZE,
                iconSize = TILE_ACTION_GLYPH,
            )
        }
        if (actions.onRemoveFromCollection != null) {
            AzkarOverflowMenu(
                icon = Icons.Filled.MoreVert,
                contentDescription = stringResource(Res.string.libraryActionMore),
                items =
                    listOf(
                        AzkarMenuItem(
                            label = stringResource(Res.string.collectionDetailActionRemoveFromCollection),
                            onClick = actions.onRemoveFromCollection,
                            icon = ActionIcons.removeFromCollection,
                        ),
                    ),
                buttonSize = TILE_ACTION_SIZE,
                iconSize = TILE_ACTION_GLYPH,
            )
        }
    }
}

/**
 * One remembrance in a collection, as a single horizontal row — the same tile in the list view.
 *
 * The wash becomes a leading square instead of a header band, and the passage and its translation
 * share the lines beside it instead of sitting above a footer. The list exists because a grid is the
 * wrong shape for reading — twenty-nine cards of the same category side by side waste the width a row
 * spends on text — and it is the shape to reach for on a narrow window, where the card has no room to
 * show a category in words and the reader needs the passage itself to take up more of it.
 *
 * What it is not is a different tile. The same [TileActions] group sits on its trailing edge that the
 * card draws under its divider, so switching between the two views moves the layout and nothing else:
 * the reader who has learned where the bookmark and the copy button are does not have to look for them
 * again.
 *
 * Fixed height for the same reason the card has one: rows whose text budget changes with their
 * content turn a scrolling column into a ragged edge.
 */
@Composable
internal fun RemembranceTileRow(
    tile: RemembranceTile,
    actions: RemembranceTileActions,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    memberCount: Int = 0,
    dragGesture: Modifier = Modifier,
) {
    val colors = AzkarTheme.colors
    AzkarCard(
        modifier =
            modifier
                .border(
                    width = if (selected) TILE_SELECTED_BORDER else TILE_BORDER,
                    color = if (selected) colors.primary else colors.border,
                    shape = RoundedCornerShape(TILE_SHAPE),
                ).pressableClick(scale = 1f, onClick = actions.onRead),
        shape = RoundedCornerShape(TILE_SHAPE),
        contentPadding = ROW_PADDING,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier =
                    Modifier
                        .size(ROW_THUMB_SIZE)
                        .clip(RoundedCornerShape(ROW_THUMB_SHAPE)),
            ) {
                CollectionCoverArt(
                    iconKey = tile.iconKey,
                    modifier = Modifier.matchParentSize(),
                    iconSize = ROW_THUMB_GLYPH,
                )
            }

            Spacer(Modifier.width(ROW_THUMB_GAP))

            RowText(tile = tile, memberCount = memberCount, modifier = Modifier.weight(1f))

            Spacer(Modifier.width(ROW_ACTION_GAP))

            TileActions(tile = tile, actions = actions, dragGesture = dragGesture)
        }
    }
}

/**
 * The three lines a row spends its width on, in the order a reader takes them.
 *
 * Split out so the row itself reads as its actual shape — a thumbnail, some words, some buttons —
 * rather than as a column of type settings. The card's equivalent is [TileBody]; this is the same
 * three facts arranged for a line rather than a tile.
 */
@Composable
private fun RowText(
    tile: RemembranceTile,
    memberCount: Int,
    modifier: Modifier = Modifier,
) {
    val colors = AzkarTheme.colors
    Column(modifier = modifier) {
        Text(
            text = tile.primaryPreview,
            color = colors.textPrimary,
            fontSize = ROW_PASSAGE_SP,
            lineHeight = ROW_PASSAGE_LINE_SP,
            fontWeight = FontWeight.SemiBold,
            maxLines = ROW_PASSAGE_LINES,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = TRANSLATION_QUOTE_OPEN + tile.secondaryPreview + TRANSLATION_QUOTE_CLOSE,
            color = colors.hadithEnglish,
            fontSize = ROW_TRANSLATION_SP,
            lineHeight = ROW_TRANSLATION_LINE_SP,
            fontStyle = FontStyle.Italic,
            maxLines = ROW_TRANSLATION_LINES,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = tileReferenceText(tile = tile, memberCount = memberCount),
            color = colors.hadithSource,
            fontSize = TILE_REFERENCE_SP,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Preview
@Composable
private fun RemembranceTileRowPreview() {
    AzkarThemePreview {
        Column(
            modifier = Modifier.width(640.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            previewRemembranceTile().let { tile ->
                RemembranceTileRow(
                    tile = tile,
                    actions = previewRemembranceTileActions(),
                    memberCount = 54,
                )
                RemembranceTileRow(
                    tile = tile.copy(id = 102L, isFavorite = true, position = 0),
                    actions = previewRemembranceTileActions(removable = true),
                    selected = true,
                )
            }
        }
    }
}

/**
 * The card's border, corner radius and the two text blocks' line budgets.
 *
 * The line counts are why [MEMBER_TILE_HEIGHT] is a number rather than `wrapContentHeight`: three
 * lines of translation on one card and two on the next is the whole difference between a grid that
 * reads as a grid and one that reads as a ragged column, and only a fixed height enforces it.
 */
private const val TILE_SHAPE = 12
private val TILE_BORDER = 1.dp
private val TILE_SELECTED_BORDER = 1.5.dp

private val TILE_HEADER_HEIGHT = 86.dp
private val TILE_HEADER_GLYPH = 46.dp
private val TILE_BODY_PADDING = 14.dp

private val TILE_PASSAGE_SP = 17.sp
private val TILE_PASSAGE_LINE_SP = 24.sp
private const val TILE_PASSAGE_LINES = 3
private val TILE_PASSAGE_GAP = 6.dp

private val TILE_TRANSLATION_SP = 12.sp
private val TILE_TRANSLATION_LINE_SP = 17.sp
private const val TILE_TRANSLATION_LINES = 2

private val TILE_REFERENCE_SP = 10.sp

private val TILE_FOOTER_GAP = 8.dp
private val TILE_ACTION_SIZE = 26.dp
private val TILE_ACTION_GLYPH = 12.dp
private val TILE_ACTION_GAP = 6.dp

/** The list row: a leading square, three lines of text beside it, and the two actions. */
private val ROW_PADDING = 12.dp
private val ROW_THUMB_SIZE = 64.dp
private val ROW_THUMB_GLYPH = 28.dp
private val ROW_THUMB_SHAPE = 8.dp
private val ROW_THUMB_GAP = 12.dp
private val ROW_ACTION_GAP = 12.dp
private val ROW_PASSAGE_SP = 14.sp
private val ROW_PASSAGE_LINE_SP = 19.sp
private const val ROW_PASSAGE_LINES = 1
private val ROW_TRANSLATION_SP = 12.sp
private val ROW_TRANSLATION_LINE_SP = 16.sp
private const val ROW_TRANSLATION_LINES = 1

/**
 * Where a source line stops being a publisher and starts being a citation.
 *
 * Kept because the search results in the library still print the clamped publisher beside a row, even
 * though the collection card no longer has anywhere to put a badge. That is the one other reader of a
 * tile's `source`, and it is why this is a shared function rather than a private detail of a file
 * that used to draw a badge.
 */
private const val SOURCE_TITLE_SEPARATOR = " - "
private const val SOURCE_NUMBER_MARK = "#"

/**
 * The quotation marks wrapped around the translation.
 *
 * Written here rather than pulled from resources because they are punctuation: they are the same in
 * every language the app ships, and a translatable quotation mark would be a translation of nothing.
 */
private const val TRANSLATION_QUOTE_OPEN = "“ "
private const val TRANSLATION_QUOTE_CLOSE = " ”"
