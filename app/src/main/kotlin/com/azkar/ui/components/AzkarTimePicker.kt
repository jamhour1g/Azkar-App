package com.azkar.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.TimePickerDisplayMode
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.azkar.generated.resources.Res
import com.azkar.generated.resources.reminderManagerTimePickerCancel
import com.azkar.generated.resources.reminderManagerTimePickerClock
import com.azkar.generated.resources.reminderManagerTimePickerConfirm
import com.azkar.generated.resources.reminderManagerTimePickerTitle
import com.azkar.generated.resources.reminderManagerTimePickerToggleDisplay
import com.azkar.ui.preview.RoborazziDesktopPreviewExclude
import com.azkar.ui.theme.AzkarPreview
import com.azkar.ui.theme.AzkarSpacing
import com.azkar.ui.theme.AzkarTheme
import org.jetbrains.compose.resources.stringResource
import java.time.DayOfWeek

/**
 * The days a schedule runs on, as seven toggles.
 *
 * Each day is independent rather than chosen from a list of presets — "daily" is all seven, "weekdays"
 * is Monday to Friday and anything else is a specific set — so a preset row would only be a second
 * spelling of combinations the chips already express, and would need its own state to stay in step
 * with them.
 *
 * Monday first, because [DayOfWeek.entries] is Monday first and the labels come from the caller.
 * A week that starts on Sunday in one control and Monday in the next is not a detail.
 *
 * @param dayLabel renders a day as the reader's language; a parameter rather than a resource lookup
 * so this control carries no knowledge of which screen is asking.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AzkarDayPicker(
    activeDays: Set<DayOfWeek>,
    onDayChange: (DayOfWeek, Boolean) -> Unit,
    dayLabel: @Composable (DayOfWeek) -> String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AzkarSpacing.xs),
        verticalArrangement = Arrangement.spacedBy(AzkarSpacing.xs),
    ) {
        DayOfWeek.entries.forEach { day ->
            val active = day in activeDays
            AzkarFilterChip(
                text = dayLabel(day),
                selected = active,
                onClick = { onDayChange(day, !active) },
                enabled = enabled,
            )
        }
    }
}

/**
 * A time, typed or turned on a dial.
 *
 * Both halves are the stock Material 3 control rather than something rebuilt here. An earlier version
 * of this was two dropdowns of hours and minutes, on the reasoning that the app had no picker of its
 * own — which is true, and is also an argument for using the one Compose ships instead of writing a
 * fourth way to set a time. The dial is [TimePicker]; the typed half is a text field over the same
 * [TimePickerState] machinery via [TimeInput], and the two are the same control in the two display
 * modes Material 3 already defines, so the dialog can hand the reader over from one to the other
 * without the field and the dial disagreeing about what the answer is.
 *
 * They are two ways into one value rather than two values. The field always shows [AzkarClock]'s
 * rendering of the stored minute, so turning the dial is visible in the text immediately, and typing
 * is visible in the dial the moment the text parses. A picker with two independent states would need a
 * rule for which one wins, and every such rule is wrong for somebody.
 *
 * Typing is not committed until it parses. [AzkarClock.parse] returns null for half-written input —
 * that is the whole reason it returns null rather than guessing — and while it does, the text stands
 * as typed. The alternative, normalising the field on every keystroke, is what makes a time field
 * impossible to type into: `9:45 PM` becomes `9:00 AM` the moment the `9` is followed by anything, and
 * the reader loses the character they were in the middle of entering.
 *
 * The dialog's own colours come from [TimePickerDefaults.colors], which reads
 * [androidx.compose.material3.MaterialTheme.colorScheme]. That is not a shortcut around this app's
 * colour rules — it is the one seam [com.azkar.ui.theme.AzkarTheme] already projects through, so the
 * dial is themed by the same tokens as everything else without this file knowing any of them.
 *
 * @param minuteOfDay the stored value, a minute since midnight, which is how every schedule in the
 * app keeps time.
 * @param onMinuteChange receives a whole minute-of-day, so the caller's writer needs no arithmetic.
 */
@Composable
fun AzkarTimePicker(
    minuteOfDay: Int,
    onMinuteChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val locale = AzkarTheme.locale
    var text by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf(false) }
    var dialOpen by remember { mutableStateOf(false) }

    AzkarOutlinedField(
        value = if (editing) text else AzkarClock.format(minuteOfDay, locale),
        onValueChange = { entered ->
            if (!enabled) return@AzkarOutlinedField
            text = entered
            editing = true
            AzkarClock.parse(entered)?.let(onMinuteChange)
        },
        placeholder = AzkarClock.format(minuteOfDay, locale),
        modifier = modifier,
        leadingIcon = AzkarIcons.Clock,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
        trailingContent = {
            TooltipIconActionButton(
                icon = AzkarIcons.Clock,
                tooltip = stringResource(Res.string.reminderManagerTimePickerClock),
                onClick = { dialOpen = true },
                enabled = enabled,
            )
        },
    )

    if (dialOpen && enabled) {
        AzkarTimePickerDialog(
            minuteOfDay = minuteOfDay,
            onConfirm = {
                onMinuteChange(it)
                dialOpen = false
                editing = false
            },
            onDismiss = { dialOpen = false },
        )
    }
}

/**
 * The Material 3 dial, in the app's dialog pane.
 *
 * [AzkarDialogSurface] rather than one of Material 3's own dialogs, for the same reason the rest of
 * the app's dialogs use it: the pane's padding and border are already the app's, and a time picker
 * sitting in a differently-padded window reads as a different application. The picker *inside* it is
 * untouched Material 3.
 *
 * Both display modes are offered and the reader chooses, because the dial is unusable for typing
 * `4:45` and the keyboard is unusable for finding four forty-five. Material 3 already models this as
 * one state with two renderings, so switching between them keeps the selection — which is why the
 * toggle is [TimePickerState.selection] rather than a second field.
 */
@Composable
private fun AzkarTimePickerDialog(
    minuteOfDay: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val state =
        rememberTimePickerState(
            initialHour = (minuteOfDay / MINUTES_IN_HOUR).coerceIn(0, HOURS_IN_DAY - 1),
            (minuteOfDay % MINUTES_IN_HOUR).coerceIn(0, MINUTES_IN_HOUR - 1),
            false,
        )
    var displayMode by remember { mutableStateOf(TimePickerDisplayMode.Picker) }

    Dialog(onDismissRequest = onDismiss) {
        AzkarDialogSurface(modifier = Modifier.widthIn(min = 360.dp, max = 400.dp)) {
            Column(
                modifier = Modifier.padding(AzkarSpacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(AzkarSpacing.md),
            ) {
                Text(
                    text = stringResource(Res.string.reminderManagerTimePickerTitle),
                    color = AzkarTheme.colors.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                // Bounded rather than left to itself: the dial is taller than the pane's own minimum,
                // and without a bound the two display modes would jump the dialog's height as the
                // reader toggles between them.
                Box(modifier = Modifier.heightIn(max = DIAL_MAX_HEIGHT)) {
                    if (displayMode == TimePickerDisplayMode.Input) {
                        TimeInput(state = state)
                    } else {
                        TimePicker(state = state, colors = TimePickerDefaults.colors())
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(AzkarSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AzkarOutlinedButton(
                        text = stringResource(Res.string.reminderManagerTimePickerToggleDisplay),
                        onClick = {
                            displayMode =
                                if (displayMode == TimePickerDisplayMode.Picker) {
                                    TimePickerDisplayMode.Input
                                } else {
                                    TimePickerDisplayMode.Picker
                                }
                        },
                    )
                    PrimaryButton(
                        text = stringResource(Res.string.reminderManagerTimePickerConfirm),
                        onClick = { onConfirm(state.hour * MINUTES_IN_HOUR + state.minute) },
                    )
                }
                AzkarOutlinedButton(
                    text = stringResource(Res.string.reminderManagerTimePickerCancel),
                    onClick = onDismiss,
                )
            }
        }
    }
}

private const val MINUTES_IN_HOUR = 60
private const val HOURS_IN_DAY = 24
private val DIAL_MAX_HEIGHT = 380.dp

@AzkarPreview
@Composable
internal fun AzkarTimePickerPreview() {
    AzkarTimePicker(minuteOfDay = 8 * 60 + 30, onMinuteChange = {})
}

@AzkarPreview
@RoborazziDesktopPreviewExclude
@Composable
internal fun AzkarTimePickerDialogPreview() {
    AzkarTimePickerDialog(
        minuteOfDay = 8 * 60 + 30,
        onConfirm = {},
        onDismiss = {},
    )
}

@AzkarPreview
@Composable
internal fun AzkarDayPickerPreview() {
    Box(Modifier.width(420.dp)) {
        AzkarDayPicker(
            activeDays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
            onDayChange = { _, _ -> },
            dayLabel = { day -> day.name.take(3) },
        )
    }
}
