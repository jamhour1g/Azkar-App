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
 * The discard is a loss of data, so it has to be reported somewhere a program can read, not only
 * somewhere a person can read. A log line is a diagnostic: it says what happened to whoever is
 * looking at the log, and nothing more. A caller that needs to know cannot ask a log — it has to
 * read one, which means attaching to a logger that the whole process shares and then hoping that
 * every line it collected was caused by its own call.
 *
 * So the mapper does not log. It hands the discard to [onDropped], and the caller decides what
 * happens to it. The application wires that to a log through [droppedExplanationsReported], which
 * is the line the application has always written, under the logger name it has always had. A test
 * wires it to something it can assert on.
 *
 * The default of [droppedExplanationsReported] keeps the production call — and the logger's name,
 * which is derived from this enclosing declaration — exactly as they were.
 */
internal object RemembranceMapper {
    fun RemembranceEntity.toRemembrance(
        onDropped: (Long, List<String>) -> Unit,
        translations: Map<Locale, LocalizedTextRow>,
        explanations: Map<Locale, LocalizedTextRow>,
        tags: Set<Tag>,
    ): Remembrance {
        val id = id.value
        val droppedExplanations = explanations.keys - translations.keys
        if (droppedExplanations.isNotEmpty()) {
            onDropped(id, droppedExplanations.map { it.toString() })
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

    /**
     * The report the application makes when the mapper drops an explanation.
     *
     * The message and the payload are the ones the application has always written, so the line a
     * person finds in a log is unchanged. What is new is that the report is a value the caller can
     * hand to something else — a callback that records it, a callback that counts it — instead of a
     * line that can only be read.
     *
     * The [logger] is a parameter for the same reason it is one everywhere else here: a logger
     * reached through a name is a singleton, and a test that wants to see this line has to own the
     * logger it was written to. The default keeps the production logger, and the name derived from
     * this enclosing declaration, exactly as they were.
     */
    fun droppedExplanationsReported(logger: KLogger = KotlinLogging.logger {}): (Long, List<String>) -> Unit =
        { id, dropped ->
            logger.atWarn {
                message = "Dropped explanations without translation"
                payload =
                    mapOf(
                        "remembranceId" to id,
                        "droppedExplanations" to dropped,
                    )
            }
        }
}
