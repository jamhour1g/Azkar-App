package com.azkar.data.mapping

import com.azkar.domain.model.Remembrance

/**
 * One row of either `*_translation` table, reduced to what a [Remembrance.Translations] needs.
 *
 * The domain pairs an explanation and a translation under one id each, so the loader reduces the
 * entities to this before handing them over. Both sides — the id and the text — are already unwrapped
 * from Exposed's types, so nothing below has to know a translation is stored as an entity row.
 */
internal data class LocalizedTextRow(
    val id: Long,
    val text: String,
)
