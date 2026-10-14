package com.azkar.ui.notifications

import de.infix.testBalloon.framework.core.testSuite
import org.assertj.core.api.Assertions.assertThat
import java.time.LocalDateTime
import kotlin.random.Random

val QueueScheduleSuite by testSuite("The shared reminder queue schedule") {
    test("emits the latest elapsed slot inside a daily window") {
        val slot = QueueSchedule.dueSlot(LocalDateTime.parse("2026-10-14T10:37:00"), 8 * 60, 22 * 60, 60)

        assertThat(slot).isEqualTo(LocalDateTime.parse("2026-10-14T10:00:00"))
        assertThat(QueueSchedule.dueSlot(LocalDateTime.parse("2026-10-14T07:59:00"), 480, 1320, 60)).isNull()
        assertThat(QueueSchedule.dueSlot(LocalDateTime.parse("2026-10-14T22:00:00"), 480, 1320, 60)).isNull()
    }

    test("keeps interval slots continuous through an overnight window") {
        val slot = QueueSchedule.dueSlot(LocalDateTime.parse("2026-10-15T01:10:00"), 22 * 60, 4 * 60, 90)

        assertThat(slot).isEqualTo(LocalDateTime.parse("2026-10-15T01:00:00"))
    }

    test("manual mode honors the saved sequence, ignores duplicates, and appends new sources") {
        val order =
            QueueSchedule.cycleOrder(
                itemIds = listOf(4, 2, 4, 1),
                mode = QueueMode.MANUAL,
                manualOrder = listOf(1, 4, 99, 1),
                dropChance = { 0 },
            )

        assertThat(order).containsExactly(1, 4, 2)
    }

    test("weighted mode treats the value as a per-cycle skip chance") {
        val noneSkipped =
            QueueSchedule.cycleOrder(
                itemIds = listOf(1, 2, 3),
                mode = QueueMode.WEIGHTED,
                manualOrder = emptyList(),
                dropChance = { 0 },
                random = Random(7),
            )
        val partial =
            QueueSchedule.cycleOrder(
                itemIds = listOf(1, 2, 3),
                mode = QueueMode.WEIGHTED,
                manualOrder = emptyList(),
                dropChance = { if (it == 3L) 0 else 100 },
                random = Random(7),
            )
        val allSkipped =
            QueueSchedule.cycleOrder(
                itemIds = listOf(1, 2, 3),
                mode = QueueMode.WEIGHTED,
                manualOrder = emptyList(),
                dropChance = { 100 },
                random = Random(7),
            )

        assertThat(noneSkipped).containsExactlyInAnyOrder(1, 2, 3)
        assertThat(partial).containsExactly(3)
        assertThat(allSkipped).isEmpty()
    }
}
