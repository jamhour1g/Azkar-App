package com.azkar.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.azkar.generated.resources.Res
import com.azkar.generated.resources.greetingTitle
import com.azkar.generated.resources.hero_day
import com.azkar.generated.resources.hero_night
import com.azkar.ui.components.AzkarIcons
import com.azkar.ui.components.AzkarSurfaceCard
import com.azkar.ui.components.mirrorHorizontalGradient
import com.azkar.ui.shell.userDisplayName
import com.azkar.ui.theme.AzkarPreview
import com.azkar.ui.theme.AzkarTheme
import com.azkar.ui.theme.isRtlLayout
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** The greeting band is a fixed band, not a card that grows with its content. */
private val HERO_HEIGHT = 190.dp

/**
 * The painted welcome band that replaces the JavaFX `.greeting-container`.
 *
 * The old greeting was three centred lines on the page background. This is the same information —
 * the salutation, the name, the Hijri/Gregorian date — laid over the mosque illustration from the
 * brand pack, which is what gives the dashboard its sense of place.
 *
 * Two details are load-bearing:
 * - The artwork is cropped with [ContentScale.Crop] anchored to [Alignment.BottomEnd]. The source
 *   PNGs are much wider than the band, so the leading edge is cropped away and the mosque stays put
 *   at the trailing edge at any window width.
 * - The scrim is a horizontal gradient, not a flat wash. A flat one either greys out the sky or
 *   leaves the skyline too dark to sit under the greeting; a gradient keeps the leading third
 *   readable while the illustration keeps its own colour for the rest of the band.
 */
@Composable
fun HomeHero(
    dateText: String,
    modifier: Modifier = Modifier,
) {
    val colors = AzkarTheme.colors
    val artwork = if (colors.isDark) Res.drawable.hero_night else Res.drawable.hero_day

    AzkarSurfaceCard(
        modifier = modifier.fillMaxWidth().height(HERO_HEIGHT),
        shape = RoundedCornerShape(20.dp),
        background = SolidColor(colors.background),
        elevation = 0.dp,
        border = null,
        contentPadding = 0.dp,
    ) {
        Image(
            painter = painterResource(artwork),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alignment = Alignment.BottomEnd,
        )
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        mirrorHorizontalGradient(
                            colors = listOf(colors.heroScrimStart, colors.heroScrimEnd),
                            isRtl = isRtlLayout(),
                        ),
                    ),
        )
        HeroCopy(dateText = dateText, modifier = Modifier.align(Alignment.CenterStart))
    }
}

@AzkarPreview
@Composable
internal fun HomeHeroPreview() {
    HomeHero(dateText = "Friday, 20 February")
}

/**
 * The three texts, in the order the reference design stacks them: a small letter-spaced salutation,
 * the name, then the date on its own line with a calendar glyph.
 *
 * The name comes from the OS account rather than a profile record — this is a single-user desktop
 * app with no sign-in — so it is omitted entirely when the platform gives nothing usable instead of
 * being padded out with a placeholder.
 */
@Composable
private fun HeroCopy(
    dateText: String,
    modifier: Modifier = Modifier,
) {
    val colors = AzkarTheme.colors
    val name = userDisplayName()

    Column(
        modifier = modifier.padding(start = 34.dp, end = 34.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = stringResource(Res.string.greetingTitle).uppercase(),
            color = colors.textSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 2.sp,
        )
        if (name.isNotEmpty()) {
            Text(
                text = name,
                color = colors.textPrimary,
                fontSize = 44.sp,
                fontWeight = FontWeight.ExtraBold,
                lineHeight = 52.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            modifier = Modifier.padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Icon(
                imageVector = AzkarIcons.Calendar,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = dateText,
                color = colors.textSecondary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
