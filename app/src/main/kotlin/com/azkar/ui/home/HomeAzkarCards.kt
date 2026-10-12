package com.azkar.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.azkar.generated.resources.Res
import com.azkar.generated.resources.noHadithSource
import com.azkar.generated.resources.todaysAzkar
import com.azkar.ui.components.ActionIcons
import com.azkar.ui.components.AzkarIcons
import com.azkar.ui.components.AzkarSurfaceCard
import com.azkar.ui.components.mirrorHorizontalGradient
import com.azkar.ui.components.pressableClick
import com.azkar.ui.theme.AzkarTheme
import com.azkar.ui.theme.AzkarThemePreview
import com.azkar.ui.theme.isRtlLayout
import org.jetbrains.compose.resources.stringResource

/** The brand tile's corner radius; matched to its size so it reads as a squircle, not a lozenge. */
private val AZKAR_TILE_SHAPE = RoundedCornerShape(20.dp)

/** The watermark's resting opacity — present enough to texture the card, faint enough to ignore. */
private const val WATERMARK_ALPHA = 0.16f

/**
 * The full-width "Today's Azkar" card: the brand tile on the leading edge and the day's dhikr on
 * the trailing side, over the pale blue wash.
 *
 * This replaces `DailyHadithCard`. That component's signature is still reachable through
 * [DailyHadithDetailsDialog], which the card opens; what changed is that the accent bar and the
 * separate "read more" button became the brand tile and a click target covering the whole card.
 *
 * [text] is whatever `RemembranceI18n` resolved for the *current* locale — Arabic in the Arabic
 * locale, the English rendering in the English one — so it deliberately does not pin a font family.
 * Pinning Amiri here (as the previous implementation did) meant the English locale set an English
 * sentence in a naskh face, which reads as a broken font rather than a choice. The theme already
 * hands back Amiri for Arabic scripts and Inter for Latin, which is the split we actually want.
 *
 * The parameters keep their `arabic`/`english` names from the JavaFX migration even though the
 * values are locale-resolved; see [HadithState].
 */
@Composable
fun TodaysAzkarCard(
    arabic: String,
    english: String,
    source: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AzkarTheme.colors
    val sourceText = source ?: stringResource(Res.string.noHadithSource)

    AzkarSurfaceCard(
        modifier = modifier.fillMaxWidth().pressableClick(scale = 1f, onClick = onClick),
        background = mirrorHorizontalGradient(listOf(colors.azkarCardStart, colors.azkarCardEnd), isRtlLayout()),
        contentPadding = 20.dp,
    ) {
        Icon(
            imageVector = AzkarIcons.Khatim,
            contentDescription = null,
            tint = colors.patternTint.copy(alpha = WATERMARK_ALPHA),
            modifier = Modifier.align(Alignment.CenterEnd).size(190.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            AzkarBrandTile(modifier = Modifier.size(72.dp))
            Spacer(Modifier.width(20.dp))
            AzkarCardCopy(
                arabic = arabic,
                english = english,
                sourceText = sourceText,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Preview
@Composable
private fun TodaysAzkarCardPreview() {
    AzkarThemePreview {
        Column(
            modifier = Modifier.width(720.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TodaysAzkarCard(
                arabic = "اللَّهُمَّ أَعِنِّي عَلَى ذِكْرِكَ وَشُكْرِكَ وَحُسْنِ عِبَادَتِكَ",
                english = "O Allah, help me to remember You, thank You, and worship You well.",
                source = "Abu Dawud 1521",
                onClick = {},
            )
            TodaysAzkarCard(
                arabic = "سُبْحَانَ اللَّهِ وَبِحَمْدِهِ",
                english = "Glory to Allah, and praise be to Him.",
                source = null,
                onClick = {},
            )
        }
    }
}

/**
 * The card's text column: label, the dhikr itself in Arabic, its translation, and where it is from.
 *
 * Split out of [TodaysAzkarCard] purely for length — it is the whole reason the card is tall, and
 * keeping it in its own composable lets the card stay a layout of three elements.
 */
@Composable
private fun AzkarCardCopy(
    arabic: String,
    english: String,
    sourceText: String,
    modifier: Modifier = Modifier,
) {
    val colors = AzkarTheme.colors
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text(
            text = stringResource(Res.string.todaysAzkar),
            color = colors.primaryStrong,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = arabic,
            color = colors.textPrimary,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 42.sp,
        )
        Text(
            text = english,
            color = colors.hadithEnglish,
            fontSize = 15.sp,
            fontStyle = FontStyle.Italic,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = ActionIcons.source,
                contentDescription = null,
                tint = colors.hadithSource,
                modifier = Modifier.size(13.dp),
            )
            Text(
                text = sourceText,
                color = colors.hadithSource,
                fontSize = 12.sp,
            )
        }
    }
}

/**
 * The 72dp brand tile that leads the azkar card.
 *
 * It carries a second, much smaller [AzkarIcons.Khatim] in its trailing corner. The watermark is
 * normally a large-scale device, and reproducing it at 1/10th the scale next to the glyph is what
 * makes the tile read as branded rather than as a coloured square.
 */
@Composable
private fun AzkarBrandTile(modifier: Modifier = Modifier) {
    val colors = AzkarTheme.colors
    Box(
        modifier =
            modifier
                .clip(AZKAR_TILE_SHAPE)
                .background(Brush.linearGradient(listOf(colors.brandTileStart, colors.brandTileEnd))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = AzkarIcons.Khatim,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.22f),
            modifier = Modifier.align(Alignment.BottomEnd).padding(3.dp).size(26.dp),
        )
        Icon(
            imageVector = AzkarIcons.BookOpen,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(34.dp),
        )
    }
}
