package com.azkar.ui.notifications.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.azkar.generated.resources.Res
import com.azkar.generated.resources.notificationDismiss
import com.azkar.ui.components.AzkarCard
import com.azkar.ui.components.AzkarIcons
import com.azkar.ui.theme.AzkarSpacing
import com.azkar.ui.theme.AzkarTheme
import com.azkar.ui.theme.CardSize
import org.jetbrains.compose.resources.stringResource

/**
 * One reminder, drawn as the app draws everything else.
 *
 * The whole point of the overlay is that this is the *app's* card rather than a system's: the same
 * palette, the same radii, the same typography as anything behind it, so a reminder arriving over a
 * browser looks like it came from Azkar and not from the desktop. That is why there is no drawing
 * code here beyond arranging three text runs and one icon — the card *is* [AzkarCard], so a theme
 * change restyles the notification for free.
 *
 * Two affordances, both of them requested:
 *
 * - **The body opens Azkar** at whatever this notice points at. The whole card is the target rather
 *   than just its title, because a notification the user is reading is by definition a notification
 *   they want to act on, and a 12dp-tall strip of title text is not something to aim at.
 * - **The cross dismisses** it. Dismissing says "I have seen this", not "do not remind me": the
 *   queue's progress was already committed before the card existed, so closing it cannot unsend a
 *   reminder, and cannot stop the schedule either.
 *
 * Hovering freezes the countdown. The window is unfocusable by design — it must not take the
 * keyboard away from whatever the user is typing in — so hover is the only signal available that
 * they have actually read it, and it is the one that should buy them more time.
 */
@Composable
fun NotificationCard(
    item: PresentedNotice,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    onPauseChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var hovered by remember { mutableStateOf(false) }
    LaunchedEffect(hovered) { onPauseChanged(hovered) }
    val dismissLabel = stringResource(Res.string.notificationDismiss)
    AzkarCard(
        modifier =
            modifier
                .fillMaxWidth()
                .hoverTracking { hovered = it }
                .clickable(onClick = onOpen),
        size = CardSize.SM,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(AzkarSpacing.xs),
            ) {
                Text(
                    text = item.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AzkarTheme.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = item.message,
                    fontSize = 13.sp,
                    color = AzkarTheme.colors.textSecondary,
                    maxLines = MESSAGE_MAX_LINES,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                modifier =
                    Modifier
                        .padding(start = AzkarSpacing.sm)
                        .semantics { contentDescription = dismissLabel }
                        .size(DISMISS_TOUCH_TARGET)
                        .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = AzkarIcons.Close,
                    contentDescription = null,
                    tint = AzkarTheme.colors.textSecondary,
                    modifier = Modifier.size(DISMISS_ICON),
                )
            }
        }
    }
}

/**
 * Reports whether the pointer is over this composable.
 *
 * Hand-rolled over [PointerEventType.Enter]/[PointerEventType.Exit] rather than using a hover
 * effect, because the only thing being observed is presence and not treatment — the card's
 * appearance is identical hovered and not, so there is nothing to animate and nothing to remember
 * beyond the boolean.
 */
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

/** Room for a title and four wrapped lines, which is what a remembrance of any length comes to. */
private const val MESSAGE_MAX_LINES = 4

/** 28dp of visible cross inside a 40dp target: small enough to stay out of the way, big enough to hit. */
private val DISMISS_ICON = 14.dp

private val DISMISS_TOUCH_TARGET = 40.dp
