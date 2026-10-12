package com.azkar.data.mapping

import com.azkar.data.table.entity.RemembranceEntity
import com.azkar.domain.model.Remembrance
import com.azkar.domain.model.Tag
import com.azkar.domain.model.impl.RemembranceImpl
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.Locale

/**
 * Assembles the immutable domain [Remembrance] from the rows Exposed read.
 *
 * A remembrance is only meaningful when a locale has *both* a translation and an explanation, so the
 * two maps are intersected and anything the caller supplied without a counterpart is dropped. That
 * drop is worth a log line rather than silence: it means a dataset or an import handed over a half
 * translation, and a user seeing a blank explanation has no way of telling that from an empty row.
 *
 * ### Why the logger is declared inside this object
 *
 * `RemembranceAggregateMappingSuite` attaches a list appender to the logger named
 * `com.azkar.data.mapping.RemembranceMapper` and asserts on what it captures. A `private val logger`
 * at file scope would be a property of the *file facade* — `RemembranceMapperKt` — and that is a
 * sibling of this object in the logger hierarchy, not a child of it. The appender would sit on a
 * logger no event is ever emitted to, capture nothing, and the assertion would be testing an empty
 * list while reading as a passing test. Declaring it here makes the name the suite expects the name
 * the events actually carry.
 */
internal object RemembranceMapper {
    private val logger = KotlinLogging.logger {}

    fun RemembranceEntity.toRemembrance(
        translations: Map<Locale, LocalizedTextRow>,
        explanations: Map<Locale, LocalizedTextRow>,
        tags: Set<Tag>,
    ): Remembrance {
        val id = id.value
        val droppedTranslations = translations.keys - explanations.keys
        val droppedExplanations = explanations.keys - translations.keys
        if (droppedTranslations.isNotEmpty() || droppedExplanations.isNotEmpty()) {
            logger.atWarn {
                message = "Dropped locales missing counterpart"
                payload =
                    mapOf(
                        "remembranceId" to id,
                        "droppedTranslations" to droppedTranslations,
                        "droppedExplanations" to droppedExplanations,
                    )
            }
        }

        val shared = translations.keys.intersect(explanations.keys)
        require(shared.isNotEmpty()) {
            "No locale has both translation and explanation for id=$id"
        }

        val grouped =
            shared.associateWith { locale ->
                val explanation = explanations.getValue(locale)
                val translation = translations.getValue(locale)
                Remembrance.Translations(
                    Remembrance.Translations.Pair(explanation.id, explanation.text),
                    Remembrance.Translations.Pair(translation.id, translation.text),
                )
            }

        return RemembranceImpl(
            id = id,
            grade = grade,
            isFavorite = isFavorite,
            source = sourceText,
            createdAt = createdAt.toInstant(),
            updatedAt = updatedAt.toInstant(),
            translations = grouped,
            tags = tags,
        )
    }
}
