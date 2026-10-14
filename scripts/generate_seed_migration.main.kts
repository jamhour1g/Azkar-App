#!/usr/bin/env -S kotlinc -script
@file:DependsOn(
    "org.xerial:sqlite-jdbc:3.53.4.0",
)

/*
 * Regenerates `data/src/main/resources/db/seed/muslim_data.tsv` from the public "Muslim Data Azkars"
 * SQLite dataset (Apache-2.0). `SEED_DATA_NOTICE.md` next to the generated file pins the commit and
 * the license.
 *
 * Run it from the repository root, because the output path is resolved against the working
 * directory:
 *
 *     kotlinc -script scripts/generate_seed_migration.main.kts
 *
 * The shebang needs `env -S` (coreutils 8.30+) to pass `-script` as a separate argument, and the
 * file needs its execute bit, so `./scripts/generate_seed_migration.main.kts` works too.
 *
 * The extension has to be `.main.kts`, because only that one loads the Maven script definition from
 * `kotlin-main-kts.jar`, which reads the annotation; a plain `.kts` gets a definition with no
 * resolver and fails with `unresolved reference 'DependsOn'`. The SQLite JDBC jar is fetched from
 * Maven Central on the first run. The generator is a synchronous command-line tool, so it uses the
 * JDK HTTP client directly rather than bridging asynchronous networking with a blocking coroutine.
 */
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Path
import java.sql.DriverManager
import java.sql.ResultSet
import java.time.Duration
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteIfExists
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.outputStream
import kotlin.io.path.writeText

private val DATASET_URL =
    "https://raw.githubusercontent.com/my-prayers/muslim-data-android/f7968b8765bfa23706751edadc3fed2fa17a9864" +
        "/muslim-data/src/main/assets/database/muslim_db_v2.5.1.db"

private val OUTPUT = Path.of("data/src/main/resources/db/seed/muslim_data.tsv")

private val SOURCE = "Muslim Data Azkars"
private val DATASET_TAG = "Muslim Data"
private val COMMIT = "f7968b8765bfa23706751edadc3fed2fa17a9864"
private val LICENSE = "Apache-2.0"

private val COLUMN = "\t"
private val TAG = "|"
private val MIN_ENGLISH_LENGTH = 3
private val NO_REFERENCE_AR = "المرجع غير متاح في بيانات المصدر."
private val NO_REFERENCE_EN = "Reference unavailable in source dataset."

private val MORNING_HINTS = listOf("morning", "waking", "sunrise")
private val EVENING_HINTS = listOf("evening", "night", "sleep")

/** `(?U)` widens `\s` to Unicode whitespace, which it does not cover on the JVM otherwise. */
private val WHITESPACE = Regex("(?U)\\s+")

private val HEADER =
    listOf(
        "# Seed data sourced from public web dataset.",
        "# Source: $SOURCE",
        "# Repository: https://github.com/my-prayers/muslim-data-android",
        "# Commit: $COMMIT",
        "# License: $LICENSE",
        "# Languages imported: ar, en",
        "#",
        "# Tab separated, one remembrance per line. Columns:",
        "#   1. source",
        "#   2. arabic text",
        "#   3. english text",
        "#   4. arabic explanation",
        "#   5. english explanation",
        "#   6. tags, '$TAG' separated",
        "#",
        "# Tags are lower case because that is the form `tag.name` stores them in — see Tag.fold.",
        "# The capitalised form is a presentation concern, applied when a tag is displayed.",
        "#",
        "# Every remembrance is stored with grade UNSPECIFIED, and an explanation falls back to a",
        "# placeholder when the source dataset carries no reference for it. Loaded in one",
        "# transaction by com.azkar.data.seed.SeedDataLoader.",
    )

private val SELECT_REMEMBRANCES =
    """
    SELECT
      item._id AS id,
      COALESCE(category_translation.category_name, 'General') AS category,
      COALESCE(chapter_translation.chapter_name, 'General') AS chapter,
      COALESCE(arabic.item_translation, item.item) AS arabic,
      english.item_translation AS english,
      COALESCE(references_ar.text, '') AS reference_ar,
      COALESCE(references_en.text, '') AS reference_en
    FROM azkar_item item
    JOIN azkar_chapter chapter ON chapter._id = item.chapter_id
    JOIN azkar_category category ON category._id = chapter.category_id
    LEFT JOIN azkar_chapter_translation chapter_translation
      ON chapter_translation.chapter_id = chapter._id AND chapter_translation.language = 'en'
    LEFT JOIN azkar_category_translation category_translation
      ON category_translation.category_id = category._id AND category_translation.language = 'en'
    LEFT JOIN azkar_item_translation arabic
      ON arabic.item_id = item._id AND arabic.language = 'ar'
    LEFT JOIN azkar_item_translation english
      ON english.item_id = item._id AND english.language = 'en'
    LEFT JOIN (
      SELECT reference.item_id, GROUP_CONCAT(translation.reference, ' | ') AS text
      FROM azkar_reference reference
      JOIN azkar_reference_translation translation
        ON translation.reference_id = reference._id AND translation.language = 'ar'
      GROUP BY reference.item_id
    ) references_ar ON references_ar.item_id = item._id
    LEFT JOIN (
      SELECT reference.item_id, GROUP_CONCAT(translation.reference, ' | ') AS text
      FROM azkar_reference reference
      JOIN azkar_reference_translation translation
        ON translation.reference_id = reference._id AND translation.language = 'en'
      GROUP BY reference.item_id
    ) references_en ON references_en.item_id = item._id
    WHERE english.item_translation IS NOT NULL
    ORDER BY item._id
    """

private data class Remembrance(
    val id: Long,
    val category: String,
    val chapter: String,
    val arabic: String,
    val english: String,
    val referenceAr: String,
    val referenceEn: String,
)

private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build()

private fun HttpClient.download(url: String, into: Path) {
    val request =
        HttpRequest
            .newBuilder(URI.create(url))
            .timeout(Duration.ofMinutes(5))
            .GET()
            .build()
    val response = send(request, HttpResponse.BodyHandlers.ofInputStream())
    response.body().use { input ->
        check(response.statusCode() in 200..299) { "Dataset download failed with HTTP ${response.statusCode()}" }
        into.outputStream().use { output -> input.copyTo(output) }
    }
}

private fun ResultSet.remembrances(): Sequence<Remembrance> = generateSequence { if (next()) asRemembrance() else null }

private fun ResultSet.asRemembrance() =
    Remembrance(
        id = getLong("id"),
        category = getString("category").cleaned(),
        chapter = getString("chapter").cleaned(),
        arabic = getString("arabic").cleaned(),
        english = getString("english").cleaned(),
        referenceAr = getString("reference_ar").cleaned(),
        referenceEn = getString("reference_en").cleaned(),
    )

private fun String?.cleaned() = orEmpty().replace("\u200e", "").replace("\u200f", "").replace(WHITESPACE, " ").trim()

private fun String.isArabic() = any { it in '؀'..'ۿ' }

private fun Remembrance.tags() =
    buildList {
        add(DATASET_TAG)
        if (category.isNotEmpty()) {
            add(category)
        }
        if (mentionsAnyOf(MORNING_HINTS)) {
            add("Morning")
        }
        if (mentionsAnyOf(EVENING_HINTS)) {
            add("Evening")
        }
    }

private val Remembrance.searchable get() = "$category $chapter".lowercase()

private fun Remembrance.mentionsAnyOf(hints: List<String>) = hints.any(searchable::contains)

private fun Remembrance.render() =
    listOf(
        "$SOURCE #$id - $chapter",
        arabic,
        english,
        referenceAr.ifEmpty { NO_REFERENCE_AR },
        referenceEn.ifEmpty { NO_REFERENCE_EN },
        // Folded, because that is the form `tag.name` stores: the seed writes straight into the
        // column without going through TagImpl, so the generator is the place it has to happen.
        tags().joinToString(TAG).lowercase(),
    ).joinToString(COLUMN)

private fun render(remembrances: List<Remembrance>) =
    (HEADER + remembrances.map(Remembrance::render)).joinToString(separator = "\n", postfix = "\n")

client.use { http ->
    val dataset = createTempFile(suffix = ".db")
    try {
        println("Downloading the source dataset")
        http.download(DATASET_URL, dataset)

        val remembrances =
            DriverManager.getConnection("jdbc:sqlite:${dataset.toAbsolutePath().invariantSeparatorsPathString}")
                .use { connection ->
                    connection.prepareStatement(SELECT_REMEMBRANCES).use { statement ->
                        statement.executeQuery().use { records ->
                            records
                                .remembrances()
                                .filter { it.arabic.isArabic() && it.english.length >= MIN_ENGLISH_LENGTH }
                                .distinctBy(Remembrance::arabic)
                                .toList()
                        }
                    }
                }

        check(remembrances.isNotEmpty()) { "The source dataset yielded no remembrances" }

        OUTPUT.parent.createDirectories()
        OUTPUT.writeText(render(remembrances))

        println("Wrote ${remembrances.size} remembrances to ${OUTPUT.toAbsolutePath()}")
    } finally {
        dataset.deleteIfExists()
    }
}
