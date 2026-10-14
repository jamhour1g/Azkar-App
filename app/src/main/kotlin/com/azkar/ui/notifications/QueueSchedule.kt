package com.azkar.ui.notifications

import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/** Pure queue ordering and time-window calculations, kept independent of Compose and storage. */
object QueueSchedule {
    /**
     * The most recent interval slot in the active window, or null outside it.
     * A window may cross midnight; a missed slot coalesces to the latest slot rather than replaying
     * a burst of notifications when the computer wakes.
     */
    fun dueSlot(
        now: LocalDateTime,
        startMinute: Int,
        endMinute: Int,
        intervalMinutes: Int,
    ): LocalDateTime? {
        require(startMinute in 0..1439)
        require(endMinute in 0..1439 && startMinute != endMinute)
        require(intervalMinutes > 0)

        val time = now.toLocalTime()
        val crossesMidnight = endMinute < startMinute
        val startDate =
            when {
                !crossesMidnight -> now.toLocalDate()
                time.hour * 60 + time.minute < endMinute -> now.toLocalDate().minusDays(1)
                else -> now.toLocalDate()
            }
        val start = startDate.atTime(LocalTime.of(startMinute / 60, startMinute % 60))
        val end =
            startDate
                .plusDays(if (crossesMidnight) 1 else 0)
                .atTime(LocalTime.of(endMinute / 60, endMinute % 60))
        if (now.isBefore(start) || !now.isBefore(end)) return null

        val elapsedMinutes = ChronoUnit.MINUTES.between(start, now)
        return start.plusMinutes((elapsedMinutes / intervalMinutes) * intervalMinutes)
    }

    /**
     * Prepares one cycle. Weighted mode uses per-item overrides, otherwise the lowest configured
     * source drop chance for an item present in overlapping collections. That prevents overlap from
     * multiplying an item's influence; a higher drop chance only makes it more likely to be skipped.
     */
    fun cycleOrder(
        itemIds: List<Long>,
        mode: QueueMode,
        manualOrder: List<Long>,
        dropChance: (Long) -> Int,
        random: kotlin.random.Random = kotlin.random.Random.Default,
    ): List<Long> {
        val unique = itemIds.distinct()
        if (unique.isEmpty()) return emptyList()
        return when (mode) {
            QueueMode.MANUAL -> {
                val available = unique.toSet()
                manualOrder.distinct().filter { it in available } + unique.filterNot { it in manualOrder }
            }

            QueueMode.RANDOM -> unique.shuffled(random)

            QueueMode.WEIGHTED -> {
                val included = unique.filter { random.nextInt(100) >= dropChance(it).coerceIn(0, 100) }
                included
            }
        }
    }
}
