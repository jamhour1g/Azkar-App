package com.azkar.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.azkar.generated.resources.*
import com.azkar.ui.components.ActionIcons
import com.azkar.ui.components.AzkarDialogSurface
import com.azkar.ui.components.AzkarIcons
import com.azkar.ui.components.AzkarSurfaceCard
import com.azkar.ui.components.TooltipIconButton
import com.azkar.ui.components.mirrorHorizontalGradient
import com.azkar.ui.components.pressableClick
import com.azkar.ui.preview.previewFavoriteState
import com.azkar.ui.theme.AzkarTheme
import com.azkar.ui.theme.AzkarThemePreview
import com.azkar.ui.theme.isRtlLayout
import org.jetbrains.compose.resources.stringResource

/**
 * The favourites watermark's resting opacity.
 *
 * Below the azkar card's 0.16f, but not by much, and for a reason that only holds in one palette:
 * the dark wash underneath is already a saturated amber, so matching the blue card's opacity there
 * would print the texture twice over. The light wash is barely off white — see the note on
 * [AzkarColors.favoriteCardStart] — so in the light palette this watermark, the card's edge and the
 * count pill are what actually say "amber" out loud.
 */
private const val FAVORITE_WATERMARK_ALPHA = 0.13f

/** The count pill is a wash of the accent rather than a second solid colour, so it needs a low alpha. */
private const val FAVORITE_BADGE_ALPHA = 0.18f

/**
 * The prev/next targets.
 *
 * The FXML arrows were 28dp discs, which are easy to miss with a trackpad and sit well under the
 * 48dp a touch target wants. 36dp is comfortable for a pointer without letting the pair out-measure
 * the 20sp dhikr it belongs to.
 */
private val FAVORITE_NAV_TARGET_SIZE = 36.dp

/** The arrowhead inside [FAVORITE_NAV_TARGET_SIZE]; scaled up from the 12dp default to match. */
private val FAVORITE_NAV_GLYPH_SIZE = 16.dp

/** The `.hadith-vertical-line` + `hadith-texts` pair moved into the details dialog. */
@Composable
fun DailyHadithDetailsDialog(
    arabic: String,
    english: String,
    source: String?,
    onDismiss: () -> Unit,
) {
    val colors = AzkarTheme.colors
    Dialog(onDismissRequest = onDismiss) {
        AzkarDialogSurface(modifier = Modifier.widthIn(max = 700.dp)) {
            Text(
                text = stringResource(Res.string.dailyHadithDetailsTitle),
                color = colors.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(Res.string.dailyHadithDetailsHeader),
                color = colors.textSecondary,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    Modifier
                        .width(3.dp)
                        .height(140.dp)
                        .background(colors.primary),
                )
                Spacer(Modifier.width(20.dp))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = arabic,
                        color = colors.textPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 32.sp,
                    )
                    Text(
                        text = english,
                        color = colors.hadithEnglish,
                        fontSize = 16.sp,
                        fontStyle = FontStyle.Italic,
                    )
                    Text(
                        text = source ?: stringResource(Res.string.noHadithSource),
                        color = colors.hadithSource,
                        fontSize = 13.sp,
                    )
                }
            }
        }
    }
}

/**
 * `FavoritesCardComponent` — the user's own pick, over the warm amber wash.
 *
 * The JavaFX version was a plain white bordered card with a 30sp `Favorite Azkar` heading above
 * 24sp of Arabic and every line centred. Three things were wrong with that, and all three were the
 * same mistake in different clothes: the chrome out-ranked the content. A heading that big reads as
 * the subject of the card, and the dhikr was demoted to a caption under it; centred multi-line prose
 * in a 380dp rail gives the eye no entry point, because neither the Arabic nor its explanation has a
 * line it can start from; and a flat white surface put the card in the same design language as an
 * empty panel while [TodaysAzkarCard] sat a few centimetres away with a wash and a khatim on it.
 *
 * So this card now mirrors its sibling's construction — [AzkarSurfaceCard], a mirrored wash, the
 * same [AzkarIcons.Khatim] watermark — in the accent's hue, and spends the space the heading used to
 * waste on the dhikr instead. The heading survives as a 15sp label because the star and the label
 * are what tell the user this is *their* pick rather than another card of the day's.
 *
 * One deliberate departure from the sibling: the edge is drawn in [AzkarColors.favoriteCardBorder]
 * rather than the shared [AzkarColors.border]. The wash alone cannot carry the distinction in the
 * light palette, because the deepest amber the grey body text can sit on is indistinguishable from
 * the page; an edge can, and it costs the reader nothing in legibility.
 *
 * The arrow glyphs were flipped by `configureDirectionalIcons()`; [isRtlLayout] drives the same
 * swap now that the whole tree already carries a layout direction.
 */
@Composable
fun FavoritesCard(
    state: FavoriteState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onBrowse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AzkarTheme.colors
    AzkarSurfaceCard(
        modifier = modifier.fillMaxWidth(),
        background =
            mirrorHorizontalGradient(
                listOf(colors.favoriteCardStart, colors.favoriteCardEnd),
                isRtlLayout(),
            ),
        border = BorderStroke(1.dp, colors.favoriteCardBorder),
        contentPadding = 18.dp,
    ) {
        Icon(
            imageVector = AzkarIcons.Khatim,
            contentDescription = null,
            tint = colors.favoritePatternTint.copy(alpha = FAVORITE_WATERMARK_ALPHA),
            modifier = Modifier.align(Alignment.CenterEnd).size(190.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FavoritesHeader(state)
            FavoritesBody(state)
            HorizontalDivider(color = colors.border)
            FavoritesFooter(state, onPrevious, onNext, onBrowse)
        }
    }
}

@Preview
@Composable
private fun FavoritesCardPreview() {
    AzkarThemePreview {
        Column(
            modifier = Modifier.width(560.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            FavoritesCard(
                state = previewFavoriteState(),
                onPrevious = {},
                onNext = {},
                onBrowse = {},
            )
        }
    }
}

/**
 * The label row: star, name, and how many favourites the arrows are cycling through.
 *
 * [FavoriteState.total] doubles as the badge's visibility switch. With one favourite there is
 * nothing to count, and with none the footer says so; printing "1 / 1" over a card the user cannot
 * page through would be noise.
 */
@Composable
private fun FavoritesHeader(state: FavoriteState) {
    val colors = AzkarTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Icon(
            imageVector = AzkarIcons.Star,
            contentDescription = null,
            tint = colors.accent,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = stringResource(Res.string.favoriteAzkar),
            color = colors.textPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
        if (state.total > 1) {
            Text(
                text = state.total.toString(),
                color = colors.accentInk,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier =
                    Modifier
                        .clip(CircleShape)
                        .background(colors.accent.copy(alpha = FAVORITE_BADGE_ALPHA))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

/**
 * The dhikr itself: the locale-resolved text, its explanation, and where it comes from.
 *
 * Leading-aligned, and SemiBold rather than Bold at a smaller size — the previous 24sp Bold centred
 * on three lines was both the loudest type on the screen and the hardest to read. The source moves
 * down to a caption behind a bookmark, matching [TodaysAzkarCard]'s idiom, so a book reference no
 * longer reads as a second explanation.
 */
@Composable
private fun FavoritesBody(state: FavoriteState) {
    val colors = AzkarTheme.colors
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = state.arabic,
            color = colors.textPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 30.sp,
            textAlign = TextAlign.Start,
        )
        Text(
            text = state.english,
            color = colors.textSecondary,
            fontSize = 14.sp,
            lineHeight = 21.sp,
            textAlign = TextAlign.Start,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = ActionIcons.source,
                contentDescription = null,
                tint = colors.hadithSource,
                modifier = Modifier.size(12.dp),
            )
            Text(
                text = state.source,
                color = colors.hadithSource,
                fontSize = 12.sp,
            )
        }
    }
}

/**
 * Where the user is in the carousel, the arrows that move them, and the way out to the library.
 *
 * The old left-hand slot held a filled `★ Favorited` chip, which restated the heading it sat under
 * and carried no information. It is replaced by the position counter, which is the one thing about
 * a carousel the user cannot otherwise see.
 */
@Composable
private fun FavoritesFooter(
    state: FavoriteState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onBrowse: () -> Unit,
) {
    val colors = AzkarTheme.colors
    val rtl = isRtlLayout()
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text =
                if (state.total > 0) {
                    stringResource(Res.string.favoritePosition, state.position, state.total)
                } else {
                    stringResource(Res.string.noFavoritesYet)
                },
            color = colors.textSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TooltipIconButton(
                icon = if (rtl) Icons.Filled.ChevronRight else Icons.Filled.ChevronLeft,
                tooltip = stringResource(Res.string.favoritePrevLabel),
                onClick = onPrevious,
                enabled = state.navigationEnabled,
                buttonSize = FAVORITE_NAV_TARGET_SIZE,
                iconSize = FAVORITE_NAV_GLYPH_SIZE,
                background = colors.favoritesNavBackground,
                tint = colors.favoritesNavText,
            )
            TooltipIconButton(
                icon = if (rtl) Icons.Filled.ChevronLeft else Icons.Filled.ChevronRight,
                tooltip = stringResource(Res.string.favoriteNextLabel),
                onClick = onNext,
                enabled = state.navigationEnabled,
                buttonSize = FAVORITE_NAV_TARGET_SIZE,
                iconSize = FAVORITE_NAV_GLYPH_SIZE,
                background = colors.favoritesNavBackground,
                tint = colors.favoritesNavText,
            )
        }
        BrowseButton(text = stringResource(Res.string.azkarLibrary), onClick = onBrowse)
    }
}

/**
 * `.browse-btn` — the hand-off to the Azkar library.
 *
 * The FXML version was a pill filled with `highlight`, which made it the loudest element in a rail
 * whose subject is a dhikr: a saturated block competing with the card itself. An outline in the
 * library's own blue says the same "this goes elsewhere" without adding a second filled surface,
 * and it stays legible on the warm wash.
 */
@Composable
private fun BrowseButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AzkarTheme.colors
    Row(
        modifier =
            modifier
                .clip(CircleShape)
                .border(1.dp, colors.browseIcon, CircleShape)
                .pressableClick(scale = 1f, onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = text,
            color = colors.browseIcon,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
            contentDescription = null,
            tint = colors.browseIcon,
            modifier = Modifier.size(14.dp),
        )
    }
}
