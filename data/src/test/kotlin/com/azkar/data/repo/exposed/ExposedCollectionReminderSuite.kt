package com.azkar.data.repo.exposed

import com.azkar.data.table.CollectionReminderTable
import com.azkar.data.transaction.ExposedTransactionRunner
import com.azkar.domain.exception.CollectionNotFoundException
import com.azkar.domain.model.CollectionReminder
import com.azkar.domain.model.ReminderCadence
import com.azkar.domain.model.impl.RemembranceCollectionImpl
import com.azkar.domain.service.CollectionReminderService
import com.azkar.testUtils.ExposedRepositories
import com.azkar.testUtils.TestDatabase
import com.azkar.testUtils.seededCollections
import de.infix.testBalloon.framework.core.testSuite
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.DayOfWeek
import java.time.Instant
import java.time.OffsetDateTime

/**
 * Reminders on disk, and the two rules the service adds on top.
 *
 * A suite of its own because reminders have a property nothing else in the data layer has: the tick
 * *writes* what it read. Every other repository here is read, then decide, then never write back, so
 * a schedule edited in the gap between the read and the firing would survive unnoticed. Most of what
 * follows is about that not happening, plus the rules that belong to the service rather than to
 * storage — a reminder needs its collection, and editing a schedule leaves the pause and the last
 * firing alone.
 */
val ExposedCollectionReminderSuite by testSuite("ExposedCollectionRepository: reminders") {
    val migratedDatabase = testFixture { TestDatabase.bootstrapWithH2() }

    seededCollections(migratedDatabase) {
        testSuite("storing a schedule") {
            test("a new collection has no reminder") { repositories ->
                val id = newCollection(repositories, "Morning copy")

                assertThat(repositories.collectionReminders.findByCollection(id)).isNull()
            }

            test("a saved schedule reads back whole") { repositories ->
                val id = newCollection(repositories, "Morning copy")

                val saved =
                    repositories.collectionReminders.save(
                        CollectionReminder(
                            collectionId = id,
                            cadence = ReminderCadence.WEEKDAYS,
                            minuteOfDay = 6 * 60 + 30,
                        ),
                    )

                assertThat(saved.cadence).isEqualTo(ReminderCadence.WEEKDAYS)
                assertThat(saved.minuteOfDay).isEqualTo(6 * 60 + 30)
                assertThat(saved.timeOfDay.hour).isEqualTo(6)
                assertThat(saved.timeOfDay.minute).isEqualTo(30)
                assertThat(saved.dayOfWeek).isNull()
                assertThat(saved.enabled).isTrue()
                assertThat(saved.lastTriggeredAt).isNull()
            }

            test("a weekly schedule keeps the day it names") { repositories ->
                val id = newCollection(repositories, "Thursday azkar")

                val saved =
                    repositories.collectionReminders.save(
                        CollectionReminder(
                            collectionId = id,
                            cadence = ReminderCadence.WEEKLY,
                            minuteOfDay = 20 * 60,
                            dayOfWeek = DayOfWeek.THURSDAY,
                        ),
                    )

                assertThat(saved.dayOfWeek).isEqualTo(DayOfWeek.THURSDAY)
                assertThat(repositories.collectionReminders.findByCollection(id)?.dayOfWeek)
                    .isEqualTo(DayOfWeek.THURSDAY)
            }

            test("saving again replaces the schedule rather than adding a second one") { repositories ->
                val id = newCollection(repositories, "Morning copy")
                repositories.collectionReminders.save(CollectionReminder(id, ReminderCadence.DAILY, 6 * 60))

                repositories.collectionReminders.save(
                    CollectionReminder(id, ReminderCadence.WEEKLY, 20 * 60, dayOfWeek = DayOfWeek.FRIDAY),
                )

                assertThat(repositories.collectionReminders.findAll()).hasSize(1)
                assertThat(repositories.collectionReminders.findByCollection(id)?.cadence)
                    .isEqualTo(ReminderCadence.WEEKLY)
            }

            test("the collection's own id is the key, so a second row cannot be written") { repositories ->
                val id = newCollection(repositories, "Morning copy")
                repositories.collectionReminders.save(CollectionReminder(id, ReminderCadence.DAILY, 6 * 60))

                // Straight at the table, past the repository's read-then-decide. The two racing to
                // be the schedule that fires is the thing the key exists to prevent, and a race
                // cannot be tested by taking turns.
                val failure =
                    failureOf {
                        transaction(repositories.database) {
                            CollectionReminderTable.insert {
                                it[CollectionReminderTable.id] = EntityID(id, CollectionReminderTable)
                                it[cadence] = ReminderCadence.DAILY
                                it[minuteOfDay] = 9 * 60
                            }
                        }
                    }

                assertThat(failure).isNotNull()
                assertThat(repositories.collectionReminders.findByCollection(id)?.minuteOfDay)
                    .isEqualTo(6 * 60)
            }

            test("saving what is already stored does not move the moment it was edited") { repositories ->
                val id = newCollection(repositories, "Morning copy")
                val reminder =
                    repositories.collectionReminders.save(CollectionReminder(id, ReminderCadence.DAILY, 6 * 60))
                val before = editedAt(repositories, id)

                repositories.collectionReminders.save(reminder)

                assertThat(editedAt(repositories, id)).isEqualTo(before)
            }

            test("a paused schedule is still the schedule it was") { repositories ->
                val id = newCollection(repositories, "Morning copy")
                val stored =
                    repositories.collectionReminders.save(
                        CollectionReminder(id, ReminderCadence.WEEKLY, 20 * 60, dayOfWeek = DayOfWeek.SUNDAY),
                    )

                repositories.collectionReminders.save(stored.copy(enabled = false))

                val read = repositories.collectionReminders.findByCollection(id)
                assertThat(read?.enabled).isFalse()
                assertThat(read?.cadence).isEqualTo(ReminderCadence.WEEKLY)
                assertThat(read?.dayOfWeek).isEqualTo(DayOfWeek.SUNDAY)
                assertThat(read?.minuteOfDay).isEqualTo(20 * 60)
            }

            test("findAll returns one row per collection, in id order") { repositories ->
                val first = newCollection(repositories, "One")
                val second = newCollection(repositories, "Two")
                repositories.collectionReminders.save(CollectionReminder(second, ReminderCadence.DAILY, 6 * 60))
                repositories.collectionReminders.save(CollectionReminder(first, ReminderCadence.DAILY, 7 * 60))

                assertThat(repositories.collectionReminders.findAll().map { it.collectionId })
                    .containsExactly(first, second)
            }

            test("deleting removes the row and says how many went") { repositories ->
                val id = newCollection(repositories, "Morning copy")
                repositories.collectionReminders.save(CollectionReminder(id, ReminderCadence.DAILY, 6 * 60))

                assertThat(repositories.collectionReminders.deleteByCollection(id)).isEqualTo(1)
                assertThat(repositories.collectionReminders.findByCollection(id)).isNull()
            }

            test("deleting a reminder that is not there is not a failure") { repositories ->
                val id = newCollection(repositories, "Morning copy")

                assertThat(repositories.collectionReminders.deleteByCollection(id)).isZero()
            }

            test("a row this build cannot interpret is skipped rather than guessed at") { repositories ->
                val unreadable = newCollection(repositories, "From a newer build")
                // A weekly row with no day. Nothing can read it: the model insists the two agree.
                transaction(repositories.database) {
                    CollectionReminderTable.insert {
                        it[CollectionReminderTable.id] = EntityID(unreadable, CollectionReminderTable)
                        it[cadence] = ReminderCadence.WEEKLY
                        it[minuteOfDay] = 6 * 60
                    }
                }
                val readable = newCollection(repositories, "Readable")
                repositories.collectionReminders.save(CollectionReminder(readable, ReminderCadence.DAILY, 6 * 60))

                assertThat(repositories.collectionReminders.findAll().map { it.collectionId })
                    .containsExactly(readable)
                assertThat(repositories.collectionReminders.findByCollection(unreadable)).isNull()
            }
        }

        testSuite("recording a firing") {
            test("stamps the firing and leaves the schedule alone") { repositories ->
                val id = newCollection(repositories, "Morning copy")
                repositories.collectionReminders.save(
                    CollectionReminder(id, ReminderCadence.WEEKLY, 20 * 60, dayOfWeek = DayOfWeek.MONDAY),
                )
                val fired = Instant.parse("2026-06-03T06:00:00Z")

                val changed = repositories.collectionReminders.markTriggered(id, fired)

                val read = repositories.collectionReminders.findByCollection(id)
                assertThat(changed).isEqualTo(1)
                assertThat(read?.lastTriggeredAt).isEqualTo(fired)
                assertThat(read?.cadence).isEqualTo(ReminderCadence.WEEKLY)
                assertThat(read?.dayOfWeek).isEqualTo(DayOfWeek.MONDAY)
                assertThat(read?.minuteOfDay).isEqualTo(20 * 60)
            }

            test("a second firing moves the stamp rather than adding a row") { repositories ->
                val id = newCollection(repositories, "Morning copy")
                repositories.collectionReminders.save(CollectionReminder(id, ReminderCadence.DAILY, 6 * 60))
                val second = Instant.parse("2026-06-04T06:00:00Z")

                repositories.collectionReminders.markTriggered(id, Instant.parse("2026-06-03T06:00:00Z"))
                repositories.collectionReminders.markTriggered(id, second)

                assertThat(repositories.collectionReminders.findAll()).hasSize(1)
                assertThat(repositories.collectionReminders.findByCollection(id)?.lastTriggeredAt).isEqualTo(second)
            }

            test("a firing for a collection with no reminder changes nothing") { repositories ->
                val id = newCollection(repositories, "Morning copy")

                val changed = repositories.collectionReminders.markTriggered(id, Instant.now())

                assertThat(changed).isZero()
                assertThat(reminderRowCount(repositories, id)).isZero()
            }
        }

        testSuite("the collection going away") {
            test("takes its reminder with it") { repositories ->
                val id = newCollection(repositories, "Morning copy")
                repositories.collectionReminders.save(CollectionReminder(id, ReminderCadence.DAILY, 6 * 60))
                assertThat(repositories.collectionReminders.findAll()).hasSize(1)

                repositories.collections.deleteById(id)

                // A schedule left ticking for a shelf that is not there is the one failure this
                // feature cannot have, so the row count is read rather than the cascade trusted.
                assertThat(reminderRowCount(repositories, id)).isZero()
                assertThat(repositories.collectionReminders.findAll()).isEmpty()
            }

            test("leaves another collection's reminder alone") { repositories ->
                val gone = newCollection(repositories, "Morning copy")
                val staying = newCollection(repositories, "Evening copy")
                repositories.collectionReminders.save(CollectionReminder(gone, ReminderCadence.DAILY, 6 * 60))
                repositories.collectionReminders.save(CollectionReminder(staying, ReminderCadence.DAILY, 20 * 60))

                repositories.collections.deleteById(gone)

                assertThat(repositories.collectionReminders.findAll().map { it.collectionId })
                    .containsExactly(staying)
            }
        }

        testSuite("the service's own rules") {
            test("a schedule needs its collection to be there") { repositories ->
                val failure =
                    failureOf {
                        serviceFor(repositories).save(404L, ReminderCadence.DAILY, 6 * 60)
                    }

                assertThat(failure).isInstanceOfSatisfying(CollectionNotFoundException::class.java) {
                    assertThat(it.collectionId).isEqualTo(404L)
                }
            }

            test("a firing needs its collection to be there too") { repositories ->
                val failure =
                    failureOf {
                        serviceFor(repositories).markTriggered(404L, Instant.parse("2026-06-03T06:00:00Z"))
                    }

                assertThat(failure).isInstanceOf(CollectionNotFoundException::class.java)
            }

            test("a new schedule starts enabled, with nothing recorded") { repositories ->
                val id = newCollection(repositories, "Morning copy")

                val saved = serviceFor(repositories).save(id, ReminderCadence.DAILY, 6 * 60)

                assertThat(saved.enabled).isTrue()
                assertThat(saved.lastTriggeredAt).isNull()
            }

            test("saving a schedule leaves a pause in place") { repositories ->
                val id = newCollection(repositories, "Morning copy")
                val service = serviceFor(repositories)
                service.save(id, ReminderCadence.DAILY, 6 * 60)
                service.setEnabled(id, false)

                service.save(id, ReminderCadence.DAILY, 9 * 60)

                val read = service.findByCollection(id)
                assertThat(read?.enabled).isFalse()
                assertThat(read?.minuteOfDay).isEqualTo(9 * 60)
            }

            test("saving a schedule leaves the last firing in place") { repositories ->
                val id = newCollection(repositories, "Morning copy")
                val service = serviceFor(repositories)
                val fired = Instant.parse("2026-06-03T06:00:00Z")
                service.save(id, ReminderCadence.DAILY, 6 * 60)
                service.markTriggered(id, fired)

                service.save(id, ReminderCadence.DAILY, 7 * 60)

                val read = service.findByCollection(id)
                assertThat(read?.lastTriggeredAt).isEqualTo(fired)
                assertThat(read?.minuteOfDay).isEqualTo(7 * 60)
            }

            test("resuming a collection with no reminder does not invent one") { repositories ->
                val id = newCollection(repositories, "Morning copy")

                serviceFor(repositories).setEnabled(id, true)

                assertThat(repositories.collectionReminders.findByCollection(id)).isNull()
            }

            test("deleting is how a reminder is removed") { repositories ->
                val id = newCollection(repositories, "Morning copy")
                val service = serviceFor(repositories)
                service.save(id, ReminderCadence.DAILY, 6 * 60)

                service.delete(id)

                assertThat(service.findByCollection(id)).isNull()
                assertThat(service.findAll()).isEmpty()
            }

            test("findAll hands back the whole shelf of schedules") { repositories ->
                val first = newCollection(repositories, "One")
                val second = newCollection(repositories, "Two")
                val service = serviceFor(repositories)
                service.save(first, ReminderCadence.DAILY, 6 * 60)
                service.save(second, ReminderCadence.WEEKLY, 20 * 60, dayOfWeek = DayOfWeek.FRIDAY)

                assertThat(service.findAll().map { it.collectionId }).containsExactly(first, second)
            }
        }
    }
}

private suspend fun newCollection(
    repositories: ExposedRepositories,
    name: String,
): Long = requireNotNull(repositories.collections.save(RemembranceCollectionImpl(name = name)).id)

private fun serviceFor(repositories: ExposedRepositories): CollectionReminderService {
    val transactions = ExposedTransactionRunner(repositories.database)
    return CollectionReminderService(
        ExposedCollectionReminderRepository(transactions),
        ExposedCollectionRepository(transactions, ExposedTagRepository(transactions)),
        transactions,
    )
}

private fun reminderRowCount(
    repositories: ExposedRepositories,
    collectionId: Long,
): Int =
    transaction(repositories.database) {
        CollectionReminderTable
            .selectAll()
            .where { CollectionReminderTable.id eq collectionId }
            .count()
            .toInt()
    }

private fun editedAt(
    repositories: ExposedRepositories,
    collectionId: Long,
): OffsetDateTime =
    transaction(repositories.database) {
        CollectionReminderTable
            .selectAll()
            .where { CollectionReminderTable.id eq collectionId }
            .first()[CollectionReminderTable.updatedAt]
    }

private suspend inline fun failureOf(crossinline block: suspend () -> Unit): Throwable? =
    runCatching { block() }.exceptionOrNull()
