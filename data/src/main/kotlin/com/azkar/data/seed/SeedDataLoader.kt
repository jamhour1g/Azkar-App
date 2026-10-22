package com.azkar.data.seed

import com.azkar.data.table.ExplanationTranslationTable
import com.azkar.data.table.RemembranceTable
import com.azkar.data.table.RemembranceTagTable
import com.azkar.data.table.RemembranceTranslationTable
import com.azkar.data.table.TagTable
import com.azkar.domain.model.HadithGrade
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
)

/**
 * Loads the bundled azkar dataset into an empty database.
 *
 * The dataset is a tab-separated resource rather than code or SQL: one line per remembrance, one
 * batch insert per table, the whole thing inside the caller's transaction. `scripts/
 * generate_seed_migration.main.kts` regenerates it — see `SEED_DATA_NOTICE.md` next to the file for
 * provenance.
 *
 * Must be called from inside a transaction, like every other write in this module.
 */
internal object SeedDataLoader {
    private const val RESOURCE = "/db/seed/muslim_data.tsv"
    private const val COMMENT_PREFIX = '#'
    private const val TAG_SEPARATOR = '|'
    private const val COLUMN_SEPARATOR = '\t'

    // The dataset ships Arabic and English only; the resource columns are the two of them plus
    // the commentary that stands in for a missing reference.
    private const val ARABIC_LOCALE = "ar"
    private const val ENGLISH_LOCALE = "en"

    private const val COLUMN_COUNT = 6

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
        val rows = readRows()
        if (rows.isEmpty()) {
            logger.warn { "Seed resource [$RESOURCE] holds no rows" }
            return SeedCounts(remembrances = 0, tags = 0)
        }

        val tagIds = insertTags(rows)
        val remembranceIds = insertRemembrances(rows)
        insertTranslations(rows, remembranceIds)
        insertExplanations(rows, remembranceIds)
        insertTagLinks(rows, remembranceIds, tagIds)

        logger.info { "Seeded ${rows.size} remembrances and ${tagIds.size} tags" }

        return SeedCounts(remembrances = rows.size, tags = tagIds.size)
    }

    private fun readRows(): List<SeedRow> =
        openResource()
            .bufferedReader(Charsets.UTF_8)
            .useLines { lines ->
                lines
                    .filterNot { it.isBlank() || it.startsWith(COMMENT_PREFIX) }
                    .map(::parseRow)
                    .toList()
            }

    private fun openResource() =
        checkNotNull(SeedDataLoader::class.java.getResourceAsStream(RESOURCE)) {
            "Seed resource [$RESOURCE] is missing from the classpath"
        }

    private fun parseRow(line: String): SeedRow {
        val columns = line.split(COLUMN_SEPARATOR)
        require(columns.size == COLUMN_COUNT) {
            "Seed row must have $COLUMN_COUNT tab separated columns, found ${columns.size}: $line"
        }
        return SeedRow(
            source = columns[0],
            arabic = columns[1],
            english = columns[2],
            explanationArabic = columns[3],
            explanationEnglish = columns[4],
            tags = columns[5].split(TAG_SEPARATOR),
        )
    }

    private fun insertTags(rows: List<SeedRow>): Map<String, Long> {
        val names = rows.flatMap { it.tags }.distinct()
        val generated =
            TagTable.batchInsert(names) { tagName ->
                this[TagTable.name] = tagName
            }
        return names.zip(generated.map { it[TagTable.id].value }).toMap()
    }

    private fun insertRemembrances(rows: List<SeedRow>): List<Long> =
        RemembranceTable
            .batchInsert(rows) { row ->
                this[RemembranceTable.sourceText] = row.source
                this[RemembranceTable.grade] = HadithGrade.UNSPECIFIED
            }.map { it[RemembranceTable.id].value }

    private fun insertTranslations(
        rows: List<SeedRow>,
        remembranceIds: List<Long>,
    ) {
        val localized = rows.localized(remembranceIds, SeedRow::arabic, SeedRow::english)
        RemembranceTranslationTable.batchInsert(localized) { row ->
            this[RemembranceTranslationTable.ownerId] = row.remembranceId
            this[RemembranceTranslationTable.localeCode] = row.localeCode
            this[RemembranceTranslationTable.text] = row.text
        }
    }

    private fun insertExplanations(
        rows: List<SeedRow>,
        remembranceIds: List<Long>,
    ) {
        ExplanationTranslationTable.batchInsert(
            rows.localized(remembranceIds, SeedRow::explanationArabic, SeedRow::explanationEnglish),
        ) { row ->
            this[ExplanationTranslationTable.ownerId] = row.remembranceId
            this[ExplanationTranslationTable.localeCode] = row.localeCode
            this[ExplanationTranslationTable.text] = row.text
        }
    }

    private fun insertTagLinks(
        rows: List<SeedRow>,
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

    /** One per locale per remembrance, in the order the resource stores them. */
    private fun List<SeedRow>.localized(
        remembranceIds: List<Long>,
        arabicText: (SeedRow) -> String,
        englishText: (SeedRow) -> String,
    ): List<LocalizedText> =
        flatMapIndexed { index, row ->
            val remembranceId = remembranceIds[index]
            listOf(
                LocalizedText(remembranceId, ARABIC_LOCALE, arabicText(row)),
                LocalizedText(remembranceId, ENGLISH_LOCALE, englishText(row)),
            )
        }

    private data class SeedRow(
        val source: String,
        val arabic: String,
        val english: String,
        val explanationArabic: String,
        val explanationEnglish: String,
        val tags: List<String>,
    )

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
