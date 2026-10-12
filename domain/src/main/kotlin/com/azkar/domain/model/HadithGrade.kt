package com.azkar.domain.model

/**
 * The classification of a hadith's authenticity or reliability.
 *
 * A grade gives a general indication of how strongly a hadith is considered reliable according to
 * traditional scholarship. It is used to provide context for a [Remembrance].
 */
enum class HadithGrade {
    /** **Sahih (authentic):** the highest level of reliability; widely accepted as sound. */
    SAHIH,

    /** **Hasan (good):** reliable, though slightly lower in strength than [SAHIH]. */
    HASAN,

    /** **Da'if (weak):** weak in authenticity; typically not used as a primary source for rulings. */
    DAIF,

    /** Used when the authenticity of a hadith has not been specified or no classification exists. */
    UNSPECIFIED,
}
