package com.azkar.data.seed

import com.azkar.data.table.ExplanationTranslationTable
import com.azkar.data.table.RemembranceTable
import com.azkar.data.table.RemembranceTagTable
import com.azkar.data.table.RemembranceTranslationTable
import com.azkar.data.table.TagTable
import com.azkar.domain.model.HadithGrade
import com.azkar.domain.model.Tag
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.selectAll

/**
 * How much the dataset loader actually inserted.
 *
 * The counts are the loader's answer to "what did you do", and they are the answer a test should
 * ask for. The log line that also carries them is a *report*, meant for a person reading a console,
 * and a test that reads a report is reading the side effect of an action rather than the action
 * itself: the line is only there if the level happens to allow it, only says what it says while
 * nobody else is logging under the same name, and says it a beat later than the work it describes.
 * The counts are the work itself, and they cannot be lost on the way to the caller.
 *
 * Both counts are zero when the loader decided there was nothing to do.
 */
internal data class SeedCounts(
    val remembrances: Int,
    val tags: Int,
    val remembranceIds: Map<String, Long> = emptyMap(),
)

/** Loads the validated library into an empty database inside the caller's transaction. */
internal object SeedDataLoader {
    /**
     * Inserts the dataset, unless the database already holds remembrances, and reports what it
     * inserted.
     *
     * The check is what makes this safe to run on every start: a database that already has
     * content is a database that has been seeded, or a database the user has filled in, and
     * neither should gain a second copy of the dataset.
     *
     * [logger] is a parameter because the three lines this logs are the only way a person finds out
     * that the dataset was skipped, and a test that wants to know the same thing should not have to
     * watch a global logger for a line that any other caller might have produced at the same time.
     * The default keeps the production logger — and the name derived from this object — as it was.
     */
    fun load(logger: KLogger = KotlinLogging.logger {}): SeedCounts {
        if (RemembranceTable.selectAll().limit(1).any()) {
            logger.debug { "Remembrance table is not empty; skipping seed data" }
            return SeedCounts(remembrances = 0, tags = 0)
        }
        return seed(logger)
    }

    /**
     * Inserts the rows the dataset holds and reports how many of each it inserted.
     *
     * Split out of [load] so that the "is there anything to do at all" question and the work
     * itself are two functions, each with a single way out.
     */
    private fun seed(logger: KLogger): SeedCounts {
        val dataset = SeedDataset.bundled
        val rows = dataset.remembrances
        val tagIds = insertTags(dataset.tags)
        val remembranceIds = insertRemembrances(rows)
        insertTranslations(rows, remembranceIds)
        insertExplanations(rows, remembranceIds)
        insertTagLinks(rows, remembranceIds, tagIds)

        logger.info { "Seeded ${rows.size} remembrances and ${tagIds.size} tags" }

        return SeedCounts(
            remembrances = rows.size,
            tags = tagIds.size,
            remembranceIds = rows.map { it.key }.zip(remembranceIds).toMap(),
        )
    }

    private fun insertTags(tags: List<SeedTag>): Map<String, Long> {
        val generated =
            TagTable.batchInsert(tags) { tag ->
                this[TagTable.name] = Tag.fold(tag.name)
                this[TagTable.nameLocaleCode] = tag.nameLocale
            }
        return tags.map { it.key }.zip(generated.map { it[TagTable.id].value }).toMap()
    }

    private fun insertRemembrances(rows: List<SeedRemembrance>): List<Long> =
        RemembranceTable
            .batchInsert(rows) { row ->
                this[RemembranceTable.sourceText] = row.source
                this[RemembranceTable.grade] = HadithGrade.valueOf(row.grade)
            }.map { it[RemembranceTable.id].value }

    private fun insertTranslations(
        rows: List<SeedRemembrance>,
        remembranceIds: List<Long>,
    ) {
        val localized = rows.localized(remembranceIds) { it.text }
        RemembranceTranslationTable.batchInsert(localized) { row ->
            this[RemembranceTranslationTable.ownerId] = row.remembranceId
            this[RemembranceTranslationTable.localeCode] = row.localeCode
            this[RemembranceTranslationTable.text] = row.text
        }
    }

    private fun insertExplanations(
        rows: List<SeedRemembrance>,
        remembranceIds: List<Long>,
    ) {
        ExplanationTranslationTable.batchInsert(
            rows.localized(remembranceIds) { it.explanation },
        ) { row ->
            this[ExplanationTranslationTable.ownerId] = row.remembranceId
            this[ExplanationTranslationTable.localeCode] = row.localeCode
            this[ExplanationTranslationTable.text] = row.text
        }
    }

    private fun insertTagLinks(
        rows: List<SeedRemembrance>,
        remembranceIds: List<Long>,
        tagIds: Map<String, Long>,
    ) {
        val links =
            rows.flatMapIndexed { index, row ->
                val remembranceId = remembranceIds[index]
                row.tags.map { tagName -> TagLink(remembranceId, tagIds.getValue(tagName)) }
            }
        RemembranceTagTable.batchInsert(links, shouldReturnGeneratedValues = false) { link ->
            this[RemembranceTagTable.remembranceId] = link.remembranceId
            this[RemembranceTagTable.tagId] = link.tagId
        }
    }

    private fun List<SeedRemembrance>.localized(
        remembranceIds: List<Long>,
        text: (SeedTranslation) -> String?,
    ): List<LocalizedText> =
        flatMapIndexed { index, row ->
            row.translations.mapNotNull { (locale, translation) ->
                text(translation)?.takeIf { it.isNotBlank() }?.let {
                    LocalizedText(remembranceIds[index], locale, it)
                }
            }
        }

    private data class LocalizedText(
        val remembranceId: Long,
        val localeCode: String,
        val text: String,
    )

    private data class TagLink(
        val remembranceId: Long,
        val tagId: Long,
    )
}
