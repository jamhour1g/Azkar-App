package com.azkar.domain.model

/**
 * Where a [RemembranceCollection] came from.
 *
 * The split records provenance and nothing else. It used to also decide who could change a
 * collection, and a shipped row used to resolve its members from a tag rather than from rows, which
 * is why this distinction once carried two kinds of information. Neither survives: membership is
 * stored the same way for every collection, and every collection accepts the same edits, so what is
 * left is the sentence on the card's badge and whether the Included or Yours chip offers it.
 */
enum class CollectionKind {
    /** Shipped by the application. */
    OFFICIAL,

    /** Created by the user. */
    CUSTOM,
}
