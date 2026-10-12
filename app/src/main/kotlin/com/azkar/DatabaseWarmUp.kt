package com.azkar

import com.azkar.data.config.DatabaseManager
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

private val logger = KotlinLogging.logger {}

/**
 * Coordinates database initialization and cleanup with the application's lifecycle.
 *
 * Database initialization runs on the configured coroutine dispatcher, defaulting to
 * [Dispatchers.IO], so blocking JDBC operations do not execute on the UI thread.
 *
 * When [close] is called, the initialization job is cancelled and resource cleanup is
 * registered to run when that job completes. Because coroutine cancellation does not
 * necessarily interrupt blocking JDBC operations, initialization may continue after
 * cancellation has been requested, so cleanup is deliberately deferred to the completion
 * callback rather than run inline.
 *
 * Cleanup only runs for a warm-up that ran to its end — one [close] cancelled mid-flight never
 * finished, so it never owned anything to hand back. A failure along the way is logged and
 * swallowed rather than propagated, because an escaping exception would take the window down
 * with it; the throwable rides along on the log line so the failure stays diagnosable.
 *
 * [warmUp] and [release] are injected to make the lifecycle independently testable without
 * accessing the process-wide [DatabaseManager]. Production uses the singleton's initialization
 * and cleanup methods.
 *
 * [start] and [close] are non-suspending so they can be called from application lifecycle
 * callbacks without waiting for database operations to finish. Resource cleanup may therefore
 * occur after [close] returns.
 *
 * This class is intended for a single start/close lifecycle. It does not guarantee that resource
 * cleanup has completed when [close] returns.
 */
internal class DatabaseWarmUp(
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val warmUp: suspend () -> Unit = DatabaseManager::warmUp,
    private val release: () -> Unit = DatabaseManager::close,
) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val warmedUp = AtomicBoolean(false)

    private var job: Job? = null

    // Catch Throwable so no failure from the warm-up escapes into the window's scope and takes
    // the application down with it. `DatabaseManager.warmUp` throws
    // `DatabaseInitializationException`, but the warm-up is injected and a failure reaching this
    // point is by definition not the app's fault to recover from — the job swallows it, logs it
    // with the throwable attached, and lets the window open regardless.
    @Suppress("TooGenericExceptionCaught")
    fun start() {
        check(job == null) {
            "Database warm-up has already been started"
        }

        logger.debug { "Starting database warm-up" }

        job =
            scope.launch {
                try {
                    warmUp()
                    logger.debug { "Database warm-up completed" }
                } catch (cancellation: CancellationException) {
                    // Not a failure: `close` asked for this, so it is neither logged nor counted as
                    // a warm-up that finished.
                    throw cancellation
                } catch (failure: Throwable) {
                    logger.error(failure) {
                        "Database warm-up failed"
                    }
                }

                warmedUp.set(true)
            }
    }

    override fun close() {
        val currentJob = job ?: return

        logger.debug { "Stopping database warm-up" }

        // Registered rather than called directly: a blocking JDBC warm-up is not guaranteed to
        // notice cancellation, so the job may still be running when `close` returns and the pool
        // it opened is not yet safe to close.
        currentJob.invokeOnCompletion {
            if (warmedUp.get()) {
                release()
            }
        }

        currentJob.cancel()
        scope.cancel()
        job = null
    }
}
