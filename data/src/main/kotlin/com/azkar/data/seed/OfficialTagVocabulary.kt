package com.azkar.data.seed

import com.azkar.domain.model.Tag
import java.util.Locale

/**
 * The wording of the labels the shipped dataset files its remembrances under.
 *
 * The dataset names a tag by a folded slug — `morning`, `morning & evening`, `hajj & umrah` — and that
 * slug is its identity: it is what [TagTable.name] stores, what the unique index is built on, and what
 * a rename collides with. It is also not something anybody would want to read. "morning" is a
 * database key that happens to be spelled in letters; the label on a chip is "Morning Azkar".
 *
 * So the slug stays where identity lives and the wording lives here, in the one module that already
 * owns every other piece of shipped content. There is deliberately no `values-ar/strings.xml` copy of
 * this: that is for the app's own chrome, and a shipped label is data that ships with the data.
 *
 * English is spelled here rather than reused from `collection.name`, because the two disagree on
 * purpose. The `morning` tag is "Morning Azkar" as a category and the collection is also "Morning
 * Azkar", but the collection named "After Prayer" is filed under the tag "Prayer" — and a reader
 * looking at the chip on a shelf should see the category, not the shelf it happens to seed. Where they
 * do agree this file is still the source, so there is one answer per label to keep in step.
 *
 * Adding a tag to the dataset without adding it here is a quiet failure, and
 * `OfficialTagVocabularySuite` is what makes it loud: every slug in the catalogue and every slug the
 * dataset carries is required to appear here with a name in every shipped language.
 */
internal object OfficialTagVocabulary {
    /** The languages the shipped wording is written in, as BCP-47 tags. */
    private val LANGUAGES = listOf(ENGLISH_TAG, ARABIC_TAG)

    private const val ENGLISH_TAG = "en"
    private const val ARABIC_TAG = "ar"

    /**
     * One label's wording, keyed by the folded slug the dataset uses for it.
     *
     * The map is keyed by slug rather than holding a list so that a missing label is a `null` lookup —
     * which the seed reports — rather than a row in a list that happens not to be there.
     */
    val labels: Map<String, Map<String, String>> =
        mapOf(
            "morning" to wording("Morning Azkar", "أذكار الصباح"),
            "evening" to wording("Evening Azkar", "أذكار المساء"),
            "morning & evening" to wording("Morning & Evening Azkar", "أذكار الصباح والمساء"),
            "prayer" to wording("After Prayer", "أذكار بعد الصلاة"),
            "praising allah" to wording("Praising Allah", "الثناء على الله"),
            "joy & distress" to wording("Joy & Distress", "الفرح والهمّ"),
            "good etiquette" to wording("Good Etiquette", "حسن الأدب"),
            "sickness & death" to wording("Sickness & Death", "المرض والموت"),
            "home & family" to wording("Home & Family", "البيت والأسرة"),
            "nature" to wording("Nature", "الطبيعة"),
            "travel" to wording("Travel Azkar", "أذكار السفر"),
            "food & drink" to wording("Food & Drink", "الطعام والشراب"),
            "hajj & umrah" to wording("Hajj & Umrah", "الحجّ والعمرة"),
            // The dataset's own provenance marker rather than a subject. It is a tag like any other,
            // and it is drawn on chips, so it needs a word — see [com.azkar.domain.model.Tag] for why
            // the slug under it is never shown.
            "muslim data" to wording("Muslim Data", "بيانات المسلمين"),
        )

    /**
     * The wording for [slug], or an empty map for a label this vocabulary does not carry.
     *
     * Folding the argument rather than the caller is what keeps the lookup total: the dataset stores
     * every name folded and a caller holding a slug from anywhere else should not have to know that.
     * An unrecognised slug comes back empty rather than null, so the caller can write what it has and
     * the shelf simply falls back to the stored name — which is the right answer for a tag the user
     * typed themselves.
     */
    fun wordingFor(slug: String): Map<String, String> = labels[Tag.fold(slug)].orEmpty()

    /** The folded slugs this vocabulary carries wording for, which the suite checks the dataset against. */
    val slugs: Set<String> get() = labels.keys

    private fun wording(
        english: String,
        arabic: String,
    ): Map<String, String> = listOf(ENGLISH_TAG to english, ARABIC_TAG to arabic).toMap()

    /** The tag rows' own language, kept here so the two never drift into answering different questions. */
    fun shippedLocales(): List<Locale> = LANGUAGES.map(Locale::forLanguageTag)
}
