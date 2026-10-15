package com.azkar.ui.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.azkar.generated.resources.Res
import com.azkar.generated.resources.reminderManagerHeroCountdownDays
import com.azkar.generated.resources.reminderManagerHeroCountdownHours
import com.azkar.generated.resources.reminderManagerHeroCountdownMinutes
import com.azkar.generated.resources.reminderManagerHeroCountdownNow
import com.azkar.generated.resources.reminderManagerHeroQueueEmpty
import com.azkar.generated.resources.reminderManagerHeroQueueLabel
import com.azkar.generated.resources.reminderManagerHeroQueueOff
import com.azkar.generated.resources.reminderManagerHeroTitle
import com.azkar.generated.resources.reminderManagerHeroWindow
import com.azkar.ui.components.AzkarClock
import com.azkar.ui.components.AzkarIcons
import com.azkar.ui.components.AzkarSurfaceCard
import com.azkar.ui.components.mirrorHorizontalGradient
import com.azkar.ui.theme.AzkarSpacing
import com.azkar.ui.theme.AzkarTheme
import com.azkar.ui.theme.AzkarThemePreview
import com.azkar.ui.theme.isRtlLayout
import org.jetbrains.compose.resources.stringResource
import java.time.Duration
import java.time.LocalDateTime

/** Joins the window and the countdown without the `listOf()` ceremony at every call site. */
private const val SEPARATOR = " · "

/** Counts for the countdown, so the branches read as durations rather than as magic numbers. */
private const val MINUTES_IN_HOUR = 60L

private const val MINUTES_IN_DAY = 24 * MINUTES_IN_HOUR

/**
 * The "up next" card that opens the screen.
 *
 * Everything below it is a control for a schedule nobody sees until the schedule runs, which is why
 * the old screen opened on the first of six stacked cards instead. A settings page answers "what
 * does this do?" only once you have changed it; this one answers "what is going to happen?" before,
 * and the rest of the page holds the settings for that answer.
 *
 * The answer comes from the same schedule the runtime fires from — [QueueSchedule.nextSlot] rather
 * than a second opinion rendered here — so the card cannot promise a reminder the app was never
 * going to send.
 *
 * This used to carry a second row for the next prayer time as well. Prayer alerts no longer belong
 * to the queue: the per-prayer switches live on the home screen next to the times they gate, and the
 * location that decides those times is an app-level setting. A row for something this screen cannot
 * change is a row that teaches the wrong page, so the card answers one question instead of two.
 *
 * @param queueNow is read on the desktop's zone, which is the zone the queue's window belongs to.
 * Taking the clock as a parameter rather than calling it here keeps the countdown testable and stops
 * the card from disagreeing with the runtime on the day a zone changes.
 */
@Composable
internal fun RemindersSummaryCard(
    preview: QueuePreview,
    config: ReminderQueueConfig,
    queueNow: LocalDateTime,
    modifier: Modifier = Modifier,
) {
    val colors = AzkarTheme.colors
    val passage = preview.passage
    AzkarSurfaceCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        contentPadding = AzkarSpacing.xl,
        background =
            mirrorHorizontalGradient(
                colors = listOf(colors.azkarCardStart, colors.azkarCardEnd),
                isRtl = isRtlLayout(),
            ),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(AzkarSpacing.md)) {
            Text(
                text = stringResource(Res.string.reminderManagerHeroTitle),
                color = colors.primaryStrong,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
            SummaryRow(
                icon = AzkarIcons.BookOpen,
                label = stringResource(Res.string.reminderManagerHeroQueueLabel),
                headline =
                    when {
                        !preview.enabled -> stringResource(Res.string.reminderManagerHeroQueueOff)
                        passage != null -> remembranceLabel(passage)
                        else -> stringResource(Res.string.reminderManagerHeroQueueEmpty)
                    },
                detail =
                    preview.slot
                        ?.let { slot ->
                            listOf(windowText(config), countdownText(slot, queueNow)).joinToString(SEPARATOR)
                        }.orEmpty(),
            )
        }
    }
}

/**
 * The summary's one line: a glyph, what it is, and the answer.
 *
 * Label over headline over detail rather than a single line, because "in 2h 15m" and the passage it
 * refers to are different claims and one line has to pick which is the headline. Picking wrong makes
 * the card read as either a clock or a passage preview when it is both, and the count is the part
 * that changes every minute — the part that needs to be findable at a glance.
 */
@Composable
private fun SummaryRow(
    icon: ImageVector,
    label: String,
    headline: String,
    detail: String,
) {
    val colors = AzkarTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AzkarSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.primaryStrong,
                modifier = Modifier.size(19.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = colors.textSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = headline,
                color = colors.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (detail.isNotBlank()) {
                Text(
                    text = detail,
                    color = colors.textSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * The schedule behind the queue's countdown: the window the next slot has to fall inside.
 *
 * [AzkarClock] rather than a private 24-hour pattern, because this card used to be the one place in
 * the app that printed a clock the reader's own hands did not match.
 */
@Composable
private fun windowText(config: ReminderQueueConfig): String =
    stringResource(
        Res.string.reminderManagerHeroWindow,
        AzkarClock.format(config.windowStartMinute, AzkarTheme.locale),
        AzkarClock.format(config.windowEndMinute, AzkarTheme.locale),
    )

/**
 * "in 45m", "in 2h 15m", "in 3d 4h" or "now".
 *
 * Truncating to whole units rather than rounding is what keeps a queue that fires on the hour from
 * reading "in 0m" for the last sixty seconds of that hour. A countdown that is briefly wrong makes
 * the entire card untrustworthy, and this is the one number on it that moves.
 *
 * The days branch exists because the queue can now be limited to particular weekdays. A Monday-only
 * queue read on a Saturday is more than a day out, and the hours form would have printed "in 70h" —
 * a number with no unit a reader can place in a week, next to a window that says the queue does not
 * run today at all.
 */
@Composable
private fun countdownText(
    target: LocalDateTime,
    now: LocalDateTime,
): String {
    val minutes = Duration.between(now, target).toMinutes()
    return when {
        minutes <= 0L -> stringResource(Res.string.reminderManagerHeroCountdownNow)
        minutes < MINUTES_IN_HOUR -> stringResource(Res.string.reminderManagerHeroCountdownMinutes, minutes)
        minutes < MINUTES_IN_DAY ->
            stringResource(
                Res.string.reminderManagerHeroCountdownHours,
                minutes / MINUTES_IN_HOUR,
                minutes % MINUTES_IN_HOUR,
            )
        else ->
            stringResource(
                Res.string.reminderManagerHeroCountdownDays,
                minutes / MINUTES_IN_DAY,
                minutes % MINUTES_IN_DAY / MINUTES_IN_HOUR,
            )
    }
}

@Preview
@Composable
private fun RemindersSummaryCardPreview() {
    AzkarThemePreview {
        val config =
            ReminderQueueConfig(
                enabled = true,
                collectionIds = listOf(1L),
                intervalMinutes = 60,
            )
        val now = LocalDateTime.of(2026, 3, 14, 9, 5)
        Box(modifier = Modifier.width(460.dp).padding(16.dp)) {
            RemindersSummaryCard(
                preview =
                    QueuePreview(
                        enabled = true,
                        hasSources = true,
                        slot = now.plusMinutes(95),
                        passage = null,
                    ),
                config = config,
                queueNow = now,
            )
        }
    }
}
