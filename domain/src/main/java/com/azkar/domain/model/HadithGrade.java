package com.azkar.domain.model;

/// Represents the classification of a hadith's authenticity or reliability.
///
/// A `HadithGrade` provides a general indication of how strongly
/// a hadith is considered reliable according to traditional scholarship.
/// These grades can be used to provide context for a [Remembrance].
public enum HadithGrade {
    /// **Sahih (authentic):** The highest level of reliability.
    /// Widely accepted as sound and trustworthy.
    SAHIH,

    /// **Hasan (good):** Considered reliable, though slightly
    /// lower in strength than [#SAHIH]. Still generally acceptable.
    HASAN,

    /// **Da'if (weak):** Considered weak in authenticity.
    /// Typically not used as a primary source for rulings,
    /// but may still appear in remembrance collections.
    DAIF,

    /// Used when the authenticity of a hadith has not been specified
    /// or no classification is provided.
    UNSPECIFIED,
}
