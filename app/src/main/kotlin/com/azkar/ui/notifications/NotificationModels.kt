package com.azkar.ui.notifications

import kotlinx.serialization.Serializable
import java.time.DayOfWeek

/** The order in which an active queue cycle is prepared. */
@Serializable
enum class QueueMode {
    MANUAL,
    RANDOM,
    WEIGHTED,
}

/**
 * How the queue decides *when* to speak.
 *
 * Deliberately a separate axis from [QueueMode]. Mode decides *which* passage comes next; cadence
 * decides *when the queue speaks at all*, and the two are chosen for unrelated reasons — somebody who
 * wants the queue shuffled every hour still wants gaps between the reminders, and somebody who wants
 * a gap between the reminders still wants them in the order they typed. Folding cadence into mode
 * would have forced both of those people to describe themselves with a single label and to change
 * something they did not want changed.
 */
@Serializable
enum class QueueCadence {
    /** One passage every [ReminderQueueConfig.intervalMinutes], but only inside the daily window. */
    INTERVAL,

    /**
     * One passage every [ReminderQueueConfig.intervalMinutes], all day, on the days that are switched on.
     *
     * The difference from [INTERVAL] is not the gap and not the interval — it is the window. An
     * interval queue is a timetable: it opens at one time, speaks on the hour, and closes. This one is
     * a metronome the user left running, and a metronome does not stop because it is half past ten.
     *
     * It also uses the user's own [ReminderQueueConfig.intervalMinutes]. An earlier version overrode
     * it with a fixed five-minute gap, which quietly threw away whatever interval they had chosen and
     * then fired 168 times across a fourteen-hour window — the opposite of the restraint the override
     * was written to buy. Nothing here decides how often to speak; that is the one number the user owns.
     */
    CONTINUOUS,
}

/** User-owned inputs for the single shared remembrance queue. */
@Serializable
data class ReminderQueueConfig(
    val enabled: Boolean = false,
    val mode: QueueMode = QueueMode.MANUAL,
    val cadence: QueueCadence = QueueCadence.INTERVAL,
    val collectionIds: List<Long> = emptyList(),
    val individualRemembranceIds: List<Long> = emptyList(),
    /** Explicit passage order for manual mode; newly-added passages are appended. */
    val manualOrder: List<Long> = emptyList(),
    /** The days the queue speaks at all. Every day unless narrowed; applies to both cadences. */
    val activeDays: Set<DayOfWeek> = ALL_DAYS,
    /** Opens the daily window. Read only when [cadence] is [QueueCadence.INTERVAL]. */
    val windowStartMinute: Int = 8 * 60,
    /** Closes the daily window. Read only when [cadence] is [QueueCadence.INTERVAL]. */
    val windowEndMinute: Int = 22 * 60,
    /** The gap between passages, for both cadences. Kept so switching cadence costs the user nothing. */
    val intervalMinutes: Int = 60,
    /** Larger values mean a greater chance of being skipped during a weighted cycle. */
    val defaultDropChance: Int = 0,
    val collectionDropChances: Map<String, Int> = emptyMap(),
    val remembranceDropOverrides: Map<String, Int> = emptyMap(),
) {
    init {
        require(windowStartMinute in 0..1439)
        require(windowEndMinute in 0..1439)
        require(windowStartMinute != windowEndMinute)
        require(intervalMinutes in 5..1440)
        require(defaultDropChance in 0..100)
        require(activeDays.isNotEmpty())
    }

    /**
     * The gap the scheduler spaces deliveries by.
     *
     * One property rather than one per cadence on purpose. An earlier version answered this from the
     * cadence, with continuous mode overriding the stored value — which meant the reminders screen
     * previewed one gap and the runtime fired another the moment the user touched anything that
     * rewrote the config, and left the interval they had set sitting in the field unused. Both
     * cadences now speak on the interval the user chose; [cadence] decides whether the daily window
     * applies, which is the only question left for it to answer.
     */
    val effectiveIntervalMinutes: Int get() = intervalMinutes

    companion object {
        /** All seven days, the default a freshly-created queue starts from. */
        val ALL_DAYS: Set<DayOfWeek> = DayOfWeek.entries.toSet()

        /** Monday to Friday, offered as a preset because it is what a working week is. */
        val WEEKDAYS: Set<DayOfWeek> =
            setOf(
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY,
            )

        /** Saturday and Sunday, offered as a preset because it is the other half of the week. */
        val WEEKEND: Set<DayOfWeek> = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
    }
}

/** Progress is stored separately from editable settings so process restarts do not reshuffle a cycle. */
@Serializable
data class ReminderQueueProgress(
    val sourceFingerprint: String = "",
    val cycleOrder: List<Long> = emptyList(),
    val nextIndex: Int = 0,
    val lastDeliveredSlot: String? = null,
)

/** Supported regional calculation presets. */
@Serializable
enum class PrayerCalculationMethod(
    val displayName: String,
    val fajrAngle: Double,
    val ishaAngle: Double?,
    val fixedIshaMinutes: Int? = null,
) {
    MUSLIM_WORLD_LEAGUE("Muslim World League", 18.0, 17.0),
    EGYPTIAN("Egyptian General Authority", 19.5, 17.5),
    KARACHI("University of Islamic Sciences, Karachi", 18.0, 18.0),
    UMM_AL_QURA("Umm al-Qura (Makkah)", 18.5, null, fixedIshaMinutes = 90),
    NORTH_AMERICA("ISNA / North America", 15.0, 15.0),
}

/** Coordinates and calculation preferences used by both Home and the notification service. */
@Serializable
data class PrayerLocationSettings(
    val latitude: Double = 31.9,
    val longitude: Double = 35.2,
    val city: String = "Ramallah, Palestine",
    val zoneId: String = "Asia/Hebron",
    val method: PrayerCalculationMethod = PrayerCalculationMethod.MUSLIM_WORLD_LEAGUE,
    val autoDetect: Boolean = true,
) {
    init {
        require(latitude in -90.0..90.0)
        require(longitude in -180.0..180.0)
        require(zoneId.isNotBlank())
    }
}

@Serializable
data class PrayerNotificationProgress(
    val lastDeliveredLocalDates: Map<String, String> = emptyMap(),
)

/** A notification's typed destination, so a click can open the relevant app surface. */
sealed interface NotificationAction {
    data class Remembrance(
        val id: Long,
    ) : NotificationAction

    data object PrayerTimes : NotificationAction

    data object OpenApp : NotificationAction
}

data class NotificationNotice(
    val title: String,
    val message: String,
    val action: NotificationAction,
)
