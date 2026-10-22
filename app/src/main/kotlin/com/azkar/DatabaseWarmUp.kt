package com.azkar

import com.azkar.data.config.DatabaseManager
import io.github.oshai.kotlinlogging.KLogger
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
 * finished, so it never owned anything to hand back. A failure along the way is reported and
 * swallowed rather than propagated, because an escaping exception would take the window down
 * with it.
 *
 * That reporting goes through [onFailure], which is the method's own channel for the failure.
 * The failure happens in a background coroutine that swallows it by design, so [start] returns
 * before anyone could observe it and the caller has nothing to await: without a channel of its
 * own, the failure would only ever exist as a line in a log file. A caller that wants to know
 * asks for it by supplying a callback; the default logs it, which is what the application
 * wants, and a test that wants the failure can hand in a callback that records it.
 *
 * [warmUp] and [release] are injected to make the lifecycle independently testable without
 * accessing the process-wide [DatabaseManager]. Production uses the singleton's initialization
 * and cleanup methods.
 *
 * [logger] is injected for the same reason and by the same argument. The failure line below is
 * the one a person diagnosing a slow start looks for, and the only test that can say anything
 * about it is one that owns the logger the failure was written to. A logger reached through a
 * name is shared by the whole process: a test that attaches to it collects every line logged
 * under that name while its window is open, including the ones another test caused, and the
 * warm-up runs on [Dispatchers.IO] by default, so the line is not even written on the thread
 * that started it. The default keeps the production logger, and the name derived from this
 * class, exactly as they were.
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
    private val warmUp: suspend () -> Unit = { DatabaseManager.default.warmUp() },
    private val release: () -> Unit = { DatabaseManager.default.close() },
    private val logger: KLogger = KotlinLogging.logger {},
    private val onFailure: (Throwable) -> Unit = { failure ->
        logger.error(failure) { "Database warm-up failed" }
    },
) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val warmedUp = AtomicBoolean(false)

    private var job: Job? = null

    // Catch Throwable so no failure from the warm-up escapes into the window's scope and takes
    // the application down with it. `DatabaseManager.warmUp` throws
    // `DatabaseInitializationException`, but the warm-up is injected and a failure reaching this
    // point is by definition not the app's fault to recover from — the job swallows it, hands it
    // to `onFailure` so the caller has a channel of its own, and lets the window open regardless.
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
                    onFailure(failure)
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
