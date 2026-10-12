package com.azkar.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.azkar.generated.resources.Res
import com.azkar.generated.resources.collectionsToastTitle
import com.azkar.generated.resources.libraryActionCopy
import com.azkar.generated.resources.libraryActionCopyCopied
import com.azkar.generated.resources.libraryActionFavorite
import com.azkar.generated.resources.remembranceReaderBack
import com.azkar.generated.resources.remembranceReaderMissingHint
import com.azkar.generated.resources.remembranceReaderMissingTitle
import com.azkar.ui.components.ActionIcons
import com.azkar.ui.components.AzkarCard
import com.azkar.ui.components.TooltipIconButton
import com.azkar.ui.components.pressableClick
import com.azkar.ui.shell.NotificationPriority
import com.azkar.ui.shell.ShowToast
import com.azkar.ui.theme.AzkarTheme
import org.jetbrains.compose.resources.stringResource

/**
 * One remembrance, in full.
 *
 * This is what every card in the app opens, and it is a page rather than the dialog it replaced for a
 * reason that is easy to state and easy to get wrong: a dialog's whole purpose is to be dismissed, and
 * it sizes itself to prove it — which meant the Arabic, the citation, the meaning and the reference
 * could not all be shown at once at a size any of them is readable at. Everything on this page is the
 * same text the tile was a *preview* of. A container that hides most of what it was opened for is the
 * wrong shape for that, and no amount of extra content in a 460dp box fixes it.
 *
 * So the page shows the passage whole: the Arabic, the citation beside it, the meaning, and the
 * reference in full rather than clamped to its publisher. It also shows the tags, which is the one part
 * of the model the tile never had room for and the reader always wants — a collection is a set of
 * remembrances *about* something, and "Morning" is what says which.
 *
 * Reads the shared [CollectionsCatalogue] rather than being handed the row. That is why the star here is
 * the star on the tile you clicked: two screens showing two copies of a flag is a bug waiting for the
 * first write that fails.
 *
 * @param detail the page's holder, built by [rememberRemembranceReaderState] so that the caller owns
 * its lifetime — and so a screenshot can hand this function a state it already has.
 * @param onBack pops the stack rather than navigating to a named screen, because this page is reachable
 * from four different screens and only the stack knows which of them opened it.
 */
@Composable
fun RemembranceReaderScreen(
    detail: RemembranceReaderState,
    onBack: () -> Unit,
    showToast: ShowToast,
    modifier: Modifier = Modifier,
) {
    val collections = detail.catalogue
    val colors = AzkarTheme.colors

    LaunchedEffect(collections) { collections.loadData() }

    val remembrance = detail.remembrance
    if (remembrance == null) {
        ReaderMissingState(onBack = onBack, modifier = modifier)
        return
    }

    val presenter = detail.presenter
    val clipboard = LocalClipboardManager.current
    val toastTitle = stringResource(Res.string.collectionsToastTitle)
    val copiedLabel = stringResource(Res.string.libraryActionCopyCopied)

    val arabic = presenter.arabicText(remembrance)
    val translation = presenter.localizedPrimaryText(remembrance)
    val citation = presenter.localizedSecondaryText(remembrance)
    val source = presenter.sourceLabel(remembrance)

    // The Arabic and the meaning, and nothing else. A citation is a reference rather than part of the
    // passage, so pasting one without the other leaves the reader with something they cannot use, and
    // pasting all four turns the clipboard into a wall of text where the two lines that matter are lost.
    val copyable =
        remember(arabic, translation) {
            listOf(arabic, translation).filter { it.isNotBlank() }.joinToString(COPY_LINE_BREAK)
        }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(colors.background)
                .padding(READER_PAGE_PADDING)
                .verticalScroll(rememberScrollState()),
    ) {
        ReaderHeader(
            onBack = onBack,
            tags = presenter.categories(remembrance),
            isFavorite = remembrance.isFavorite,
            onCopy =
                if (copyable.isNotBlank()) {
                    (
                        {
                            clipboard.setText(AnnotatedString(copyable))
                            showToast(toastTitle, copiedLabel, NotificationPriority.LOW)
                        }
                    )
                } else {
                    null
                },
            onToggleFavorite = { detail.toggleFavorite() },
        )

        ReaderPassageCard(
            arabic = arabic,
            citation = citation,
            translation = translation,
            source = source,
            modifier = Modifier.fillMaxWidth().padding(top = READER_REGION_GAP),
        )
    }
}

/**
 * The passage itself, in the one card the page is made of.
 *
 * Its own function so that the two decisions this screen makes about the text — the full citation
 * rather than the publisher, and no cap on the meaning — are stated once, next to each other, rather
 * than buried in the middle of the page's layout.
 */
@Composable
private fun ReaderPassageCard(
    arabic: String,
    citation: String,
    translation: String,
    source: String,
    modifier: Modifier = Modifier,
) {
    AzkarCard(
        modifier = modifier,
        shape = RoundedCornerShape(READER_CARD_SHAPE),
        contentPadding = READER_CARD_PADDING,
    ) {
        RemembranceText(
            arabic = arabic,
            citation = citation,
            translation = translation,
            // The full citation rather than the publisher, because the reference is the reason to
            // come to this page for anything but the Arabic. The tiles clamp it because a card has
            // eleven characters; a page has the width to answer "which hadith".
            source = source,
            // No cap here, and that is the difference between this page and the card. A visitor
            // who came for the meaning is owed all of it: an ellipsis at the end of a translation
            // is a sentence the reader has to go and find somewhere else, and this is the one
            // screen in the app with the width to finish it. The card's three lines are not a
            // house rule this page has to obey.
            translationLines = Int.MAX_VALUE,
        )
    }
}

/**
 * What the page says when the row it was opened for is no longer there.
 *
 * A whole page rather than a branch inside one, because there is no passage to read and therefore
 * nothing in common with the reader beyond the way out: the header is there for its back link, and
 * neither trailing control is offered, which is what [ReaderHeader]'s nullable lambdas are for.
 */
@Composable
private fun ReaderMissingState(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(AzkarTheme.colors.background)
                .padding(READER_PAGE_PADDING),
    ) {
        ReaderHeader(
            onBack = onBack,
            tags = emptyList(),
            isFavorite = false,
            onCopy = null,
            onToggleFavorite = null,
        )
        CollectionsEmptyState(
            title = stringResource(Res.string.remembranceReaderMissingTitle),
            hint = stringResource(Res.string.remembranceReaderMissingHint),
            modifier = Modifier.heightIn(min = READER_EMPTY_STATE_MIN_HEIGHT),
        )
    }
}

/**
 * The way out, what the passage is about, and the two things you can do with it.
 *
 * The tags sit in the header rather than under the passage because they are the answer to "which one is
 * this" — asked before the text is read, not after — and because a row of small labels above a card of
 * Arabic is where the eye already goes first.
 *
 * Both trailing controls are optional because they each need something to act on: there is nothing to
 * copy if the passage has neither an Arabic nor a meaning, and there is nothing to star if the row is
 * gone. Passing the lambdas as nullable rather than rendering a disabled button says which of the two
 * reasons applies, where a greyed-out control only says that something is unavailable.
 */
@Composable
private fun ReaderHeader(
    onBack: () -> Unit,
    tags: List<String>,
    isFavorite: Boolean,
    onCopy: (() -> Unit)?,
    onToggleFavorite: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ReaderBackLink(onClick = onBack)
            Spacer(Modifier.weight(1f))
            if (onCopy != null) {
                TooltipIconButton(
                    icon = ActionIcons.copy,
                    tooltip = stringResource(Res.string.libraryActionCopy),
                    onClick = onCopy,
                    buttonSize = READER_ACTION_SIZE,
                    iconSize = READER_ACTION_GLYPH,
                )
                Spacer(Modifier.width(READER_ACTION_GAP))
            }
            if (onToggleFavorite != null) {
                TooltipIconButton(
                    icon = ActionIcons.favorite(on = isFavorite),
                    tooltip = stringResource(Res.string.libraryActionFavorite),
                    onClick = onToggleFavorite,
                    buttonSize = READER_ACTION_SIZE,
                    iconSize = READER_ACTION_GLYPH,
                    favorite = isFavorite,
                )
            }
        }

        if (tags.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = READER_TAGS_GAP),
                horizontalArrangement = Arrangement.spacedBy(READER_TAG_GAP),
            ) {
                tags.forEach { tag ->
                    ReaderTag(label = tag, modifier = Modifier.weight(1f, fill = false))
                }
            }
        }
    }
}

/**
 * The way back, worded for where the reader was opened from.
 *
 * Plain "Back" rather than "Back to Collections" because this page is reached from the recitation
 * session as well as from the shelf, and from a collection's grid as well as from a search result.
 * Naming a destination would be wrong in at least two of those cases, and a back control that names the
 * wrong place is worse than one that names none.
 *
 * A text link rather than the arrow-only circle the collection page uses, for the same reason that page
 * names its own: a bare arrow under a header that still highlights Library has to be decoded, and here
 * the arrow is the only thing in the row saying "this is not where you started".
 *
 * Surface and outline rather than bare ink, which is what it used to draw. Against the flat page the
 * grey was legible but looked *off* — the same ink the app uses for secondary copy, with no edge to
 * say "press me", sitting above a card that had a clear rounded border and above copy and favourite
 * buttons that had no surface at all. The pairing here is the app's own control idiom, the one the
 * toolbar's reset button uses: [colors.highlight] under a [colors.border] rule with
 * [colors.primaryStrong] ink. The collection page's back link wears the band's glass instead, because
 * there the backing is artwork and a translucent surface is the only contrast on offer.
 */
@Composable
private fun ReaderBackLink(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AzkarTheme.colors
    Row(
        modifier =
            modifier
                .clip(RoundedCornerShape(READER_LINK_SHAPE))
                .background(colors.highlight)
                .border(READER_LINK_RULE, colors.border, RoundedCornerShape(READER_LINK_SHAPE))
                .pressableClick(scale = 1f, onClick = onClick)
                .padding(horizontal = READER_LINK_PAD_X, vertical = READER_LINK_PAD_Y),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = null,
            tint = colors.primaryStrong,
            modifier = Modifier.size(READER_LINK_GLYPH),
        )
        Spacer(Modifier.width(READER_LINK_GAP))
        Text(
            text = stringResource(Res.string.remembranceReaderBack),
            color = colors.primaryStrong,
            fontSize = READER_LINK_SP,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * One of the passage's tags.
 *
 * Drawn as a bordered pill rather than as an [com.azkar.ui.components.AzkarFilterChip] on purpose: a
 * chip is a control that does something when pressed, and nothing happens when these are pressed. The
 * publisher's tag is shown alongside the others for the same reason the reference is not clamped — it
 * is what says where the text came from, and it is a fact about the passage rather than a category of
 * it.
 */
@Composable
private fun ReaderTag(
    label: String,
    modifier: Modifier = Modifier,
) {
    val colors = AzkarTheme.colors
    Box(
        modifier =
            modifier
                .clip(CircleShape)
                .border(READER_TAG_BORDER, colors.border, CircleShape)
                .padding(horizontal = READER_TAG_PAD_X, vertical = READER_TAG_PAD_Y),
    ) {
        Text(
            text = label,
            color = colors.textSecondary,
            fontSize = READER_TAG_SP,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The page's own padding, matching the rest of the library so a hop between pages does not jump. */
private val READER_PAGE_PADDING = 16.dp

private val READER_REGION_GAP = 14.dp

/**
 * A floor rather than a height for the "gone" panel.
 *
 * The panel fills the space it is given, and a scrolling column has none to give, so without this it
 * would collapse to the height of its two lines — which reads as a page that failed to load rather than
 * as a page saying the thing is not here any more.
 */
private val READER_EMPTY_STATE_MIN_HEIGHT = 320.dp

private val READER_CARD_SHAPE = 20.dp

private val READER_CARD_PADDING = 24.dp

private val READER_ACTION_SIZE = 32.dp

private val READER_ACTION_GLYPH = 14.dp

private val READER_ACTION_GAP = 8.dp

private val READER_LINK_SHAPE = 8.dp

private val READER_LINK_RULE = 1.dp

private val READER_LINK_GLYPH = 16.dp

private val READER_LINK_GAP = 6.dp

private val READER_LINK_SP = 13.sp

private val READER_LINK_PAD_X = 8.dp

private val READER_LINK_PAD_Y = 4.dp

private val READER_TAGS_GAP = 12.dp

private val READER_TAG_GAP = 6.dp

private val READER_TAG_BORDER = 1.dp

private val READER_TAG_PAD_X = 10.dp

private val READER_TAG_PAD_Y = 4.dp

private val READER_TAG_SP = 11.sp

/** Two lines of the same passage, separated by a blank one rather than run together. */
private const val COPY_LINE_BREAK = "\n\n"
