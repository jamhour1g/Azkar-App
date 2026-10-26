package com.azkar.data.seed

import com.azkar.domain.model.LocalizedPair

/** Shipped collection wording and explicit ordered membership from the reviewed seed resource. */
internal object OfficialCollectionCatalogue {
    data class Entry(
        val tagName: String?,
        val name: String,
        val description: String?,
        val iconKey: String?,
        val translations: Map<String, LocalizedPair>,
        val members: List<String>,
        val nameLocale: String,
    )

    val entries: List<Entry> by lazy {
        val dataset = SeedDataset.bundled
        val tagsByKey = dataset.tags.associateBy { it.key }
        dataset.collections.map { collection ->
            Entry(
                tagName = tagsByKey.getValue(collection.tag).name,
                name = collection.name,
                description = collection.description,
                iconKey = collection.iconKey,
                translations = collection.translations.mapValues { it.value.toLocalizedPair() },
                members = collection.members,
                nameLocale = collection.nameLocale,
            )
        }
    }
}
