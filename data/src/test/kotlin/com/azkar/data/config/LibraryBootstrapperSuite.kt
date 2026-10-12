package com.azkar.data.config

import com.azkar.data.repo.exposed.ExposedCollectionRepository
import com.azkar.data.repo.exposed.ExposedTagRepository
import com.azkar.data.storage.CoverStorage
import com.azkar.data.table.CollectionTable
import com.azkar.data.transaction.ExposedTransactionRunner
import com.azkar.domain.repo.LibrarySeedState
import com.azkar.domain.service.CollectionService
import com.azkar.testUtils.TestDatabase
import de.infix.testBalloon.framework.core.testSuite
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.nio.file.Files

/**
 * The bootstrap is the installation's one-shot, and these tests keep it honest: a fresh home gets
 * the shipped shelf exactly once, a repeat visit must not re-write it, a database deleted behind
 * the settings file has to be rebuilt, and the covers sweep is the one part that has to run every
 * launch rather than once.
 *
 * The seed state is the in-memory double rather than a real preference store, so each test can
 * stage the gate it needs without touching the filesystem's state directory. The two rebuild tests
 * stage it the way a deleted database leaves it — the gate set, the database empty — because
 * `TestDatabase` hands back a migrated but unseeded schema, which is exactly the state a
 * recreated file is in.
 */
val LibraryBootstrapperSuite by testSuite("Library bootstrapper") {
    test("a first run writes the dataset and marks it seeded") {
        val database = TestDatabase.bootstrapWithH2()
        val seedState = InMemorySeedState()
        val transactions = ExposedTransactionRunner(database)
        val bootstrapper =
            LibraryBootstrapper(
                transactions = transactions,
                seedState = seedState,
                collectionService =
                    CollectionService(
                        ExposedCollectionRepository(transactions, ExposedTagRepository(transactions)),
                        transactions,
                    ),
                collections = ExposedCollectionRepository(transactions, ExposedTagRepository(transactions)),
                covers = CoverStorage(Files.createTempDirectory("azkar-bootstrap-covers")),
            )

        bootstrapper.bootstrap()

        assertThat(seedState.isSeeded()).isTrue()
        transaction(database) {
            assertThat(CollectionTable.selectAll().count()).isGreaterThan(0)
        }
    }

    test("a first run creates the favourites shelf and remembers its id") {
        val database = TestDatabase.bootstrapWithH2()
        val seedState = InMemorySeedState()
        val transactions = ExposedTransactionRunner(database)
        val collections = ExposedCollectionRepository(transactions, ExposedTagRepository(transactions))
        val bootstrapper =
            LibraryBootstrapper(
                transactions = transactions,
                seedState = seedState,
                collectionService = CollectionService(collections, transactions),
                collections = collections,
                covers = CoverStorage(Files.createTempDirectory("azkar-bootstrap-covers")),
            )

        bootstrapper.bootstrap()

        val stored = seedState.favoritesCollectionId()
        assertThat(stored).isNotNull()
        transaction(database) {
            val row = CollectionTable.selectAll().where { CollectionTable.id eq stored!! }.single()
            assertThat(row[CollectionTable.name]).isEqualTo("Favorites")
            assertThat(row[CollectionTable.kind].name).isEqualTo("CUSTOM")
        }
    }

    test("a second run does not rewrite the dataset") {
        val database = TestDatabase.bootstrapWithH2()
        val seedState = InMemorySeedState()
        val transactions = ExposedTransactionRunner(database)
        val collections = ExposedCollectionRepository(transactions, ExposedTagRepository(transactions))
        val bootstrapper =
            LibraryBootstrapper(
                transactions = transactions,
                seedState = seedState,
                collectionService = CollectionService(collections, transactions),
                collections = collections,
                covers = CoverStorage(Files.createTempDirectory("azkar-bootstrap-covers")),
            )

        bootstrapper.bootstrap()
        val firstCount = transaction(database) { CollectionTable.selectAll().count() }

        bootstrapper.bootstrap()
        val secondCount = transaction(database) { CollectionTable.selectAll().count() }

        assertThat(secondCount).isEqualTo(firstCount)
    }

    test("a database deleted behind the settings file is rebuilt from the shipped dataset") {
        val database = TestDatabase.bootstrapWithH2()
        val seedState = InMemorySeedState().apply { markSeeded() }
        val transactions = ExposedTransactionRunner(database)
        val collections = ExposedCollectionRepository(transactions, ExposedTagRepository(transactions))
        val bootstrapper =
            LibraryBootstrapper(
                transactions = transactions,
                seedState = seedState,
                collectionService = CollectionService(collections, transactions),
                collections = collections,
                covers = CoverStorage(Files.createTempDirectory("azkar-bootstrap-covers")),
            )

        bootstrapper.bootstrap()

        assertThat(seedState.isSeeded()).isTrue()
        transaction(database) {
            assertThat(CollectionTable.selectAll().count()).isGreaterThan(0)
        }
    }

    test("a rebuilt database gets a starred shelf rather than the id the deleted one had") {
        val database = TestDatabase.bootstrapWithH2()
        val seedState =
            InMemorySeedState().apply {
                markSeeded()
                setFavoritesCollectionId(42L)
            }
        val transactions = ExposedTransactionRunner(database)
        val collections = ExposedCollectionRepository(transactions, ExposedTagRepository(transactions))
        val bootstrapper =
            LibraryBootstrapper(
                transactions = transactions,
                seedState = seedState,
                collectionService = CollectionService(collections, transactions),
                collections = collections,
                covers = CoverStorage(Files.createTempDirectory("azkar-bootstrap-covers")),
            )

        bootstrapper.bootstrap()

        val stored = seedState.favoritesCollectionId()
        assertThat(stored).isNotEqualTo(42L)
        transaction(database) {
            val row = CollectionTable.selectAll().where { CollectionTable.id eq stored!! }.single()
            assertThat(row[CollectionTable.name]).isEqualTo("Favorites")
        }
    }

    test("an already-seeded installation still sweeps unreferenced cover files") {
        val database = TestDatabase.bootstrapWithH2()
        val seedState = InMemorySeedState()
        seedState.markSeeded()
        val coversDir = Files.createTempDirectory("azkar-bootstrap-covers")
        val orphan = coversDir.resolve("orphan.jpg")
        Files.write(orphan, byteArrayOf(1, 2, 3))
        val transactions = ExposedTransactionRunner(database)
        val collections = ExposedCollectionRepository(transactions, ExposedTagRepository(transactions))
        val bootstrapper =
            LibraryBootstrapper(
                transactions = transactions,
                seedState = seedState,
                collectionService = CollectionService(collections, transactions),
                collections = collections,
                covers = CoverStorage(coversDir),
            )

        bootstrapper.bootstrap()

        assertThat(Files.exists(orphan)).isFalse()
    }
}

/** The seed gate as an in-memory map, so a test can stage it without a preference store. */
private class InMemorySeedState : LibrarySeedState {
    private var seeded = false
    private var favoritesId: Long? = null

    override suspend fun isSeeded(): Boolean = seeded

    override suspend fun markSeeded() {
        seeded = true
    }

    override suspend fun forget() {
        seeded = false
        favoritesId = null
    }

    override suspend fun favoritesCollectionId(): Long? = favoritesId

    override suspend fun setFavoritesCollectionId(collectionId: Long) {
        favoritesId = collectionId
    }
}
