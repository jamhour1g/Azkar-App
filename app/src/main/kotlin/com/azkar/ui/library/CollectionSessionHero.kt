package com.azkar.ui.library

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.azkar.domain.model.RemembranceCollection
import com.azkar.generated.resources.Res
import com.azkar.generated.resources.collectionDetailActionRemindMe
import com.azkar.generated.resources.collectionSessionActionNext
import com.azkar.generated.resources.collectionsActionBack
import com.azkar.generated.resources.collectionsActionFavorite
import com.azkar.generated.resources.collectionsActionInReminders
import com.azkar.generated.resources.collectionsActionUnfavorite
import com.azkar.generated.resources.hero_day
import com.azkar.generated.resources.hero_night
import com.azkar.generated.resources.libraryActionMore
import com.azkar.ui.components.ActionIcons
import com.azkar.ui.components.AzkarIcons
import com.azkar.ui.components.AzkarMenuItem
import com.azkar.ui.components.AzkarOverflowMenu
import com.azkar.ui.components.KhatimPattern
import com.azkar.ui.components.mirrorHorizontalGradient
import com.azkar.ui.components.pressableClick
import com.azkar.ui.library.data.CollectionStats
import com.azkar.ui.preview.previewCollection
import com.azkar.ui.preview.previewCollectionStats
import com.azkar.ui.preview.previewCollectionStatsVariants
import com.azkar.ui.theme.AzkarTheme
import com.azkar.ui.theme.AzkarThemePreview
import com.azkar.ui.theme.isRtlLayout
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * The band at the top of a recitation session: which collection is being recited, and how much of it is
 * left.
 *
 * The same band the collection page opens with, and deliberately not a second design. A session is
 * reached *from* a collection and recites that same collection, so two pages answering "what is this?"
 * with different artwork, different facts or a different set of controls would be the app disagreeing
 * with itself one screen apart — and the session is the page where a reader is least inclined to
 * question what they are being told.
 *
 * What differs is what the band is *for*. On the collection page it is a way into the work; here the
 * work has already started, so the band's job is to report and stay out of the way. Which is why the
 * action it leads with is the one that moves the session on, and why the passage itself is the largest
 * thing on the screen rather than anything here.
 *
 * There is no progress track in here, and that is the one real omission. The collection page prints
 * one because a collection is something you approach gradually; a session is something you are inside,
 * and a session says where it has reached in a strip of its own sitting directly above the passage it is
 * reporting on. An estimate three hundred pixels from the thing being estimated reads as an estimate
 * about something else.
 *
 * @param publisher the clamped name of whoever published the collection, shown as a badge beside the
 * back link. Empty when no member carries a source, in which case the badge is not drawn rather than
 * being filled with a placeholder: a collection with no publisher is a fact, and a badge claiming
 * otherwise would be a small lie in the most prominent corner of the page.
 * @param inReminders whether the queue draws from this collection. The same yes or no the
 * collection page's bell gives.
 *
 * Whether the collection itself is starred is read off [collection] rather than passed alongside it.
 * It is a fact about the collection rather than about the passage, which is why it is the same flag the
 * shelf's star sets: starring a collection from inside its session has to mean the same thing as
 * starring it from the shelf, or the two screens disagree about a fact about the collection itself.
 * And how many members there are is [CollectionStats.totalMembers] rather than a number of its own —
 * the same reason: a band that printed a count nobody asked it to compute is a band that can print a
 * different count from the strip under it.
 */
@Composable
internal fun CollectionSessionHero(
    collection: RemembranceCollection,
    publisher: String,
    stats: CollectionStats,
    inReminders: Boolean,
    actions: SessionHeroActions,
    modifier: Modifier = Modifier,
) {
    val colors = AzkarTheme.colors
    val artwork = if (colors.isDark) Res.drawable.hero_night else Res.drawable.hero_day
    val isFavorite = collection.isFavorite
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(BAND_HEIGHT)
                .clip(RoundedCornerShape(BAND_SHAPE)),
    ) {
        Image(
            painter = painterResource(artwork),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alignment = Alignment.BottomEnd,
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    mirrorHorizontalGradient(
                        listOf(colors.heroScrimStart, colors.heroScrimEnd),
                        isRtlLayout(),
                    ),
                ),
        )
        KhatimPattern(tint = colors.artPatternTint, modifier = Modifier.fillMaxSize())

        SessionCopy(
            collection = collection,
            publisher = publisher,
            stats = stats,
            onBack = actions.onBack,
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .widthIn(max = BAND_COPY_MAX_WIDTH)
                    .fillMaxWidth()
                    .padding(BAND_INSET),
        )

        SessionControls(
            isFavorite = isFavorite,
            inReminders = inReminders,
            actions = actions,
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = BAND_INSET, end = BAND_INSET),
        )
    }
}

/**
 * What the session band's own controls do.
 *
 * The same gathering [CollectionHeroActions] does on the collection page, and for the same reason:
 * five callbacks in a row is a signature nobody reads correctly, and the band's controls are one thing
 * being described rather than five unrelated arguments.
 */
internal data class SessionHeroActions(
    val onBack: () -> Unit,
    val onNext: () -> Unit,
    val onToggleFavorite: () -> Unit,
    val onToggleReminder: () -> Unit,
    val menuItems: List<AzkarMenuItem>,
)

/**
 * Everything the band writes, in the order it writes it.
 *
 * Split out of [CollectionSessionHero] so that the band above reads as what it decides — how big the
 * window is, and what sits along its two edges — rather than as one long column of copy.
 */
@Composable
private fun SessionCopy(
    collection: RemembranceCollection,
    publisher: String,
    stats: CollectionStats,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AzkarTheme.colors
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(BAND_GAP),
    ) {
        SessionBackLink(stringResource(Res.string.collectionsActionBack), onBack)
        if (publisher.isNotBlank()) {
            PublisherBadge(publisher)
        }
        Text(
            text = collection.name,
            color = colors.textPrimary,
            fontSize = BAND_TITLE_SP,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val description = collection.description
        if (!description.isNullOrBlank()) {
            Text(
                text = description,
                color = colors.textSecondary,
                fontSize = BAND_BODY_SP,
                lineHeight = BAND_BODY_LINE_SP,
                maxLines = BAND_DESCRIPTION_LINES,
                overflow = TextOverflow.Ellipsis,
            )
        }
        HeroFacts(collection = collection, memberCount = stats.totalMembers, stats = stats)
    }
}

/**
 * The row of controls along the band's trailing edge.
 *
 * Its own composable so the band's copy and its band's controls can be read independently: everything
 * in this row is an action the reader can take, and everything in the other is a fact they can only
 * read, and the difference is the reason they sit at opposite edges.
 */
@Composable
private fun SessionControls(
    isFavorite: Boolean,
    inReminders: Boolean,
    actions: SessionHeroActions,
    modifier: Modifier = Modifier,
) {
    val colors = AzkarTheme.colors
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HERO_PILL_GAP),
    ) {
        // Leads the group, as it does on the collection page, and here for a sharper reason: in a
        // session the passage in front of you is the thing you came for, so moving past it is the
        // one action on the page whose result cannot be had any other way.
        HeroIconAction(
            icon = Icons.Filled.PlayArrow,
            contentDescription = stringResource(Res.string.collectionSessionActionNext),
            onClick = actions.onNext,
            filled = true,
        )
        HeroPill(
            icon = ActionIcons.favorite(isFavorite),
            label =
                stringResource(
                    if (isFavorite) {
                        Res.string.collectionsActionUnfavorite
                    } else {
                        Res.string.collectionsActionFavorite
                    },
                ),
            onClick = actions.onToggleFavorite,
            selected = isFavorite,
        )
        HeroPill(
            icon = AzkarIcons.Bell,
            label = reminderLabel(inReminders),
            onClick = actions.onToggleReminder,
            selected = inReminders,
        )
        AzkarOverflowMenu(
            icon = Icons.Filled.MoreVert,
            contentDescription = stringResource(Res.string.libraryActionMore),
            items = actions.menuItems,
            buttonSize = BAND_OVERFLOW_SIZE,
            iconSize = BAND_OVERFLOW_GLYPH,
            background = colors.artPillSurface,
        )
    }
}

@Preview
@Composable
private fun CollectionSessionHeroPreview() {
    AzkarThemePreview {
        Column(
            modifier = Modifier.width(1200.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CollectionSessionHero(
                collection = previewCollection(),
                publisher = "Sahih al-Bukhari",
                stats = previewCollectionStats(),
                inReminders = true,
                actions =
                    SessionHeroActions(
                        onBack = {},
                        onNext = {},
                        onToggleFavorite = {},
                        onToggleReminder = {},
                        menuItems =
                            listOf(
                                AzkarMenuItem(label = "Restart", onClick = {}, icon = ActionIcons.restart),
                                AzkarMenuItem(label = "Share", onClick = {}, icon = ActionIcons.share),
                            ),
                    ),
            )
            CollectionSessionHero(
                collection = previewCollection(),
                publisher = "Sahih al-Bukhari",
                stats = previewCollectionStatsVariants()[1],
                inReminders = false,
                actions =
                    SessionHeroActions(
                        onBack = {},
                        onNext = {},
                        onToggleFavorite = {},
                        onToggleReminder = {},
                        menuItems = emptyList(),
                    ),
            )
        }
    }
}

/**
 * What the bell says about the schedule, in words.
 *
 * The same three-way answer the collection page's bell gives, and for the same reason: a paused
 * schedule and no schedule at all have no next firing between them, so the label has to be told which
 * of the two it is rather than derived from a time that does not exist.
 */
@Composable
private fun reminderLabel(inReminders: Boolean): String =
    stringResource(
        if (inReminders) Res.string.collectionsActionInReminders else Res.string.collectionDetailActionRemindMe,
    )

/**
 * The way out, drawn as a link rather than as an icon button.
 *
 * Text because the reader of a session is further into the app than a reader of a collection is, and
 * the thing underneath is the library rather than a sibling page: "Back to Collections" says which of
 * the three top-level destinations they would land in, where an arrow under a header that still
 * highlights Library has to be decoded. It is the collection page's own component, so the two bands
 * offer the same exit in the same words in the same place.
 */
@Composable
private fun SessionBackLink(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .clip(RoundedCornerShape(BACK_LINK_SHAPE))
                .pressableClick(onClick = onClick)
                .padding(horizontal = BACK_LINK_PAD_X, vertical = BACK_LINK_PAD_Y),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = null,
            tint = AzkarTheme.colors.textSecondary,
            modifier = Modifier.size(BACK_LINK_GLYPH),
        )
        Spacer(Modifier.width(BACK_LINK_GAP))
        Text(
            text = label,
            color = AzkarTheme.colors.textSecondary,
            fontSize = BAND_LINK_SP,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Who published the collection, as a small glass badge.
 *
 * A badge rather than a fourth fact in the row below, because it is provenance rather than progress:
 * those three answer "how much is there and how long will it take", and this answers "who says this is
 * worth saying", which is a different question and deserves somewhere other than fourth in a list of
 * numbers.
 *
 * Its surface and ink are the theme's fixed dark-glass pair rather than the pills' theme-following one,
 * because a badge is not a control: it has no state to be read against, so there is nothing but the
 * artwork behind it to stay legible against, and the artwork is bright at one end and dark at the other.
 */
@Composable
private fun PublisherBadge(
    publisher: String,
    modifier: Modifier = Modifier,
) {
    val colors = AzkarTheme.colors
    Row(
        modifier =
            modifier
                .height(BAND_BADGE_HEIGHT)
                .clip(CircleShape)
                .background(colors.artBadgeSurface)
                .padding(horizontal = BADGE_PAD_X),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = AzkarIcons.BookOpen,
            contentDescription = null,
            tint = colors.artBadgeInk,
            modifier = Modifier.size(BADGE_GLYPH),
        )
        Spacer(Modifier.width(BADGE_TEXT_GAP))
        Text(
            text = publisher,
            color = colors.artBadgeInk,
            fontSize = BAND_BADGE_SP,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The band's height, and the arithmetic behind it.
 *
 * The collection page's height, not a second number. Both bands show the same title, the same
 * description and the same three facts, so a shorter band here would make the two read as different
 * screens rather than as the same one with different controls in the corner.
 *
 * What the collection page spends on a progress track is spent here on the back link and the publisher
 * badge, which are two lines of text it does not have; the two therefore come out the same height
 * without either having been sized for the other's contents.
 */
private val BAND_HEIGHT = 212.dp

private val BAND_SHAPE = 20.dp

private val BAND_INSET = 20.dp

private val BAND_COPY_MAX_WIDTH = 660.dp

private val BAND_GAP = 9.dp

private val BAND_TITLE_SP = 24.sp

private val BAND_BODY_SP = 12.sp

private val BAND_BODY_LINE_SP = 17.sp

private const val BAND_DESCRIPTION_LINES = 2

private val BAND_LINK_SP = 12.sp

/** The overflow is a circle rather than a pill because it carries no words. */
private val BAND_OVERFLOW_SIZE = 36.dp

private val BAND_OVERFLOW_GLYPH = 14.dp

private val BAND_BADGE_HEIGHT = 26.dp

private val BAND_BADGE_SP = 11.sp

private val BADGE_GLYPH = 13.dp

private val BADGE_PAD_X = 10.dp

private val BADGE_TEXT_GAP = 6.dp

private val BACK_LINK_SHAPE = 8.dp

private val BACK_LINK_PAD_X = 6.dp

private val BACK_LINK_PAD_Y = 2.dp

private val BACK_LINK_GLYPH = 14.dp

private val BACK_LINK_GAP = 6.dp
