package com.azkar.data.config

import ch.qos.logback.classic.Level
import com.azkar.data.seed.SeedDataLoader
import com.azkar.data.table.ExplanationTranslationTable
import com.azkar.data.table.RemembranceTable
import com.azkar.data.table.RemembranceTranslationTable
import com.azkar.data.table.TagTable
import com.azkar.testUtils.TestDatabase
import com.azkar.testing.captureLogs
import de.infix.testBalloon.framework.core.testSuite
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.Locale

/**
 * The bundled dataset is loaded by a resource file rather than by a migration, so nothing else in
 * the suite would notice if it stopped loading. The counts here are deliberately coarse: the
 * point is that the rows landed at all, not that the dataset matches a snapshot.
 */
val SeedDataSuite by testSuite("Seed data") {
    // Migrated and seeded once for the whole suite and deliberately never reset — the seeded rows
    // are precisely what is under test, so every test in here reads the same database. The dataset
    // is loaded by the bootstrapper rather than by the migration, so a suite that wants it has to
    // ask for it the way the app does.
    val migrated =
        testFixture {
            TestDatabase.bootstrapWithH2().also { database ->
                transaction(database) { SeedDataLoader.load() }
            }
        }

    migrated asParameterForAll {
        test("seeds at least 250 remembrances, each with Arabic and English text") { database ->
            transaction(database) {
                assertThat(RemembranceTable.selectAll().count())
                    .describedAs("Total seeded remembrances")
                    .isGreaterThanOrEqualTo(250)
            }
            SEEDED_LOCALES.forEach { locale ->
                assertThat(countTranslations(database, locale))
                    .describedAs("Remembrances seeded with a $locale translation")
                    .isGreaterThanOrEqualTo(250)
                assertThat(countExplanations(database, locale))
                    .describedAs("Remembrances seeded with a $locale explanation")
                    .isGreaterThanOrEqualTo(250)
            }
        }

        test("seeds exactly one 'muslim data' tag") { database ->
            transaction(database) {
                val matches = TagTable.selectAll().where { TagTable.name eq "muslim data" }

                assertThat(matches.count()).isEqualTo(1)
                assertThat(matches.single()[TagTable.name])
                    .describedAs("The seed writes the tag folded, as the column stores it")
                    .isEqualTo("muslim data")
            }
        }
    }

    // Deliberately outside the shared fixture: the fixture has already loaded the dataset by the
    // time a test starts, and this wants to watch the very call that does it.
    test("a first start says how much of the dataset it just loaded") {
        val database = TestDatabase.bootstrapWithH2()
        val logs =
            captureLogs(SEED_LOADER_LOGGER) {
                transaction(database) { SeedDataLoader.load() }
            }

        // This is the one line the shipped app prints on a user's very first launch, so it is worth
        // a test: the dataset is a resource file, and a resource file can silently stop parsing.
        val milestones = logs.messagesAt(Level.INFO)
        assertThat(milestones).hasSize(1)
        assertThat(milestones.single())
            .describedAs("The seeding milestone, which names both counts")
            .matches("""Seeded \d+ remembrances and \d+ tags""")
    }
}

/** The logger [SeedDataLoader] writes to, named by hand so the test cannot drift from the class. */
private const val SEED_LOADER_LOGGER = "com.azkar.data.seed.SeedDataLoader"

/** The two locales the seed is expected to cover. */
private val SEEDED_LOCALES = listOf("ar", "en")

private fun countTranslations(
    database: Database,
    locale: String,
): Long =
    transaction(database) {
        RemembranceTranslationTable
            .selectAll()
            .where { RemembranceTranslationTable.localeCode eq locale.seedLanguageTag() }
            .count()
    }

private fun countExplanations(
    database: Database,
    locale: String,
): Long =
    transaction(database) {
        ExplanationTranslationTable
            .selectAll()
            .where { ExplanationTranslationTable.localeCode eq locale.seedLanguageTag() }
            .count()
    }

/** The seed stores locales as language tags, so `"ar"` has to become `"ar"`-as-a-tag. */
private fun String.seedLanguageTag(): String = Locale.forLanguageTag(this).toLanguageTag()
