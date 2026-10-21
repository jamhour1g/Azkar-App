package com.azkar.ui.notifications.presentation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.azkar.generated.resources.Res
import com.azkar.generated.resources.notificationAddFavorite
import com.azkar.generated.resources.notificationCardPosition
import com.azkar.generated.resources.notificationDismiss
import com.azkar.generated.resources.notificationRemoveFavorite
import com.azkar.i18n.textDirectionOf
import com.azkar.ui.components.ActionIcons
import com.azkar.ui.components.AzkarSurfaceCard
import com.azkar.ui.components.mirrorHorizontalGradient
import com.azkar.ui.components.pressableClick
import com.azkar.ui.notifications.NotificationAction
import com.azkar.ui.notifications.NotificationContent
import com.azkar.ui.theme.AzkarMotion
import com.azkar.ui.theme.AzkarTheme
import com.azkar.ui.theme.isRtlLayout
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * One reminder, drawn the way the app draws everything else.
 *
 * The whole point of the overlay is that this is the *app's* card rather than a system's: the same
 * palette, the same radii, the same typography as anything behind it, so a reminder arriving over a
 * browser looks like it came from Azkar and not from the desktop. That is why there is no drawing
 * code here beyond arranging text runs and glyphs — the card *is* [AzkarSurfaceCard], so a theme
 * change restyles the notification for free.
 *
 * ## What a card says, and what it does not
 *
 * Where you are in the cycle, the passage, the way to close it, and the way to keep it. An earlier
 * revision said considerably more — which collection the passage was drawn from, how the tradition
 * grades it, where it is filed, plus a row of controls to copy it, star it and move to the next one
 * — and the result was a card *about* a passage that had to be read past in order to reach it.
 * Every one of those facts is answered by the screen the click opens. Which is not a reason to
 * economise; it is the reason the card can be the passage. A reminder is something you glance at on
 * the way to something else, and the only thing worth spending that glance on is the thing the
 * reminder is for. The cycle position stays because it is the one piece of context that changes what
 * the passage means — it tells a reader who has already read this cycle that they are being walked
 * through it again, which is the difference between a nagging and a queue.
 *
 * The star is the one control that came back, and it came back for a reason the others did not have.
 * Copy and next were controls about *the app* — they acted on the queue and on a clipboard, and
 * every one of them had a home the click already opens. Whether a passage is kept does not: it is
 * the one thing a reader can want to do *at* a reminder rather than *after* it. A reader who stars a
 * passage in the library and then meets that same passage in the queue has to be able to star it from
 * the queue too, or the passage behaves differently depending on which surface it happened to turn
 * up on. It is one glyph in the corner, it costs the passage no room, and it is the only gesture on
 * the card that does not also open the app.
 *
 * Neither does the card carry a translation, a source, a grade or a play button. A translation is a
 * *reading* of the passage and belongs to the screen the reader chooses to open; a source and a grade
 * are references rather than parts of what is being said; and there is no audio in this app, so a
 * speaker would be a control that does nothing.
 *
 * What it does carry along the top is a bar, and it is not one of those four: it is the card saying
 * how long it intends to stay, which a card that silently removes itself cannot do.
 *
 * ## Size
 *
 * The card never decides its own width: [NotificationOverlayWindow] and the settings preview both lay
 * it out at [NotificationPresentationSettings.cardWidthDp], which is the measure the passage wraps to
 * and is the one dimension a reader genuinely has an opinion about. The height is the caller's to
 * choose through [sizing]:
 *
 * - [OverlayCardSizing.FIXED] — the caller hands it a bounded box of the chosen width and height, and
 *   the passage takes what is left of it after the header, ellipsizing when there is not enough. This
 *   is what makes the height setting mean something rather than only adding space, and it is the mode
 *   that wants a **bounded parent**.
 * - [OverlayCardSizing.FIT] — the caller hands it a width and nothing else, and every part grows to
 *   its own height: the passage is as tall as its lines need. Nothing here uses `weight` in this mode,
 *   which is not a stylistic choice — a weighted child in an unbounded column is given zero height, so
 *   the passage would vanish rather than wrap.
 *
 * ## The bar is a clock, not a decoration
 *
 * [remaining] is how much of this card's on-screen time is left, as a fraction, and the card
 * re-asks for it while it is on screen. The overlay has no other way to draw the same thing: only
 * [NotificationPresenter] knows when this particular card was accepted and how long it was accepted
 * for, and a card that timed its own eight seconds would disagree with the presenter the moment the
 * two clocks were not started in the same millisecond.
 */
@Composable
fun NotificationCard(
    item: PresentedNotice,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    onPauseChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    remaining: () -> Float = { FULL_REMAINING },
    sizing: OverlayCardSizing = OverlayCardSizing.FIXED,
    /**
     * Keeps or unkeeps the passage this card is about, and reports whether the write landed.
     *
     * `null` means the card cannot offer a star at all, and the star is then not drawn — which is
     * the right answer for a prayer time, for a bare notice, and for a caller that has no way to
     * write. The overlay always supplies it, so a real reminder is always starable.
     *
     * Suspend, and asked whether the write succeeded, because the card flips its star before the
     * write rather than after it: a star that only appears once the database has agreed would put a
     * round trip between the press and the picture. The answer is what lets a failed write take the
     * star back, rather than leaving a claim on screen that nothing behind it agrees with.
     */
    onToggleFavorite: (suspend (Long, Boolean) -> Boolean)? = null,
) {
    var hovered by remember { mutableStateOf(false) }
    LaunchedEffect(hovered) { onPauseChanged(hovered) }
    val favorite = rememberFavoriteControl(item, onToggleFavorite)
    val colors = AzkarTheme.colors
    // A fixed card is handed a bounded box and the passage takes what is left of it; a fitted card is
    // handed only a width and every part of it is as tall as it needs to be. `fillMaxSize` rather than
    // `fillMaxHeight` because the card also has to reach its own edges horizontally; on the vertical
    // axis it is a no-op once the slot is unbounded, which is exactly the fitted case.
    val fillsSlot = sizing == OverlayCardSizing.FIXED
    AzkarSurfaceCard(
        modifier =
            modifier
                .then(if (fillsSlot) Modifier.fillMaxSize() else Modifier.fillMaxWidth())
                .hoverTracking { hovered = it }
                .pressableClick(scale = 1f, onClick = onOpen),
        shape = MaterialTheme.shapes.extraLarge,
        background = SolidColor(colors.cardBackground),
        contentPadding = 0.dp,
    ) {
        Column(modifier = Modifier.then(if (fillsSlot) Modifier.fillMaxSize() else Modifier.fillMaxWidth())) {
            CountdownRule(remaining = remaining)
            Column(
                modifier =
                    Modifier
                        .then(if (fillsSlot) Modifier.fillMaxSize() else Modifier.fillMaxWidth())
                        .padding(horizontal = CARD_SIDE_PADDING, vertical = CARD_BLOCK_PADDING),
                verticalArrangement = Arrangement.spacedBy(CARD_BLOCK_GAP),
            ) {
                when (val content = item.content) {
                    is NotificationContent.Remembrance ->
                        RemembranceBody(
                            content = content,
                            favorite = favorite,
                            onDismiss = onDismiss,
                            fillsSlot = fillsSlot,
                        )

                    is NotificationContent.Prayer ->
                        PrayerBody(content = content, onDismiss = onDismiss, fillsSlot = fillsSlot)

                    null -> PlainBody(item = item, onDismiss = onDismiss, fillsSlot = fillsSlot)
                }
            }
        }
    }
}

/**
 * The star on this card, or nothing at all when it cannot have one.
 *
 * The state lives here rather than in the notice because the notice is the scheduler's answer — a
 * snapshot of the flag as it was when the passage was published, and a snapshot cannot answer a
 * question asked after it. The card keeps its own copy, seeded from that snapshot, and moves it
 * optimistically so the glyph answers the press immediately.
 *
 * A failed write takes the copy back. It only does so if the copy is still the one it set: a reader
 * who presses the star twice while the first write is still in flight has asked for something else
 * in the meantime, and a rollback that ignored that would answer the second press with the first
 * press's failure.
 */
@Composable
private fun rememberFavoriteControl(
    item: PresentedNotice,
    onToggleFavorite: (suspend (Long, Boolean) -> Boolean)?,
): FavoriteControl? {
    val scope = rememberCoroutineScope()
    val id = (item.action as? NotificationAction.Remembrance)?.id
    val content = item.content as? NotificationContent.Remembrance
    var favorite by remember(item.id) { mutableStateOf(content?.isFavorite ?: false) }
    val toggle = onToggleFavorite
    if (id == null || content == null || toggle == null) return null
    return FavoriteControl(
        isFavorite = favorite,
        onToggle = {
            val next = !favorite
            favorite = next
            scope.launch {
                val written = toggle(id, next)
                if (!written && favorite == next) {
                    favorite = !next
                }
            }
        },
    )
}

/**
 * The star, and the passage it belongs to.
 *
 * A pair rather than two parameters, because the two only ever make sense together: there is no
 * star without a passage to star, and no passage to star without a star.
 */
private class FavoriteControl(
    val isFavorite: Boolean,
    val onToggle: () -> Unit,
)

/**
 * The card's clock, along its top edge: how much of the reminder's on-screen time is left.
 *
 * This started as decoration — a hairline of brand gradient that marked a stack as being made of the
 * app's own material rather than as somebody's toast — and it is still that. What changed is that the
 * hairline now *empties*, which turns a mark of provenance into the one thing a reminder card had no
 * honest way to say: that it is temporary. A reminder that vanishes after eight seconds used to
 * vanish without warning; the reader either knew the number by heart or missed the passage entirely.
 * Now the bar is the warning, and it is the same eight seconds the presenter will actually enforce
 * rather than an estimate drawn next to it.
 *
 * The fill is the gradient revealed over the gradient, not a second bar drawn on top of the first:
 * [clipRect] keeps the stops where they were, so the two ends of the ramp stay the two ends of the
 * ramp as the bar drains instead of re-flowing into whatever width is left. A bar that re-flows
 * looks like a different bar every frame.
 *
 * The track underneath is the same gradient at a quarter of its strength, which is what makes a
 * nearly-spent card legible as a card rather than as a scrap of colour on the corner of the screen.
 *
 * The remaining fill stays anchored to the left and its right edge travels left as time runs out.
 * This is deliberately a physical direction rather than a layout direction: a countdown should read
 * the same way in either language, like a reverse progress bar, instead of changing its motion when the
 * card switches between English and Arabic.
 *
 * The tick is a plain [delay] rather than `withFrameNanos` because the *sampling* does not need to
 * be frame-accurate: thirty readings a second is past what a three-pixel strip can express, and a
 * frame clock is not something every composition this card appears in has — the settings preview and
 * the screenshot harness both draw it somewhere without one. Only this composable is invalidated,
 * not the stack around it, because the fraction is remembered here and read through [remaining].
 *
 * What is sampled is a *target*, not the number that is drawn. The drawn value chases that target
 * on [AzkarMotion.countdown] — a straight line, short enough to be outrun by the next reading — so
 * the strip slides at one speed instead of stepping once per poll. Thirty small jumps a second is a
 * lot of jumps for a bar this thin, and on a three-pixel strip they read as a stutter rather than
 * as a clock.
 *
 * The target only ever goes down, because [CountdownState] keeps the smallest fraction it has been
 * told. A card on its way off the screen is still asking how much time it has, and the answer can
 * come back as "all of it" from a reader that has already forgotten the card. Believing that would
 * fill the bar up again on the way out, which is the one motion a countdown must never make.
 */
@Composable
private fun CountdownRule(remaining: () -> Float) {
    val colors = AzkarTheme.colors
    val isRtl = isRtlLayout()
    val fill = mirrorHorizontalGradient(listOf(colors.brandTileStart, colors.secondary), isRtl)
    val track =
        mirrorHorizontalGradient(
            listOf(
                colors.brandTileStart.copy(alpha = TRACK_ALPHA),
                colors.secondary.copy(alpha = TRACK_ALPHA),
            ),
            isRtl,
        )
    val countdown = remember { CountdownState() }
    // The reader is remembered rather than the reading: the overlay hands the card a fresh lambda
    // every time the stack is recomposed, and a loop keyed on that would be restarted by every
    // card that arrives.
    val sampler = rememberUpdatedState(remaining)
    LaunchedEffect(Unit) {
        while (isActive) {
            countdown.sample(sampler.value())
            delay(COUNTDOWN_TICK_MILLIS)
        }
    }
    val fraction by animateFloatAsState(
        targetValue = countdown.target,
        animationSpec = AzkarMotion.countdown(),
        label = "notificationCountdownFill",
    )
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(ACCENT_RULE_HEIGHT)
                .background(track)
                .drawBehind {
                    clipRect(
                        left = 0f,
                        right = size.width * fraction.coerceIn(0f, 1f),
                    ) {
                        drawRect(fill)
                    }
                },
    )
}

/**
 * A passage, under a header that says where in the cycle it falls.
 *
 * The counter is the only claim the card makes, and it is drawn in the quiet ink rather than in a
 * pill: it is a position, not a label, and a pill would give it the same standing as the passage
 * underneath it. It is hidden for a cycle of one, where "1 of 1" is arithmetic rather than progress.
 *
 * Tracked out rather than set tight, because at eleven pixels the gaps between the digits and the
 * letterforms are what tell the eye how many separate things there are. A label with no tracking reads
 * as one word; a tracked one reads as a caption, which is what it is.
 */
@Composable
private fun ColumnScope.RemembranceBody(
    content: NotificationContent.Remembrance,
    favorite: FavoriteControl?,
    onDismiss: () -> Unit,
    fillsSlot: Boolean,
) {
    CardHeader(onDismiss = onDismiss, favorite = favorite) {
        if (content.total > 1) {
            Text(
                text = stringResource(Res.string.notificationCardPosition, content.position, content.total),
                fontSize = LABEL_SP,
                letterSpacing = LABEL_TRACKING,
                fontWeight = FontWeight.Medium,
                color = AzkarTheme.colors.textSecondary,
                maxLines = 1,
            )
        }
    }

    PassageBox(
        text = content.text,
        modifier = if (fillsSlot) Modifier.weight(1f) else Modifier,
        fillSlot = fillsSlot,
    )
}

/**
 * A prayer time, on the same shell as a passage so the two do not read as two different products.
 *
 * It prints the prayer, the time and the place, and nothing else — no next-prayer row, no link. The
 * whole card is already the link: every part of it that is not the cross opens the prayer times, so
 * a button inside it would be a second, smaller version of the thing the card already does.
 */
@Composable
private fun ColumnScope.PrayerBody(
    content: NotificationContent.Prayer,
    onDismiss: () -> Unit,
    fillsSlot: Boolean,
) {
    val colors = AzkarTheme.colors

    CardHeader(onDismiss = onDismiss) {
        Text(
            text = content.prayer,
            fontSize = LABEL_SP,
            letterSpacing = LABEL_TRACKING,
            fontWeight = FontWeight.Medium,
            color = colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }

    Text(
        text = content.time,
        modifier = if (fillsSlot) Modifier.weight(1f) else Modifier.fillMaxWidth(),
        fontSize = PRAYER_TIME_SP,
        letterSpacing = PRAYER_TIME_TRACKING,
        fontWeight = FontWeight.SemiBold,
        color = colors.textPrimary,
        textAlign = TextAlign.Center,
        maxLines = 1,
    )

    if (content.city.isNotBlank()) {
        Text(
            text = content.city,
            modifier = Modifier.fillMaxWidth(),
            fontSize = CITY_SP,
            fontWeight = FontWeight.Medium,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * A reminder published without a payload — the proof-of-concept cards, and anything that predates
 * the rich content model.
 *
 * Kept rather than deleted so that a caller which has not been updated still draws something
 * legible instead of a shell with a gradient on top of nothing.
 */
@Composable
private fun ColumnScope.PlainBody(
    item: PresentedNotice,
    onDismiss: () -> Unit,
    fillsSlot: Boolean,
) {
    val colors = AzkarTheme.colors
    CardHeader(onDismiss = onDismiss) {
        Text(
            text = item.title,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    Text(
        text = item.message,
        modifier = if (fillsSlot) Modifier.weight(1f) else Modifier.fillMaxWidth(),
        fontSize = 13.sp,
        color = colors.textSecondary,
        maxLines = PLAIN_BODY_MAX_LINES,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * The passage, in an inset box, filling whatever the card has left.
 *
 * Inset because it is the only text on the card that is not a label, and a label does not deserve a
 * frame of its own — the frame would tell the reader this was a quotation of something they had not
 * yet seen.
 *
 * The paragraph direction is read off the text rather than assumed. A card that forced
 * right-to-left would mis-order an English passage's punctuation and its quotation marks, and a card
 * that forced left-to-right would do the same to the Arabic; the text itself already says which way
 * it runs, so the box asks it. The fallback is the interface's own direction, which is the right
 * answer for a passage that is nothing but digits.
 *
 * The line height sits well above the font size for the same reason stacked marks sit well above
 * their letters: a line box sized to the glyphs clips the diacritics off the top of the line, and on
 * a reminder the reader may only ever see this once.
 *
 * Medium rather than bold, which is the one weight decision here that was a reversal. Bold is what a
 * *label* is set in; a passage is prose, and prose set in a display weight looks shorter and harder to
 * read than the same prose at regular — the extra ink tightens the counters and the joins without adding
 * anything, which on Arabic costs the most, because the dots and the vowel marks that distinguish one
 * letter from another are exactly the parts a heavy weight fills in. Medium keeps the presence a
 * glance-sized card needs and hands the legibility back. Amiri has no Medium, and the text backend
 * resolves a missing weight to the nearest it does have, so the bundled face lands on its regular.
 */
@Composable
private fun PassageBox(
    text: String,
    modifier: Modifier = Modifier,
    fillSlot: Boolean = true,
) {
    if (text.isBlank()) return
    val colors = AzkarTheme.colors
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(PassageBoxShape)
                .background(colors.highlight)
                .border(PASSAGE_BOX_BORDER_WIDTH, colors.border, PassageBoxShape)
                .padding(horizontal = PASSAGE_HORIZONTAL_PADDING, vertical = PASSAGE_VERTICAL_PADDING),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(
            LocalLayoutDirection provides textDirectionOf(text, LocalLayoutDirection.current),
        ) {
            Text(
                text = text,
                modifier = if (fillSlot) Modifier.fillMaxSize() else Modifier.fillMaxWidth(),
                fontSize = PASSAGE_SP,
                lineHeight = PASSAGE_LINE_SP,
                fontWeight = PASSAGE_WEIGHT,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
                maxLines = PASSAGE_MAX_LINES,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The header: whatever this card has to say about itself, then the star and the cross.
 *
 * [claims] is a scope rather than a parameter because the three callers make different claims — a
 * passage says where it is in the cycle, a prayer names itself, a bare notice names itself as a
 * title — and a parameter list for "whatever text goes on the leading side" would be a worse way of
 * saying that than a slot is. The cross is pinned to the trailing edge by the weight rather than by
 * an arrangement, so the three stay together when the card is mirrored.
 *
 * The star sits between the two, and only for a passage that can be starred. Putting it next to the
 * cross rather than next to the cycle counter is deliberate: the counter is a claim the card makes
 * about itself, and the star is a claim the reader makes about the passage, and the reader's own
 * claim belongs on the reader's side of the card.
 */
@Composable
private fun CardHeader(
    onDismiss: () -> Unit,
    favorite: FavoriteControl? = null,
    claims: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CARD_BLOCK_GAP),
    ) {
        claims()
        Spacer(Modifier.weight(1f))
        favorite?.let { FavoriteButton(it) }
        DismissButton(onDismiss = onDismiss)
    }
}

/**
 * The star, and the only control on the card that does something other than open or close it.
 *
 * Drawn the way the cross is drawn — a bare glyph in a fixed target rather than a button with a
 * surface — because the card's own chrome is quiet, and a filled circle next to a line of text would
 * out-shout the passage it is attached to. The target is the cross's size for the same reason the
 * cross is that size: a reader aiming at a corner of a card is aiming at the corner, and a target
 * that is much larger than the glyph would sit on top of the passage.
 *
 * The two states are the app's two states everywhere else: a filled star when the passage is kept
 * and an outline when it is not, in the accent when it is on. The accent is the one place the card
 * uses a colour other than the quiet ink, and it is spent on the one thing the reader chose.
 */
@Composable
private fun FavoriteButton(control: FavoriteControl) {
    val label =
        stringResource(
            if (control.isFavorite) {
                Res.string.notificationRemoveFavorite
            } else {
                Res.string.notificationAddFavorite
            },
        )
    Box(
        modifier =
            Modifier
                .semantics { contentDescription = label }
                .size(FAVORITE_TOUCH_TARGET)
                .pressableClick(scale = FAVORITE_PRESS_SCALE, onClick = control.onToggle),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = ActionIcons.favorite(control.isFavorite),
            contentDescription = null,
            tint =
                if (control.isFavorite) {
                    AzkarTheme.colors.accent
                } else {
                    AzkarTheme.colors.textSecondary
                },
            modifier = Modifier.size(FAVORITE_ICON),
        )
    }
}

@Composable
private fun DismissButton(onDismiss: () -> Unit) {
    val dismissLabel = stringResource(Res.string.notificationDismiss)
    Box(
        modifier =
            Modifier
                .semantics { contentDescription = dismissLabel }
                .size(DISMISS_TOUCH_TARGET)
                .pressableClick(scale = DISMISS_PRESS_SCALE, onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = ActionIcons.dismiss,
            contentDescription = null,
            tint = AzkarTheme.colors.textSecondary,
            modifier = Modifier.size(DISMISS_ICON),
        )
    }
}

/** Reports whether the pointer is over this composable. */
private fun Modifier.hoverTracking(onHoverChanged: (Boolean) -> Unit): Modifier =
    pointerInput(onHoverChanged) {
        awaitPointerEventScope {
            while (true) {
                when (awaitPointerEvent().type) {
                    PointerEventType.Enter -> onHoverChanged(true)
                    PointerEventType.Exit -> onHoverChanged(false)
                    else -> Unit
                }
            }
        }
    }

private val ACCENT_RULE_HEIGHT = 3.dp

/**
 * How strong the spent part of the bar is drawn.
 *
 * A quarter, because the track has to be clearly *there* — a bar whose empty half disappeared would
 * make a nearly-expired card look like a shrinking bar rather than like a bar running out — without
 * being mistaken for the fill itself. Anything above about a third and the two halves read as one bar
 * that is only slowly draining.
 */
private const val TRACK_ALPHA = 0.25f

/**
 * How often the bar re-reads its card's remaining time.
 *
 * Just under a thirtieth of a second, which is more often than a three-pixel strip can show and far
 * cheaper than a frame callback per visible card. The presenter's own expiry loop runs at a tenth of
 * a second, so this reads a value that is at most one poll stale.
 */
private const val COUNTDOWN_TICK_MILLIS = 33L

/** The default for a card nobody is counting down, which is what the settings preview renders. */
private const val FULL_REMAINING = 1f

private val CARD_SIDE_PADDING = 14.dp
private val CARD_BLOCK_PADDING = 12.dp
private val CARD_BLOCK_GAP = 8.dp

private val DISMISS_TOUCH_TARGET = 32.dp
private val DISMISS_ICON = 14.dp
private const val DISMISS_PRESS_SCALE = 0.9f

/**
 * The star's target and glyph, matched to the cross's.
 *
 * Same size on purpose: the two sit side by side in the same corner, and a reader who has learned
 * to aim for one of them should not have to aim differently for the other. The glyph is a touch
 * smaller than the cross's because a star at the cross's size fills its target — the cross is a
 * stroke and the star is a shape, and the shape has to be a little smaller to read as a mark rather
 * than as a blot.
 */
private val FAVORITE_TOUCH_TARGET = 32.dp
private val FAVORITE_ICON = 13.dp
private const val FAVORITE_PRESS_SCALE = 0.9f

private val PassageBoxShape = RoundedCornerShape(12.dp)
private val PASSAGE_BOX_BORDER_WIDTH = 1.dp
private val PASSAGE_HORIZONTAL_PADDING = 12.dp
private val PASSAGE_VERTICAL_PADDING = 10.dp

/**
 * The size a passage is read at on a card this size.
 *
 * Seventeen rather than eighteen, and the difference is a whole extra line of it fitting in the common
 * preset. A reminder that ellipsizes a hadith is a reminder that arrived truncated, and the truncation
 * is invisible — there is no ellipsis at the end of a glance, there is just a sentence that stops
 * mid-thought and a reader who assumes that is all of it.
 */
private val PASSAGE_SP = 17.sp

/**
 * Its leading, as one-and-a-half times the size.
 *
 * Set as its own number rather than left to the font's default so that the ratio is a decision on the
 * page instead of a property of whichever face the reader picked. It is also the ratio Arabic needs:
 * stacked marks sit above their letters and below the line beneath, and a naskh face at normal leading
 * collides with itself in a way a Latin face does not.
 */
private val PASSAGE_LINE_SP = 26.sp

private val PASSAGE_WEIGHT = FontWeight.Medium

/**
 * Ceiling on the passage's lines, set by the tallest preset rather than by the shortest.
 *
 * The card's real limit is its height, and [PASSAGE_LINE_SP] against that height is what ellipsizes
 * it. This number only has to be large enough not to be the binding constraint at the tallest size
 * the settings allow, so that changing the height setting never silently starts clipping a passage
 * that used to fit.
 */
private const val PASSAGE_MAX_LINES = 8

/**
 * Every caption on the card — the cycle counter, a prayer's name — is set at this size and tracked by
 * this much.
 *
 * One value for both rather than a size each, because the counter and the prayer name occupy the same
 * slot in the same row and are read as the same kind of thing. Two different sizes there would say the
 * card has two priorities, and it has one.
 */
private val LABEL_SP = 11.sp

/**
 * Tracking for [LABEL_SP], which is the value that makes the label legible as a caption.
 *
 * A tenth of the size. Tight enough to read as one piece of text, open enough that the digits in a
 * position counter do not touch the word beside them.
 */
private val LABEL_TRACKING = 1.1.sp

private const val PLAIN_BODY_MAX_LINES = 6

private val PRAYER_TIME_SP = 30.sp

/**
 * Tracking for the prayer time, and the only negative value on the card.
 *
 * A time is a short run of digits and punctuation, and at thirty pixels a proportional face sets it with
 * a visible gap between every pair. Closing it by less than a pixel per gap makes the string read as one
 * object rather than as the characters it is made of, which matters here because the time is the whole
 * content of this card.
 */
private val PRAYER_TIME_TRACKING = (-0.5).sp

/**
 * The city, in medium rather than the regular the labels use.
 *
 * It is the second line of the card rather than a caption over the first, and at eleven pixels in the
 * quiet ink it disappeared against the inset it sits on. A half-step of weight is enough to keep it
 * subordinate to the time without making it the thing that has to be read.
 */
private val CITY_SP = 11.5.sp
