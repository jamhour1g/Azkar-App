package com.azkar.ui.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.azkar.generated.resources.Res
import com.azkar.generated.resources.reminderManagerDayFriday
import com.azkar.generated.resources.reminderManagerDayMonday
import com.azkar.generated.resources.reminderManagerDaySaturday
import com.azkar.generated.resources.reminderManagerDaySunday
import com.azkar.generated.resources.reminderManagerDayThursday
import com.azkar.generated.resources.reminderManagerDayTuesday
import com.azkar.generated.resources.reminderManagerDayWednesday
import com.azkar.generated.resources.reminderManagerQueueCadenceContinuous
import com.azkar.generated.resources.reminderManagerQueueCadenceContinuousHint
import com.azkar.generated.resources.reminderManagerQueueCadenceHint
import com.azkar.generated.resources.reminderManagerQueueCadenceInterval
import com.azkar.generated.resources.reminderManagerQueueCadenceLabel
import com.azkar.generated.resources.reminderManagerQueueDaysHint
import com.azkar.generated.resources.reminderManagerQueueDaysLabel
import com.azkar.generated.resources.reminderManagerQueueDropChance
import com.azkar.generated.resources.reminderManagerQueueDropChanceHint
import com.azkar.generated.resources.reminderManagerQueueEnable
import com.azkar.generated.resources.reminderManagerQueueEnableHint
import com.azkar.generated.resources.reminderManagerQueueIntervalHint
import com.azkar.generated.resources.reminderManagerQueueIntervalLabel
import com.azkar.generated.resources.reminderManagerQueueIntervalValue
import com.azkar.generated.resources.reminderManagerQueueModeHint
import com.azkar.generated.resources.reminderManagerQueueModeInLibraryOrder
import com.azkar.generated.resources.reminderManagerQueueModeLabel
import com.azkar.generated.resources.reminderManagerQueueModeRandom
import com.azkar.generated.resources.reminderManagerQueueModeWeighted
import com.azkar.generated.resources.reminderManagerQueueModeWeightedHint
import com.azkar.generated.resources.reminderManagerQueueWindowEnd
import com.azkar.generated.resources.reminderManagerQueueWindowHint
import com.azkar.generated.resources.reminderManagerQueueWindowLabel
import com.azkar.generated.resources.reminderManagerQueueWindowStart
import com.azkar.ui.components.AzkarDayPicker
import com.azkar.ui.components.AzkarTimePicker
import com.azkar.ui.components.AzkarToggleSwitch
import com.azkar.ui.components.HintText
import com.azkar.ui.components.SelectRow
import com.azkar.ui.components.SettingRow
import com.azkar.ui.components.StepperRow
import com.azkar.ui.notifications.QueueCadence
import com.azkar.ui.notifications.QueueMode
import com.azkar.ui.notifications.ReminderQueueConfig
import com.azkar.ui.notifications.ReminderScheduleEditor
import com.azkar.ui.preview.rememberPreviewReminderScheduleEditor
import com.azkar.ui.theme.AzkarSpacing
import com.azkar.ui.theme.AzkarTheme
import com.azkar.ui.theme.AzkarThemePreview
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import java.time.DayOfWeek

/*
 * The queue's schedule, on the settings page's notifications tab.
 *
 * This card was the first half of the reminders screen, above the sources it was scheduling. It moved
 * because of what it *is*: an order, a cadence, an interval, a weekday strip and a pair of hour
 * pickers is a description of how the app behaves, and every one of those five is still true when no
 * queue is switched on, no passage is queued and the reminders screen is never opened. A setting whose
 * only meaning is "what the other page does" is not that — it is half of that page.
 *
 * What stays behind is the half that cannot exist without a catalogue: the collections and passages
 * the queue draws from, and the order it walks them in. Those are the answers to "what will I see",
 * which is what that page's summary is a preview of, so it keeps them beside it.
 *
 * The design is unchanged by the move. The card is a [SectionCard] like every other one on this page,
 * its rows are the shared [SettingRow] and [SelectRow], and the only control it lost in the journey
 * was nothing: it already had no border, no shadow and no title treatment of its own to reconcile
 * with the settings header it now sits under.
 */

/**
 * The intervals offered for the queue, in minutes.
 *
 * Fixed rather than free-entered because these are the gaps a waking day divides into, and the old
 * screen's free-text interval field could be set to a value the config's own `require` rejects — the
 * field showed the number, the save silently clamped it, and nothing told the user.
 */
private val INTERVAL_CHOICES = listOf(5, 10, 15, 30, 60, 90, 120, 180)

/**
 * On/off, order, cadence, interval, days and window.
 *
 * Everything below the master switch is only rendered while the queue is on, which is the one
 * deliberate behaviour change from the old card — it showed a full set of controls for a queue that
 * was switched off, so a disabled feature occupied more of the screen than the rest of the page put
 * together and the eye could not tell which parts were live. It is also what keeps the notifications
 * tab short: the card-appearance and startup cards below it are all live with the queue off.
 */
@Composable
internal fun ReminderScheduleCard(
    config: ReminderQueueConfig,
    schedule: ReminderScheduleEditor,
    modifier: Modifier = Modifier,
) {
    SettingsGroupCard(modifier = modifier, contentPadding = AzkarSpacing.xl) {
        SettingRow(
            label = stringResource(Res.string.reminderManagerQueueEnable),
            hint = stringResource(Res.string.reminderManagerQueueEnableHint),
        ) {
            AzkarToggleSwitch(
                checked = config.enabled,
                onCheckedChange = schedule::setEnabled,
            )
        }

        SelectRow(
            label = stringResource(Res.string.reminderManagerQueueModeLabel),
            selected = config.mode,
            options = QueueMode.entries,
            optionLabel = ::queueModeLabel,
            onSelect = schedule::setMode,
            hint = stringResource(Res.string.reminderManagerQueueModeHint),
            enabled = config.enabled,
        )
        if (config.mode == QueueMode.WEIGHTED) {
            HintText(stringResource(Res.string.reminderManagerQueueModeWeightedHint))
            StepperRow(
                label = stringResource(Res.string.reminderManagerQueueDropChance),
                value = config.defaultDropChance,
                onValueChange = schedule::setDefaultDropChance,
                range = 0..100,
                hint = stringResource(Res.string.reminderManagerQueueDropChanceHint),
                enabled = config.enabled,
            )
        }
        CadenceRows(config = config, schedule = schedule, enabled = config.enabled)
        DaysRow(config = config, schedule = schedule, enabled = config.enabled)
        if (config.cadence == QueueCadence.INTERVAL) {
            WindowRow(config = config, schedule = schedule, enabled = config.enabled)
        }
    }
}

/**
 * How often the queue speaks: on a chosen interval, or without stopping.
 *
 * A third mode — "keep running" — sits beside "on an interval" rather than replacing it, because the
 * two are answers to different questions. The interval asks *when the queue speaks*; continuous says
 * the window does not get a say, that the queue keeps working through its cycle until the day is
 * switched off. That is not a large value of the same dial: "between eight and ten, every hour" and
 * "every hour" are different promises, and only one of them has an evening attached to it.
 *
 * The interval menu is shown in **both** modes. It was hidden while continuous was selected, on the
 * reasoning that a queue with no interval has nothing for the control to set — which quietly took the
 * user's own gap away with it and left them on the five-minute default, one hundred and sixty-eight
 * times a day. A queue that never stops still needs to say how often "never stops" means.
 *
 * What continuous mode drops is the window, and only the window; see [WindowRow].
 */
@Composable
private fun CadenceRows(
    config: ReminderQueueConfig,
    schedule: ReminderScheduleEditor,
    enabled: Boolean,
) {
    SelectRow(
        label = stringResource(Res.string.reminderManagerQueueCadenceLabel),
        selected = config.cadence,
        options = QueueCadence.entries,
        optionLabel = ::cadenceLabel,
        onSelect = schedule::setCadence,
        hint = stringResource(Res.string.reminderManagerQueueCadenceHint),
        enabled = enabled,
    )
    SelectRow(
        label = stringResource(Res.string.reminderManagerQueueIntervalLabel),
        selected = config.intervalMinutes,
        options = INTERVAL_CHOICES,
        optionLabel = ::intervalLabel,
        onSelect = schedule::setIntervalMinutes,
        fieldWidth = 170.dp,
        hint = stringResource(Res.string.reminderManagerQueueIntervalHint),
        enabled = enabled,
    )
    if (config.cadence == QueueCadence.CONTINUOUS) {
        HintText(stringResource(Res.string.reminderManagerQueueCadenceContinuousHint))
    }
}

/**
 * The days the queue is allowed to run on.
 *
 * Shown before the window rather than after it, because the window says what a day looks like and
 * this says which days exist. A Monday-only queue with a 9-to-5 window reads left to right as
 * "Mondays, between nine and five", and the two rows are the whole schedule.
 */
@Composable
private fun DaysRow(
    config: ReminderQueueConfig,
    schedule: ReminderScheduleEditor,
    enabled: Boolean,
) {
    SettingRow(
        label = stringResource(Res.string.reminderManagerQueueDaysLabel),
        hint = stringResource(Res.string.reminderManagerQueueDaysHint),
        enabled = enabled,
    ) {
        AzkarDayPicker(
            activeDays = config.activeDays,
            onDayChange = schedule::setDayActive,
            dayLabel = ::activeDayLabel,
            modifier = Modifier.width(320.dp),
            enabled = enabled,
        )
    }
}

/**
 * The active window, as a start and an end picker on one line.
 *
 * Shown only for [QueueCadence.INTERVAL], and that conditional is the whole of what distinguishes a
 * timetable from a metronome. The stored minutes are left alone while continuous is selected, so
 * switching back to an interval restores the hours the user had rather than quietly replacing them
 * with defaults.
 */
@Composable
private fun WindowRow(
    config: ReminderQueueConfig,
    schedule: ReminderScheduleEditor,
    enabled: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(AzkarSpacing.sm)) {
        Text(
            text = stringResource(Res.string.reminderManagerQueueWindowLabel),
            color = if (enabled) AzkarTheme.colors.textPrimary else AzkarTheme.colors.textSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(AzkarSpacing.md)) {
            LabelledTimePicker(
                label = stringResource(Res.string.reminderManagerQueueWindowStart),
                minuteOfDay = config.windowStartMinute,
                onMinuteChange = schedule::setWindowStart,
                modifier = Modifier.weight(1f),
                enabled = enabled,
            )
            LabelledTimePicker(
                label = stringResource(Res.string.reminderManagerQueueWindowEnd),
                minuteOfDay = config.windowEndMinute,
                onMinuteChange = schedule::setWindowEnd,
                modifier = Modifier.weight(1f),
                enabled = enabled,
            )
        }
        HintText(stringResource(Res.string.reminderManagerQueueWindowHint))
    }
}

/**
 * A window boundary, labelled above its picker.
 *
 * The label sits outside [AzkarTimePicker] rather than inside it because that control owns an hour
 * and a minute under one value, and a single label over the pair would name the hour and quietly
 * ignore the minute.
 */
@Composable
private fun LabelledTimePicker(
    label: String,
    minuteOfDay: Int,
    onMinuteChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(AzkarSpacing.xs)) {
        Text(
            text = label,
            color = AzkarTheme.colors.textSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
        AzkarTimePicker(minuteOfDay = minuteOfDay, onMinuteChange = onMinuteChange, enabled = enabled)
    }
}

@Composable
private fun queueModeLabel(mode: QueueMode): String =
    when (mode) {
        QueueMode.MANUAL -> stringResource(Res.string.reminderManagerQueueModeInLibraryOrder)
        QueueMode.RANDOM -> stringResource(Res.string.reminderManagerQueueModeRandom)
        QueueMode.WEIGHTED -> stringResource(Res.string.reminderManagerQueueModeWeighted)
    }

@Composable
private fun intervalLabel(minutes: Int): String =
    pluralStringResource(Res.plurals.reminderManagerQueueIntervalValue, minutes, minutes)

/**
 * A cadence as the user named it.
 *
 * "Keep running" rather than "Continuous", because the two words answer different questions and the
 * queue's own question is whether it ever stops. A user who reads "Continuous" as a density setting
 * will look for a number beside it; there is not one, and the sentence under the row is what tells
 * them so.
 */
@Composable
private fun cadenceLabel(cadence: QueueCadence): String =
    when (cadence) {
        QueueCadence.INTERVAL -> stringResource(Res.string.reminderManagerQueueCadenceInterval)
        QueueCadence.CONTINUOUS -> stringResource(Res.string.reminderManagerQueueCadenceContinuous)
    }

/**
 * A weekday's short name for the day strip.
 *
 * Seven full names on one row overflow the card on any window narrower than the 760dp the settings
 * column is capped at, so these are abbreviations — except in Arabic, where the names are one or two
 * short words and shortening them would produce strings no reader would recognise.
 */
@Composable
private fun activeDayLabel(day: DayOfWeek): String =
    when (day) {
        DayOfWeek.MONDAY -> stringResource(Res.string.reminderManagerDayMonday)
        DayOfWeek.TUESDAY -> stringResource(Res.string.reminderManagerDayTuesday)
        DayOfWeek.WEDNESDAY -> stringResource(Res.string.reminderManagerDayWednesday)
        DayOfWeek.THURSDAY -> stringResource(Res.string.reminderManagerDayThursday)
        DayOfWeek.FRIDAY -> stringResource(Res.string.reminderManagerDayFriday)
        DayOfWeek.SATURDAY -> stringResource(Res.string.reminderManagerDaySaturday)
        DayOfWeek.SUNDAY -> stringResource(Res.string.reminderManagerDaySunday)
    }

@Preview
@Composable
private fun ReminderScheduleCardPreview() {
    AzkarThemePreview {
        ReminderScheduleCard(
            config = ReminderQueueConfig(enabled = true, cadence = QueueCadence.CONTINUOUS),
            schedule = rememberPreviewReminderScheduleEditor(),
        )
    }
}
