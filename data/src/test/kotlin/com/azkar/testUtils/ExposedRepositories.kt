package com.azkar.testUtils

import com.azkar.data.repo.exposed.ExposedCollectionRepository
import com.azkar.data.repo.exposed.ExposedRecitationRepository
import com.azkar.data.repo.exposed.ExposedRemembranceRepository
import com.azkar.data.repo.exposed.ExposedTagRepository
import com.azkar.data.seed.CollectionSeed
import com.azkar.data.transaction.ExposedTransactionRunner
import de.infix.testBalloon.framework.core.Test
import de.infix.testBalloon.framework.core.TestFixture
import de.infix.testBalloon.framework.core.TestSuiteScope
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/**
 * The scope a test-level fixture opens up: a suite scope whose tests receive the fixture value as
 * a parameter. Naming it keeps the harness helpers below down to one readable line.
 */
typealias TestLevelFixtureScope<Value> = TestFixture.Scope<suspend Test.ExecutionScope.(value: Value) -> Unit>

/**
 * The Exposed repositories under test, sharing one throwaway in-memory database.
 *
 * [ExposedTagRepository] holds no state, and [ExposedRemembranceRepository] needs a tag
 * repository to resolve shared tags, so one instance of each serves every test that gets a
 * harness — and sharing them keeps a test that writes through one repository and reads back
 * through the other honest about hitting the same rows. [ExposedCollectionRepository] is
 * independent of the other two: it resolves an official collection's members through its own
 * statements rather than through a repository. [ExposedRecitationRepository] is likewise
 * independent — it holds no reference to the others, and the service layer is what tells it which
 * collections exist.
 */
class ExposedRepositories(
    val database: Database,
    val tags: ExposedTagRepository,
    val remembrances: ExposedRemembranceRepository,
    val collections: ExposedCollectionRepository,
    val recitations: ExposedRecitationRepository,
)

/**
 * A per-test fixture: a freshly migrated, empty database plus the repositories built on top of
 * it. The test bodies registered inside the block receive the harness as their parameter.
 *
 * [TestDatabase.bootstrapWithH2] creates the schema and loads the seed dataset, so
 * [TestDatabase.reset] is what leaves the tables empty. Both are the expensive part though, so a
 * suite that only cares about the rows it writes itself is better off building the schema once —
 * [seededRemembrances] is that shape.
 */
fun TestSuiteScope.exposedRepositories(content: TestLevelFixtureScope<ExposedRepositories>.() -> Unit) {
    testFixture {
        val database = TestDatabase.bootstrapWithH2()
        TestDatabase.reset(database)
        repositories(database)
    } asParameterForEach content
}

/**
 * A per-test fixture layered on a database that is migrated once for the whole suite.
 *
 * The suite owns [database] and passes it in; every test then gets the same schema with the rows
 * wiped and replaced by [seedReferenceRemembrances]. Migrating once and reusing the schema keeps
 * the suite fast without letting one test's rows reach the next.
 */
fun TestSuiteScope.seededRemembrances(
    database: TestFixture<Database>,
    content: TestLevelFixtureScope<ExposedRepositories>.() -> Unit,
) {
    testFixture {
        val migrated = database()
        TestDatabase.reset(migrated)
        seedReferenceRemembrances(migrated)
        repositories(migrated)
    } asParameterForEach content
}

/**
 * A per-test fixture over a schema migrated once, with the shipped collections in place.
 *
 * The official collections are seeded *after* the reference remembrances, which is the order
 * [com.azkar.data.config.migrateToLatestSchema] uses and the order that matters: an official collection
 * resolves its members through tag rows that have to exist first. The reference tags are the
 * two the catalogue knows by name — `morning` and `evening`, stored folded while the catalogue
 * spells them `Morning` and `Evening`, which is what keeps the tag match honest — so the resulting
 * shelf has two populated collections, one favorites-backed collection and eleven empty ones.
 */
fun TestSuiteScope.seededCollections(
    database: TestFixture<Database>,
    content: TestLevelFixtureScope<ExposedRepositories>.() -> Unit,
) {
    testFixture {
        val migrated = database()
        TestDatabase.reset(migrated)
        seedReferenceRemembrances(migrated)
        transaction(migrated) { CollectionSeed.load() }
        repositories(migrated)
    } asParameterForEach content
}

private fun repositories(database: Database): ExposedRepositories {
    val transactions = ExposedTransactionRunner(database)
    val tags = ExposedTagRepository(transactions)
    return ExposedRepositories(
        database = database,
        tags = tags,
        remembrances = ExposedRemembranceRepository(tags, transactions),
        collections = ExposedCollectionRepository(transactions, tags),
        recitations = ExposedRecitationRepository(transactions),
    )
}

/**
 * The fixed starting rows the remembrance-repository suite asserts against.
 *
 * They are inserted with raw SQL so the ids are known up front and the assertions can name them.
 * Identity columns are then pushed past the hand-written values, so a later generated id can
 * never collide with one of these — including the id 9 that one of the tests inserts itself.
 */
private fun seedReferenceRemembrances(database: Database) {
    transaction(database) {
        exec(
            """
            INSERT INTO remembrance (id, source, grade)
            VALUES (1,'Bukhari','SAHIH'),
                   (2,'Muslim','HASAN'),
                   (3,'Tirmidhi','DAIF')
            """.trimIndent(),
        )
        // A translation and an explanation must both exist for a locale: the mapper keeps only
        // the locales present in the two tables.
        exec(
            """
            INSERT INTO remembrance_translation (remembrance_id, locale_code, text)
            VALUES (1,'ar','سبحان الله'),
                   (2,'ar','الحمد لله'),
                   (3,'ar','لا إله إلا الله')
            """.trimIndent(),
        )
        exec(
            """
            INSERT INTO explanation_translation (remembrance_id, locale_code, text)
            VALUES (1,'ar','شرح 1'),
                   (2,'ar','شرح 2'),
                   (3,'ar','شرح 3')
            """.trimIndent(),
        )
        // Lower case, because this insert bypasses TagImpl and so bypasses the folding every other
        // write goes through — the stored form is an invariant of the column, not of the code that
        // happens to fill it.
        exec("INSERT INTO tag (id, name) VALUES (10,'morning'), (11,'evening')")
        exec("INSERT INTO remembrance_tag (remembrance_id, tag_id) VALUES (1,10), (2,11), (3,10)")
        // The star is a column on the remembrance rather than a row of its own, so the same statement
        // that picks the rows out of a seed set is all it takes to star them.
        exec("UPDATE remembrance SET is_favorite = TRUE WHERE id IN (1, 3)")
        IDENTITY_TABLES.forEach { table -> exec("ALTER TABLE $table ALTER COLUMN id RESTART WITH 100") }
    }
}

private val IDENTITY_TABLES =
    listOf("remembrance", "remembrance_translation", "explanation_translation", "tag")
