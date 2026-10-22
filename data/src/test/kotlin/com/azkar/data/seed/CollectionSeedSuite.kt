package com.azkar.data.seed

import com.azkar.data.repo.exposed.ExposedCollectionRepository
import com.azkar.data.repo.exposed.ExposedTagRepository
import com.azkar.data.table.TagTable
import com.azkar.data.transaction.ExposedTransactionRunner
import com.azkar.domain.model.CollectionKind
import com.azkar.domain.model.impl.RemembranceCollectionImpl
import com.azkar.testUtils.TestDatabase
import de.infix.testBalloon.framework.core.testSuite
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/**
 * The collection seed, and the two things about it that can fail silently.
 *
 * The seed writes a collection's membership out as rows rather than deriving it, so a catalogue tag
 * that no longer matches anything in the dataset produces a collection that loads cleanly, reports
 * no error, and is permanently and quietly empty. The tag checks here exist to make that loud —
 * they are the same guards the derived design needed, and they are still the only thing standing
 * between a renamed tag and a shelf of empty cards.
 *
 * The other half is that the seed runs exactly once, behind the bootstrap's gate. It is therefore an
 * insert, not an upsert: there is no catalogue-owned column left to restore, because every column is
 * the user's now. What it must still guarantee is that running it again is harmless.
 */
val CollectionSeedSuite by testSuite("Seeding the official collections") {
    test("every shipped collection is present, official, and fully described") {
        withSeed { seeded ->
            val official = seeded.collections.findAll().filter { it.kind == CollectionKind.OFFICIAL }

            assertThat(official.map { it.name })
                .containsExactlyInAnyOrderElementsOf(OfficialCollectionCatalogue.entries.map { it.name })
            assertThat(official).allSatisfy { collection ->
                assertThat(collection.name).isNotBlank()
                assertThat(collection.description).isNotBlank()
                assertThat(collection.iconKey).isNotBlank()
            }
        }
    }

    test("a shipped collection starts unpinned, off the home rail, and with no cover") {
        withSeed { seeded ->
            val morning = seeded.collectionNamed(OfficialCollectionCatalogue.entries.first())

            assertThat(morning.isPinned).isFalse()
            assertThat(morning.isOnHome).isFalse()
            assertThat(morning.coverImagePath).isNull()
            // Null rather than a timestamp: a collection nobody has opened has not been visited, and
            // a default here would make the recently-opened ordering report a visit at seed time.
            assertThat(morning.lastOpenedAt).isNull()
        }
    }

    test("every shipped collection resolves at least one remembrance") {
        withSeed { seeded ->
            val empty =
                OfficialCollectionCatalogue.entries.filter { entry ->
                    seeded.collectionNamed(entry).members.isEmpty()
                }

            // The guard the derived-membership design needed and cannot get from the DDL: a tag
            // renamed in `muslim_data.tsv` without the catalogue following leaves a collection that
            // loads fine, reports no error, and is permanently empty.
            assertThat(empty.map { it.name })
                .describedAs("A shipped collection with no members means its tag matched nothing")
                .isEmpty()
        }
    }

    test("every catalogue tag is a tag the seed dataset actually uses") {
        withSeed { seeded ->
            val datasetTags = tagNamesIn(seeded.database)
            val missing =
                OfficialCollectionCatalogue.entries
                    .mapNotNull { it.tagName }
                    .filterNot { it.lowercase() in datasetTags }

            assertThat(missing).describedAs("A catalogue tag no row carries can never resolve").isEmpty()
        }
    }

    test("no two catalogue entries claim the same tag") {
        val entries = OfficialCollectionCatalogue.entries

        assertThat(entries.mapNotNull { it.tagName }).doesNotHaveDuplicates()
    }

    test("every entry is tag-driven, because the starred shelf is not shipped any more") {
        val entries = OfficialCollectionCatalogue.entries

        assertThat(entries.mapNotNull { it.tagName })
            .describedAs("The starred shelf is the user's, so it cannot be a catalogue entry")
            .hasSize(entries.size)
    }

    test("a shipped collection carries its own tag as a label") {
        withSeed { seeded ->
            val labels =
                OfficialCollectionCatalogue.entries.associate { entry ->
                    entry.name to seeded.collectionNamed(entry).tags.map { it.name.lowercase() }
                }

            // The same tag both resolves the members and names the shelf, which is what lets the
            // card derive its icon without the catalogue carrying an icon rule per collection.
            labels.forEach { (name, tags) ->
                assertThat(tags)
                    .describedAs("$name should be labelled with the tag it filters by")
                    .hasSize(1)
            }
        }
    }

    test("seeding again is a no-op rather than a second set of rows") {
        withSeed { seeded ->
            val before = seeded.collections.findAll().map { it.id to it.updatedAt }

            reseed(seeded.database)

            assertThat(seeded.collections.findAll().map { it.id to it.updatedAt }).isEqualTo(before)
        }
    }

    test("seeding again never overwrites what the user changed") {
        withSeed { seeded ->
            val morning = seeded.collectionNamed(OfficialCollectionCatalogue.entries.first())
            // Tamper with everything at once, because that is what a user editing a shipped shelf
            // looks like from the seed's side: the name, the description, the icon, the pin and the
            // cover are all theirs now, and the seed has no business taking any of them back.
            transaction(seeded.database) {
                exec(
                    "UPDATE collection SET name = 'Mine', description = 'Mine', icon_key = 'star', " +
                        "pinned_at = CURRENT_TIMESTAMP, cover_image_path = 'cover.png' WHERE id = ${morning.id}",
                )
            }

            reseed(seeded.database)

            val after = requireNotNull(seeded.collections.findById(morning.id))
            assertThat(after.name).isEqualTo("Mine")
            assertThat(after.description).isEqualTo("Mine")
            assertThat(after.iconKey).isEqualTo("star")
            assertThat(after.isPinned).isTrue()
            assertThat(after.coverImagePath).isEqualTo("cover.png")
        }
    }

    test("seeding leaves a custom collection entirely alone") {
        withSeed { seeded ->
            val mineId =
                requireNotNull(
                    seeded.collections
                        .save(RemembranceCollectionImpl(name = "Mine", description = "My own shelf"))
                        .id,
                )

            reseed(seeded.database)

            val reloaded = requireNotNull(seeded.collections.findById(mineId))
            assertThat(reloaded.name).isEqualTo("Mine")
            assertThat(reloaded.description).isEqualTo("My own shelf")
            assertThat(reloaded.kind).isEqualTo(CollectionKind.CUSTOM)
            // A custom collection gets no labels from the seed, because the seed only labels what it
            // also seeded. Inventing one here would hand the user a label they never chose.
            assertThat(reloaded.tags).isEmpty()
        }
    }
}

/** The seeded database plus a collection repository over it, which is all this suite needs. */
private class SeededCollections(
    val database: Database,
    val collections: ExposedCollectionRepository,
) {
    /**
     * The collection a catalogue entry produced.
     *
     * Matched by name rather than by a key of our own, because there is no longer a key to match:
     * the seed's whole point was to stop carrying an identity that has to be recognised across
     * launches.
     */
    suspend fun collectionNamed(entry: OfficialCollectionCatalogue.Entry) =
        requireNotNull(collections.findAll().singleOrNull { it.name == entry.name }) {
            "The seed did not produce ${entry.name}"
        }
}

/**
 * Runs [block] against a database of its own, with the real seed dataset already in it.
 *
 * The dataset goes first for the same reason the bootstrapper loads it first: the collections
 * resolve their membership through the tags the dataset creates, so a collection seeded into a
 * database with no tags in it would come up permanently empty.
 *
 * The database is per test rather than per suite because some of these tests write — one renames a
 * shipped collection, another adds one — and a test that writes has to be writing to a database
 * nothing else can see.
 */
private suspend fun withSeed(block: suspend (SeededCollections) -> Unit) {
    val database = TestDatabase.bootstrapWithH2()
    transaction(database) { SeedDataLoader.load() }
    transaction(database) { CollectionSeed.load() }
    val transactions = ExposedTransactionRunner(database)
    block(SeededCollections(database, ExposedCollectionRepository(transactions, ExposedTagRepository(transactions))))
}

/** Runs the seed the way a second call to the bootstrapper would. */
private fun reseed(database: Database) {
    transaction(database) { CollectionSeed.load() }
}

/** The lowercased names of every tag row, which is the set a catalogue tag can resolve against. */
private fun tagNamesIn(database: Database): Set<String> =
    transaction(database) {
        TagTable.selectAll().mapTo(mutableSetOf()) { it[TagTable.name].lowercase() }
    }
