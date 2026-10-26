package com.azkar.ui.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.azkar.generated.resources.Res
import com.azkar.generated.resources.settingsNotificationsScheduleHint
import com.azkar.generated.resources.settingsNotificationsScheduleTitle
import com.azkar.ui.notifications.NotificationSettingsState
import com.azkar.ui.notifications.ReminderQueueConfig
import com.azkar.ui.preview.rememberPreviewNotificationSettingsState
import com.azkar.ui.theme.AzkarPreview
import com.azkar.ui.theme.AzkarSpacing
import org.jetbrains.compose.resources.stringResource

// `ui/shell/SettingsReminderSections.kt` — the two blocks under Reminders: when a reminder is due, and
// whether the app is running to deliver one. Those used to be four things in one block called "On screen
// & desktop notifications", sharing a card with the card's own size and duration and with start-on-login,
// so the one question "may this reminder reach me at all?" was answered by a switch that happened to be
// the fourth row of a card about how a reminder looks. How the card is drawn is under Appearance now,
// beside the theme; when it fires is here; and start-on-login, the one setting that decides whether there
// is a process to deliver anything, is here too.

/**
 * When the queue is on, and what it draws from.
 *
 * Its own block above the delivery one, rather than its top card of the reminders screen, because it is
 * the setting that decides whether anything below has anything to show: a queue that never fires makes
 * the delivery channel a question about a card that will not appear. Reading it first also keeps the
 * promise the rail makes — that every block on this page is named, and the one that decides whether the
 * others matter is named in the same voice as the rest.
 *
 * It used to lead the reminders screen instead, which sat above the very passages it was scheduling: a
 * page whose first answer was "every hour, between eight and ten" and whose last was a search box.
 *
 * What is *not* here is the queue's contents: which passages rotate and in what order are answers to
 * "what will I see", so they stay beside the summary that shows the answer.
 */
@Composable
internal fun SettingsReminderScheduleSection(
    queue: ReminderQueueConfig,
    settings: NotificationSettingsState,
    modifier: Modifier = Modifier,
) {
    SettingsSectionBlock(
        title = stringResource(Res.string.settingsNotificationsScheduleTitle),
        subtitle = stringResource(Res.string.settingsNotificationsScheduleHint),
        modifier = modifier,
    ) {
        ReminderScheduleCard(config = queue, schedule = settings.schedule)
    }
}

@AzkarPreview
@Composable
internal fun SettingsReminderSectionsPreview() {
    Column(
        modifier = Modifier.width(820.dp).padding(AzkarSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(AzkarSpacing.section),
    ) {
        SettingsReminderScheduleSection(
            queue = ReminderQueueConfig(enabled = true),
            settings = rememberPreviewNotificationSettingsState(),
        )
    }
}
