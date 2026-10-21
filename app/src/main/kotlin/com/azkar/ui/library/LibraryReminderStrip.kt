package com.azkar.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.azkar.generated.resources.Res
import com.azkar.generated.resources.libraryReminderStripCount
import com.azkar.generated.resources.libraryReminderStripEmpty
import com.azkar.generated.resources.libraryReminderStripOff
import com.azkar.generated.resources.libraryReminderStripUpNext
import com.azkar.ui.components.AzkarIcons
import com.azkar.ui.notifications.QueuePreview
import com.azkar.ui.notifications.QueueReading
import com.azkar.ui.notifications.QueueSources
import com.azkar.ui.notifications.ReminderQueueConfig
import com.azkar.ui.notifications.notificationLabel
import com.azkar.ui.notifications.queuePreview
import com.azkar.ui.theme.AzkarTheme
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/**
 * One line under the library's banner saying what the queue will say next, and how much it has to
 * say.
 *
 * The Reminders screen used to hold this, and it was the only place the answer was visible. Moving
 * the management of the reminders into the library left the answer with nowhere to live, and a queue
 * whose next reminder cannot be seen is a queue the user has to trust rather than one they can check.
 * One line, under the banner the shelf already sits under, is enough to answer it.
 *
 * The passage it names is read from the runtime's own progress, so the strip cannot promise a
 * passage the queue is not about to show — see [queuePreview] for why the two are asked the same
 * question in the same order.
 */
@Composable
internal fun LibraryReminderStrip(
    state: CollectionsState,
    modifier: Modifier = Modifier,
) {
    val colors = AzkarTheme.colors
    val config = state.reminders.queueConfig
    val progress = state.reminders.queueProgress
    val picksId = state.catalogue.reminderPicksCollectionId
    val selection =
        remember(state.catalogue.allCollections, config, picksId) {
            QueueSources.select(state.catalogue.allCollections, config, picksId)
        }
    val now = summaryClock()
    val reading =
        remember(selection, state.catalogue.allRemembrances) {
            QueueReading(
                orderedIds = selection.orderedIds,
                remembrances = state.catalogue.allRemembrances,
            )
        }
    val preview = queuePreview(now, ZoneId.systemDefault(), config, progress, reading)

    val message = stripMessage(config = config, preview = preview, uiLocale = state.uiLocale)
    val sources =
        stringResource(
            Res.string.libraryReminderStripCount,
            state.reminderCollectionIds.size,
            selection.orderedIds.size,
        )

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(STRIP_GAP),
    ) {
        Icon(
            imageVector = AzkarIcons.Bell,
            contentDescription = null,
            tint = if (config.enabled && preview.hasSources) colors.accent else colors.textSecondary,
            modifier = Modifier.size(STRIP_ICON_SIZE),
        )
        Text(
            text = message,
            color = colors.textPrimary,
            fontSize = STRIP_TEXT_SP,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = sources,
            color = colors.textSecondary,
            fontSize = STRIP_META_SP,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The line's own words.
 *
 * Three answers rather than one, because "the queue is switched off", "the queue has nothing to
 * say" and "the queue will say this next" are three different facts about the same queue, and
 * collapsing them into one sentence would leave the user guessing which of the three it was.
 */
@Composable
private fun stripMessage(
    config: ReminderQueueConfig,
    preview: QueuePreview,
    uiLocale: Locale,
): String =
    when {
        !config.enabled -> stringResource(Res.string.libraryReminderStripOff)
        !preview.hasSources -> stringResource(Res.string.libraryReminderStripEmpty)
        else -> {
            val label = preview.passage?.let { notificationLabel(it, uiLocale) }.orEmpty()
            if (label.isBlank()) {
                stringResource(Res.string.libraryReminderStripEmpty)
            } else {
                stringResource(Res.string.libraryReminderStripUpNext, label)
            }
        }
    }

/**
 * A clock that reads itself once every [SUMMARY_TICK_MILLIS].
 *
 * The strip only changes when the queue does, and the queue's next slot is a quarter of an hour
 * away at best, so a clock that ticks every second would be a recomposition sixty times a minute
 * for a line that changes four times an hour.
 */
@Composable
private fun summaryClock(): Instant {
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(SUMMARY_TICK_MILLIS)
            now = Instant.now()
        }
    }
    return now
}

/** How often the strip redraws itself, in milliseconds. */
private const val SUMMARY_TICK_MILLIS = 20_000L

/** The gap between the bell and the words, and between the words and the count. */
private val STRIP_GAP = 8.dp

/** The bell's size, and the two text sizes beside it. */
private val STRIP_ICON_SIZE = 16.dp
private val STRIP_TEXT_SP = 12.sp
private val STRIP_META_SP = 11.sp
