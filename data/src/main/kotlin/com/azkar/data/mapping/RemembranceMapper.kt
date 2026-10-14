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
 * A translation is meaningful without an explanation, so every translation is retained and gets an
 * empty explanation pair when its locale has no explanation row. Explanations without a translation
 * are still discarded and logged because they cannot be attached to a rendered translation.
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
        val droppedExplanations = explanations.keys - translations.keys
        if (droppedExplanations.isNotEmpty()) {
            logger.atWarn {
                message = "Dropped explanations without translation"
                payload =
                    mapOf(
                        "remembranceId" to id,
                        "droppedExplanations" to droppedExplanations,
                    )
            }
        }

        require(translations.isNotEmpty()) {
            "No translations found for id=$id"
        }

        val grouped =
            translations.mapValues { (locale, translation) ->
                val explanation = explanations[locale]
                Remembrance.Translations(
                    Remembrance.Translations.Pair(explanation?.id, explanation?.text.orEmpty()),
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
