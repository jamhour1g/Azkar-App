package com.azkar.ui.notifications

import androidx.compose.runtime.Composable
import com.azkar.generated.resources.Res
import com.azkar.generated.resources.reminderManagerDayFriday
import com.azkar.generated.resources.reminderManagerDayMonday
import com.azkar.generated.resources.reminderManagerDaySaturday
import com.azkar.generated.resources.reminderManagerDaySunday
import com.azkar.generated.resources.reminderManagerDayThursday
import com.azkar.generated.resources.reminderManagerDayTuesday
import com.azkar.generated.resources.reminderManagerDayWednesday
import com.azkar.generated.resources.reminderManagerQueueCadenceContinuous
import com.azkar.generated.resources.reminderManagerQueueCadenceInterval
import com.azkar.generated.resources.reminderManagerQueueIntervalValue
import com.azkar.generated.resources.reminderManagerQueueModeInLibraryOrder
import com.azkar.generated.resources.reminderManagerQueueModeRandom
import com.azkar.generated.resources.reminderManagerQueueModeWeighted
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import java.time.DayOfWeek

/**
 * The words the queue's own settings are named in.
 *
 * They live here rather than beside the settings card because there is now more than one place that
 * has to say them: the settings card names the mode and the cadence, and the library's look-ahead
 * dialog names the same mode and the same cadence to someone who has not opened the settings. Two
 * copies of a label are two answers to the same question, and the day names in particular are
 * abbreviations that would not survive being written out twice by hand.
 *
 * Internal rather than public: these are the app's own words for its own settings, not an API.
 */
@Composable
internal fun queueModeLabel(mode: QueueMode): String =
    when (mode) {
        QueueMode.MANUAL -> stringResource(Res.string.reminderManagerQueueModeInLibraryOrder)
        QueueMode.RANDOM -> stringResource(Res.string.reminderManagerQueueModeRandom)
        QueueMode.WEIGHTED -> stringResource(Res.string.reminderManagerQueueModeWeighted)
    }

@Composable
internal fun intervalLabel(minutes: Int): String =
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
internal fun cadenceLabel(cadence: QueueCadence): String =
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
internal fun activeDayLabel(day: DayOfWeek): String =
    when (day) {
        DayOfWeek.MONDAY -> stringResource(Res.string.reminderManagerDayMonday)
        DayOfWeek.TUESDAY -> stringResource(Res.string.reminderManagerDayTuesday)
        DayOfWeek.WEDNESDAY -> stringResource(Res.string.reminderManagerDayWednesday)
        DayOfWeek.THURSDAY -> stringResource(Res.string.reminderManagerDayThursday)
        DayOfWeek.FRIDAY -> stringResource(Res.string.reminderManagerDayFriday)
        DayOfWeek.SATURDAY -> stringResource(Res.string.reminderManagerDaySaturday)
        DayOfWeek.SUNDAY -> stringResource(Res.string.reminderManagerDaySunday)
    }
