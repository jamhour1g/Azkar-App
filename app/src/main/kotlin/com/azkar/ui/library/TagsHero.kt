package com.azkar.ui.library

/**
 * `ui/library/TagsHero.kt` — the band across the top of the Manage tags page.
 *
 * It lives apart from the page for the same reason the toolbar and the dialogs do: it is one object
 * with its own reasons, not a section of the list. The page is a list of tags under a toolbar, and
 * this is the banner you open to read the size of the whole vocabulary before you read any of it.
 */

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.azkar.generated.resources.Res
import com.azkar.generated.resources.tagsActionBack
import com.azkar.generated.resources.tagsActionNew
import com.azkar.generated.resources.tagsStatCollections
import com.azkar.generated.resources.tagsStatRemembrances
import com.azkar.generated.resources.tagsStatTags
import com.azkar.generated.resources.tagsSubtitle
import com.azkar.generated.resources.tagsTitle
import com.azkar.i18n.usesArabicTypeface
import com.azkar.ui.components.AzkarIcons
import com.azkar.ui.components.AzkarSurfaceCard
import com.azkar.ui.components.KhatimPattern
import com.azkar.ui.components.mirrorHorizontalGradient
import com.azkar.ui.theme.AzkarSpacing
import com.azkar.ui.theme.AzkarTheme
import com.azkar.ui.theme.AzkarThemePreview
import com.azkar.ui.theme.isRtlLayout
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The band across the top: the page's name, a way back, the one control that adds, and what the whole
 * vocabulary adds up to.
 *
 * It is the collection detail band, in every part that carries the app's look: the same three painted
 * layers, the same back link, the same brand-gradient badge, the same pill for the action, the same
 * [HeroFact] strip, and the same radius, inset and gaps to the dp. All of those are borrowed rather
 * than restated, because the two bands sit one navigation apart and a reader who has seen one has seen
 * the other — so a difference between them does not read as a design choice, it reads as this page
 * having been forgotten.
 *
 * The three layers are the reason this band was rebuilt. It used to carry the artwork under a plain
 * vertical wash with a mosque glyph floated in the corner; the collection band carries the artwork
 * under an RTL-aware wash *and* the [KhatimPattern] texture. That texture is what makes the two
 * look like the same object, and the extra mosque was not helping: the illustration already is the
 * app's artwork and already has a mosque in it.
 *
 * The height is a sum of the copy alone and the copy is anchored to the top, both of them borrowed
 * from [CollectionDetailHero]. That band is where these two habits came from and they are worth
 * stating, because this one got both of them wrong first: it pinned the title and the facts to the
 * bottom of a fixed height with a spring between, which on a tall window put the subtitle halfway down
 * the picture and made the back link read as a toolbar sitting on a poster rather than as the first
 * line of a header. The spring spent its slack on the gap between two rows that are one object.
 *
 * The counts are here as well as on every row because they are the only three figures about the
 * vocabulary as a whole, and a page whose rows all carry the same two numbers answers nothing about
 * its own size. They are deliberately *not* filtered: they describe the library, not the current
 * selection, and a band whose totals moved when a dropdown did would be reporting on itself.
 */
@Composable
internal fun TagsHero(
    stats: TagStats,
    onBack: () -> Unit,
    onCreate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AzkarTheme.colors
    // Read once here rather than inside the layout, so that the height the band is given and the
    // arithmetic that justifies it are two names apart and can only disagree by being edited apart.
    val height = if (AzkarTheme.locale.usesArabicTypeface) HERO_HEIGHT_ARABIC else HERO_HEIGHT_LATIN
    AzkarSurfaceCard(
        modifier = modifier.fillMaxWidth().height(height),
        shape = RoundedCornerShape(HERO_SHAPE),
        background = SolidColor(colors.background),
        elevation = 0.dp,
        border = BorderStroke(1.dp, colors.border),
        contentPadding = 0.dp,
    ) {
        HeroBackdrop()
        Column(
            modifier = Modifier.fillMaxSize().padding(HERO_INSET),
            verticalArrangement = Arrangement.spacedBy(HERO_GAP),
        ) {
            HeroNavRow(onBack = onBack, onCreate = onCreate)
            HeroTitle()
            TagHeroStats(stats)
        }
    }
}

/**
 * The three layers the band is painted on: the artwork, the wash that makes the copy legible over it,
 * and the pattern.
 *
 * This is [CollectionDetailHero]'s `HeroBackdrop` by another name, written out rather than shared
 * only because the original is private to its file. The three have to stay in this order — the
 * pattern sits *over* the artwork, not behind it — and the wash is mirrored rather than fixed because
 * the copy is at the leading edge, which in Arabic is the right-hand one.
 */
@Composable
private fun HeroBackdrop() {
    val colors = AzkarTheme.colors
    Image(
        painter = painterResource(heroIllustration()),
        contentDescription = null,
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.Crop,
        alignment = Alignment.BottomEnd,
    )
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(mirrorHorizontalGradient(HeroScrim(colors), isRtlLayout())),
    )
    KhatimPattern(
        tint = colors.artPatternTint,
        modifier = Modifier.fillMaxSize(),
    )
}

/**
 * The way back and the one control that adds, both as pills.
 *
 * [HeroPill] rather than an icon button because that is what the collection band puts here, and a
 * back link that is a bare glyph is one the reader has to decode. Naming the destination costs a
 * line of width and saves the decode.
 */
@Composable
private fun HeroNavRow(
    onBack: () -> Unit,
    onCreate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HeroPill(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            label = stringResource(Res.string.tagsActionBack),
            onClick = onBack,
        )
        Spacer(Modifier.weight(1f))
        HeroPill(
            icon = Icons.Filled.Add,
            label = stringResource(Res.string.tagsActionNew),
            onClick = onCreate,
        )
    }
}

/** The mark, the page's name, and the one line saying what the page is for. */
@Composable
private fun HeroTitle(modifier: Modifier = Modifier) {
    val colors = AzkarTheme.colors
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TagMark()
        Spacer(Modifier.width(HERO_TITLE_GAP))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(Res.string.tagsTitle),
                color = colors.textPrimary,
                fontSize = HERO_TITLE_SP,
                lineHeight = HERO_TITLE_LINE_SP,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(Res.string.tagsSubtitle),
                color = colors.textPrimary.copy(alpha = HERO_SUBTITLE_ALPHA),
                fontSize = HERO_BODY_SP,
                lineHeight = HERO_BODY_LINE_SP,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = HERO_TITLE_GAP),
            )
        }
    }
}

/**
 * The rounded square beside the title.
 *
 * The brand gradient and a white glyph, which is the collection band's badge exactly. It was a
 * washed white square for as long as this band was a blue gradient, where a wash was the only thing
 * that would read on it; over the artwork a white square is a hole in the picture.
 */
@Composable
private fun TagMark(modifier: Modifier = Modifier) {
    val colors = AzkarTheme.colors
    Box(
        modifier =
            modifier
                .size(HERO_BADGE)
                .clip(RoundedCornerShape(HERO_BADGE_SHAPE))
                .background(Brush.linearGradient(listOf(colors.brandTileStart, colors.brandTileEnd))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Label,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(HERO_BADGE_GLYPH),
        )
    }
}

/**
 * The three figures under the title, with rules between them.
 *
 * [HeroFact] and [HeroFactDivider], borrowed whole. An earlier version of this row drew its own facts
 * and argued that the shared one could not be used because it paints muted ink — which was reasoning
 * from the old blue band, where muted grey genuinely was a contrast the band could not pay. It is not
 * a blue band any more, and two fact strips in one app that differ only in their ink is the exact
 * drift this rebuild is for.
 */
@Composable
private fun TagHeroStats(
    stats: TagStats,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HERO_FACT_GAP),
    ) {
        HeroFact(
            icon = Icons.AutoMirrored.Filled.Label,
            text = pluralStringResource(Res.plurals.tagsStatTags, stats.total, stats.total),
        )
        HeroFactDivider()
        HeroFact(
            icon = AzkarIcons.BookOpen,
            text = stringResource(Res.string.tagsStatCollections, stats.usedInCollections),
        )
        HeroFactDivider()
        HeroFact(
            icon = Icons.AutoMirrored.Outlined.FormatListBulleted,
            text = stringResource(Res.string.tagsStatRemembrances, stats.taggedRemembrances),
        )
    }
}

// Every measurement here is the collection band's to the dp, so the two read as one object rather
// than as two pages' separate ideas of a header. The height is the shelf banner's.
private val HERO_SHAPE = 20.dp

/**
 * The band's height, as a sum of the copy alone.
 *
 * Nothing here is pinned to the bottom. The collection band anchors its copy to the top inset — that
 * is what keeps it level with the row of action pills beside it — and this one copies that, so the
 * slack falls at the bottom where it cannot move anything. An earlier version of this band did the
 * opposite and put a spring between the back link and the title, which pushed the title and the facts
 * to the foot of a fixed 236dp and left the two rows of the band's copy reading as two unrelated
 * things with a hole between them.
 *
 * The number that matters is that the sum is of *line boxes* and not of font sizes, because those are
 * not the same thing here. The title and the subtitle both name a `lineHeight`, the back link is a
 * [HeroPill] and so is a fixed height, and the facts row asks for a `fontSize` and inherits the line
 * box of whatever `LocalTextStyle` is ambient — which for this app is `bodyLarge`, 24sp of leading in
 * Inter and 32sp in Amiri. An 11sp fact therefore occupies a 24sp box, and the band is taller in
 * Arabic whether or not anybody chose that, which is why there are two of these.
 *
 * Measured rather than counted, by laying the copy out and reading the bounds of the last line in
 * each script: a 36 back link, 9 of gap, a 58 title block (the badge asks for 42 and the column wins,
 * at 12 of subtitle padding, a 29 title box and a 17 subtitle box), 9, and a 26 fact row — 138 of
 * copy. One more subtitle line is reserved whether or not there is one, for the reason
 * [CollectionDetailHero] reserves two description lines: the subtitle is the one part of this copy
 * the app cannot size in advance, and a band that grew when a translation wrapped would move
 * everything below it every time the locale changed. That is 155, and 20 more above and 20 below for
 * the band's own margin, which comes to 195. Arabic is the same arrangement with 32 for the fact row.
 *
 * Sizing this from nominal font sizes is what the first version of the constant did, and it came
 * out 41dp short — which on its own is invisible, and which is the whole reason it is worth
 * measuring rather than counting.
 */
private val HERO_HEIGHT_LATIN = 195.dp

/**
 * The same sum in Arabic, which is taller because Amiri's leadings are.
 *
 * A second constant rather than a fudge factor on the first: the two figures differ by exactly the
 * one line whose box the script decides — the fact row's 6 — and nothing else does.
 */
private val HERO_HEIGHT_ARABIC = 201.dp

private val HERO_INSET = 20.dp
private val HERO_GAP = 9.dp
private val HERO_BADGE = 42.dp
private val HERO_BADGE_GLYPH = 23.dp
private val HERO_BADGE_SHAPE = 14.dp
private val HERO_TITLE_GAP = 12.dp
private val HERO_TITLE_SP = 24.sp
private val HERO_TITLE_LINE_SP = 29.sp
private val HERO_BODY_SP = 12.sp
private val HERO_BODY_LINE_SP = 17.sp
private const val HERO_SUBTITLE_ALPHA = 0.78f
private val HERO_FACT_GAP = 12.dp

/**
 * Previews of the pieces rather than of the screen.
 *
 * [TagsScreen] is a stack of previewed pieces over a holder whose contents only a real gateway can
 * fill, and seeding that holder for a preview would mean either a constructor argument nothing else
 * passes or a setter that exists only to be called here. The band, the toolbar and the rows are where
 * the layout decisions are, so those are what a preview can usefully show.
 */
@Preview
@Composable
private fun TagsHeroPreview() {
    AzkarThemePreview(width = 1040.dp) {
        TagsHero(
            stats = TagStats(total = 8, usedInCollections = 6, taggedRemembrances = 270),
            onBack = {},
            onCreate = {},
            modifier = Modifier.padding(AzkarSpacing.screenPaddingHorizontal),
        )
    }
}
