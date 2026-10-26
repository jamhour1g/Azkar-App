package com.azkar.data.seed

import com.azkar.domain.model.Tag
import java.util.Locale

/** The seed resource owns tag wording as well as the records that use it. */
internal object OfficialTagVocabulary {
    val labels: Map<String, Map<String, String>> by lazy {
        SeedDataset.bundled.tags.associate { Tag.fold(it.name) to it.translations }
    }

    fun wordingFor(slug: String): Map<String, String> = labels[Tag.fold(slug)].orEmpty()

    val slugs: Set<String> get() = labels.keys

    fun shippedLocales(): List<Locale> = listOf("en", "ar").map(Locale::forLanguageTag)
}
