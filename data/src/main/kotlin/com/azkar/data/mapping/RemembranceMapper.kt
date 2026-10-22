package com.azkar.data.mapping

import com.azkar.data.table.entity.RemembranceEntity
import com.azkar.domain.model.Remembrance
import com.azkar.domain.model.Tag
import com.azkar.domain.model.impl.RemembranceImpl
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.Locale

/**
 * Assembles the immutable domain [Remembrance] from the rows Exposed read.
 *
 * A translation is meaningful without an explanation, so every translation is retained and gets an
 * empty explanation pair when its locale has no explanation row. Explanations without a translation
 * are still discarded — they cannot be attached to a rendered translation — and the discard is
 * reported through the callback the caller supplies.
 *
 * ### Why the report is a callback
 *
 * The discard is a loss of data, so it is reported. The report is a log line, which says what
 * happened to whoever is looking at the log.
 *
 * ### Why the logger is a parameter
 *
 * The logger is handed in rather than looked up. A logger reached through a name is a singleton
 * that the whole process shares, so a test that wants to see this line has to own the logger it
 * was written to. The default keeps the production logger, and the name derived from this
 * enclosing declaration, exactly as they were.
 */
internal object RemembranceMapper {
    fun RemembranceEntity.toRemembrance(
        logger: KLogger = KotlinLogging.logger {},
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
