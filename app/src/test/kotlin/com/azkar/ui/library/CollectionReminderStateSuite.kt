package com.azkar.ui.library

import com.azkar.TestDispatchers
import com.azkar.domain.model.CollectionReminder
import com.azkar.domain.model.ReminderCadence
import com.azkar.ui.library.data.CollectionReminderGateway
import com.azkar.ui.library.data.CollectionReminderRow
import de.infix.testBalloon.framework.core.testSuite
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The reminder schedules, and the tick that fires them.
 *
 * Every test builds its own gateway and state inside the test body. TestBalloon runs a suite's tests
 * concurrently, so a harness held at suite scope would have one test's schedule overwritten by
 * another's mid-tick — and a tick that fires nothing is exactly what a test asserts on, so the
 * failure would look like a passing schedule rather than a shared one.
 *
 * Every instant is written out rather than read from a clock, and the zone is fixed to UTC for the
 * same reason: a scheduler test that passes because the machine happened to be in a zone where
 * nobody has ever asked a question about midnight is not a test.
 */
val CollectionReminderStateSuite by testSuite("The reminder schedules") {
    testSuite("reading the schedules") {
        test("a schedule shows up once the read lands") {
            val harness = harnessWith(daily())
            harness.state.load()
            assertThat(harness.state.rows).containsOnlyKeys(COLLECTION_ID)
        }

        test("the row carries the name and size the announcement needs") {
            val harness = harnessWith(daily(), details = mapOf(COLLECTION_ID to ("Morning Azkar" to 52)))
            harness.state.load()
            val row = harness.state.forCollection(COLLECTION_ID)!!
            assertThat(row.collectionName).isEqualTo("Morning Azkar")
            assertThat(row.memberCount).isEqualTo(52)
        }

        test("a schedule whose collection has gone still shows rather than disappearing") {
            val harness = harnessWith(daily())
            harness.state.load()
            assertThat(harness.state.forCollection(COLLECTION_ID)).isNotNull()
            assertThat(harness.state.forCollection(COLLECTION_ID)!!.collectionName).isEmpty()
        }

        test("a read that fails leaves the schedules already on screen alone") {
            val harness = harnessWith(daily())
            harness.state.load()
            harness.gateway.failure = IllegalStateException("database is away")
            harness.state.load()
            assertThat(harness.state.rows).containsOnlyKeys(COLLECTION_ID)
        }
    }

    testSuite("the tick") {
        test("a reminder whose time has passed today is owed straight away") {
            val harness = harnessWith(daily(), details = mapOf(COLLECTION_ID to ("Morning Azkar" to 52)))
            harness.state.load()
            val fired = runBlocking { harness.state.tick(NOW) }
            assertThat(fired).containsExactly(FiredReminder(COLLECTION_ID, "Morning Azkar", 52))
        }

        test("the same reminder does not come round twice") {
            val harness = harnessWith(daily())
            harness.state.load()
            assertThat(runBlocking { harness.state.tick(NOW) }).hasSize(1)
            assertThat(runBlocking { harness.state.tick(NOW) }).isEmpty()
        }

        test("the firing is acknowledged on the schedule, so the next tick finds nothing owed") {
            val harness = harnessWith(daily())
            harness.state.load()
            runBlocking { harness.state.tick(NOW) }
            assertThat(
                harness.gateway.reminders
                    .getValue(COLLECTION_ID)
                    .lastTriggeredAt,
            ).isEqualTo(NOW)
        }

        test("a paused reminder never comes round") {
            val harness = harnessWith(daily(enabled = false))
            harness.state.load()
            assertThat(runBlocking { harness.state.tick(NOW) }).isEmpty()
            assertThat(harness.state.dueCollections).isEmpty()
        }

        test("a reminder acknowledged at its own time is owed the next day rather than the same") {
            val harness = harnessWith(daily(lastTriggeredAt = Instant.parse("2026-06-03T06:00:00Z")))
            harness.state.load()
            assertThat(runBlocking { harness.state.tick(NOW) }).isEmpty()
            val tomorrow = Instant.parse("2026-06-04T12:00:00Z")
            assertThat(runBlocking { harness.state.tick(tomorrow) }).hasSize(1)
        }

        test("a reminder that has been owed since yesterday still comes round today") {
            val harness = harnessWith(daily())
            harness.state.load()
            val tomorrow = Instant.parse("2026-06-04T07:00:00Z")
            assertThat(runBlocking { harness.state.tick(tomorrow) }).hasSize(1)
        }

        test("a weekly reminder acknowledged last week is not owed again this week") {
            val harness = harnessWith(weekly(DayOfWeek.MONDAY, lastTriggeredAt = Instant.parse("2026-06-01T06:00:00Z")))
            harness.state.load()
            assertThat(runBlocking { harness.state.tick(NOW) }).isEmpty()
        }

        test("a weekly reminder is owed on the day it names, even days after it") {
            val harness = harnessWith(weekly(DayOfWeek.THURSDAY))
            harness.state.load()
            assertThat(runBlocking { harness.state.tick(NOW) }).hasSize(1)
        }

        test("every reminder that came round is reported, not just the first") {
            val harness =
                harnessWith(
                    daily(collectionId = 10L),
                    daily(collectionId = 11L),
                    daily(collectionId = 12L, enabled = false),
                )
            harness.state.load()
            assertThat(runBlocking { harness.state.tick(NOW) }.map { it.collectionId }).containsExactly(10L, 11L)
        }

        test("a fired collection is badged for the rail until it is dealt with") {
            val harness = harnessWith(daily())
            harness.state.load()
            runBlocking { harness.state.tick(NOW) }
            assertThat(harness.state.dueCollections).containsExactly(COLLECTION_ID)
        }

        test("a failure to acknowledge does not take the announcement back with it") {
            val fake = fakeWith(daily())
            val state = stateFor(FragileGateway(fake).apply { failsToAcknowledge = true })
            state.load()
            val fired = runBlocking { state.tick(NOW) }
            assertThat(fired).hasSize(1)
            assertThat(state.dueCollections).containsExactly(COLLECTION_ID)
            assertThat(state.forCollection(COLLECTION_ID)!!.reminder.lastTriggeredAt).isEqualTo(NOW)
        }

        test("nothing is owed before anything has been read, so the tick is harmless") {
            val harness = harnessWith(daily())
            assertThat(runBlocking { harness.state.tick(NOW) }).isEmpty()
        }
    }

    testSuite("setting a schedule") {
        test("the schedule is on screen before the write lands") {
            val probe = WriteProbeGateway(fakeWith(daily()))
            val state = stateFor(probe)
            probe.reader = { state.rows }
            state.save(COLLECTION_ID, ReminderCadence.WEEKLY, SIX_AM, DayOfWeek.SUNDAY)
            val seen = probe.rowsWhenWritten!!
            assertThat(seen).containsOnlyKeys(COLLECTION_ID)
            assertThat(seen.getValue(COLLECTION_ID).reminder.cadence).isEqualTo(ReminderCadence.WEEKLY)
        }

        test("editing a schedule leaves the pause and the last firing exactly as they were") {
            val harness = harnessWith(daily(enabled = false, lastTriggeredAt = Instant.parse("2026-05-01T06:00:00Z")))
            harness.state.load()
            harness.state.save(COLLECTION_ID, ReminderCadence.WEEKLY, SIX_AM, DayOfWeek.SUNDAY)
            val stored = harness.gateway.reminders.getValue(COLLECTION_ID)
            assertThat(stored.enabled).isFalse()
            assertThat(stored.lastTriggeredAt).isEqualTo(Instant.parse("2026-05-01T06:00:00Z"))
        }

        test("what comes back from the write is what the screen then shows") {
            val harness = harnessWith(details = mapOf(COLLECTION_ID to ("Morning Azkar" to 52)))
            harness.state.save(
                collectionId = COLLECTION_ID,
                cadence = ReminderCadence.DAILY,
                minuteOfDay = SIX_AM,
                dayOfWeek = null,
                collectionName = "Provisional name",
            )
            assertThat(harness.state.forCollection(COLLECTION_ID)!!.collectionName).isEqualTo("Morning Azkar")
        }

        test("a save that fails takes the row back and reports it") {
            val harness = harnessWith()
            harness.gateway.failure = IllegalStateException("database is away")
            var failures = 0
            harness.state.save(
                collectionId = COLLECTION_ID,
                cadence = ReminderCadence.DAILY,
                minuteOfDay = SIX_AM,
                dayOfWeek = null,
                onFailure = { failures++ },
            )
            assertThat(harness.state.rows).isEmpty()
            assertThat(failures).isEqualTo(1)
        }

        test("a save that succeeds reports it") {
            val harness = harnessWith()
            var successes = 0
            harness.state.save(
                collectionId = COLLECTION_ID,
                cadence = ReminderCadence.DAILY,
                minuteOfDay = SIX_AM,
                dayOfWeek = null,
                onSuccess = { successes++ },
            )
            assertThat(successes).isEqualTo(1)
        }
    }

    testSuite("pausing and removing") {
        test("pausing shows on screen straight away") {
            val harness = harnessWith(daily())
            harness.state.load()
            harness.state.setEnabled(COLLECTION_ID, false)
            assertThat(
                harness.state
                    .forCollection(COLLECTION_ID)!!
                    .reminder.enabled,
            ).isFalse()
        }

        test("a pause that fails snaps back, because nothing has been announced yet") {
            val harness = harnessWith(daily())
            harness.state.load()
            harness.gateway.failure = IllegalStateException("database is away")
            var failures = 0
            harness.state.setEnabled(COLLECTION_ID, false, onFailure = { failures++ })
            assertThat(
                harness.state
                    .forCollection(COLLECTION_ID)!!
                    .reminder.enabled,
            ).isTrue()
            assertThat(failures).isEqualTo(1)
        }

        test("resuming stores the schedule as running again") {
            val harness = harnessWith(daily(enabled = false))
            harness.state.load()
            harness.state.setEnabled(COLLECTION_ID, true)
            assertThat(
                harness.gateway.reminders
                    .getValue(COLLECTION_ID)
                    .enabled,
            ).isTrue()
        }

        test("removing drops the row and the badge straight away") {
            val harness = harnessWith(daily())
            harness.state.load()
            runBlocking { harness.state.tick(NOW) }
            harness.state.remove(COLLECTION_ID)
            assertThat(harness.state.rows).isEmpty()
            assertThat(harness.state.dueCollections).isEmpty()
        }

        test("a removal that fails brings the row back by asking the database") {
            val fake = fakeWith(daily())
            val state = stateFor(FragileGateway(fake).apply { failsToDelete = true })
            state.load()
            var failures = 0
            state.remove(COLLECTION_ID, onFailure = { failures++ })
            assertThat(state.rows).containsOnlyKeys(COLLECTION_ID)
            assertThat(failures).isEqualTo(1)
            assertThat(fake.reminders).containsKey(COLLECTION_ID)
        }

        test("a removal that fails with the database unreadable leaves the row gone until the next read") {
            val fake = fakeWith(daily())
            val state = stateFor(FragileGateway(fake).apply { failsToDelete = true })
            state.load()
            fake.failure = IllegalStateException("database is away")
            state.remove(COLLECTION_ID)
            assertThat(state.rows).isEmpty()
            fake.failure = null
            state.load()
            assertThat(state.rows).containsOnlyKeys(COLLECTION_ID)
        }

        test("removing something that has no schedule does nothing") {
            val harness = harnessWith()
            harness.state.load()
            harness.state.remove(COLLECTION_ID)
            assertThat(harness.state.rows).isEmpty()
        }
    }

    testSuite("the badge and the next coming-round") {
        test("acknowledging clears the badge for that collection only") {
            val harness = harnessWith(daily(collectionId = 10L), daily(collectionId = 11L))
            harness.state.load()
            runBlocking { harness.state.tick(NOW) }
            harness.state.acknowledge(10L)
            assertThat(harness.state.dueCollections).containsExactly(11L)
        }

        test("a badge for a schedule that has gone does not survive a reload") {
            val harness = harnessWith(daily())
            harness.state.load()
            runBlocking { harness.state.tick(NOW) }
            harness.gateway.reminders = emptyMap()
            harness.state.load()
            assertThat(harness.state.dueCollections).isEmpty()
        }

        test("the next coming-round is the one still to come, not today's") {
            val harness = harnessWith(weekly(DayOfWeek.MONDAY))
            harness.state.load()
            assertThat(harness.state.nextTriggerAt(COLLECTION_ID, now()))
                .isEqualTo(LocalDateTime.parse("2026-06-08T06:00"))
        }

        test("a paused reminder has no next coming-round at all") {
            val harness = harnessWith(daily(enabled = false))
            harness.state.load()
            assertThat(harness.state.nextTriggerAt(COLLECTION_ID, now())).isNull()
        }

        test("a collection with no schedule has no next coming-round either") {
            val harness = harnessWith()
            harness.state.load()
            assertThat(harness.state.nextTriggerAt(COLLECTION_ID, now())).isNull()
        }
    }
}

private const val COLLECTION_ID = 10L
private const val SIX_AM = 6 * 60
private val UTC: ZoneId = ZoneId.of("UTC")

/** Noon on a Wednesday, so a weekday rule and a weekly rule disagree about today. */
private val NOW: Instant = Instant.parse("2026-06-03T12:00:00Z")

private fun now(): LocalDateTime = LocalDateTime.ofInstant(NOW, UTC)

private fun daily(
    collectionId: Long = COLLECTION_ID,
    enabled: Boolean = true,
    lastTriggeredAt: Instant? = null,
): CollectionReminder =
    CollectionReminder(
        collectionId = collectionId,
        cadence = ReminderCadence.DAILY,
        minuteOfDay = SIX_AM,
        enabled = enabled,
        lastTriggeredAt = lastTriggeredAt,
    )

private fun weekly(
    day: DayOfWeek,
    collectionId: Long = COLLECTION_ID,
    lastTriggeredAt: Instant? = null,
): CollectionReminder =
    CollectionReminder(
        collectionId = collectionId,
        cadence = ReminderCadence.WEEKLY,
        minuteOfDay = SIX_AM,
        dayOfWeek = day,
        lastTriggeredAt = lastTriggeredAt,
    )

/**
 * The state under test plus the fake behind it.
 *
 * The fake is held rather than only the gateway the state talks to, because most of what these tests
 * assert on is what the *database* was told — whether a pause was stored, whether a firing was
 * acknowledged — and those are only visible on the fake's own records.
 */
private class ReminderHarness(
    val state: CollectionReminderState,
    val gateway: FakeCollectionsGateway,
)

private fun stateFor(gateway: CollectionReminderGateway): CollectionReminderState =
    CollectionReminderState(CoroutineScope(TestDispatchers.unconfined), gateway, UTC)

/** The fake, seeded with the schedules the test needs the gateway to serve. */
private fun fakeWith(
    vararg schedules: CollectionReminder,
    details: Map<Long, Pair<String, Int>> = emptyMap(),
): FakeCollectionsGateway =
    FakeCollectionsGateway().also { gateway ->
        gateway.reminders = schedules.associateByTo(mutableMapOf()) { it.collectionId }
        gateway.reminderDetails = details
    }

private fun harnessWith(
    vararg schedules: CollectionReminder,
    details: Map<Long, Pair<String, Int>> = emptyMap(),
): ReminderHarness {
    val gateway = fakeWith(*schedules, details = details)
    return ReminderHarness(stateFor(gateway), gateway)
}

/**
 * Watches what the screen is showing at the moment the write is attempted.
 *
 * The point of the optimistic save is that the row appears before the database has been asked, and
 * with an unconfined scope the write completes inside the call — so the only way to see the frame in
 * between is to look from inside the write itself.
 */
private class WriteProbeGateway(
    private val inner: FakeCollectionsGateway,
) : CollectionReminderGateway by inner {
    /** Set by the test once the state exists, because the gateway is built before it. */
    var reader: () -> Map<Long, CollectionReminderRow> = { emptyMap() }
    var rowsWhenWritten: Map<Long, CollectionReminderRow>? = null

    override suspend fun saveReminder(
        collectionId: Long,
        cadence: ReminderCadence,
        minuteOfDay: Int,
        dayOfWeek: DayOfWeek?,
    ): CollectionReminderRow {
        rowsWhenWritten = reader()
        return inner.saveReminder(collectionId, cadence, minuteOfDay, dayOfWeek)
    }
}

/**
 * A gateway that can be told to fail on exactly one call and behaves on the rest.
 *
 * Delegation rather than six written-out methods: this fake exists to fail on particular calls, and a
 * copy of the other four would be a second place for its behaviour to drift from the real gateway's.
 * The distinction matters — a test that fails *every* call also fails the re-read the state makes to
 * recover, which proves nothing about the recovery.
 */
private class FragileGateway(
    private val inner: CollectionReminderGateway,
) : CollectionReminderGateway by inner {
    var failsToAcknowledge = false
    var failsToDelete = false

    override suspend fun deleteReminder(collectionId: Long) {
        if (failsToDelete) error("the removal did not land")
        inner.deleteReminder(collectionId)
    }

    override suspend fun markReminderTriggered(
        collectionId: Long,
        at: Instant,
    ) {
        if (failsToAcknowledge) error("the acknowledgement did not land")
        inner.markReminderTriggered(collectionId, at)
    }
}
