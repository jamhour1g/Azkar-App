package com.azkar.domain.model

import java.time.Instant

/**
 * How far through one remembrance the user has got, within one collection.
 *
 * Scoped to the collection rather than to the remembrance alone, because "where am I in Morning
 * Azkar?" is a question about the collection: the same passage can sit in three collections at three
 * different points, and one shared counter would make finishing one of them silently advance the
 * others. It also makes a session resumable — closing the app mid-way through leaves the position
 * intact instead of restarting the collection from its first member.
 *
 * [lastRecitedAt] is separate from [repeatCount] because the two answer different questions. The
 * count is "how many times, in total"; the stamp is "when, most recently", which is what decides
 * whether a reminder should treat a passage as already done today.
 */
data class RecitationProgress(
    val remembranceId: Long,
    val repeatCount: Int,
    val lastRecitedAt: Instant? = null,
) {
    init {
        require(remembranceId > 0L) { "Recitation progress must belong to a real remembrance" }
        require(repeatCount >= 0) { "Repeat count cannot be negative, was $repeatCount" }
    }
}
