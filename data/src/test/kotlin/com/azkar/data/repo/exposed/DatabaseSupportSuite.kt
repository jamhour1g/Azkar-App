package com.azkar.data.repo.exposed

import ch.qos.logback.classic.Level
import com.azkar.domain.exception.CollectionNotFoundException
import com.azkar.domain.exception.PersistenceFailureException
import com.azkar.testUtils.TestDatabase
import com.azkar.testing.captureLogs
import de.infix.testBalloon.framework.core.testSuite
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/**
 * Behaviour of [guarded], the one function every repository method in this layer runs through, and
 * which therefore had no direct test of its own until now.
 *
 * A real driver failure is provoked rather than simulated. `ExposedSQLException` cannot be built
 * by hand — its constructor wants the `Transaction` and `StatementContext` list that Exposed
 * itself assembles — so a stand-in would have to be a mock of the very type under test, proving
 * only that `guarded` catches `ExposedSQLException` and nothing about whether Exposed raises one
 * for a statement that cannot run. Asking H2 for a table that is not there produces the genuine
 * article, driver cause and all.
 */
val DatabaseSupportSuite by testSuite("DatabaseSupport") {
    // One schema for the whole suite. Nothing here writes a row, so nothing has to be reset.
    val migratedDatabase = testFixture { TestDatabase.bootstrapWithH2() }
    var answer: String? = null

    test("a successful action hands its value back and logs nothing at all") {
        val logs =
            captureLogs(EXPOSED_PACKAGE_LOGGER) {
                answer = guarded(POINTLESS_OPERATION) { "answered" }
            }

        assertThat(answer).isEqualTo("answered")
        assertThat(logs.events).isEmpty()
    }

    test("a driver failure becomes a PersistenceFailureException naming the operation") {
        val database = migratedDatabase()

        assertThatThrownBy { failingRead(database) }
            .isInstanceOf(PersistenceFailureException::class.java)
            .hasMessage(FAILED_READ)
    }

    test("the driver's own failure rides along as the cause, so the statement stays diagnosable") {
        val database = migratedDatabase()

        assertThatThrownBy { failingRead(database) }
            .describedAs("The translation must not discard what the driver actually said")
            .hasCauseInstanceOf(ExposedSQLException::class.java)
    }

    test("a domain failure travels through untouched, and stays matchable by the caller") {
        assertThatThrownBy {
            guarded("Failed to look up a collection that is not there") {
                throw CollectionNotFoundException(7L)
            }
        }.describedAs("Rewrapping a domain failure would hide the type its callers match on")
            .isNotInstanceOf(PersistenceFailureException::class.java)
            .isInstanceOf(CollectionNotFoundException::class.java)
    }

    test("the failure is logged with the operation, before it is translated") {
        val database = migratedDatabase()

        val logs =
            captureLogs(EXPOSED_PACKAGE_LOGGER) {
                runCatching { failingRead(database) }
            }

        assertThat(logs.messagesAt(Level.WARN)).containsExactly(FAILED_READ)
    }
}

/**
 * The operation description the failing read passes to [guarded], spelled out once because two of
 * the tests above assert that the log line and the exception quote the same text — which is the
 * whole reason the text is a parameter rather than something [guarded] composes for itself.
 */
private const val FAILED_READ = "Failed to read a table that was never migrated"

/**
 * The operation description the succeeding action passes to [guarded].
 *
 * Named alongside [FAILED_READ] for the same reason: the text is only ever checked for *absence*,
 * so what it says does not matter — only that this exact string would have been logged had the
 * action failed.
 */
private const val POINTLESS_OPERATION = "Failed to answer a question nobody asked"

/**
 * The package every logger written here lives under.
 *
 * `DatabaseSupport.kt` holds a file-level `private val logger = KotlinLogging.logger {}`, and
 * kotlin-logging names that after the file's class — so the events land on a child of this name
 * rather than on it. Capturing at the package collects the child either way, which keeps the test
 * from pinning an internal derivation that a Kotlin or kotlin-logging upgrade could change
 * underneath it. Nothing else logs inside these blocks, so asserting on the whole event list stays
 * exact rather than becoming approximate.
 */
private const val EXPOSED_PACKAGE_LOGGER = "com.azkar.data.repo.exposed"

/**
 * Runs [guarded] over a statement H2 must reject.
 *
 * `table_that_was_never_migrated` is not in the schema, so the failure is raised by the driver
 * rather than by anything this module decided to reject — which is what makes this a test of
 * [guarded] rather than of the schema.
 */
private fun failingRead(database: Database) {
    transaction(database) {
        val returned = guarded(FAILED_READ) { exec("SELECT * FROM table_that_was_never_migrated") }
        error("The statement was expected to fail, and it came back with $returned")
    }
}
