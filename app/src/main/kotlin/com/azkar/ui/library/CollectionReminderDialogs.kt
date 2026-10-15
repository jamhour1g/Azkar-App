package com.azkar.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.azkar.domain.model.CollectionReminder
import com.azkar.domain.model.ReminderCadence
import com.azkar.generated.resources.Res
import com.azkar.generated.resources.collectionReminderCadenceDaily
import com.azkar.generated.resources.collectionReminderCadenceLabel
import com.azkar.generated.resources.collectionReminderCadenceWeekdays
import com.azkar.generated.resources.collectionReminderCadenceWeekly
import com.azkar.generated.resources.collectionReminderDayFriday
import com.azkar.generated.resources.collectionReminderDayLabel
import com.azkar.generated.resources.collectionReminderDayMonday
import com.azkar.generated.resources.collectionReminderDaySaturday
import com.azkar.generated.resources.collectionReminderDaySunday
import com.azkar.generated.resources.collectionReminderDayThursday
import com.azkar.generated.resources.collectionReminderDayTuesday
import com.azkar.generated.resources.collectionReminderDayWednesday
import com.azkar.generated.resources.collectionReminderDialogHint
import com.azkar.generated.resources.collectionReminderDialogTitle
import com.azkar.generated.resources.collectionReminderPauseHint
import com.azkar.generated.resources.collectionReminderPaused
import com.azkar.generated.resources.collectionReminderRemove
import com.azkar.generated.resources.collectionReminderRemoveBody
import com.azkar.generated.resources.collectionReminderRemoveTitle
import com.azkar.generated.resources.collectionReminderRunning
import com.azkar.generated.resources.collectionReminderTimeLabel
import com.azkar.generated.resources.collectionsActionCancel
import com.azkar.generated.resources.collectionsActionDelete
import com.azkar.generated.resources.collectionsActionSave
import com.azkar.ui.components.AzkarClock
import com.azkar.ui.components.AzkarDialogSurface
import com.azkar.ui.components.AzkarTimePicker
import com.azkar.ui.components.AzkarToggleSwitch
import com.azkar.ui.components.PrimaryButton
import com.azkar.ui.components.SecondaryButton
import com.azkar.ui.theme.AzkarTheme
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/**
 * A schedule being written, held while the dialog is open.
 *
 * A state holder rather than four composable locals because the dropdowns are independent: changing
 * the cadence to weekly has to bring a day into being, and changing it away has to take that day back
 * out, so the rule cannot live in whichever composable happens to own the cadence picker.
 *
 * It normalises on every change rather than only on submit. The domain model refuses a daily reminder
 * that names a day, and a schedule the dialog holds but cannot save is a schedule the user thinks they
 * have set.
 */
@Stable
class ReminderDraft
    private constructor(
        initialCadence: ReminderCadence,
        initialMinuteOfDay: Int,
        initialDayOfWeek: DayOfWeek?,
    ) {
        var cadence: ReminderCadence by mutableStateOf(initialCadence)
            private set

        var minuteOfDay: Int by mutableStateOf(initialMinuteOfDay)
            private set

        var dayOfWeek: DayOfWeek? by mutableStateOf(initialDayOfWeek)
            private set

        val hour: Int get() = minuteOfDay / 60
        val minute: Int get() = minuteOfDay % 60

        fun onCadenceChanged(value: ReminderCadence) {
            cadence = value
            dayOfWeek =
                if (value == ReminderCadence.WEEKLY) {
                    dayOfWeek ?: DEFAULT_DAY
                } else {
                    null
                }
        }

        /** Rebuilds the minute from the hour, keeping whatever minute of the hour was already chosen. */
        fun onHourChanged(value: Int) {
            minuteOfDay = value * 60 + minuteOfDay % 60
        }

        fun onMinuteChanged(value: Int) {
            minuteOfDay = minuteOfDay / 60 * 60 + value
        }

        /** The weekday a weekly schedule falls on. Ignored unless the cadence is weekly. */
        fun onDayChanged(value: DayOfWeek) {
            if (cadence == ReminderCadence.WEEKLY) dayOfWeek = value
        }

        companion object {
            private val DEFAULT_DAY = DayOfWeek.MONDAY
            private const val DEFAULT_MINUTE_OF_DAY = 6 * 60

            /** A brand new schedule, at the hour most people recite. */
            fun create() = ReminderDraft(ReminderCadence.DAILY, DEFAULT_MINUTE_OF_DAY, null)

            /** The schedule already stored for a collection, opened for editing. */
            fun of(reminder: CollectionReminder) =
                ReminderDraft(reminder.cadence, reminder.minuteOfDay, reminder.dayOfWeek)
        }
    }

/** What the editor asks the page to do, bundled so the dialog can stay a view of a draft. */
@Immutable
data class ReminderEditorCallbacks(
    val onSave: (cadence: ReminderCadence, minuteOfDay: Int, dayOfWeek: DayOfWeek?) -> Unit,
    val onRemove: () -> Unit,
    val onDismiss: () -> Unit,
    /**
     * Pauses or resumes the stored schedule.
     *
     * Not part of the draft and not applied by Save: the switch is about whether an existing schedule
     * runs, and making the user press a second button to stop something sounding at them would be the
     * wrong way round. It therefore acts the moment it is moved, which is also why it can fail on its
     * own — the page rolls it back and says so rather than letting a switch sit in a position the
     * database never reached.
     */
    val onToggleEnabled: (Boolean) -> Unit = {},
)

/**
 * Writes the schedule for one collection.
 *
 * Four dropdowns rather than a time picker because the project has none and a desktop window this size
 * has room for the choice to be made with the keyboard and the mouse. The minutes step in fives
 * because a reminder five minutes past is precise enough to be worth having and precise enough that
 * nobody needs the other fifty-five.
 *
 * The pause switch appears only for a schedule that already exists, because a schedule being written
 * cannot be paused — pausing is a claim about something already stored, and offering it for a schedule
 * that does not exist yet would be a switch over nothing.
 */
@Composable
internal fun CollectionReminderDialog(
    draft: ReminderDraft,
    collectionName: String,
    isExisting: Boolean,
    isEnabled: Boolean = true,
    callbacks: ReminderEditorCallbacks,
) {
    Dialog(onDismissRequest = callbacks.onDismiss) {
        AzkarDialogSurface(modifier = Modifier.widthIn(min = 420.dp, max = 520.dp)) {
            Column {
                Text(
                    text = stringResource(Res.string.collectionReminderDialogTitle),
                    color = AzkarTheme.colors.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = collectionName,
                    color = AzkarTheme.colors.primaryStrong,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Text(
                    text = stringResource(Res.string.collectionReminderDialogHint),
                    color = AzkarTheme.colors.textSecondary,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
                ReminderFields(draft, modifier = Modifier.padding(top = 14.dp))
                // Pausing and removing both answer "stop this coming round", so they sit together under
                // the pickers that say *when* it would have come round: what the schedule is, then what
                // is being done about it.
                if (isExisting) {
                    ReminderPauseRow(
                        isEnabled = isEnabled,
                        onToggle = callbacks.onToggleEnabled,
                        modifier = Modifier.padding(top = PAUSE_ROW_GAP),
                    )
                    SecondaryButton(
                        text = stringResource(Res.string.collectionReminderRemove),
                        onClick = callbacks.onRemove,
                        modifier = Modifier.padding(top = PAUSE_ACTION_GAP),
                    )
                }
                Spacer(Modifier.width(8.dp))
                ReminderDialogActions(draft, callbacks)
            }
        }
    }
}

/** The four pickers, so the dialog body reads as a heading, a hint, the pickers and the buttons. */
@Composable
private fun ReminderFields(
    draft: ReminderDraft,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ReminderFieldLabel(stringResource(Res.string.collectionReminderCadenceLabel))
        AzkarDropdown(
            selected = draft.cadence,
            options = ReminderCadence.entries,
            label = ::cadenceLabel,
            onSelect = draft::onCadenceChanged,
            fillAvailableWidth = true,
        )
        if (draft.cadence == ReminderCadence.WEEKLY) {
            ReminderFieldLabel(stringResource(Res.string.collectionReminderDayLabel))
            AzkarDropdown(
                selected = draft.dayOfWeek,
                options = DayOfWeek.entries,
                label = ::dayLabel,
                onSelect = draft::onDayChanged,
                fillAvailableWidth = true,
            )
        }
        ReminderFieldLabel(stringResource(Res.string.collectionReminderTimeLabel))
        TimePickers(draft)
    }
}

/**
 * The switch that pauses a stored schedule without forgetting it.
 *
 * This control used to live on a reminder card that sat under the band's title, and it went when the
 * card did. What replaced that card is the bell that opens this editor, so the switch has to live here
 * too — a schedule that can be set but not silenced, from anywhere the user can reach, is not a
 * feature so much as a way to be told at the wrong moment.
 *
 * The label states the state in words rather than naming the control, because the control's own
 * position is the thing a glance already answers and the words are there for the case where it does not
 * — a switch that reads "off" beside a label that insists on "on" is worse than either alone. The hint
 * underneath is deliberately valid in both positions, so it explains what pausing *does* (keeps the
 * time, drops the notifications) rather than narrating the switch.
 */
@Composable
private fun ReminderPauseRow(
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AzkarTheme.colors
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text =
                    stringResource(
                        if (isEnabled) {
                            Res.string.collectionReminderRunning
                        } else {
                            Res.string.collectionReminderPaused
                        },
                    ),
                color = colors.textPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(Res.string.collectionReminderPauseHint),
                color = colors.textSecondary,
                fontSize = 11.sp,
                lineHeight = PAUSE_HINT_LINE_SP,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = PAUSE_HINT_GAP),
            )
        }
        Spacer(Modifier.width(PAUSE_SWITCH_GAP))
        AzkarToggleSwitch(checked = isEnabled, onCheckedChange = onToggle)
    }
}

/**
 * The hour and minute, as one time.
 *
 * This was two dropdowns of hours and minutes, each wide enough for its widest entry, on the
 * reasoning that the app had no time picker. It does — [AzkarTimePicker], and the one underneath it is
 * Material 3's — and keeping a second, worse spelling of the same value here meant the two dialogs in
 * the app that set a time set it by different rules and spelled it differently.
 *
 * The draft's two setters stay, because the rest of the draft still thinks in hours and minutes; only
 * the control changed. Choosing an hour keeps the minute, which is [ReminderDraft.onHourChanged]'s
 * long-standing rule and is the reason the picker only has to report the whole minute back.
 */
@Composable
private fun TimePickers(draft: ReminderDraft) {
    AzkarTimePicker(
        minuteOfDay = draft.minuteOfDay,
        onMinuteChange = { minute ->
            if (minute / MINUTES_IN_HOUR != draft.hour) draft.onHourChanged(minute / MINUTES_IN_HOUR)
            if (minute % MINUTES_IN_HOUR != draft.minute) draft.onMinuteChanged(minute % MINUTES_IN_HOUR)
        },
    )
}

@Composable
private fun ReminderDialogActions(
    draft: ReminderDraft,
    callbacks: ReminderEditorCallbacks,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SecondaryButton(text = stringResource(Res.string.collectionsActionCancel), onClick = callbacks.onDismiss)
        Spacer(Modifier.width(8.dp))
        PrimaryButton(
            text = stringResource(Res.string.collectionsActionSave),
            onClick = { callbacks.onSave(draft.cadence, draft.minuteOfDay, draft.dayOfWeek) },
        )
    }
}

/**
 * Asks before dropping a schedule.
 *
 * A confirmation because the button beside it saves: two adjacent actions that look alike and do
 * opposite things is exactly the pair a user fires by muscle memory, and only one of them can be taken
 * back.
 */
@Composable
internal fun ConfirmReminderRemovalDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        AzkarDialogSurface(modifier = Modifier.widthIn(min = 360.dp, max = 460.dp)) {
            Column {
                Text(
                    text = stringResource(Res.string.collectionReminderRemoveTitle),
                    color = AzkarTheme.colors.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(Res.string.collectionReminderRemoveBody),
                    color = AzkarTheme.colors.textSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    SecondaryButton(text = stringResource(Res.string.collectionsActionCancel), onClick = onDismiss)
                    Spacer(Modifier.width(8.dp))
                    PrimaryButton(text = stringResource(Res.string.collectionsActionDelete), onClick = onConfirm)
                }
            }
        }
    }
}

@Composable
private fun ReminderFieldLabel(text: String) {
    Text(
        text = text,
        color = AzkarTheme.colors.textSecondary,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
    )
}

/**
 * A cadence in the user's language.
 *
 * [ReminderCadence] names the three cases and nothing else; what "weekdays" is worth saying out loud
 * is the app's business, so the mapping lives here rather than as a KDoc the user never reads.
 */
@Composable
private fun cadenceLabel(cadence: ReminderCadence): String =
    stringResource(
        when (cadence) {
            ReminderCadence.DAILY -> Res.string.collectionReminderCadenceDaily
            ReminderCadence.WEEKDAYS -> Res.string.collectionReminderCadenceWeekdays
            ReminderCadence.WEEKLY -> Res.string.collectionReminderCadenceWeekly
        },
    )

/**
 * A weekday in the user's language.
 *
 * The JVM's own name first, because it is translated into every locale the app supports and stays
 * right as translations arrive — with the seven hand-written strings as the fallback, because a locale
 * the JVM does not know should still get a readable day rather than "MONDAY".
 */
@Composable
private fun dayLabel(day: DayOfWeek): String {
    val locale = AzkarTheme.locale
    val fromPlatform = runCatching { day.getDisplayName(TextStyle.FULL, locale) }.getOrNull()
    if (!fromPlatform.isNullOrBlank()) return fromPlatform
    return stringResource(dayTextResource(day))
}

/**
 * The clock face, on both halves of it.
 *
 * Zero-padded bare numbers used to stand here, which made this dialog the one place in the app where
 * a schedule could be read as `22:30` with nothing to say whether that was evening or morning. It goes
 * through [AzkarClock] now, which is also what the editor's [AzkarTimePicker] displays, so the row a
 * schedule is written in and the line it is read back on cannot drift apart.
 */
private fun dayTextResource(day: DayOfWeek): StringResource =
    when (day) {
        DayOfWeek.MONDAY -> Res.string.collectionReminderDayMonday
        DayOfWeek.TUESDAY -> Res.string.collectionReminderDayTuesday
        DayOfWeek.WEDNESDAY -> Res.string.collectionReminderDayWednesday
        DayOfWeek.THURSDAY -> Res.string.collectionReminderDayThursday
        DayOfWeek.FRIDAY -> Res.string.collectionReminderDayFriday
        DayOfWeek.SATURDAY -> Res.string.collectionReminderDaySaturday
        DayOfWeek.SUNDAY -> Res.string.collectionReminderDaySunday
    }

/**
 * A scheduled time on the clock face, in the user's language and with no zone attached.
 *
 * A `LocalTime` rather than an [java.time.Instant] because that is what the schedule holds: a time of
 * day means nothing until a zone is applied, and the zone is the machine's to choose at the moment it
 * asks what is next.
 */
internal fun formatReminderTime(
    minuteOfDay: Int,
    locale: Locale,
): String = AzkarClock.format(minuteOfDay, locale)

/**
 * The air between the pickers and the pause switch, and between the pause switch and the button that
 * forgets the schedule.
 *
 * The first is the larger of the two because a switch is a control the user has to recognise before
 * they know what it is for, and the second is the smaller of the two because a button whose label
 * already says what it does needs only enough air to not read as part of the row above it.
 */
private val PAUSE_ROW_GAP = 16.dp

private val PAUSE_ACTION_GAP = 10.dp

/** The air between the switch and the words that explain it, kept to a single line's worth of blank. */
private val PAUSE_SWITCH_GAP = 14.dp

private val PAUSE_HINT_GAP = 2.dp

/**
 * The hint's line box.
 *
 * Set rather than left to the label's own line height because this is the one line of copy in the
 * dialog that is allowed to wrap, and a wrapped line at the label's 12sp box would open a gap the
 * switch beside it does not share — leaving the row visibly lopsided rather than simply two lines tall.
 */
private val PAUSE_HINT_LINE_SP = 15.sp

/** Minutes in an hour, for handing [AzkarTimePicker]'s single value back to the draft's two fields. */
private const val MINUTES_IN_HOUR = 60
