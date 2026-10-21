package com.azkar.ui.notifications

import androidx.compose.runtime.Stable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.DayOfWeek

/**
 * Writes for the queue's schedule, and nothing else.
 *
 * These used to be an inner class of the reminders screen's state holder, which was the only correct
 * place for the ones that need to read the catalogue. But most of them do not: turning the queue on,
 * choosing its order, its cadence, its weekdays and its window are edits to a [ReminderQueueConfig] and
 * to nothing else, and they are made from the settings page. Keeping them nested under a state holder
 * that loaded collections and passages meant the settings page had to construct the whole holder — a
 * twenty-method gateway and a catalogue read — to render one card.
 *
 * So the split follows the data, not the screen. What needed the catalogue was the passage picker,
 * and the picker is gone: a single passage is reminded by joining the reserved "Reminder picks"
 * collection, which is an ordinary collection and is toggled like any other. Everything else writes
 * through one instance type over the same [NotificationPreferencesRepository] singleton — so the switch
 * flipped on the settings page is the switch the runtime and the library read.
 *
 * Nothing is held here. Every method reads the current config from `preferences.queue.value` inside
 * its own write rather than taking one as an argument, which is safe because that repository is
 * process-wide and its flow values are the single source of truth for what is stored — and which
 * keeps every `copy` chain, which needs the whole record, out of the call sites.
 */
@Stable
class ReminderScheduleEditor(
    private val scope: CoroutineScope,
    private val preferences: NotificationPreferencesRepository,
) {
    fun setEnabled(enabled: Boolean) {
        scope.launch { preferences.updateQueue { it.copy(enabled = enabled) } }
    }

    fun setMode(mode: QueueMode) {
        scope.launch { preferences.updateQueue { it.copy(mode = mode) } }
    }

    /** Clamped to the 0..100 the config's own `require` allows, so a typo cannot throw on save. */
    fun setDefaultDropChance(chance: Int) {
        scope.launch { preferences.updateQueue { it.copy(defaultDropChance = chance.coerceIn(0, 100)) } }
    }

    fun setIntervalMinutes(minutes: Int) {
        scope.launch { preferences.updateQueue { it.copy(intervalMinutes = minutes.coerceIn(5, 1440)) } }
    }

    /**
     * Switches between "fire every N minutes" and "keep going all day".
     *
     * Only the cadence is written; the interval that [setIntervalMinutes] stored is left alone, so a
     * user who spends an afternoon on an interval and then tries continuous can come back to the
     * interval they built without rebuilding it.
     */
    fun setCadence(cadence: QueueCadence) {
        scope.launch { preferences.updateQueue { it.copy(cadence = cadence) } }
    }

    /**
     * Turns one weekday on or off, and will not turn the last one off.
     *
     * [ReminderQueueConfig] requires a non-empty day set, and the repository's own `validated` would
     * answer an empty one by restoring *all* days. Turning Tuesday off on a Monday-only queue would
     * therefore quietly switch on all seven — the opposite of the press, with no way to see what
     * happened. Ignoring the press instead keeps the control honest: the chip the user clicked is the
     * only chip that does not respond, and it is visibly the last one still on.
     */
    fun setDayActive(
        day: DayOfWeek,
        active: Boolean,
    ) {
        scope.launch {
            preferences.updateQueue { config ->
                val days = config.activeDays
                when {
                    active -> config.copy(activeDays = days + day)
                    days.size > 1 -> config.copy(activeDays = days - day)
                    else -> config
                }
            }
        }
    }

    /**
     * Moves the window's start, dragging the end with it when the two would collide.
     *
     * [ReminderQueueConfig] requires the two minutes to differ, and a start/end pair is edited one
     * field at a time — so setting the start to exactly the current end is the one edit that would
     * otherwise throw out of a repository write and leave the switch untouched. Nudging the end
     * forward by a minute is the smallest change that keeps the pair legal.
     */
    fun setWindowStart(minute: Int) {
        scope.launch {
            preferences.updateQueue { config ->
                config.copy(
                    windowStartMinute = minute,
                    windowEndMinute =
                        if (minute == config.windowEndMinute) {
                            (minute + 1) % MINUTES_IN_DAY
                        } else {
                            config.windowEndMinute
                        },
                )
            }
        }
    }

    /** The mirror of [setWindowStart]: nudging the start *back* rather than forward. */
    fun setWindowEnd(minute: Int) {
        scope.launch {
            preferences.updateQueue { config ->
                config.copy(
                    windowStartMinute =
                        if (minute == config.windowStartMinute) {
                            (minute + MINUTES_IN_DAY - 1) % MINUTES_IN_DAY
                        } else {
                            config.windowStartMinute
                        },
                    windowEndMinute = minute,
                )
            }
        }
    }

    fun setCollectionEnabled(
        id: Long,
        enabled: Boolean,
    ) {
        scope.launch {
            preferences.updateQueue { config ->
                config.copy(
                    collectionIds =
                        if (enabled) {
                            (config.collectionIds + id).distinct()
                        } else {
                            config.collectionIds - id
                        },
                )
            }
        }
    }

    fun setCollectionDropChance(
        id: Long,
        chance: Int,
    ) {
        scope.launch {
            preferences.updateQueue { config ->
                config.copy(
                    collectionDropChances =
                        config.collectionDropChances +
                            (id.toString() to chance.coerceIn(0, 100)),
                )
            }
        }
    }

    private companion object {
        /** Minutes in a day, for the wrap-around in the window setters. */
        const val MINUTES_IN_DAY = 24 * 60
    }
}
