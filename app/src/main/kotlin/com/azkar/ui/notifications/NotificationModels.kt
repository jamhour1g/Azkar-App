package com.azkar.ui.notifications

import kotlinx.serialization.Serializable

/** The order in which an active queue cycle is prepared. */
@Serializable
enum class QueueMode {
    MANUAL,
    RANDOM,
    WEIGHTED,
}

/** User-owned inputs for the single shared remembrance queue. */
@Serializable
data class ReminderQueueConfig(
    val enabled: Boolean = false,
    val mode: QueueMode = QueueMode.MANUAL,
    val collectionIds: List<Long> = emptyList(),
    val individualRemembranceIds: List<Long> = emptyList(),
    /** Explicit passage order for manual mode; newly-added passages are appended. */
    val manualOrder: List<Long> = emptyList(),
    val windowStartMinute: Int = 8 * 60,
    val windowEndMinute: Int = 22 * 60,
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
