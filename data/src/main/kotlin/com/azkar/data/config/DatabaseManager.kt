package com.azkar.data.config

import com.azkar.data.config.migrateToLatestSchema
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.jdbc.Database
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Owns the process-wide Exposed [Database] and its connection pool.
 *
 * On first access it opens a Hikari pool against the JDBC URL from [DataSourceConfig] and hands
 * the `DataSource` to Exposed, so connections are pooled — a bare URL makes Exposed open a
 * fresh physical connection for every single transaction, which is ruinous for the AUTO_SERVER
 * H2 file database.
 *
 * [migrateToLatestSchema] then creates the schema and loads the seed data through that same pool,
 * before the [Database] is published, so the first query can never land on an unmigrated
 * database.
 *
 * The manager is closeable and re-openable rather than one-shot: [close] drops both handles so
 * the next [database] call reconnects, which is what lets the window teardown release the pool
 * without a restart being needed afterward.
 *
 * It is a class rather than an `object` so that it can be given the logger it writes to: a
 * logger reached through a name is shared by the whole process, and a test that wants to assert
 * on the line the pool emits has to be able to own the logger behind it. [default] is the one
 * the application uses.
 */
class DatabaseManager(
    private val logger: KLogger = KotlinLogging.logger {},
) : AutoCloseable {
    private val lock = ReentrantLock()

    private var pool: HikariDataSource? = null
    private var database: Database? = null

    /** Returns the shared Exposed [Database], opening and migrating it on first use. */
    internal fun database(): Database =
        lock.withLock {
            database ?: open().also { database = it }
        }

    /**
     * Connects and migrates ahead of any real work, so the first UI query does not pay for
     * pool start-up and for creating the schema.
     */
    fun warmUp() {
        database()
    }

    override fun close(): Unit =
        lock.withLock {
            val currentPool = pool

            // Clear the handles before closing so they remain cleared if close fails.
            pool = null
            database = null

            currentPool?.let {
                if (!it.isClosed) {
                    logger.debug { "Closing database connection pool" }
                    it.close()
                }
            }
        }

    override fun toString(): String =
        lock.withLock {
            "DatabaseManager{open=${database != null}}"
        }

    // Catch Throwable to ensure the connection pool is closed even if initialization fails
    // with an Error. If cleanup also fails, preserve the original failure and attach the
    // cleanup failure as suppressed rather than masking the cause of initialization failure.
    @Suppress("TooGenericExceptionCaught")
    private fun open(): Database {
        logger.debug { "Opening application database" }

        val dataSource =
            HikariDataSource(
                poolConfig(DataSourceConfig.JDBC_URL),
            )

        return try {
            val connectedDatabase =
                Database
                    .connect(
                        dataSource,
                        {},
                        ExposedDatabaseConfig.INSTANCE,
                    ).apply {
                        migrateToLatestSchema()
                    }

            pool = dataSource
            logger.info { "Application database connected and schema migration completed" }
            connectedDatabase
        } catch (failure: Throwable) {
            try {
                dataSource.close()
            } catch (closeFailure: Throwable) {
                failure.addSuppressed(closeFailure)
            }

            logger.error(failure) {
                "Unable to open the application database"
            }

            throw failure
        }
    }

    companion object {
        /**
         * The manager the application uses.
         *
         * One instance owns the process-wide pool, and the window's teardown closes the same one,
         * so the default is shared rather than constructed afresh at every call site.
         */
        val default: DatabaseManager by lazy { DatabaseManager() }

        /**
         * Builds the pool configuration.
         */
        internal fun poolConfig(
            jdbcUrl: String,
            idleTimeout: Duration = 10_000L.milliseconds,
            connectionTimeout: Duration = 20_000L.milliseconds,
        ): HikariConfig =
            HikariConfig().apply {
                this.jdbcUrl = jdbcUrl
                driverClassName = "org.h2.Driver"
                poolName = "azkar-h2"
                minimumIdle = 1
                maximumPoolSize = 4
                this.idleTimeout = idleTimeout.inWholeMilliseconds
                this.connectionTimeout = connectionTimeout.inWholeMilliseconds
                // Exposed drives commit/rollback itself around every transaction.
                isAutoCommit = false
            }
    }
}
