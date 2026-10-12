package com.azkar.ui.library.data

import com.azkar.domain.model.RecitationProgress
import com.azkar.domain.model.Remembrance
import com.azkar.domain.model.RemembranceCollection
import java.time.Duration

/**
 * How far through a collection the user has got, and how long the whole thing takes.
 *
 * Every number here is derived from two inputs — the collection's members and the progress recorded
 * against them — so the whole class is pure functions over data it is handed. Nothing reads the
 * database or the clock, which is what lets the session screen recompute after every tap without
 * asking anything to re-read.
 *
 * "Completed" means counted at least once, not finished to a target. A collection has no target: it
 * is a set of remembrances, and saying you have recited one is a fact rather than a score. A
 * percentage therefore measures coverage, not mastery, and the copy in the interface says so.
 */
data class CollectionStats(
    val totalMembers: Int,
    val completedMembers: Int,
    val totalRepeats: Int,
    val remainingRepeats: Int,
    val estimatedRemaining: Duration,
) {
    /** Share of the collection that has been recited at least once, from 0 to 1. */
    val completion: Float
        get() = if (totalMembers == 0) 0f else completedMembers.toFloat() / totalMembers

    /** Whether there is anything left to recite. An empty collection is complete, not incomplete. */
    val isComplete: Boolean
        get() = totalMembers > 0 && completedMembers == totalMembers

    val hasMembers: Boolean
        get() = totalMembers > 0
}

/**
 * Reads [stats] off a collection and the progress recorded against it.
 *
 * A member with no progress row is not complete and contributes nothing to the total, which is the
 * same thing the database says — no row means no recitation.
 */
fun statsOf(
    collection: RemembranceCollection,
    progress: List<RecitationProgress>,
): CollectionStats {
    val counts =
        progress
            .associate { it.remembranceId to it.repeatCount }
    val remaining =
        collection.members.sumOf { member ->
            val id = member.id ?: return@sumOf 1
            maxOf(0, REMEMBRANCE_REPEATS - (counts[id] ?: 0))
        }
    return CollectionStats(
        totalMembers = collection.members.size,
        completedMembers =
            collection.members.count { member ->
                val id = member.id
                id != null && (counts[id] ?: 0) > 0
            },
        totalRepeats = progress.sumOf { it.repeatCount },
        remainingRepeats = remaining,
        estimatedRemaining = Duration.ofSeconds(remaining * REMEMBRANCE_SECONDS),
    )
}

/**
 * The first member that has not been recited yet, or `null` when the collection is finished.
 *
 * Members are walked in the collection's own order rather than by count, because "the next one" is a
 * question about the shelf's sequence — the same order the detail screen lists them in. An
 * unpersisted member cannot be recorded, so it is treated as done: it would otherwise be offered
 * forever.
 */
fun nextMember(
    collection: RemembranceCollection,
    progress: List<RecitationProgress>,
): Remembrance? {
    val counts = progress.associate { it.remembranceId to it.repeatCount }
    return collection.members.firstOrNull { member ->
        val id = member.id ?: return@firstOrNull false
        (counts[id] ?: 0) < REMEMBRANCE_REPEATS
    }
}

/** Whether [member] is one of the first [limit] members yet to be recited, for the session's preview. */
fun upcomingMembers(
    collection: RemembranceCollection,
    progress: List<RecitationProgress>,
    limit: Int,
): List<Remembrance> {
    if (limit <= 0) {
        return emptyList()
    }
    val counts = progress.associate { it.remembranceId to it.repeatCount }
    return collection.members
        .filter { member ->
            val id = member.id ?: return@filter false
            (counts[id] ?: 0) < REMEMBRANCE_REPEATS
        }.take(limit)
}

/**
 * How long one recitation of one remembrance is assumed to take, in seconds.
 *
 * A guess, and labelled as one in the interface: it is the average of a few slow readings with room
 * for the pause in between, not a measurement of this user. It exists so "about six minutes left" is
 * on screen at all — a precise figure derived from a constant would be a lie with more digits.
 */
private const val REMEMBRANCE_SECONDS = 20L

/**
 * How many times each member is recited per pass through the collection.
 *
 * One, which is the ordinary case, and fixed rather than configurable because a collection is a set
 * of remembrances rather than a regimen; the repeat count a user builds up is what the reminder
 * rework will schedule against.
 */
private const val REMEMBRANCE_REPEATS = 1
