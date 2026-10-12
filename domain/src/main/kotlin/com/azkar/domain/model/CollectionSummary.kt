package com.azkar.domain.model

import java.time.Instant

/**
 * A collection as a list of them needs it: what to write on the card, and how big it is.
 *
 * The Home rail names a collection, counts its passages and says when it was last recited. Getting
 * those from a whole [RemembranceCollection] means loading every member as a full aggregate — both
 * locales of the translation and the explanation, plus tags — for each of the several collections on
 * screen, and then reading a single integer off the end of the result. That made the cost of
 * painting a rail of three cards proportional to the size of the whole library.
 *
 * This is the same fold [RecitationSummary] is, for the same reason and in the same spirit: a list
 * asking three questions about many rows should not have to pay to assemble them. The members are
 * still one read away — this is a projection, not a replacement, and a card that needs a passage
 * from a collection still asks for the collection itself.
 *
 * [homePinnedAt] is carried rather than a `pinned` flag because the rail's order is recency, and a
 * flag cannot order anything.
 *
 * [coverImagePath] is carried so a rail card can paint the same picture the shelf card does.
 * Resolving it means reading and decoding a file, which is exactly the cost this type exists to
 * avoid — so it is read here and decoded only by the cards that actually draw it.
 */
data class CollectionSummary(
    val id: Long,
    val name: String,
    val description: String,
    val iconKey: String,
    val coverImagePath: String?,
    val memberCount: Int,
    val homePinnedAt: Instant?,
) {
    init {
        require(id > 0L) { "A summary must belong to a real collection" }
        require(memberCount >= 0) { "Member count cannot be negative, was $memberCount" }
    }
}
