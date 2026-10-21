package com.azkar.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.azkar.generated.resources.Res
import com.azkar.generated.resources.collectionsActionOpen
import com.azkar.generated.resources.collectionsMemberCount
import com.azkar.generated.resources.collectionsProgressFormat
import com.azkar.generated.resources.libraryActionMore
import com.azkar.ui.components.ActionIcons
import com.azkar.ui.components.AzkarCard
import com.azkar.ui.components.AzkarMenuItem
import com.azkar.ui.components.AzkarOverflowMenu
import com.azkar.ui.components.CompletionTrack
import com.azkar.ui.components.IconTint
import com.azkar.ui.components.TooltipIconButton
import com.azkar.ui.components.pressableClick
import com.azkar.ui.library.model.CollectionTile
import com.azkar.ui.preview.previewCollectionTiles
import com.azkar.ui.theme.AzkarTheme
import com.azkar.ui.theme.AzkarThemePreview
import com.azkar.ui.theme.CardSize
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * One collection on the shelf: a cover, the kind of collection it is, its name, and a line of the
 * first thing in it.
 *
 * The badge sits on the cover near its bottom edge, and the cover's two corner controls sit at its
 * top — the menu on the right, where a card's actions belong, and the pin on the left, where it can
 * never overlap the menu.
 *
 * @param preview the opening words of one of the collection's own remembrances. That, rather than the
 * description, is what tells a user which collection this is before they open it — every shipped
 * collection's description says the same kind of thing, so eleven descriptions side by side say
 * nothing at all.
* @param items the overflow menu's contents. Built by the screen that owns the shelf, so a grid of
 * cards does not each carry a copy of the same five entries.
 * @param covers the decoder that turns a stored cover name into a picture. Nullable so that a caller
 * with no storage behind it — a preview, a screenshot — can pass nothing, but deliberately **not
 * defaulted**: a card that quietly has no decoder cannot draw the picture the user picked and falls
 * back to the generated wash, which is indistinguishable from the app having thrown the cover away.
 * Every call site has to say what it has.
 *
 * Every text region below states an explicit `lineHeight` and sits in a box of a fixed height, and the
 * caller gives the whole card a fixed height too. That is not fussiness: Amiri's natural line box
 * is far taller than Inter's, so a card sized by its content came out taller in Arabic than in
 * English, and because a preview that fits on one line is *shorter* than one that needs two, the
 * cards in a single row ended up different heights from each other. An explicit `lineHeight` is what
 * normalises the two scripts — it replaces the font's own metrics — and the fixed boxes stop a short
 * preview from making its card shorter than its neighbour.
 */
@Composable
fun CollectionCard(
    tile: CollectionTile,
    preview: String,
    items: List<AzkarMenuItem>,
    onOpen: () -> Unit,
    covers: CollectionCoverCache?,
    modifier: Modifier = Modifier,
    trailingAction: (@Composable () -> Unit)? = null,
) {
    AzkarCard(
        modifier =
            modifier
                .fillMaxWidth()
                .pressableClick(scale = 1f, onClick = onOpen),
        size = CardSize.MD,
        shape = RoundedCornerShape(CARD_SHAPE.dp),
        contentPadding = 0.dp,
    ) {
        Column {
            CardCover(tile = tile, items = items, covers = covers)
            Column(modifier = Modifier.padding(horizontal = CARD_INSET, vertical = 10.dp)) {
                Text(
                    text = tile.name,
                    color = AzkarTheme.colors.textPrimary,
                    fontSize = NAME_SP,
                    lineHeight = NAME_LINE_SP,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.height(NAME_ROW_HEIGHT),
                )
                Text(
                    text = preview,
                    color = AzkarTheme.colors.textSecondary,
                    fontSize = PREVIEW_SP,
                    lineHeight = PREVIEW_LINE_SP,
                    fontStyle = FontStyle.Italic,
                    maxLines = PREVIEW_LINES,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp).height(PREVIEW_BLOCK_HEIGHT),
                )
            }
            CardFooter(tile = tile, onOpen = onOpen, trailingAction = trailingAction)
        }
    }
}

@Preview
@Composable
private fun CollectionCardPreview() {
    AzkarThemePreview {
        Column(
            modifier = Modifier.width(1100.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                previewCollectionTiles().forEach { tile ->
                    CollectionCard(
                        tile = tile,
                        preview = "The most beloved of deeds to Allah are the most consistent.",
                        items =
                            listOf(
                                AzkarMenuItem(label = "Edit", onClick = {}, icon = ActionIcons.edit),
                                AzkarMenuItem(label = "Share", onClick = {}, icon = ActionIcons.share),
                            ),
                        onOpen = {},
                        covers = null,
                        modifier = Modifier.width(240.dp),
                    )
                }
            }
        }
    }
}

/**
 * The picture at the top of the card, the badge on it, and the two controls in its corners.
 *
 * The generated wash rather than a mosaic of the collection's own passages. A mosaic is a good
 * answer when a cover has to prove what is inside it, but at this size four snippets share a band
 * that is 116dp tall and the badge sits across the bottom of it, so the text ends up unreadable
 * under the badge — decoration that hides the thing it decorates. The preview directly below the
 * cover already carries one of those passages in full sentences.
 *
 * A cover the user chose replaces the wash entirely — see [CollectionCoverArt] — and every overlay
 * here is a sibling in the same box, so the badge, the pin, the star and the menu are placed over the
 * photograph without any of them knowing which kind of cover is underneath.
 */
@Composable
private fun CardCover(
    tile: CollectionTile,
    items: List<AzkarMenuItem>,
    covers: CollectionCoverCache?,
) {
    val colors = AzkarTheme.colors
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(CARD_COVER_HEIGHT)
                .clip(RoundedCornerShape(topStart = CARD_SHAPE.dp, topEnd = CARD_SHAPE.dp)),
    ) {
        CollectionCoverArt(
            iconKey = tile.iconKey,
            modifier = Modifier.fillMaxSize(),
            iconSize = CARD_COVER_GLYPH,
            coverImagePath = tile.coverImagePath,
            covers = covers,
        )
        // The pin, the star and the bell share the leading corner because all three are marks *about*
        // the card rather than controls on it: nothing to press, nothing to open, only a claim about
        // where the collection sits, whether the user wants it, or whether the queue draws from it.
        // They stack left to right in a fixed order — pin first, because it is the older of the claims
        // and the one that moves the card, and the bell last because it is the newest — and the row is
        // as wide as it needs to be, so a card that is pinned but not starred does not reserve room for
        // a star it is not showing.
        val marks =
            listOfNotNull(
                if (tile.isPinned) ActionIcons.pin(true) else null,
                if (tile.isFavorite) ActionIcons.favorite(true) else null,
                if (tile.inReminders) ActionIcons.reminder(true) else null,
            )
        if (marks.isNotEmpty()) {
            Row(
                modifier = Modifier.align(Alignment.TopStart).padding(CORNER_INSET),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(COVER_MARK_GAP),
            ) {
                marks.forEach { mark ->
                    CoverMark(icon = mark)
                }
            }
        }
        // The marks and the menu are on opposite sides because they answer different questions: the
        // marks say something about this card, the menu offers to do something to it. Putting them all
        // in one corner meant that marking a card hid the menu behind its own badge, and the menu is
        // on every card while the marks are on none.
        AzkarOverflowMenu(
            icon = Icons.Filled.MoreVert,
            contentDescription = stringResource(Res.string.libraryActionMore),
            items = items,
            modifier = Modifier.align(Alignment.TopEnd).padding(CORNER_INSET),
            buttonSize = 24.dp,
            iconSize = 13.dp,
            background = colors.cardBackground,
        )
        CollectionBadge(tile = tile, modifier = Modifier.align(Alignment.BottomStart))
        CoverProgress(tile = tile)
    }
}

/**
 * One of the card's marks: a glyph on a disc, tinted with the accent.
 *
 * Out of a private function because the pin and the star are the same object drawn twice and the only
 * thing that differs is the glyph — the disc, its size and its colour are what make a row of them read
 * as one row of badges rather than as two unrelated stickers.
 *
 * The accent rather than the primary because nothing here is a control: there is no state to press and
 * no action to take, so these want the colour the app already uses for "this one is marked" rather than
 * the one it uses for "press this".
 */
@Composable
private fun CoverMark(icon: ImageVector) {
    IconTint(color = AzkarTheme.colors.accent) {
        Box(
            modifier =
                Modifier
                    .size(COVER_MARK_SIZE)
                    .clip(CircleShape)
                    .background(AzkarTheme.colors.cardBackground),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(COVER_MARK_GLYPH),
            )
        }
    }
}

/**
 * The pill that says whether a collection ships with the app or the user made it.
 *
 * It says the *kind* rather than the collection's name, which the title three lines below already
 * says. A badge repeating the title would be decoration; this answers a question the title does not
 * — can this one be renamed, does it come from the catalogue — and answering it on every card saves
 * opening each one to find out.
 *
 * It is inset by [CARD_INSET], the same measure the title and the footer's count use, so the card has
 * one left edge rather than three that disagree by a couple of dp — the difference is invisible in
 * isolation and visible as a card whose badge appears to slide out of line with the words under it.
 * It is also lifted clear of the cover's bottom edge instead of sitting welded to it, which leaves a
 * strip of picture below it and makes the pill read as a label laid on the image rather than as a
 * structural part of the card. The layout this card came from had the pill straddling the edge; half
 * on and half off put its lower half across the gap to the title, which is worse than either
 * alternative, so it sits wholly on the picture with air around it.
 *
 * It is filled with the card's own surface at full strength, where it used to be a translucent wash of
 * the window's background. That was tuned against covers that were dark in both themes; now that a
 * cover is a pale tint of the card colour in the light theme, a near-white pill at four-fifths strength
 * over it was a slightly paler patch of the same colour. `cardBackground` is the one colour guaranteed
 * to contrast with the cover in both themes, and the hairline above keeps the pill an object in its own
 * right on the dark wash, where a solid dark pill would otherwise be a hole.
 */
@Composable
private fun CollectionBadge(
    tile: CollectionTile,
    modifier: Modifier = Modifier,
) {
    val colors = AzkarTheme.colors
    Box(
        modifier =
            modifier
                .padding(start = CARD_INSET, bottom = BADGE_LIFT)
                .clip(RoundedCornerShape(BADGE_SHAPE.dp))
                .background(colors.cardBackground)
                .border(1.dp, colors.border, RoundedCornerShape(BADGE_SHAPE.dp))
                .padding(horizontal = BADGE_PAD_X, vertical = BADGE_PAD_Y),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTint(color = colors.primary) {
                Icon(
                    imageVector = iconForCollection(tile.iconKey),
                    contentDescription = null,
                    modifier = Modifier.size(BADGE_ICON_SIZE.dp),
                )
            }
            Spacer(Modifier.width(BADGE_ICON_GAP.dp))
            Text(
                text = stringResource(tile.kind.labelResource),
                color = colors.textPrimary,
                fontSize = BADGE_TEXT_SP,
                lineHeight = BADGE_TEXT_LINE_SP,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The row under the preview: how far through the collection the reader is, and a way in.
 *
 * Once something has been recited the count line becomes "12 of 61" rather than "61 remembrances",
 * because the second figure is already in the first and repeating it would spend the row saying
 * less. A collection that has not been started keeps the count alone — "0 of 61" on fourteen cards
 * is fourteen identical statements, and the only one worth making is the one that changed.
 *
 * There is deliberately no source line here. The layout this card came from drew a provenance line
 * under every cover, and there is nothing honest to put in one — the app does not know where a
 * remembrance came from, only what the user starred. A row of invented provenance would be worse
 * than the missing row.
 *
 * **Separated by a hairline rather than a filled band.** The band was `hoverNeutralSoft` with its
 * alpha overwritten, and overwriting alpha is not the same operation as making something fainter:
 * `Color.copy` replaces it, so a theme colour that meant "8% white" was being drawn at a flat 35%,
 * which over the dark card's `#121B2E` came out a mid grey `#646A77` — a slab of colour at the
 * bottom of every card, heavier than the one line of 10sp text it was holding. A hairline says the
 * same thing with a pixel instead of a block.
 */
@Composable
private fun CardFooter(
    tile: CollectionTile,
    onOpen: () -> Unit,
    trailingAction: (@Composable () -> Unit)? = null,
) {
    val colors = AzkarTheme.colors
    val count =
        if (tile.recitedCount > 0) {
            stringResource(Res.string.collectionsProgressFormat, tile.recitedCount, tile.memberCount)
        } else {
            pluralStringResource(Res.plurals.collectionsMemberCount, tile.memberCount, tile.memberCount)
        }
    Column {
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = CARD_INSET),
            color = colors.border,
        )
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = CARD_INSET, end = CARD_INSET, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = count,
                color = colors.textSecondary,
                fontSize = FOOTER_TEXT_SP,
                lineHeight = FOOTER_TEXT_LINE_SP,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            TooltipIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowForward,
                tooltip = stringResource(Res.string.collectionsActionOpen),
                onClick = onOpen,
                buttonSize = 26.dp,
                iconSize = 12.dp,
            )
            // The slot the shelf's own extra control sits in, when it has one. It is a slot rather
            // than a boolean because the control is the caller's — the grip that reorders the shelf
            // lives in this footer, but the card itself has no opinion about what a card can be asked
            // to do.
            trailingAction?.invoke()
        }
    }
}

/** How round a card is, in dp. The cover and the card share it so the picture has no corner of its own. */
private const val CARD_SHAPE = 12

/**
 * The card's one horizontal inset: the badge, the title and the preview share its left edge, and the
 * footer row is held by it at both ends.
 *
 * One constant rather than the three literals it replaced. The badge had drifted to 10dp while the
 * words under it sat at 12dp, which is not enough to be worth arguing for on its own — but it is
 * enough that a column of cards reads as having been laid out by hand, twice, slightly differently.
 */
private val CARD_INSET = 12.dp

/** How far the cover's two corner controls stand off it, and therefore off each other. */
private val CORNER_INSET = 8.dp

/**
 * The disc a mark is drawn on, and the glyph inside it.
 *
 * The pin has always been 22dp with a 12dp glyph and both are kept rather than recomputed, because a
 * star next to a pin at a different size reads as a different kind of thing — which would be a lie,
 * since both are the same claim in two words.
 */
private val COVER_MARK_SIZE = 22.dp

private val COVER_MARK_GLYPH = 12.dp

/**
 * The air between two marks.
 *
 * Half the glyph, so the pair reads as two badges sitting close together rather than as one badge and
 * one loose mark. A full disc's width would pull them apart; the glyphs' own width would touch.
 */
private val COVER_MARK_GAP = 6.dp

/**
 * How far the badge sits above the cover's bottom edge, so there is picture below it rather than the
 * pill meeting the seam.
 */
private val BADGE_LIFT = 8.dp

/** The badge's own padding, a touch more vertically than horizontally so the pill is not squat. */
private val BADGE_PAD_X = 9.dp
private val BADGE_PAD_Y = 5.dp

/**
 * The height every card is drawn at, in dp.
 *
 * Cover 116, then 10 above the title, [NAME_ROW_HEIGHT], 4, [PREVIEW_BLOCK_HEIGHT], 10 below, and a
 * footer of a 1dp hairline, 8, [FOOTER_TEXT_LINE_SP] and 8. Over-provisioning on purpose: the card's
 * own column puts the slack above the footer, so a little extra is air, whereas a card that came up
 * short would put its footer over the edge — and the card clips, so over the edge means gone.
 */
internal val COLLECTION_CARD_HEIGHT = 224.dp

/** The title's size, and the line box it is given so both scripts agree on it. */
private val NAME_SP = 14.sp
private val NAME_LINE_SP = 21.sp

/** The title's row: one line, held at its height so a short name cannot shorten the card. */
private val NAME_ROW_HEIGHT = 21.dp

/** The passage preview: two lines at a fixed line height, whatever the script. */
private val PREVIEW_SP = 11.sp
private val PREVIEW_LINE_SP = 16.sp
private const val PREVIEW_LINES = 2
private val PREVIEW_BLOCK_HEIGHT = 32.dp

/** The badge's own text, small enough to sit on a picture without becoming the loudest thing there. */
private val BADGE_TEXT_SP = 10.sp
private val BADGE_TEXT_LINE_SP = 14.sp

/** The footer's count, given a line height for the same reason the rest of the card is. */
private val FOOTER_TEXT_SP = 10.sp
private val FOOTER_TEXT_LINE_SP = 14.sp

/** How tall the picture at the top of a card is, in dp. */
private val CARD_COVER_HEIGHT = 116.dp

/** The size of the glyph washed into the top of the picture, in dp. */
private val CARD_COVER_GLYPH = 64.dp

/** How round the badge across the cover's edge is, in dp. */
private const val BADGE_SHAPE = 9

/** The glyph inside that badge, in dp. */
private const val BADGE_ICON_SIZE = 11

/** The gap between that glyph and the badge's words, in dp. */
private const val BADGE_ICON_GAP = 5

/** The progress track across the cover's bottom edge, in dp. */
private val COVER_TRACK_HEIGHT = 4.dp

/**
 * What the progress track looks like before any of it has been recited, and after all of it.
 *
 * One themed ink at two strengths. It used to be black for the idle end and white for the finished
 * one, which is the right idea — the only boundary that matters in a progress track is the one
 * between done and not done, and it has to be the strongest contrast on the card — but it cannot be
 * done with two fixed colours once the cover behind it is a tint the theme decides: white at 95% is
 * invisible on a pale wash, and black at 28% was invisible on the deep one. `textPrimary` is the
 * theme's own ink, so at 18% it is a faint mark and at 85% it is a solid one in either theme.
 */
@Composable
private fun BoxScope.CoverProgress(tile: CollectionTile) {
    if (tile.recitedCount <= 0) {
        return
    }
    CompletionTrack(
        completion = tile.completion,
        modifier = Modifier.align(Alignment.BottomCenter),
        height = COVER_TRACK_HEIGHT,
        shape = RectangleShape,
        trackColor = AzkarTheme.colors.textPrimary.copy(alpha = TRACK_IDLE_ALPHA),
        fillColor = AzkarTheme.colors.textPrimary.copy(alpha = TRACK_DONE_ALPHA),
    )
}

/** How faintly the untouched end of the track shows, and how solidly the recited end does. */
private const val TRACK_IDLE_ALPHA = 0.18f
private const val TRACK_DONE_ALPHA = 0.85f
