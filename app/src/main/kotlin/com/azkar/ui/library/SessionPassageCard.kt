package com.azkar.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.azkar.domain.model.Remembrance
import com.azkar.generated.resources.Res
import com.azkar.generated.resources.collectionDetailPositionFormat
import com.azkar.generated.resources.collectionSessionActionAgain
import com.azkar.generated.resources.collectionSessionActionSkip
import com.azkar.generated.resources.libraryActionCopy
import com.azkar.ui.components.AzkarCard
import com.azkar.ui.components.pressableClick
import com.azkar.ui.library.data.CollectionPresenter
import com.azkar.ui.theme.AzkarTheme
import org.jetbrains.compose.resources.stringResource

/**
 * One remembrance, laid out to be recited from.
 *
 * The order of the card is the order of the act. The Arabic is first, centred and largest, because it is
 * what is being said; the citation sits under it in the smallest type on the card, because it is
 * bookkeeping; and the translation is last and inset, because it is there for the reader who does not
 * read Arabic and is not part of the recitation at all. A card that led with the translation would be
 * asking the reader to check their understanding before they have said the thing once.
 *
 * The header carries the three things that are not about the passage's text — which number it is, what it
 * is called, and the actions that change where the session goes. They are grouped in the corner because
 * they are all decisions rather than content, and a reader who has come to recite does not read a header.
 *
 * What is deliberately *not* here is anything about the passages after this one. The old card ended with
 * an "up next" list, which was the third place on the page that said what came next; the band's next
 * button and the bottom row's Next now say it, and a preview list is a promise the session then breaks
 * every time the reader skips something.
 *
 * @param position one-based place in the collection, drawn as the numbered badge.
 * @param memberCount the collection's size, which the badge's caption is counted against.
 */
@Composable
internal fun SessionPassageCard(
    remembrance: Remembrance,
    presenter: CollectionPresenter,
    position: Int,
    memberCount: Int,
    actions: PassageActions,
    modifier: Modifier = Modifier,
) {
    val arabic = presenter.arabicText(remembrance)
    val citation = presenter.localizedSecondaryText(remembrance)
    val translation = presenter.englishText(remembrance)

    AzkarCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CARD_SHAPE),
        contentPadding = CARD_PADDING,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PassageHeader(
                title = presenter.preview(presenter.localizedPrimaryText(remembrance), TITLE_MAX),
                position = position,
                caption =
                    stringResource(
                        Res.string.collectionDetailPositionFormat,
                        position,
                        memberCount,
                    ),
                actions =
                    PassageHeaderActions(
                        // The card decides *what* gets copied; the header only has to ask. A header
                        // that took the text would have to be handed the Arabic as well as the title
                        // derived from it, and would then hold two versions of the same passage.
                        onCopy = { actions.onCopy(arabic.ifBlank { presenter.localizedPrimaryText(remembrance) }) },
                        onReciteAgain = actions.onReciteAgain,
                        onSkip = actions.onSkip,
                    ),
            )
            // The passage itself is the shared body, not this card's own: the reader page shows the
            // same three things in the same order, and a second drawing of them is a second thing to
            // forget to update. This card is the frame around it — a numbered header for a session that
            // knows where it is in a sequence, and three actions that move the session on.
            //
            // Pinned to the English meaning rather than the interface's, because a session is reciting
            // from a text: the reader needs the reference rendering of the Arabic beside it whatever
            // language they read the interface in.
            RemembranceText(
                arabic = arabic,
                citation = citation,
                translation = translation,
                source = remembranceSourceLabel(presenter.sourceLabel(remembrance)),
                // Three lines, because here the translation is a footnote to the passage above it and a
                // footnote that runs longer than the thing it annotates inverts the card.
                translationLines = CARD_TRANSLATION_LINES,
                modifier = Modifier.padding(top = CARD_BODY_GAP),
            )
        }
    }
}

/**
 * What the card's three actions do.
 *
 * Gathered for the same reason [CollectionHeroActions] gathers a hero band's: three callbacks in a row
 * is a signature nobody reads correctly, and the card and its header describe the same three answers
 * between them.
 *
 * [onCopy] takes the text rather than being told what to copy, because the card decides *what* the text
 * is — the Arabic, falling back to the interface's own rendering of the passage when a remembrance
 * carries none — and the header, which is what the reader actually taps, only has to ask for it.
 */
internal data class PassageActions(
    val onCopy: (String) -> Unit,
    val onReciteAgain: () -> Unit,
    val onSkip: () -> Unit,
)

/**
 * The same three answers, at the point where there is nothing left to choose between them.
 *
 * A second type rather than a reused [PassageActions] because copying is the one action whose argument
 * the card resolves: by the time the header draws a copy button there is nothing to ask, and giving it
 * a `(String) -> Unit` it has no way to fill would put the passage's text back into the header's
 * signature.
 */
private data class PassageHeaderActions(
    val onCopy: () -> Unit,
    val onReciteAgain: () -> Unit,
    val onSkip: () -> Unit,
)

/**
 * The numbered badge, what the passage is called, and the three actions that change where the session
 * goes.
 *
 * The badge is numbered rather than carrying the category or a mark, because inside a session the only
 * question a number answers is "how far am I", and that is the question the header is actually being
 * asked. The title beside it is truncated to a single line because it is an identification rather than
 * something to read — the passage's own text is three lines below it, in far more type.
 *
 * The actions are the collection's own icon buttons, drawn here rather than under the card because
 * "again" and "skip" are answers to "I don't want this one", and a control that asks that question
 * belongs next to the thing being declined rather than at the bottom of the screen where it reads as
 * the next step in the session.
 */
@Composable
private fun PassageHeader(
    title: String,
    position: Int,
    caption: String,
    actions: PassageHeaderActions,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NumberedBadge(number = position, modifier = Modifier.size(BADGE_SIZE))
        Spacer(Modifier.width(BADGE_GAP))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = AzkarTheme.colors.textPrimary,
                fontSize = HEADER_TITLE_SP,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = caption,
                color = AzkarTheme.colors.textSecondary,
                fontSize = HEADER_CAPTION_SP,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = HEADER_CAPTION_GAP),
            )
        }
        Spacer(Modifier.width(HEADER_ACTION_GAP))
        CardAction(
            icon = Icons.Filled.ContentCopy,
            description = stringResource(Res.string.libraryActionCopy),
            onClick = actions.onCopy,
        )
        CardAction(
            icon = Icons.Filled.Replay,
            description = stringResource(Res.string.collectionSessionActionAgain),
            onClick = actions.onReciteAgain,
        )
        CardAction(
            icon = Icons.Filled.SkipNext,
            description = stringResource(Res.string.collectionSessionActionSkip),
            onClick = actions.onSkip,
        )
    }
}

/**
 * The round numeral at the head of the header.
 *
 * A badge rather than a plain number because the number is a position and a position is easier to find
 * on a page full of arabic when it is the one thing drawn in a circle. The primary tint rather than the
 * accent the member cards use for their star, for the reason there is a ring in the strip above: the two
 * together are the session's only indication of where it is, and they should look like a set.
 */
@Composable
private fun NumberedBadge(
    number: Int,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .clip(CircleShape)
                .background(AzkarTheme.colors.primary),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = number.toString(),
            color = AzkarTheme.colors.onPrimary,
            fontSize = BADGE_TEXT_SP,
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * One of the header's three actions.
 *
 * At the 32dp the card's own row affords rather than the 44dp the session's bottom buttons use, because
 * these are adjustments to one passage and the bottom row is the session's own decision. Their
 * descriptions say what each does rather than naming a glyph, so a reader using a screen reader hears
 * "skip this one" rather than "next".
 */
@Composable
private fun CardAction(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .size(CARD_ACTION_SIZE)
                .clip(CircleShape)
                .pressableClick(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = AzkarTheme.colors.textSecondary,
            modifier = Modifier.size(CARD_ACTION_GLYPH),
        )
    }
}

/**
 * The card's own frame: rounded, and padded more generously than the member tiles are.
 *
 * More because this is the one card on the screen with nothing but reading in it. The tiles are laid out
 * in a grid and are given their air by the gaps between them; a single card in the middle of a window has
 * no such neighbours, and at the tiles' padding it reads as a paragraph with a border rather than as
 * something set aside to be read.
 */
private val CARD_SHAPE = 18.dp

private val CARD_PADDING = 20.dp

private val BADGE_SIZE = 36.dp

private val BADGE_TEXT_SP = 14.sp

private val BADGE_GAP = 12.dp

private val HEADER_TITLE_SP = 14.sp

private val HEADER_CAPTION_SP = 11.sp

private val HEADER_CAPTION_GAP = 2.dp

private val HEADER_ACTION_GAP = 8.dp

private val CARD_ACTION_SIZE = 32.dp

private val CARD_ACTION_GLYPH = 16.dp

private const val TITLE_MAX = 60

/**
 * The air between the header and the passage.
 *
 * A card's own padding plus this reads as one gap, which is why it is smaller than any of the gaps
 * inside [RemembranceText]: the header and the Arabic are two halves of one thing here, whereas the
 * blocks below it are three separate readings of the same passage.
 */
private val CARD_BODY_GAP = 4.dp

/**
 * How much of the translation the session card shows.
 *
 * Deliberately not the same number the reader page uses, and deliberately not a shared default: the two
 * surfaces answer different questions. The card is read while reciting, where the Arabic is the point and
 * the meaning is there to be checked; the reader page is opened *for* the meaning, and a page that greets
 * a visitor with an ellipsis is the thing this round set out to remove.
 */
private const val CARD_TRANSLATION_LINES = 3
