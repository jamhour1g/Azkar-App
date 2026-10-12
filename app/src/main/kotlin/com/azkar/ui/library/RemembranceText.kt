package com.azkar.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.azkar.generated.resources.Res
import com.azkar.generated.resources.collectionSessionTranslationLabel
import com.azkar.generated.resources.librarySourceLabel
import com.azkar.ui.components.AzkarIcons
import com.azkar.ui.theme.AzkarTheme
import org.jetbrains.compose.resources.stringResource

/**
 * One remembrance's text, laid out as a reader reads it: what is being said, where it is from, and what
 * somebody made of it.
 *
 * Extracted from the recitation session's card rather than written a second time for the reader page,
 * because these are the same three things in the same order and the day one of them gains a fourth
 * element it is the other that will be left showing three. Two screens asking "what does this passage
 * say" are one question, and the answer is one design.
 *
 * Every block here is optional. A passage with no Arabic, no citation or no translation drops that block
 * rather than reserving its space or falling back to something else, because a panel labelled
 * "Translation" that is empty tells the reader the app has none, which is worse than not raising the
 * question — and a citation line filled with the publisher tells them nothing they did not already know
 * from the footer.
 *
 * The three strings are parameters rather than something resolved here. That is the one decision this
 * function does *not* make for its callers, and the two callers genuinely disagree: the recitation
 * session pins the meaning to English, because a session is reciting from a text and the reader needs the
 * reference rendering beside it, while the reader page resolves it to the interface language, because
 * someone who has switched the app to Arabic asked to be told what this means in Arabic. Taking the
 * strings as given keeps that difference visible at the call site instead of hiding it behind a default.
 *
 * @param arabic the dhikr itself, pinned to Arabic by the caller however the interface is set.
 * @param citation where it comes from — a reference, not prose.
 * @param translation somebody's rendering of it, in whichever language the caller decided.
 * @param source the attribution line under the divider. Callers pass either the whole reference or just
 * its publisher: the session passes the publisher because it is identifying a row, and the reader passes
 * the whole thing because that is the page a reader came for the reference in the first place.
 */
@Composable
internal fun RemembranceText(
    arabic: String,
    citation: String,
    translation: String,
    source: String,
    translationLines: Int,
    modifier: Modifier = Modifier,
) {
    val colors = AzkarTheme.colors
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (arabic.isNotBlank()) {
            // Arabic laid out right to left even when the interface is not. Bidi resolves the character
            // order from the paragraph direction, and a paragraph declared left to right renders Arabic
            // with its trailing punctuation and any embedded numerals in the wrong place — a subtle
            // wrongness that reads as a bad font rather than as a bug, and is invisible to anyone who
            // only ever reads one of the two scripts. The direction is set around this one block so the
            // page's own rows stay in the interface's direction.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Text(
                    text = arabic,
                    color = colors.textPrimary,
                    fontSize = ARABIC_SP,
                    lineHeight = ARABIC_LINE_SP,
                    textAlign = TextAlign.Center,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = BODY_GAP),
                )
            }
        }
        if (citation.isNotBlank()) {
            Text(
                text = citation,
                color = colors.hadithSource,
                fontSize = CITATION_SP,
                lineHeight = CITATION_LINE_SP,
                textAlign = TextAlign.Center,
                maxLines = CITATION_LINES,
                overflow = TextOverflow.Ellipsis,
                modifier =
                    Modifier
                        .widthIn(max = CITATION_MAX_WIDTH)
                        .fillMaxWidth()
                        .padding(top = CITATION_GAP),
            )
        }
        if (translation.isNotBlank()) {
            TranslationPanel(
                translation = translation,
                lines = translationLines,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = PANEL_GAP),
            )
        }
        HorizontalDivider(
            color = colors.border,
            modifier = Modifier.padding(top = FOOTER_GAP),
        )
        PassageFooter(
            source = source,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = FOOTER_TEXT_GAP),
        )
    }
}

/**
 * The meaning, inset and set in quotes.
 *
 * Quoted because that is what it is: somebody else's rendering of the Arabic, and the quotation marks
 * are what keeps it from reading as a second Arabic to be recited. Inset and bordered because it is the
 * one part of the card that is not being said — it is a note beside the text, and giving it the same
 * plane as the text would make the card two passages rather than one passage and its footnote.
 *
 * Omitted entirely when there is nothing to translate, rather than filled with the citation or with the
 * source — see [RemembranceText] on why an empty labelled panel is worse than no panel.
 */
@Composable
private fun TranslationPanel(
    translation: String,
    lines: Int,
    modifier: Modifier = Modifier,
) {
    val colors = AzkarTheme.colors
    Column(
        modifier =
            modifier
                .clip(RoundedCornerShape(PANEL_SHAPE))
                .background(colors.highlight)
                .padding(horizontal = PANEL_PAD_X, vertical = PANEL_PAD_Y),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = AzkarIcons.BookOpen,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(PANEL_LABEL_GLYPH),
            )
            Spacer(Modifier.width(PANEL_LABEL_GAP))
            Text(
                text = stringResource(Res.string.collectionSessionTranslationLabel),
                color = colors.primary,
                fontSize = PANEL_LABEL_SP,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            text = "$TRANSLATION_QUOTE_OPEN$translation$TRANSLATION_QUOTE_CLOSE",
            color = colors.hadithEnglish,
            fontSize = TRANSLATION_SP,
            lineHeight = TRANSLATION_LINE_SP,
            fontStyle = FontStyle.Italic,
            maxLines = lines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = PANEL_TEXT_GAP),
        )
    }
}

/**
 * Who the passage is from.
 *
 * One cell rather than two, because the second half of the old pair described a narrator this data set
 * does not have. A footer cell with nothing honest to put in it is a place the eye goes and comes back
 * from empty, which is why the divider went with it rather than being left dividing nothing.
 *
 * The value may run past the width of this cell, so it is one line and ellipsised rather than wrapped:
 * the reference is an identifier to be recognised, and a citation broken across three lines stops being
 * one thing to recognise. The reader page, where the reference is the reason for the visit, passes the
 * whole string and gives it a line of its own.
 */
@Composable
private fun PassageFooter(
    source: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = AzkarIcons.BookOpen,
            contentDescription = null,
            tint = AzkarTheme.colors.textSecondary,
            modifier = Modifier.size(FOOTER_GLYPH),
        )
        Spacer(Modifier.width(FOOTER_TEXT_GAP))
        Column {
            Text(
                text = stringResource(Res.string.librarySourceLabel),
                color = AzkarTheme.colors.textSecondary,
                fontSize = FOOTER_LABEL_SP,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = source,
                color = AzkarTheme.colors.textPrimary,
                fontSize = FOOTER_VALUE_SP,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The translation's leading quote mark, and its trailing one.
 *
 * Written out rather than wrapped around the text at the call site, because the trailing mark has to sit
 * after the last character rather than float after it, and a wrapper's closing mark lands wherever the
 * line ends — which is not the same place on every width.
 */
private const val TRANSLATION_QUOTE_OPEN = "\u201c "

private const val TRANSLATION_QUOTE_CLOSE = " \u201d"

/** The air between the passage's own text and the citation under it. */
private val BODY_GAP = 20.dp

/**
 * Arabic, large, and given a line box far taller than the type.
 *
 * The height is not a preference. Arabic carries stacked marks — shadda, dagger alif, the small alef of a
 * quranic verse — and laid out at the line height its own size implies, those marks are clipped off the
 * top of every line that carries them. The extra box is the cost of not clipping the text of the thing
 * this app exists to show.
 */
private val ARABIC_SP = 30.sp

private val ARABIC_LINE_SP = 52.sp

/**
 * The citation under the Arabic, and the air above it.
 *
 * Muted and two lines: it is a reference, not prose, and it is the first thing that can be dropped by
 * whoever is short of room.
 */
private val CITATION_SP = 12.sp

private val CITATION_LINE_SP = 17.sp

private const val CITATION_LINES = 2

private val CITATION_GAP = 10.dp

/**
 * Held to a comfortable measure under a centred heading.
 *
 * A citation is read as one line of reference. Letting it run the full width of a wide window puts its
 * two ends further apart than anyone can compare at a glance, which is the opposite of what a reference
 * is for.
 */
private val CITATION_MAX_WIDTH = 640.dp

/** The air between the citation and the meaning panel — a bigger gap than the one above it. */
private val PANEL_GAP = 20.dp

private val PANEL_SHAPE = 12.dp

private val PANEL_PAD_X = 14.dp

private val PANEL_PAD_Y = 12.dp

private val PANEL_LABEL_SP = 11.sp

private val PANEL_LABEL_GLYPH = 12.dp

private val PANEL_LABEL_GAP = 6.dp

private val PANEL_TEXT_GAP = 8.dp

private val TRANSLATION_SP = 14.sp

private val TRANSLATION_LINE_SP = 21.sp

/** The air above the divider that separates the passage from who it is from. */
private val FOOTER_GAP = 16.dp

private val FOOTER_TEXT_GAP = 14.dp

private val FOOTER_GLYPH = 14.dp

private val FOOTER_LABEL_SP = 10.sp

private val FOOTER_VALUE_SP = 12.sp
