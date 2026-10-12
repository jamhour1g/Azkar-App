package com.azkar.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.azkar.generated.resources.Res
import com.azkar.generated.resources.collectionsTitle
import com.azkar.generated.resources.homeCollectionActionContinue
import com.azkar.generated.resources.homeCollectionActionDone
import com.azkar.generated.resources.homeCollectionActionStart
import com.azkar.generated.resources.homeCollectionProgressFormat
import com.azkar.ui.components.AzkarSurfaceCard
import com.azkar.ui.components.CompletionTrack
import com.azkar.ui.components.IconTint
import com.azkar.ui.components.mirrorHorizontalGradient
import com.azkar.ui.components.pressableClick
import com.azkar.ui.library.data.HomeCollection
import com.azkar.ui.library.data.HomeCollections
import com.azkar.ui.preview.previewDueCollectionIds
import com.azkar.ui.preview.previewRailCards
import com.azkar.ui.theme.AzkarSpacing
import com.azkar.ui.theme.AzkarTheme
import com.azkar.ui.theme.AzkarThemePreview
import com.azkar.ui.theme.isRtlLayout
import org.jetbrains.compose.resources.stringResource

/**
 * How many collection cards the rail offers.
 *
 * Four is the most that fit above the favourites carousel without pushing the azkar card and the
 * prayer table off a laptop screen, and the rail is a suggestion rather than a list — everything it
 * shows is also reachable from the shelf.
 */
private const val RAIL_LIMIT = 4

/**
 * What the Home rail is offering.
 *
 * Holds the cards rather than the gateway, so the loading rule can be exercised without a database
 * and so a failed read is a state this holder can hold rather than an exception the composition has
 * to survive. An empty rail is the normal state for someone who has not pinned or started anything,
 * which is why nothing here treats empty as a problem.
 */
@Stable
class HomeRailState {
    var cards: List<HomeCollection> by mutableStateOf(emptyList())
        private set

    /**
     * Narrows the candidates the gateway read down to the cards the rail will show.
     *
     * The narrowing and ordering are [HomeCollections]' job, not this holder's: the rule for what
     * earns a place on the first screen the user sees lives in one pure function that is tested
     * without a database, and applying it here means it runs in exactly one place. [RAIL_LIMIT] is
     * also applied here because the limit is a layout decision about how much room there is, not a
     * rule about what is worth offering.
     */
    fun replace(candidates: List<HomeCollection>) {
        cards = HomeCollections.pick(candidates, RAIL_LIMIT)
    }
}

/**
 * The rail of collections, above the favourites carousel.
 *
 * Draws nothing at all when there is nothing to offer, rather than an empty heading. A section with
 * a title and no content reads as something that failed to load, and the rail is the first thing the
 * user sees — an empty box there would say something is wrong when nothing is.
 */
@Composable
fun HomeCollectionRail(
    cards: List<HomeCollection>,
    dueIds: Set<Long>,
    onOpen: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (cards.isEmpty()) return
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(AzkarSpacing.md),
    ) {
        Text(
            text = stringResource(Res.string.collectionsTitle),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = AzkarTheme.colors.textPrimary,
        )
        cards.forEach { card ->
            HomeCollectionCard(
                card = card,
                isDue = card.id in dueIds,
                onClick = { onOpen(card.id) },
            )
        }
    }
}

@Preview
@Composable
private fun HomeCollectionRailPreview() {
    AzkarThemePreview {
        Column(
            modifier = Modifier.width(1100.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            HomeCollectionRail(
                cards = previewRailCards(),
                dueIds = previewDueCollectionIds(),
                onOpen = {},
            )
            HomeCollectionRail(cards = emptyList(), dueIds = emptySet(), onOpen = {})
        }
    }
}

/**
 * One collection as a single row: what it is, how far through it the user is, and which of the
 * three things they might do about it is on offer.
 *
 * The label is the point of the card. "Start", "Continue" and "Done" say three different things
 * about the same collection, and picking the wrong one is worse than offering none — a card that
 * says "Continue" over a finished collection sends the user to a passage they have already read.
 */
@Composable
private fun HomeCollectionCard(
    card: HomeCollection,
    isDue: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AzkarTheme.colors
    AzkarSurfaceCard(
        modifier = modifier.fillMaxWidth().pressableClick(scale = 1f, onClick = onClick),
        background = mirrorHorizontalGradient(listOf(colors.azkarCardStart, colors.azkarCardEnd), isRtlLayout()),
        contentPadding = 12.dp,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isDue) {
                    RailDueBadge()
                    Spacer(Modifier.width(NAME_GAP))
                }
                RailProgressLabel(card)
            }
            RailTrack(card)
        }
    }
}

/**
 * The mark that a schedule here has come round and not been looked at.
 *
 * A dot rather than a count of what is owed, for the reason the detail page's badge is a dot: the
 * app knows the reminder fired and nothing more. It does not know how much of the collection is still
 * unread, and a badge claiming a number would be one the user could open the collection and find
 * wrong.
 */
@Composable
private fun RailDueBadge() {
    Box(
        modifier =
            Modifier
                .size(DUE_BADGE_SIZE)
                .clip(CircleShape)
                .background(AzkarTheme.colors.accent),
    )
}

/**
 * The name, the count, and the action.
 *
 * The name takes the width it needs and the count keeps its own, so a long name wraps under the
 * action rather than squeezing it. The gap between them is a fixed spacer rather than a second
 * weighted child: the name already has the slack, and a second flexible child would split it with
 * the name and butt the two texts together.
 */
@Composable
private fun RowScope.RailProgressLabel(card: HomeCollection) {
    val colors = AzkarTheme.colors
    Text(
        text = card.name,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = colors.textPrimary,
        modifier = Modifier.weight(1f, fill = false),
    )
    Spacer(Modifier.width(NAME_GAP))
    Text(
        text =
            stringResource(
                Res.string.homeCollectionProgressFormat,
                card.recitedCount,
                card.memberCount,
            ),
        fontSize = 11.sp,
        color = colors.textSecondary,
    )
    RailActionLabel(card)
}

/** "Start", "Continue" or "Done" — whichever is true, and only ever one of them. */
@Composable
private fun RailActionLabel(card: HomeCollection) {
    val colors = AzkarTheme.colors
    val label =
        when {
            card.isResumable -> stringResource(Res.string.homeCollectionActionContinue)
            card.recitedCount > 0 -> stringResource(Res.string.homeCollectionActionDone)
            else -> stringResource(Res.string.homeCollectionActionStart)
        }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.padding(start = 8.dp),
    ) {
        if (card.recitedCount >= card.memberCount && card.memberCount > 0) {
            IconTint(color = colors.primary) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.primary,
        )
    }
}

/**
 * The completion rule.
 *
 * Drawn rather than borrowed from a progress indicator because the fill is a plain fraction with no
 * indeterminate state — a determinate Material indicator would wrap one `Box` for the same picture.
 * The gradient runs through [mirrorHorizontalGradient] so the fill starts on the reading side in
 * Arabic exactly as it does in English.
 */
@Composable
private fun RailTrack(card: HomeCollection) {
    CompletionTrack(
        completion = card.completion,
        fillBrush =
            mirrorHorizontalGradient(
                listOf(AzkarTheme.colors.primary, AzkarTheme.colors.primaryStrong),
                isRtl = false,
            ),
    )
}

/**
 * Small enough to sit beside a 13sp name without competing with it, and large enough to see at a
 * glance. A dot rather than a count, because the app knows a reminder fired and nothing more — it
 * does not know how many passages are waiting.
 */
private val DUE_BADGE_SIZE = 8.dp

/** Wide enough that two adjacent words never read as one, narrow enough for a 380dp rail. */
private val NAME_GAP = 10.dp
