package com.azkar.data.repo.exposed

import com.azkar.data.mapping.toProgress
import com.azkar.data.table.RecitationProgressTable
import com.azkar.data.table.entity.RecitationProgressEntity
import com.azkar.data.table.entity.recitationProgressId
import com.azkar.domain.model.RecitationProgress
import com.azkar.domain.model.RecitationSummary
import com.azkar.domain.repo.RecitationRepository
import com.azkar.domain.service.TransactionRunner
import org.jetbrains.exposed.v1.core.CustomFunction
import org.jetbrains.exposed.v1.core.IntegerColumnType
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.intLiteral
import org.jetbrains.exposed.v1.core.plus
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.update
import java.time.OffsetDateTime

/**
 * Exposed-backed [RecitationRepository].
 *
 * The statements live here rather than in a loader and a writer pair, and so do those of the tag and
 * reminder repositories. The other two data-module repositories keep their strategy in this file too,
 * as private methods: what a split bought them was a hop between two files for the thirteen
 * functions that only forwarded, which is not what a split is for. [ExposedRemembranceRepository]
 * documents the reasoning at length; this one has less to hide.
 *
 * [bump] is the interesting one, and it is the one place in the whole data layer that deliberately
 * does not go through an entity — see [addDelta].
 */
class ExposedRecitationRepository(
    private val transactions: TransactionRunner,
) : RecitationRepository {
    override suspend fun findByCollection(collectionId: Long): List<RecitationProgress> =
        transactions.tx("Failed to find the recitation progress of collection id=$collectionId") {
            RecitationProgressEntity
                .find { RecitationProgressTable.collectionId eq collectionId }
                .orderBy(RecitationProgressTable.remembranceId to SortOrder.ASC)
                .map { it.toProgress() }
        }

    override suspend fun findByRemembrance(remembranceId: Long): List<RecitationProgress> =
        transactions.tx("Failed to find the recitation progress of remembrance id=$remembranceId") {
            RecitationProgressEntity
                .find { RecitationProgressTable.remembranceId eq remembranceId }
                .map { it.toProgress() }
        }

    /**
     * One statement for every named collection, folded in Kotlin rather than in SQL.
     *
     * The database could group and aggregate these two figures itself, but the rows behind them are
     * one per recited member of a handful of collections — a few hundred at the outside — and folding
     * them here keeps the statement to the plain single-table read the rest of this class already
     * uses. The win over reading one collection at a time is the round trip, not the arithmetic.
     *
     * A collection with nothing recited is absent rather than present with a zero, so the caller can
     * tell "nothing recorded" from "not one of the ones I asked about".
     *
     * Rows holding a count of zero are left out of the count, and it matters: a member that was
     * recited once and then uncounted keeps a row saying zero rather than losing it, so counting rows
     * would report a member as done when it is not. Nothing currently sends a negative delta, but the
     * shelf draws this figure next to every collection's size, so the number has to be right on its
     * own rather than right only while nobody has undone a count.
     */
    override suspend fun findSummaries(collectionIds: List<Long>): Map<Long, RecitationSummary> {
        if (collectionIds.isEmpty()) {
            return emptyMap()
        }
        return transactions.tx("Failed to summarise the recitation progress of ${collectionIds.size} collections") {
            RecitationProgressEntity
                .find { RecitationProgressTable.collectionId inList collectionIds }
                .filter { it.repeatCount > 0 }
                .groupBy({ it.collectionId.value }, { it.lastRecitedAt?.toInstant() })
                .map { (collectionId, stamps) ->
                    collectionId to
                        RecitationSummary(
                            collectionId = collectionId,
                            recitedCount = stamps.size,
                            lastRecitedAt = stamps.filterNotNull().maxOrNull(),
                        )
                }.toMap()
        }
    }

    /**
     * Adds each delta to the stored count, creating the row when a member has none yet.
     *
     * Delegates the arithmetic to the database — see [addDelta] — and keeps the two decisions that
     * belong here: a delta of zero is nothing to write, and the returned figure counts only the
     * entries that actually moved.
     */
    override suspend fun bump(
        collectionId: Long,
        deltas: Map<Long, Int>,
    ): Int =
        transactions.tx("Failed to record recitation progress on collection id=$collectionId") {
            val timestamp = now()
            deltas.entries.count { (remembranceId, delta) ->
                // Nothing to add is nothing to write: reaching here with a stored row would
                // restamp `last_recited_at` for a tap the user has already been told did not
                // register, and reaching it without one would create a row saying so.
                delta != 0 && addDelta(collectionId, remembranceId, delta, timestamp)
            }
        }

    /**
     * Adds [delta] to one member's count, creating the row if there is none, and reports whether
     * that moved it.
     *
     * The one statement in this data layer that is written against the table rather than against
     * [RecitationProgressEntity], and it stays that way on purpose. The arithmetic has to happen
     * while the database holds the row: read the count, add, write the sum, and two transactions
     * running at once both read one, both write two, and one tap disappears. An entity cannot
     * express that — `EntityClass` has no set-based update, and a mutated entity issues its UPDATE
     * with a value Kotlin computed from something it read, which is the pair this exists to avoid.
     * So the statement is written by hand and the insert on the miss path goes through the entity.
     *
     * The `greatest` is the clamp, and it stays because the column is not constrained to be
     * non-negative: a row written by an import or a future feature must not be able to produce a
     * negative count either. Exposed has no function for it, so the standard one is spelled out.
     *
     * An update that matched nothing means the member has no row yet, and only a positive delta is
     * worth creating one for — a negative delta against a member with nothing stored has nothing to
     * undo, and a row saying zero would put it on the shelf as recited today. The composite primary
     * key is what keeps a member to one row.
     */
    private fun addDelta(
        collectionId: Long,
        remembranceId: Long,
        delta: Int,
        timestamp: OffsetDateTime,
    ): Boolean {
        val updated =
            RecitationProgressTable.update({
                (RecitationProgressTable.collectionId eq collectionId) and
                    (RecitationProgressTable.remembranceId eq remembranceId)
            }) {
                it[repeatCount] =
                    CustomFunction(
                        "GREATEST",
                        IntegerColumnType(),
                        repeatCount + delta,
                        intLiteral(0),
                    )
                it[RecitationProgressTable.lastRecitedAt] = timestamp
            }
        return when {
            updated > 0 -> true
            delta < 0 -> false
            else -> {
                RecitationProgressEntity.new(recitationProgressId(collectionId, remembranceId)) {
                    repeatCount = delta
                    lastRecitedAt = timestamp
                    createdAt = timestamp
                    updatedAt = timestamp
                }
                true
            }
        }
    }

    /**
     * Overwrites one count.
     *
     * An insert rather than an update-or-insert, so setting a count for a member that has no row
     * yet — which is what resetting one member of a fresh collection does — behaves the same as
     * setting one that has. The primary key rejects the second attempt, so a member can only ever
     * hold one row.
     */
    override suspend fun setCount(
        collectionId: Long,
        remembranceId: Long,
        count: Int,
    ) {
        transactions.tx("Failed to set the count of remembrance id=$remembranceId in collection id=$collectionId") {
            val timestamp = now()
            RecitationProgressTable.deleteWhere {
                (RecitationProgressTable.collectionId eq collectionId) and
                    (RecitationProgressTable.remembranceId eq remembranceId)
            }
            RecitationProgressEntity.new(recitationProgressId(collectionId, remembranceId)) {
                repeatCount = count.coerceAtLeast(0)
                lastRecitedAt = timestamp
                createdAt = timestamp
                updatedAt = timestamp
            }
        }
    }

    override suspend fun reset(collectionId: Long): Int =
        transactions.tx("Failed to reset the recitation progress of collection id=$collectionId") {
            RecitationProgressTable
                .deleteWhere { RecitationProgressTable.collectionId eq collectionId }
        }
}
