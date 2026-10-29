package com.azkar.ui.notifications

import kotlinx.serialization.Serializable
import java.time.DayOfWeek

/**
 * The order in which an active queue cycle is prepared.
 *
 * [MANUAL] keeps its name from the days when the order was a list the user typed. It no longer is:
 * the order is the order of the library, so the queue recites the collections in the order the
 * user arranged them and their passages in the order the collection holds them. The stored name
 * stays so that a queue saved before the change keeps reading the way it was written.
 */
@Serializable
enum class QueueMode {
    /** In library order — the order the user arranged their collections in. */
    MANUAL,

    /** Shuffled on every cycle. */
    RANDOM,

    /** Every passage is eligible, but a collection's own chance decides how often it is drawn. */
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
    val enabled: Boolean = true,
    val mode: QueueMode = QueueMode.RANDOM,
    val cadence: QueueCadence = QueueCadence.CONTINUOUS,
    /**
     * The collections the queue draws from.
     *
     * The only source list. A single passage is reminded by being a member of the reserved
     * "Reminder picks" collection, which is itself one of these — so the queue has exactly one
     * kind of source and the library has exactly one place to show it.
     */
    val collectionIds: List<Long> = emptyList(),
    /** The days the queue speaks at all. Every day unless narrowed; applies to both cadences. */
    val activeDays: Set<DayOfWeek> = ALL_DAYS,
    /** Opens the daily window. Read only when [cadence] is [QueueCadence.INTERVAL]. */
    val windowStartMinute: Int = 8 * 60,
    /** Closes the daily window. Read only when [cadence] is [QueueCadence.INTERVAL]. */
    val windowEndMinute: Int = 22 * 60,
    /** The gap between passages, for both cadences. Kept so switching cadence costs the user nothing. */
    val intervalMinutes: Int = 10,
    /** Larger values mean a greater chance of being skipped during a weighted cycle. */
    val defaultDropChance: Int = 0,
    /** Per-collection skip chance, keyed by the collection's id as a string. */
    val collectionDropChances: Map<String, Int> = emptyMap(),
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

    data object Update : NotificationAction
}

/**
 * What a card can draw beyond a title and a line of body, when the scheduler was able to supply it.
 *
 * [NotificationNotice] already carries a `title` and a `message`, and that pair is not going away:
 * de-duplication is defined on exactly those two plus the action. This is the *third* thing — the
 * passage itself at a size worth reading, laid out the way the reader reads, which cannot be
 * reassembled from a clipped string.
 *
 * It is deliberately this small. An earlier revision also carried the collection, the grade, the
 * source, the tags and the favourite flag, and the card drew all of them: which made a reminder into a
 * card about a passage rather than a passage. A card is glanced at on the way to something else, and
 * every one of those facts is answered by the screen the click opens — where it came from, how the
 * tradition grades it, where it is filed. What is left here is the one thing the reader cannot get
 * anywhere else without opening the app, the one line of context that says where they are in it, and
 * the one flag the card can act on by itself.
 *
 * The favourite flag is the exception, and it is back on purpose. The others are *facts about* a
 * passage, and every one of them is a reason to open the app. Whether a passage is kept is not: it is
 * the one thing a reader can want to do *at* a reminder rather than *after* it, and a reader who
 * starred a passage in the library and then met the same passage in the queue has to be able to star
 * it from the queue too — otherwise the same passage behaves differently depending on which surface
 * it happened to turn up on, which is the sort of thing nobody is meant to notice and everybody does.
 *
 * A prayer time and a queue passage are not the same kind of event and do not want the same card, so
 * the two answers are siblings here rather than one wide struct with a field per possibility.
 *
 * Every field is a plain resolved string or a primitive, never a domain object. The scheduler runs on
 * a background dispatcher outside any composition, so it cannot hand over a `Remembrance` for a card
 * to resolve later: it has to answer now, in the language the user is in, or the card would have to
 * know about translations to read it.
 */
sealed interface NotificationContent {
    /**
     * One passage from the queue.
     *
     * @param text the passage, in the language the reader's interface is in. Not the Arabic
     *   specifically: a reader who has chosen English wants to be reminded in English, and a card
     *   that answered an English interface with Arabic would be insisting on the very translation the
     *   choice was a choice *about*. The direction is not stored either — a card that pinned its
     *   paragraph right-to-left would mis-order an English passage's punctuation, and the text itself
     *   already says which way it runs.
     * @param position 1-based, and [total] the length of the cycle. Not the size of the collection:
     *   the queue may be weighted, manual, or a union of several collections, so the only honest
     *   denominator is the cycle the scheduler actually built.
     * @param isFavorite whether the reader has already kept this passage. Resolved by the scheduler
     *   from the same snapshot the passage came out of, so the star on the card agrees with the star
     *   in the library rather than being a second, private opinion about the same passage.
     */
    data class Remembrance(
        val text: String,
        val position: Int,
        val total: Int,
        val isFavorite: Boolean = false,
    ) : NotificationContent

    /**
     * One prayer's time.
     *
     * Carries [prayer] and [time] separately rather than one pre-joined string so the card can set the
     * time in the type it wants rather than inheriting whatever the scheduler's string concatenation
     * happened to produce — which is what made the old notice read as one sentence.
     */
    data class Prayer(
        val prayer: String,
        val time: String,
        val city: String,
    ) : NotificationContent
}

data class NotificationNotice(
    val title: String,
    val message: String,
    val action: NotificationAction,
    /**
     * The card's rich payload, or `null` when the scheduler had nothing beyond two strings.
     *
     * Optional rather than defaulted-to-empty so that "there is no rich card for this notice" and
     * "there is a rich card with nothing in it" cannot be confused, and so the plain fallback path
     * stays reachable for the developer harness and for any future publisher that is not the
     * scheduler.
     */
    val content: NotificationContent? = null,
)
