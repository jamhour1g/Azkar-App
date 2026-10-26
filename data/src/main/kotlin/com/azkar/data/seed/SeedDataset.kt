package com.azkar.data.seed

import com.azkar.domain.model.HadithGrade
import com.azkar.domain.model.LocalizedPair
import com.azkar.domain.model.Tag
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.util.Locale

/** Reviewed content, separate from generated database IDs and user state. */
@Serializable
internal data class SeedDataset(
    val version: Int,
    val provenance: JsonObject,
    val tags: List<SeedTag>,
    val remembrances: List<SeedRemembrance>,
    val collections: List<SeedCollection>,
) {
    fun validate(): SeedDataset =
        apply {
            require(version == 1) { "Unsupported seed format: $version" }
            require(remembrances.isNotEmpty() && collections.isNotEmpty()) { "Seed library must not be empty" }
            val tagKeys = uniqueKeys(tags.map { it.key })
            val remembranceKeys = uniqueKeys(remembrances.map { it.key })
            uniqueKeys(collections.map { it.key })
            require(tags.map { Tag.fold(it.name) }.distinct().size == tags.size) { "Duplicate seed tag name" }
            tags.forEach { tag ->
                require(tag.name.isNotBlank()) { "Blank seed tag name" }
                validateLocales(tag.translations.keys)
                require(tag.translations.values.all { it.isNotBlank() }) { "Blank tag translation" }
                require(tag.nameLocale in tag.translations) { "Missing canonical tag language" }
            }
            remembrances.forEach { remembrance ->
                HadithGrade.valueOf(remembrance.grade)
                require(remembrance.source == null || remembrance.source.isNotBlank()) { "Blank source" }
                validateLocales(remembrance.translations.keys)
                require(remembrance.translations.values.all { it.text.isNotBlank() }) { "Blank remembrance text" }
                validateLinks(remembrance.tags, tagKeys)
            }
            require(
                collections.flatMap { it.members }.toSet() == remembranceKeys,
            ) { "Seed items missing from collections" }
            collections.forEach { collection ->
                require(collection.name.isNotBlank()) { "Blank collection name" }
                validateLocales(collection.translations.keys)
                require(collection.translations.values.all { it.name.isNotBlank() }) { "Blank collection translation" }
                require(collection.nameLocale in collection.translations) { "Missing canonical collection language" }
                require(collection.tag in tagKeys) { "Unknown collection tag: ${collection.tag}" }
                require(collection.members.isNotEmpty()) { "Empty seed collection: ${collection.key}" }
                validateLinks(collection.members, remembranceKeys)
            }
        }

    private fun uniqueKeys(keys: List<String>): Set<String> {
        require(keys.all { it.isNotBlank() } && keys.distinct().size == keys.size) { "Blank or duplicate seed key" }
        return keys.toSet()
    }

    private fun validateLocales(locales: Set<String>) {
        require(locales.containsAll(listOf("ar", "en"))) { "Seed wording must include Arabic and English" }
        require(locales.all { Locale.forLanguageTag(it).toLanguageTag() == it }) { "Invalid seed locale" }
    }

    private fun validateLinks(
        keys: List<String>,
        available: Set<String>,
    ) {
        require(keys.distinct().size == keys.size && keys.all { it in available }) { "Duplicate or dangling seed link" }
    }

    companion object {
        const val RESOURCE = "/db/seed/library.json"

        val bundled: SeedDataset by lazy {
            val content =
                checkNotNull(SeedDataset::class.java.getResourceAsStream(RESOURCE)) {
                    "Seed resource [$RESOURCE] is missing from the classpath"
                }.bufferedReader(Charsets.UTF_8).use { it.readText() }
            Json.decodeFromString<SeedDataset>(content).validate()
        }
    }
}

@Serializable
internal data class SeedTag(
    val key: String,
    val name: String,
    val nameLocale: String,
    val translations: Map<String, String>,
)

@Serializable
internal data class SeedRemembrance(
    val key: String,
    val grade: String,
    val source: String?,
    val translations: Map<String, SeedTranslation>,
    val tags: List<String>,
    val provenance: JsonObject,
)

@Serializable
internal data class SeedTranslation(
    val text: String,
    val explanation: String? = null,
)

@Serializable
internal data class SeedCollection(
    val key: String,
    val tag: String,
    val name: String,
    val nameLocale: String,
    val description: String?,
    val iconKey: String?,
    val translations: Map<String, SeedCollectionTranslation>,
    val members: List<String>,
)

@Serializable
internal data class SeedCollectionTranslation(
    val name: String,
    val description: String? = null,
) {
    fun toLocalizedPair(): LocalizedPair = LocalizedPair(name, description)
}
