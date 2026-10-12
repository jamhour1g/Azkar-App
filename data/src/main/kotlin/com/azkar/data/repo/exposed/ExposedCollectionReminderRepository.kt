package com.azkar.data.repo.exposed

import com.azkar.data.mapping.toReminder
import com.azkar.data.table.CollectionReminderTable
import com.azkar.data.table.entity.CollectionReminderEntity
import com.azkar.data.table.entity.updateAndFlush
import com.azkar.domain.model.CollectionReminder
import com.azkar.domain.repo.CollectionReminderRepository
import com.azkar.domain.service.TransactionRunner
import org.jetbrains.exposed.v1.core.SortOrder
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * Exposed-backed [CollectionReminderRepository].
 *
 * The statements live here rather than in a loader and a writer pair, and so do those of the tag and
 * recitation repositories — see [ExposedRecitationRepository] for why the other two data-module
 * repositories keep their strategy in one file too.
 *
 * The key of this table is the collection's own id, so the entity's identity column stands in for what
 * the schema calls `collection_id`; [CollectionReminderEntity.collectionId] is the same number under a
 * name that says which collection it is about. Nothing else about the table is unusual.
 *
 * [save] is an insert-or-update decided by a read, all three steps inside one transaction. The
 * obvious alternative — try the update, fall through to the insert on failure — makes the primary
 * key violation the branch, and a key violation is a driver error this layer translates into a
 * [com.azkar.domain.exception.PersistenceFailureException], so a first reminder would report itself
 * as a failure. Reading first costs one statement and keeps both outcomes ordinary.
 */
class ExposedCollectionReminderRepository(
    private val transactions: TransactionRunner,
) : CollectionReminderRepository {
    /**
     * Every reminder, by collection id.
     *
     * Ordered by the id rather than by the time of day. The time is minutes into a day with no zone
     * attached, so it does not order anything until the tick has a moment to join it to, and a sort
     * the database cannot perform honestly is a sort that would have to be redone in Kotlin anyway.
     * The caller filters and orders for display.
     */
    override suspend fun findAll(): List<CollectionReminder> =
        transactions.tx("Failed to find the collection reminders") {
            CollectionReminderEntity
                .all()
                .orderBy(CollectionReminderTable.id to SortOrder.ASC)
                .mapNotNull { it.toReminder() }
        }

    override suspend fun findByCollection(collectionId: Long): CollectionReminder? =
        transactions.tx("Failed to find the reminder on collection id=$collectionId") {
            stored(collectionId)
        }

    /**
     * Stores [reminder], replacing whatever was there for its collection.
     *
     * A row already holding exactly this is left alone. The tick re-reads and re-writes the whole
     * list, and without this a firing's bookkeeping would move `updated_at` on every pass.
     */
    override suspend fun save(reminder: CollectionReminder): CollectionReminder =
        transactions.tx("Failed to store the reminder on collection id=${reminder.collectionId}") {
            val timestamp = now()
            val entity = storedEntity(reminder.collectionId)
            when {
                entity == null -> insertRow(reminder, timestamp)
                // The domain value is what `toReminder` mapped out of this very row, so
                // comparing the two is comparing the row with itself. This is the "nothing
                // actually changed" case the tick hits on every pass over an unchanged schedule.
                entity.toReminder() == reminder -> return@tx reminder
                else -> entity.updateAndFlush { copyFrom(reminder, timestamp) }
            }
            stored(reminder.collectionId) ?: reminder
        }

    override suspend fun deleteByCollection(collectionId: Long): Int =
        transactions.tx("Failed to delete the reminder on collection id=$collectionId") {
            // `delete` reports nothing, so the count is decided here: the caller's contract is a
            // number of schedules removed, and a collection with no schedule removed none.
            val entity = storedEntity(collectionId) ?: return@tx 0
            entity.delete()
            1
        }

    /**
     * Stamps the firing, and only the firing.
     *
     * A separate statement from [save] on purpose. The tick reads the reminders, decides which are
     * due and then writes them back; a write built from that read would put back whatever the read
     * saw, so a schedule the user edited in the gap would be reverted by a notification arriving
     * for the old one. This one column cannot do that.
     */
    override suspend fun markTriggered(
        collectionId: Long,
        at: Instant,
    ): Int =
        transactions.tx("Failed to record a reminder firing on collection id=$collectionId") {
            val timestamp = now()
            val entity = storedEntity(collectionId) ?: return@tx 0
            entity.updateAndFlush {
                lastTriggeredAt = OffsetDateTime.ofInstant(at, ZoneOffset.UTC)
                updatedAt = timestamp
            }
            1
        }

    private fun stored(collectionId: Long): CollectionReminder? = storedEntity(collectionId)?.toReminder()

    private fun storedEntity(collectionId: Long): CollectionReminderEntity? =
        CollectionReminderEntity.findById(collectionId)

    private fun insertRow(
        reminder: CollectionReminder,
        timestamp: OffsetDateTime,
    ) {
        // The id is the collection, and the application supplies it — this is the one table in the
        // schema whose key is not generated, so the entity is created *at* an id rather than with one.
        CollectionReminderEntity.new(reminder.collectionId) {
            copyFrom(reminder, timestamp)
            createdAt = timestamp
        }
    }
}

/** Writes [reminder]'s schedule onto this row. Shared by the insert and the update path. */
private fun CollectionReminderEntity.copyFrom(
    reminder: CollectionReminder,
    timestamp: OffsetDateTime,
) {
    cadence = reminder.cadence
    dayOfWeek = reminder.dayOfWeek?.name
    minuteOfDay = reminder.minuteOfDay
    enabled = reminder.enabled
    lastTriggeredAt = reminder.lastTriggeredAt?.let { stamped -> OffsetDateTime.ofInstant(stamped, ZoneOffset.UTC) }
    updatedAt = timestamp
}
