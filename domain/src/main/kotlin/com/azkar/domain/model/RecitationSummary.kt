package com.azkar.domain.model

import java.time.Instant

/**
 * How much of a collection has been recited, folded into one value.
 *
 * The Home rail needs two numbers for each of several collections at once, and
 * [com.azkar.domain.model.RecitationProgress] answers them one row per member — a question that
 * does not scale past a handful of collections, since loading every member's row to count the
 * recited ones is the whole table for a count of two columns.
 *
 * [recitedCount] counts *members*, not repetitions. A member recited forty times is still one
 * member done, and a completion figure built from repetitions would show a partially-read collection
 * as finished.
 */
data class RecitationSummary(
    val collectionId: Long,
    val recitedCount: Int,
    val lastRecitedAt: Instant?,
) {
    init {
        require(collectionId > 0L) { "A summary must belong to a real collection" }
        require(recitedCount >= 0) { "Recited count cannot be negative, was $recitedCount" }
    }
}
